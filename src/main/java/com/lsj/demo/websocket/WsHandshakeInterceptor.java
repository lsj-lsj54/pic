package com.lsj.demo.websocket;

import cn.hutool.core.util.StrUtil;
import com.lsj.demo.manager.auth.SpaceUserAuthManager;
import com.lsj.demo.model.constant.SpaceUserPermissionConstant;
import com.lsj.demo.model.entity.Picture;
import com.lsj.demo.model.entity.Space;
import com.lsj.demo.model.entity.SpaceUser;
import com.lsj.demo.model.entity.User;
import com.lsj.demo.model.enums.SpaceTypeEnum;
import com.lsj.demo.service.PictureService;
import com.lsj.demo.service.SpaceService;
import com.lsj.demo.service.SpaceUserService;
import com.lsj.demo.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * WebSocket 握手拦截器
 * <p>
 * 在握手阶段完成登录校验与图片编辑权限校验，只有通过的用户才能建立连接。
 * 校验通过后把 user / userId / pictureId 放进 attributes，供后续的
 * {@link PictureEditHandler} 从会话属性中读取。
 *
 * @author lsj
 */
@Slf4j
@Component
public class WsHandshakeInterceptor implements HandshakeInterceptor {

    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private SpaceUserService spaceUserService;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest) {
            HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
            // 获取请求参数
            String pictureId = servletRequest.getParameter("pictureId");
            if (StrUtil.isBlank(pictureId)) {
                log.error("缺少图片参数，拒绝握手");
                return false;
            }
            User loginUser = userService.getLoginUserOrNull(servletRequest);
            if (loginUser == null) {
                log.error("用户未登录，拒绝握手");
                return false;
            }
            // 校验图片是否存在
            Picture picture = pictureService.getById(pictureId);
            if (picture == null) {
                log.error("图片不存在，拒绝握手");
                return false;
            }
            // 校验用户是否有该图片的编辑权限
            if (!hasPictureEditPermission(picture, loginUser)) {
                log.error("没有图片编辑权限，拒绝握手");
                return false;
            }
            // 设置 attributes，供 PictureEditHandler 读取
            attributes.put("user", loginUser);
            attributes.put("userId", loginUser.getId());
            // 记得转换为 Long 类型
            attributes.put("pictureId", Long.valueOf(pictureId));
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
    }

    /**
     * 判断登录用户是否有该图片的编辑权限
     * <p>
     * 规则与 {@code PicturePermissionUtils} / {@code StpInterfaceImpl} 保持一致：
     * <ul>
     *     <li>系统管理员直接放行；</li>
     *     <li>公共图库（spaceId 为空）：仅上传者本人可编辑；</li>
     *     <li>私有空间：仅空间创建者可编辑；</li>
     *     <li>团队空间：editor / admin 角色的成员可编辑。</li>
     * </ul>
     */
    private boolean hasPictureEditPermission(Picture picture, User loginUser) {
        // 管理员直接放行
        if (userService.isAdmin(loginUser)) {
            return true;
        }
        Long spaceId = picture.getSpaceId();
        // 公共图库：仅上传者本人可编辑
        if (spaceId == null || spaceId == 0) {
            return Objects.equals(loginUser.getId(), picture.getUserId());
        }
        Space space = spaceService.getById(spaceId);
        if (space == null) {
            return false;
        }
        // 私有空间：仅创建者可编辑
        if (Objects.equals(space.getSpaceType(), SpaceTypeEnum.PRIVATE.getValue())) {
            return Objects.equals(loginUser.getId(), space.getUserId());
        }
        // 团队空间：editor / admin 角色成员可编辑
        SpaceUser spaceUser = spaceUserService.getSpaceUser(spaceId, loginUser.getId());
        if (spaceUser == null) {
            return false;
        }
        List<String> permissionList = spaceUserAuthManager.getPermissionsByRole(spaceUser.getSpaceRole());
        return permissionList.contains(SpaceUserPermissionConstant.PICTURE_EDIT);
    }
}
