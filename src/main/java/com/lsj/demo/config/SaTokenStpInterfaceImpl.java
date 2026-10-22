package com.lsj.demo.config;

import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lsj.demo.mapper.SpaceMapper;
import com.lsj.demo.mapper.SpaceUserMapper;
import com.lsj.demo.mapper.UserMapper;
import com.lsj.demo.model.entity.Space;
import com.lsj.demo.model.entity.SpaceUser;
import com.lsj.demo.model.entity.User;
import com.lsj.demo.model.enums.SpaceRoleEnum;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Sa-Token 动态权限实现。
 * <p>
 * Sa-Token 的 {@link StpInterface} 负责「按登录账号计算其角色与权限」。
 * 这里把空间成员权限编码成权限码字符串，配合自定义注解 {@code @SaCheckSpaceRole}
 * 用 {@code StpUtil.checkPermission} 做校验：
 * <ul>
 *     <li>全局角色：{@code admin} / {@code user}，由 {@link #getRoleList} 返回</li>
 *     <li>空间创建者权限码：{@code space:creator:{spaceId}}</li>
 *     <li>团队成员角色权限码：{@code space:viewer:{spaceId}} / {@code space:editor:{spaceId}} / {@code space:admin:{spaceId}}</li>
 * </ul>
 * <p>
 * 权限码按登录态动态计算，团队成员被移除后下次请求即失效，无需手动清缓存。
 *
 * @author lsj
 */
@Component
public class SaTokenStpInterfaceImpl implements StpInterface {

    @Resource
    private UserMapper userMapper;

    @Resource
    private SpaceMapper spaceMapper;

    @Resource
    private SpaceUserMapper spaceUserMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        List<String> permissionList = new ArrayList<>();
        long userId = Long.parseLong(String.valueOf(loginId));

        // 1. 我创建的空间 => space:creator:{spaceId}
        List<Space> mySpaces = spaceMapper.selectList(new QueryWrapper<Space>()
                .eq("userId", userId));
        if (CollUtil.isNotEmpty(mySpaces)) {
            for (Space space : mySpaces) {
                permissionList.add(buildSpacePermission("creator", space.getId()));
            }
        }

        // 2. 我加入的团队空间 => space:{role}:{spaceId}
        List<SpaceUser> spaceUserList = spaceUserMapper.selectList(new QueryWrapper<SpaceUser>()
                .eq("userId", userId));
        if (CollUtil.isNotEmpty(spaceUserList)) {
            for (SpaceUser spaceUser : spaceUserList) {
                String role = spaceUser.getSpaceRole();
                // 只认合法的空间角色，脏数据直接跳过
                if (SpaceRoleEnum.getEnumByValue(role) != null) {
                    permissionList.add(buildSpacePermission(role, spaceUser.getSpaceId()));
                }
            }
        }

        return permissionList;
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        List<String> roleList = new ArrayList<>();
        long userId = Long.parseLong(String.valueOf(loginId));
        User user = userMapper.selectById(userId);
        if (user != null && Objects.nonNull(user.getUserRole())) {
            roleList.add(user.getUserRole());
        }
        return roleList;
    }

    /**
     * 拼接空间权限码：space:{role}:{spaceId}
     */
    private String buildSpacePermission(String role, Long spaceId) {
        return "space:" + role + ":" + spaceId;
    }
}
