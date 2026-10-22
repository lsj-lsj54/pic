package com.lsj.demo.common;

import com.lsj.demo.exception.BusinessException;
import com.lsj.demo.exception.ErrorCode;
import com.lsj.demo.exception.ThrowUtils;
import com.lsj.demo.model.entity.Picture;
import com.lsj.demo.model.entity.Space;
import com.lsj.demo.model.entity.User;
import com.lsj.demo.service.SpaceService;
import com.lsj.demo.service.SpaceUserService;
import com.lsj.demo.service.UserService;

/**
 * 图片权限校验工具类
 * <p>
 * 图片模块的删除、编辑、重新上传都要判断「能不能动这张图」，
 * 该逻辑原先在多个方法中重复出现，这里统一提取为公共方法，避免重复代码。
 * <p>
 * 权限规则：
 * <ul>
 *     <li>管理员：全部放行</li>
 *     <li>公共空间的图片（spaceId 为空）：仅上传者本人可操作</li>
 *     <li>私有空间的图片（spaceId 不为空）：仅该空间的创建者可操作，别人空间里的图片一律拒绝</li>
 *     <li>团队空间的图片：空间中 admin / editor 角色的成员也可操作</li>
 * </ul>
 *
 * @author lsj
 */
public class PicturePermissionUtils {

    private PicturePermissionUtils() {
    }

    /**
     * 校验用户是否有操作该图片的权限
     *
     * @param picture          picture 图片
     * @param loginUser        登录的用户
     * @param userService      用户服务，用于判断当前用户是否为管理员
     * @param spaceService     空间服务，用于查询图片所属空间
     * @param spaceUserService 空间成员服务，用于识别团队空间成员角色
     */
    public static void checkPictureAuth(Picture picture, User loginUser, UserService userService,
                                        SpaceService spaceService, SpaceUserService spaceUserService) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR, "图片不能为空");
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NO_AUTH_ERROR);
        Long spaceId = picture.getSpaceId();
        if (spaceId != null) {
            // 空间图片：私有空间仅创建者可操作；团队空间按成员角色放行，复用空间的权限规则
            Space space = spaceService.getById(spaceId);
            SpacePermissionUtils.checkSpaceAuth(space, loginUser, userService, spaceUserService);
            return;
        }
        // 公共空间：上传者本人直接放行，其余人只有管理员能操作
        if (loginUser.getId().equals(picture.getUserId())) {
            return;
        }
        if (!userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
    }
}
