package com.lsj.demo.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lmax.disruptor.WorkHandler;
import com.lsj.demo.model.dto.websocket.PictureEditRequestMessage;
import com.lsj.demo.model.dto.websocket.PictureEditResponseMessage;
import com.lsj.demo.model.entity.User;
import com.lsj.demo.model.enums.PictureEditMessageTypeEnum;
import com.lsj.demo.model.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.Resource;

/**
 * 图片编辑事件消费者，作为 Disruptor 的 WorkHandler 处理 {@link PictureEditEvent}
 * <p>
 * 把事件按消息类型分发到 {@link PictureEditHandler} 的对应处理方法。
 *
 * @author lsj
 */
@Slf4j
@Component
public class PictureEditEventWorkHandler implements WorkHandler<PictureEditEvent> {

    /**
     * 加 @Lazy 打破循环依赖：
     * PictureEditHandler -> PictureEditEventProducer -> Disruptor -> 本类 -> PictureEditHandler
     */
    @Resource
    @Lazy
    private PictureEditHandler pictureEditHandler;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public void onEvent(PictureEditEvent event) throws Exception {
        PictureEditRequestMessage pictureEditRequestMessage = event.getPictureEditRequestMessage();
        WebSocketSession session = event.getSession();
        User user = event.getUser();
        Long pictureId = event.getPictureId();
        // 获取消息类别
        String type = pictureEditRequestMessage.getType();
        PictureEditMessageTypeEnum pictureEditMessageTypeEnum = PictureEditMessageTypeEnum.getEnumByValue(type);
        if (pictureEditMessageTypeEnum == null) {
            sendErrorMessage(session, user, "消息类型错误");
            return;
        }
        PictureEditHandler.PictureEditContext pictureEditContext =
                new PictureEditHandler.PictureEditContext(pictureEditRequestMessage, session, user, pictureId);
        // 调用对应的消息处理方法
        switch (pictureEditMessageTypeEnum) {
            case ENTER_EDIT:
                pictureEditHandler.handleEnterEditMessage(pictureEditContext);
                break;
            case EDIT_ACTION:
                pictureEditHandler.handleEditActionMessage(pictureEditContext);
                break;
            case EXIT_EDIT:
                pictureEditHandler.handleExitEditMessage(pictureEditContext);
                break;
            default:
                sendErrorMessage(session, user, "消息类型错误");
        }
    }

    /**
     * 向客户端发送错误消息
     */
    private void sendErrorMessage(WebSocketSession session, User user, String message) throws Exception {
        if (session == null || !session.isOpen()) {
            return;
        }
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ERROR.getValue());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(UserVO.entityToVo(user));
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(pictureEditResponseMessage)));
    }
}
