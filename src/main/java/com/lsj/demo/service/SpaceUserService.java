package com.lsj.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lsj.demo.model.dto.spaceuser.SpaceUserAddRequest;
import com.lsj.demo.model.dto.spaceuser.SpaceUserEditRequest;
import com.lsj.demo.model.dto.spaceuser.SpaceUserQueryRequest;
import com.lsj.demo.model.entity.SpaceUser;
import com.lsj.demo.model.entity.User;
import com.lsj.demo.model.vo.SpaceUserVO;

import java.util.List;

/**
 * @author lsj
 * @description 针对表【space_user(空间用户关联)】的数据库操作Service
 * @createDate 2026-10-22
 */
public interface SpaceUserService extends IService<SpaceUser> {

    /**
     * 邀请用户加入团队空间
     * <p>
     * 仅团队空间支持邀请；仅空间创建者、团队管理员或系统管理员可操作。
     *
     * @param spaceUserAddRequest 邀请请求（spaceId / userId / spaceRole）
     * @param loginUser           登录用户
     * @return 新成员记录 id
     */
    long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest, User loginUser);

    /**
     * 移除团队成员
     * <p>
     * 空间创建者、团队管理员、系统管理员可移除他人；成员也可移除自己（退出团队）。
     * 空间创建者本人的记录不允许移除。
     *
     * @param id        成员记录 id
     * @param loginUser 登录用户
     * @return 是否成功
     */
    boolean deleteSpaceUser(long id, User loginUser);

    /**
     * 修改团队成员角色
     * <p>
     * 仅空间创建者或系统管理员可调整；创建者本人的角色不可修改。
     *
     * @param spaceUserEditRequest 编辑请求（id / spaceRole）
     * @param loginUser            登录用户
     * @return 是否成功
     */
    boolean editSpaceUser(SpaceUserEditRequest spaceUserEditRequest, User loginUser);

    /**
     * 查询空间成员列表（封装类，含用户与空间信息）
     * <p>
     * 仅该空间的成员、创建者或系统管理员可见。
     *
     * @param spaceUserQueryRequest 查询请求（spaceId 必填）
     * @param loginUser             登录用户
     * @return 成员封装列表
     */
    List<SpaceUserVO> listSpaceUserVO(SpaceUserQueryRequest spaceUserQueryRequest, User loginUser);

    /**
     * 查询当前用户加入的团队空间列表（封装类，含空间信息）
     *
     * @param loginUser 登录用户
     * @return 我加入的团队空间列表
     */
    List<SpaceUserVO> listMyTeamSpace(User loginUser);

    /**
     * 校验空间成员对象
     *
     * @param spaceUser 空间成员
     * @param add       是否为创建时校验
     */
    void validSpaceUser(SpaceUser spaceUser, boolean add);

    /**
     * 获取查询对象
     *
     * @param spaceUserQueryRequest 查询请求
     * @return QueryWrapper
     */
    QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);

    /**
     * 单条记录转封装类（填充用户与空间信息）
     *
     * @param spaceUser 空间成员
     * @return 封装类
     */
    SpaceUserVO getSpaceUserVO(SpaceUser spaceUser);

    /**
     * 查询某用户在某空间中的成员记录
     *
     * @param spaceId 空间 id
     * @param userId  用户 id
     * @return 成员记录，不存在返回 null
     */
    SpaceUser getSpaceUser(Long spaceId, Long userId);

    /**
     * 判断用户是否为空间成员，且角色在给定角色集合内
     * <p>
     * 权限校验工具类使用，避免各处手写查询。
     *
     * @param spaceId    空间 id
     * @param userId     用户 id
     * @param spaceRoles 允许的角色（不传表示只要是成员即可）
     * @return 是否命中
     */
    boolean hasRole(Long spaceId, Long userId, String... spaceRoles);
}
