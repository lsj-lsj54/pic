package com.lsj.demo.common;

import cn.dev33.satoken.stp.StpUtil;
import com.lsj.demo.exception.BusinessException;
import com.lsj.demo.exception.ErrorCode;
import com.lsj.demo.exception.ThrowUtils;
import com.lsj.demo.model.entity.Space;
import com.lsj.demo.model.entity.User;
import com.lsj.demo.model.enums.SpaceRoleEnum;
import com.lsj.demo.model.enums.SpaceTypeEnum;
import com.lsj.demo.model.enums.UserRoleEnum;
import com.lsj.demo.service.SpaceUserService;
import com.lsj.demo.service.UserService;

import java.util.Objects;

/**
 * 空间权限校验工具类（基于 Sa-Token 动态权限码实现）。
 * <p>
 * 空间成员权限统一编码为权限码，由 {@code SaTokenStpInterfaceImpl} 在登录态下动态计算：
 * <ul>
 *     <li>{@code space:creator:{spaceId}} —— 空间创建者</li>
 *     <li>{@code space:viewer:{spaceId}} —— 团队空间浏览者</li>
 *     <li>{@code space:editor:{spaceId}} —— 团队空间编辑者</li>
 *     <li>{@code space:admin:{spaceId}} —— 团队空间管理员</li>
 * </ul>
 * 本工具类保留原有静态方法签名，内部改为 {@code StpUtil.hasPermission} 校验，
 * 所有调用点（图片上传/删除/编辑、空间编辑/删除/查看等）无需改动。
 * <p>
 * 规则：
 * <ul>
 *     <li>私有空间：只有空间创建者或系统管理员能看、能改；</li>
 *     <li>团队空间：成员即可查看，admin / editor 角色的成员可操作。</li>
 * </ul>
 *
 * @author lsj
 */
public class SpacePermissionUtils {

    private SpacePermissionUtils() {
    }

    /**
     * 校验用户是否有操作该空间的权限（仅空间创建者或管理员）
     * <p>
     * 三参数版本保留给不区分空间类型的调用方，默认按「私有空间」规则校验。
     */
    public static void checkSpaceAuth(Space space, User loginUser, UserService userService) {
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NO_AUTH_ERROR, "未登录");
        // 仅空间创建者或管理员可操作
        if (!isCreator(space, loginUser) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该空间");
        }
    }

    /**
     * 校验用户是否有操作该空间的权限（识别团队空间，基于 Sa-Token 权限码）
     * <p>
     * 私有空间规则不变；团队空间中，角色为 admin / editor 的成员也可以操作。
     */
    public static void checkSpaceAuth(Space space, User loginUser, UserService userService,
                                      SpaceUserService spaceUserService) {
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NO_AUTH_ERROR, "未登录");
        // 空间创建者或系统管理员直接放行
        if (isCreator(space, loginUser) || userService.isAdmin(loginUser)) {
            return;
        }
        // 团队空间：admin / editor 成员可操作（通过 Sa-Token 权限码校验）
        if (Objects.equals(SpaceTypeEnum.TEAM.getValue(), space.getSpaceType())
                && hasAnyPermission(space.getId(),
                SpaceRoleEnum.ADMIN.getValue(), SpaceRoleEnum.EDITOR.getValue())) {
            return;
        }
        throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该空间");
    }

    /**
     * 校验用户是否可以查看该空间（仅空间创建者或管理员）
     */
    public static void checkSpaceViewAuth(Space space, User loginUser, UserService userService) {
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NO_AUTH_ERROR, "未登录");
        if (!isCreator(space, loginUser) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "不能查看别人的空间");
        }
    }

    /**
     * 校验用户是否可以查看该空间（识别团队空间，基于 Sa-Token 权限码）
     * <p>
     * 团队空间的所有成员（含 viewer）都可以查看空间及其中的图片。
     */
    public static void checkSpaceViewAuth(Space space, User loginUser, UserService userService,
                                          SpaceUserService spaceUserService) {
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NO_AUTH_ERROR, "未登录");
        if (isCreator(space, loginUser) || userService.isAdmin(loginUser)) {
            return;
        }
        // 团队空间：任意角色的成员均可查看
        if (Objects.equals(SpaceTypeEnum.TEAM.getValue(), space.getSpaceType())
                && hasAnyPermission(space.getId(),
                SpaceRoleEnum.VIEWER.getValue(), SpaceRoleEnum.EDITOR.getValue(), SpaceRoleEnum.ADMIN.getValue())) {
            return;
        }
        throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "不能查看别人的空间");
    }

    /**
     * 当前登录用户是否为该空间创建者（基于 Sa-Token 权限码）
     */
    private static boolean isCreator(Space space, User loginUser) {
        // 直接比对空间归属用户 id，最快也最可靠
        return loginUser.getId().equals(space.getUserId());
    }

    /**
     * 当前登录用户是否命中任一空间角色权限码
     *
     * @param spaceId 空间 id
     * @param roles   允许的空间角色 value（viewer / editor / admin）
     */
    private static boolean hasAnyPermission(Long spaceId, String... roles) {
        for (String role : roles) {
            if (StpUtil.hasPermission(buildPermission(role, spaceId))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 拼接空间权限码：space:{role}:{spaceId}
     * <p>
     * 对外公开，供 SpaceUserServiceImpl 等其它模块复用统一的权限码格式，
     * 避免各处手写字符串导致权限码不一致。
     */
    public static String buildPermission(String role, Long spaceId) {
        return "space:" + role + ":" + spaceId;
    }
}
