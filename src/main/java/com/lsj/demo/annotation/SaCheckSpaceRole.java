package com.lsj.demo.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 空间角色校验注解（基于 Sa-Token 动态权限码实现）。
 * <p>
 * 标注在 Controller 方法上，校验当前登录用户对指定空间是否具备要求的角色。
 * 权限码由 {@code SaTokenStpInterfaceImpl#getPermissionList} 动态生成：
 * <ul>
 *     <li>{@code space:creator:{spaceId}} —— 空间创建者</li>
 *     <li>{@code space:viewer:{spaceId}} —— 团队空间浏览者</li>
 *     <li>{@code space:editor:{spaceId}} —— 团队空间编辑者</li>
 *     <li>{@code space:admin:{spaceId}} —— 团队空间管理员</li>
 * </ul>
 * <p>
 * {@code anyRole} 指定一组角色，命中其一即可通过；创建者始终拥有最高权限，单独用
 * {@code requireCreator} 控制是否仅创建者可操作。系统管理员（全局 admin 角色）默认放行。
 *
 * @author lsj
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SaCheckSpaceRole {

    /**
     * 空间 id 的来源：请求参数名（requestParam 或 @RequestBody 内的字段名）。
     * <p>
     * 切面会按此名称从方法参数（含 DTO 的 getter）中取空间 id。
     */
    String spaceIdField() default "spaceId";

    /**
     * 要求的空间角色（命中其一即可），默认 viewer。
     * <p>
     * 取值为空间角色 value：viewer / editor / admin。
     */
    String[] anyRole() default {"viewer"};

    /**
     * 是否要求必须是空间创建者（创建者权限最高，独立开关）。
     */
    boolean requireCreator() default false;
}
