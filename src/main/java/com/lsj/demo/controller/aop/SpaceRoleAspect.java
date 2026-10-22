package com.lsj.demo.controller.aop;

import cn.dev33.satoken.stp.StpUtil;
import com.lsj.demo.annotation.SaCheckSpaceRole;
import com.lsj.demo.exception.BusinessException;
import com.lsj.demo.exception.ErrorCode;
import com.lsj.demo.model.enums.UserRoleEnum;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 空间角色校验切面（Sa-Token）。
 * <p>
 * 配合 {@link SaCheckSpaceRole} 注解，在方法执行前校验当前登录用户对指定空间的角色：
 * <ol>
 *     <li>系统管理员（全局 admin 角色）直接放行；</li>
 *     <li>{@code requireCreator = true} 时，必须是空间创建者（{@code space:creator:{spaceId}}）；</li>
 *     <li>否则检查是否命中任一指定角色权限码（{@code space:{role}:{spaceId}}）。</li>
 * </ol>
 * 校验失败统一抛 {@link BusinessException}({@link ErrorCode#NO_AUTH_ERROR})，
 * 与原有权限工具的语义保持一致，前端无需改动。
 *
 * @author lsj
 */
@Aspect
@Component
public class SpaceRoleAspect {

    /**
     * 在方法执行前校验空间角色
     */
    @Before("@annotation(saCheckSpaceRole)")
    public void doCheck(JoinPoint joinPoint, SaCheckSpaceRole saCheckSpaceRole) {
        // 1. 系统管理员直接放行
        if (StpUtil.hasRole(UserRoleEnum.ADMIN.getValue())) {
            return;
        }

        // 2. 取空间 id
        Long spaceId = resolveSpaceId(joinPoint, saCheckSpaceRole.spaceIdField());
        if (spaceId == null || spaceId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间 id 不能为空");
        }

        // 3. 创建者校验
        if (saCheckSpaceRole.requireCreator()) {
            String creatorPermission = buildPermission("creator", spaceId);
            if (StpUtil.hasPermission(creatorPermission)) {
                return;
            }
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "仅空间创建者可操作");
        }

        // 4. 任一角色命中即通过
        String[] roles = saCheckSpaceRole.anyRole();
        for (String role : roles) {
            if (StpUtil.hasPermission(buildPermission(role, spaceId))) {
                return;
            }
        }
        throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该空间");
    }

    /**
     * 从方法参数中解析空间 id。
     * <p>
     * 优先从方法参数里找与 {@code spaceIdField} 同名的字段（DTO 的 getter），
     * 找不到再遍历 DTO 对象的字段。
     */
    private Long resolveSpaceId(JoinPoint joinPoint, String spaceIdField) {
        Object[] args = joinPoint.getArgs();
        // 遍历参数：直接匹配参数名或反射读取 DTO 字段
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            Long id = tryReadField(arg, spaceIdField);
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    /**
     * 尝试从对象读取指定名称的字段（优先 getter，其次直接反射字段）
     */
    private Long tryReadField(Object obj, String fieldName) {
        if (obj == null || fieldName == null || fieldName.isEmpty()) {
            return null;
        }
        // 1. getter
        String getter = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        try {
            Method m = obj.getClass().getMethod(getter);
            Object value = m.invoke(obj);
            return toLong(value);
        } catch (NoSuchMethodException e) {
            // 没有 getter，尝试直接读字段
        } catch (Exception e) {
            // 忽略，继续尝试
        }
        // 2. 直接反射字段
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return toLong(field.get(obj));
        } catch (Exception e) {
            return null;
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String buildPermission(String role, Long spaceId) {
        return "space:" + role + ":" + spaceId;
    }
}
