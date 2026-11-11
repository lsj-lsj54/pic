package com.lsj.demo.websocket;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lsj.demo.model.dto.websocket.PictureEditRequestMessage;
import com.lsj.demo.model.dto.websocket.PictureEditResponseMessage;
import com.lsj.demo.model.entity.User;
import com.lsj.demo.model.enums.PictureEditActionEnum;
import com.lsj.demo.model.enums.PictureEditMessageTypeEnum;
import com.lsj.demo.model.vo.UserVO;
import com.lsj.demo.service.UserService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 图片编辑 WebSocket 处理器
 * <p>
 * 职责：
 * <ul>
 *     <li>维护每张图片的「当前编辑者」与「已连接会话」；</li>
 *     <li>把编辑动作广播给同一张图片的其他在线用户；</li>
 *     <li>记录编辑历史，供中途加入的用户同步到最新状态。</li>
 * </ul>
 * 消息进入后不是直接处理，而是丢给 Disruptor 的 RingBuffer 异步消费（见
 * {@link PictureEditEventProducer} / {@link PictureEditEventWorkHandler}），
 * 真正落地的编辑逻辑由前端把动作应用到图片上。
 *
 * @author lsj
 */
@Slf4j
@Component
public class PictureEditHandler extends TextWebSocketHandler {

    /**
     * 每张图片的编辑状态，key: pictureId, value: 当前正在编辑的用户 ID
     */
    private final Map<Long, Long> pictureEditingUsers = new ConcurrentHashMap<>();

    /**
     * 保存所有连接的会话，key: pictureId, value: 用户会话集合
     */
    private final Map<Long, Set<WebSocketSession>> pictureSessions = new ConcurrentHashMap<>();

    /**
     * 某张图片的编辑记录，key: pictureId, value: 编辑记录，
     * 用于某个用户中途加入编辑的时候看到最新的编辑记录
     */
    private final Map<Long, List<TextMessage>> pictureEditRecodes = new ConcurrentHashMap<>();

    @Resource
    private UserService userService;

    /**
     * 使用 Spring 注入的 ObjectMapper 序列化，与 REST 接口保持一致的 Long 精度处理
     * （雪花 id 会序列化成字符串，避免前端 JSON.parse 丢失精度）。
     */
    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private PictureEditEventProducer pictureEditEventProducer;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        // 保存会话到集合中
        Set<WebSocketSession> sessionSet = pictureSessions.computeIfAbsent(pictureId, k -> ConcurrentHashMap.newKeySet());
        sessionSet.add(session);

        // 广播「加入编辑」通知
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        pictureEditResponseMessage.setMessage(String.format("%s加入编辑", user.getUserName()));
        pictureEditResponseMessage.setUser(UserVO.entityToVo(user));
        broadcastToPicture(pictureId, getTextMessage(pictureEditResponseMessage));

        // 把历史编辑记录发给新加入的用户，保证其看到最新的编辑结果
        broadcastToOneUser(pictureId, pictureEditRecodes.get(pictureId), session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 将消息解析为 PictureEditRequestMessage，并交给 Disruptor 异步处理
        PictureEditRequestMessage pictureEditRequestMessage = JSONUtil.toBean(message.getPayload(), PictureEditRequestMessage.class);
        Map<String, Object> attributes = session.getAttributes();
        User user = (User) attributes.get("user");
        Long pictureId = (Long) attributes.get("pictureId");
        pictureEditEventProducer.publishEvent(pictureEditRequestMessage, session, user, pictureId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        if (pictureId == null) {
            return;
        }
        try {
            // 断开的用户是当前编辑者时，释放编辑锁并广播退出
            if (user != null && pictureEditingUsers.remove(pictureId, user.getId())) {
                PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
                pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
                pictureEditResponseMessage.setMessage(String.format("%s退出编辑图片", user.getUserName()));
                pictureEditResponseMessage.setUser(UserVO.entityToVo(user));
                broadcastToPicture(pictureId, getTextMessage(pictureEditResponseMessage));
            }
        } catch (Exception e) {
            log.error("PictureEditHandler#afterConnectionClosed {}", ExceptionUtil.getRootCauseMessage(e));
        } finally {
            Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
            if (sessionSet != null) {
                sessionSet.remove(session);
            }
            cleanupIfNoSession(pictureId);
        }
    }

    /**
     * 处理进入编辑的消息：没有用户正在编辑时，当前用户成为编辑者；否则拒绝。
     */
    public void handleEnterEditMessage(PictureEditContext pictureEditContext) throws Exception {
        User user = pictureEditContext.getUser();
        Long pictureId = pictureEditContext.getPictureId();
        // putIfAbsent 原子地抢占编辑权，避免两个用户同时进入编辑
        Long previous = pictureEditingUsers.putIfAbsent(pictureId, user.getId());
        if (previous == null) {
            // 成功成为编辑者，广播进入编辑
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ENTER_EDIT.getValue());
            pictureEditResponseMessage.setMessage(String.format("%s开始编辑图片", user.getUserName()));
            pictureEditResponseMessage.setUser(UserVO.entityToVo(user));
            broadcastToPicture(pictureId, getTextMessage(pictureEditResponseMessage));
        } else if (!previous.equals(user.getId())) {
            // 已有其他用户正在编辑，通知当前用户
            handleEditErrorMessage(user, pictureId, pictureEditContext.getSession());
        }
    }

