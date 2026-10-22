package com.lsj.demo.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 拦截器配置。
 * <p>
 * 注册 {@link SaInterceptor} 只为每个请求初始化 Sa-Token 上下文（读取 Cookie/请求头里的 token，
 * 解析出当前登录账号），使 {@code StpUtil.getLoginIdAsLong()}、{@code StpUtil.hasPermission()}
 * 等在业务代码中可直接使用。
 * <p>
 * 这里不做统一鉴权拦截（handler 为空），登录态与权限校验仍由各接口内部的
 * {@code getLoginUser} 或自定义注解负责，避免误伤「未登录也能访问」的公开接口
 * （如公共图库列表、空间级别字典、登录/注册等）。
 *
 * @author lsj
 */
@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 只初始化认证上下文，不强制校验登录态
        registry.addInterceptor(new SaInterceptor()).addPathPatterns("/**");
    }
}
