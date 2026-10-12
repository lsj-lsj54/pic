package com.lsj.demo.model.constant;

public interface UserConstant {

    /**
     * 用户登录态键
     */
    String USER_LOGIN_STATE = "user_login";

    //  region 权限

    /**
     * 默认角色
     */
    String DEFAULT_ROLE = "user";

    /**
     * 管理员角色
     */
    String ADMIN_ROLE = "admin";
    
    // endregion

    /**
     * 默认密码（管理员创建用户时使用）
     */
    String DEFAULT_PASSWORD = "12345678";
}