    /**
     * 处理编辑动作：只有当前编辑者才能执行，并把动作广播给其他用户。
     */
    public void handleEditActionMessage(PictureEditContext pictureEditContext) throws Exception {
        PictureEditRequestMessage requestMessage = pictureEditContext.getRequestMessage();
        WebSocketSession session = pictureEditContext.getSession();
        User user = pictureEditContext.getUser();
        Long pictureId = pictureEditContext.getPictureId();
        Long editingUserId = pictureEditingUsers.get(pictureId);
        String editAction = requestMessage.getEditAction();
        PictureEditActionEnum actionEnum = PictureEditActionEnum.getEnumByValue(editAction);
        if (actionEnum == null) {
            return;
        }
        // 确认是当前编辑者
        if (editingUserId != null && editingUserId.equals(user.getId())) {
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EDIT_ACTION.getValue());
            pictureEditResponseMessage.setMessage(String.format("%s执行%s", user.getUserName(), actionEnum.getText()));
            pictureEditResponseMessage.setEditAction(editAction);
            pictureEditResponseMessage.setUser(UserVO.entityToVo(user));
            // 广播给除了当前客户端之外的其他用户，否则会造成重复编辑
            broadcastToPicture(pictureId, getTextMessage(pictureEditResponseMessage), session);

            // 记录编辑动作，供中途加入的用户同步
            PictureEditResponseMessage record = new PictureEditResponseMessage();
            record.setType(PictureEditMessageTypeEnum.EDIT_ACTION.getValue());
            record.setEditAction(editAction);
            List<TextMessage> list = pictureEditRecodes.computeIfAbsent(pictureId, k -> new CopyOnWriteArrayList<>());
            list.add(getTextMessage(record));
        }
    }

    /**
     * 处理退出编辑的消息：释放编辑锁，广播退出通知，并移除会话。
     */
    public void handleExitEditMessage(PictureEditContext pictureEditContext) {
        User user = pictureEditContext.getUser();
        Long pictureId = pictureEditContext.getPictureId();
        try {
            // 仅当前编辑者退出时才广播并释放编辑锁
            if (pictureEditingUsers.remove(pictureId, user.getId())) {
                PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
                pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
                pictureEditResponseMessage.setMessage(String.format("%s退出编辑图片", user.getUserName()));
                pictureEditResponseMessage.setUser(UserVO.entityToVo(user));
                broadcastToPicture(pictureId, getTextMessage(pictureEditResponseMessage));
            }
            Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
            if (sessionSet != null) {
                sessionSet.remove(pictureEditContext.getSession());
            }
        } catch (Exception e) {
            log.error("PictureEditHandler#handleExitEditMessage {}", ExceptionUtil.getRootCauseMessage(e));
        } finally {
            cleanupIfNoSession(pictureId);
        }
    }

    /**
     * 发送「已有其他用户正在编辑」的错误提示
     */
    private void handleEditErrorMessage(User user, Long pictureId, WebSocketSession sendSession) throws Exception {
        Long editUserId = pictureEditingUsers.get(pictureId);
        User editUser = editUserId == null ? null : userService.getById(editUserId);
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ERROR.getValue());
        pictureEditResponseMessage.setMessage(String.format("操作失败 %s 正在操作",
                editUser == null ? "其他用户" : editUser.getUserName()));
        pictureEditResponseMessage.setUser(UserVO.entityToVo(user));
        if (sendSession != null && sendSession.isOpen()) {
            sendSession.sendMessage(getTextMessage(pictureEditResponseMessage));
        }
    }

    /**
     * 广播给同一张图片的所有会话
     */
    private void broadcastToPicture(Long pictureId, TextMessage textMessage, WebSocketSession excludeSession) throws Exception {
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if (CollUtil.isNotEmpty(sessionSet)) {
            for (WebSocketSession session : sessionSet) {
                // 排除掉的 session 不发送
                if (excludeSession != null && excludeSession.equals(session)) {
                    continue;
                }
                if (session.isOpen()) {
                    session.sendMessage(textMessage);
                }
            }
        }
    }

    /**
     * 全部广播
     */
    private void broadcastToPicture(Long pictureId, TextMessage textMessage) throws Exception {
        broadcastToPicture(pictureId, textMessage, null);
    }

    /**
     * 给指定用户发送历史消息
     */
    private void broadcastToOneUser(Long pictureId, List<TextMessage> textMessages, WebSocketSession sendSession) throws Exception {
        if (CollUtil.isEmpty(textMessages) || sendSession == null || !sendSession.isOpen()) {
            return;
        }
        for (TextMessage textMessage : textMessages) {
            sendSession.sendMessage(textMessage);
        }
    }

    /**
     * 序列化响应消息为 TextMessage
     */
    private TextMessage getTextMessage(PictureEditResponseMessage pictureEditResponseMessage) throws JsonProcessingException {
        return new TextMessage(objectMapper.writeValueAsString(pictureEditResponseMessage));
    }

    /**
     * 对应图片没人连接时清理编辑状态，防止内存泄露
     */
    private void cleanupIfNoSession(Long pictureId) {
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if (sessionSet == null || sessionSet.isEmpty()) {
            pictureSessions.remove(pictureId);
            pictureEditRecodes.remove(pictureId);
            pictureEditingUsers.remove(pictureId);
        }
    }

    /**
     * 图片编辑上下文，把请求消息、会话、用户、图片 id 打包传递
     */
    @Data
    @AllArgsConstructor
    public static class PictureEditContext {
        private PictureEditRequestMessage requestMessage;
        private WebSocketSession session;
        private User user;
        private Long pictureId;
    }
}
