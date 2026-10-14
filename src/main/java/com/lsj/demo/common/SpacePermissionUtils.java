package com.lsj.demo.common;

import com.lsj.demo.exception.BusinessException;
import com.lsj.demo.exception.ErrorCode;
import com.lsj.demo.exception.ThrowUtils;
import com.lsj.demo.model.entity.Space;
import com.lsj.demo.model.entity.User;
import com.lsj.demo.service.UserService;

/**
 * 空间权限校验工具类
 * <p>
 * 空间是用户的私有容器：私有空间（spaceId 不为空）内的图片只有空间创建者和管理员能看、能改。
 * 该判断在图片上传、删除、查看等多个入口都要用，统一放在这里，避免各写一遍导致规则不一致。
 *
 * @author lsj
 */
public class SpacePermissionUtils {

    private SpacePermissionUtils() {
    }

    /**
     * 校验用户是否有操作该空间的权限（仅空间创建者或管理员）
     *
     * @param space       空间，为空时直接按「空间不存在」处理
     * @param loginUser   登录的用户，为空表示未登录
     * @param userService 用户服务，用于判断当前用户是否为管理员
     */
    public static void checkSpaceAuth(Space space, User loginUser, UserService userService) {
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NO_AUTH_ERROR, "未登录");
        // 仅空间创建者或管理员可操作
        if (!loginUser.getId().equals(space.getUserId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该空间");
        }
    }

    /**
     * 校验用户是否可以查看该空间（仅空间创建者或管理员）
     * <p>
     * 与 {@link #checkSpaceAuth} 的区别只是文案：查看失败提示「不能查看别人的空间」，
     * 便于前端区分「看不到」和「改不了」。
     *
     * @param space       空间
     * @param loginUser   登录的用户，为空表示未登录
     * @param userService 用户服务
     */
    public static void checkSpaceViewAuth(Space space, User loginUser, UserService userService) {
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NO_AUTH_ERROR, "未登录");
        if (!loginUser.getId().equals(space.getUserId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "不能查看别人的空间");
        }
    }
}
