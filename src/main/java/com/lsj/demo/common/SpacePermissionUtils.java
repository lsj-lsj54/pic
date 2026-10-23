package com.lsj.demo.common;

import com.lsj.demo.exception.BusinessException;
import com.lsj.demo.exception.ErrorCode;
import com.lsj.demo.exception.ThrowUtils;
import com.lsj.demo.model.entity.Space;
import com.lsj.demo.model.entity.SpaceUser;
import com.lsj.demo.model.entity.User;
import com.lsj.demo.model.enums.SpaceRoleEnum;
import com.lsj.demo.model.enums.SpaceTypeEnum;
import com.lsj.demo.service.SpaceUserService;
import com.lsj.demo.service.UserService;

import java.util.Arrays;
import java.util.Objects;

/**
 * 空间权限校验工具类（数据级校验：这个空间的归属是谁、这个用户在里面是什么角色）。
 * <p>
 * 与 Controller 上的注解鉴权分工：
 * <ul>
 *     <li>注解 {@code @SaSpaceCheckPermission} 在进入方法前，按请求里的 spaceId / pictureId
 *     算出「当前用户在这个空间里的角色权限码」，拦住越权调用；</li>
 *     <li>本工具类在业务层按数据库里的真实归属做二次校验，用于 service 内部复用、
 *     无法挂注解的入口（分页查询、分析、上传落库后等），也防止注解上下文解析不到时的漏判。</li>
 * </ul>
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
     * 校验用户是否有操作该空间的权限（识别团队空间）
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
        // 团队空间：admin / editor 成员可操作
        if (isTeamSpace(space)
                && hasAnyRole(spaceUserService, space.getId(), loginUser.getId(),
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
     * 校验用户是否可以查看该空间（识别团队空间）
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
        if (isTeamSpace(space)
                && hasAnyRole(spaceUserService, space.getId(), loginUser.getId(),
                SpaceRoleEnum.VIEWER.getValue(), SpaceRoleEnum.EDITOR.getValue(), SpaceRoleEnum.ADMIN.getValue())) {
            return;
        }
        throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "不能查看别人的空间");
    }

    /**
     * 当前登录用户是否为该空间创建者
     */
    private static boolean isCreator(Space space, User loginUser) {
        // 直接比对空间归属用户 id，最快也最可靠
        return loginUser.getId().equals(space.getUserId());
    }

    /**
     * 是否为团队空间
     */
    private static boolean isTeamSpace(Space space) {
        return Objects.equals(SpaceTypeEnum.TEAM.getValue(), space.getSpaceType());
    }

    /**
     * 用户在该空间中的角色是否命中给定角色之一
     * <p>
     * 直接查 space_user 表，不走 Sa-Token 权限码：这里需要的是「数据库里的真实角色」，
     * 而权限码依赖请求上下文（体里的 spaceId），在 service 内部不一定能取到。
     *
     * @param spaceUserService 空间成员服务
     * @param spaceId          空间 id
     * @param userId           用户 id
     * @param roles            允许的角色 value（viewer / editor / admin）
     */
    private static boolean hasAnyRole(SpaceUserService spaceUserService, Long spaceId, Long userId, String... roles) {
        SpaceUser spaceUser = spaceUserService.getSpaceUser(spaceId, userId);
        return spaceUser != null && Arrays.asList(roles).contains(spaceUser.getSpaceRole());
    }
}
