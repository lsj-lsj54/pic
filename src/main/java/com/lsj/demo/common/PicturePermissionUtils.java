package com.lsj.demo.common;

import com.lsj.demo.exception.BusinessException;
import com.lsj.demo.exception.ErrorCode;
import com.lsj.demo.exception.ThrowUtils;
import com.lsj.demo.model.entity.Picture;
import com.lsj.demo.model.entity.User;
import com.lsj.demo.service.UserService;

/**
 * 图片权限校验工具类
 * <p>
 * 图片模块的删除、编辑、重新上传都要判断「仅本人或管理员可操作」，
 * 该逻辑原先在多个方法中重复出现，这里统一提取为公共方法，避免重复代码。
 *
 * @author lsj
 */
public class PicturePermissionUtils {

    private PicturePermissionUtils() {
    }

    /**
     * 校验用户是否有操作该图片的权限（仅本人或管理员可操作）
     *
     * @param picture     picture 图片
     * @param loginUser   登录的用户
     * @param userService 用户服务，用于判断当前用户是否为管理员
     */
    public static void checkPictureAuth(Picture picture, User loginUser, UserService userService) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR, "图片不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 仅本人或管理员可操作
        if (!picture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
    }
}
