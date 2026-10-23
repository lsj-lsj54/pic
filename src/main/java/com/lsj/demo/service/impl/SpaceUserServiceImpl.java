package com.lsj.demo.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lsj.demo.exception.BusinessException;
import com.lsj.demo.exception.ErrorCode;
import com.lsj.demo.exception.ThrowUtils;
import com.lsj.demo.mapper.SpaceMapper;
import com.lsj.demo.mapper.SpaceUserMapper;
import com.lsj.demo.model.dto.spaceuser.SpaceUserAddRequest;
import com.lsj.demo.model.dto.spaceuser.SpaceUserEditRequest;
import com.lsj.demo.model.dto.spaceuser.SpaceUserQueryRequest;
import com.lsj.demo.model.entity.Space;
import com.lsj.demo.model.entity.SpaceUser;
import com.lsj.demo.model.entity.User;
import com.lsj.demo.model.enums.SpaceRoleEnum;
import com.lsj.demo.model.enums.SpaceTypeEnum;
import com.lsj.demo.model.vo.SpaceUserVO;
import com.lsj.demo.model.vo.SpaceVO;
import com.lsj.demo.service.SpaceUserService;
import com.lsj.demo.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author lsj
 * @description 针对表【space_user(空间用户关联)】的数据库操作Service实现
 * @createDate 2026-10-22
 */
@Service
public class SpaceUserServiceImpl extends ServiceImpl<SpaceUserMapper, SpaceUser>
        implements SpaceUserService {

    /**
     * 校验空间是否存在直接走 SpaceMapper，而不是注入 SpaceService：
     * SpaceServiceImpl 创建团队空间时要反过来调本服务写创建者的成员记录，
     * 互相注入会形成循环依赖。
     */
    private final SpaceMapper spaceMapper;

    private final UserService userService;

    public SpaceUserServiceImpl(SpaceMapper spaceMapper, UserService userService) {
        this.spaceMapper = spaceMapper;
        this.userService = userService;
    }

    @Override
    public long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest, User loginUser) {
        ThrowUtils.throwIf(spaceUserAddRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NO_AUTH_ERROR, "未登录");
        SpaceUser spaceUser = new SpaceUser();
        BeanUtils.copyProperties(spaceUserAddRequest, spaceUser);
        // 不传角色时默认给浏览者，权限最小化
        if (StrUtil.isBlank(spaceUser.getSpaceRole())) {
            spaceUser.setSpaceRole(SpaceRoleEnum.VIEWER.getValue());
        }
        validSpaceUser(spaceUser, true);
        Long spaceId = spaceUser.getSpaceId();
        Space space = spaceMapper.selectById(spaceId);
        // 只有团队空间才有成员概念
        ThrowUtils.throwIf(!Objects.equals(SpaceTypeEnum.TEAM.getValue(), space.getSpaceType()),
                ErrorCode.PARAMS_ERROR, "只有团队空间才能邀请成员");
        // 仅空间创建者、团队管理员或系统管理员可以邀请
        ThrowUtils.throwIf(!canManageMember(space, loginUser),
                ErrorCode.NO_AUTH_ERROR, "无权限邀请成员");
        // 防止重复邀请，数据库唯一索引做兜底
        long count = this.count(new QueryWrapper<SpaceUser>()
                .eq("spaceId", spaceId)
                .eq("userId", spaceUser.getUserId()));
        ThrowUtils.throwIf(count > 0, ErrorCode.PARAMS_ERROR, "该用户已在团队中");
        ThrowUtils.throwIf(!this.save(spaceUser), ErrorCode.SYSTEM_ERROR, "邀请成员失败");
        return spaceUser.getId();
    }

    @Override
    public boolean deleteSpaceUser(long id, User loginUser) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR, "成员记录 id 不能为空");
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NO_AUTH_ERROR, "未登录");
        SpaceUser spaceUser = this.getById(id);
        ThrowUtils.throwIf(spaceUser == null, ErrorCode.NOT_FOUND_ERROR, "成员记录不存在");
        Space space = spaceMapper.selectById(spaceUser.getSpaceId());
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        // 创建者本人的成员记录与空间共存亡，不能单独移除
        ThrowUtils.throwIf(Objects.equals(space.getUserId(), spaceUser.getUserId()),
                ErrorCode.OPERATION_ERROR, "不能移除空间创建者");
        // 创建者 / 团队管理员 / 系统管理员可移除他人；普通成员可移除自己（退出团队）
        boolean isSelf = Objects.equals(loginUser.getId(), spaceUser.getUserId());
        ThrowUtils.throwIf(!isSelf && !canManageMember(space, loginUser),
                ErrorCode.NO_AUTH_ERROR, "无权限移除该成员");
        return this.removeById(id);
    }

    @Override
    public boolean editSpaceUser(SpaceUserEditRequest spaceUserEditRequest, User loginUser) {
        ThrowUtils.throwIf(spaceUserEditRequest == null || spaceUserEditRequest.getId() == null,
                ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NO_AUTH_ERROR, "未登录");
        SpaceUser oldSpaceUser = this.getById(spaceUserEditRequest.getId());
        ThrowUtils.throwIf(oldSpaceUser == null, ErrorCode.NOT_FOUND_ERROR, "成员记录不存在");
        Space space = spaceMapper.selectById(oldSpaceUser.getSpaceId());
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        // 角色调整只放开给创建者和系统管理员，团队管理员也不能改，防止互相提权
        boolean isCreator = Objects.equals(space.getUserId(), loginUser.getId());
        ThrowUtils.throwIf(!isCreator && !userService.isAdmin(loginUser),
                ErrorCode.NO_AUTH_ERROR, "仅空间创建者可调整成员角色");
        ThrowUtils.throwIf(Objects.equals(space.getUserId(), oldSpaceUser.getUserId()),
                ErrorCode.OPERATION_ERROR, "不能修改空间创建者的角色");
        SpaceUser spaceUser = new SpaceUser();
        spaceUser.setId(oldSpaceUser.getId());
        spaceUser.setSpaceRole(spaceUserEditRequest.getSpaceRole());
        validSpaceUser(spaceUser, false);
        return this.updateById(spaceUser);
    }

    @Override
    public List<SpaceUserVO> listSpaceUserVO(SpaceUserQueryRequest spaceUserQueryRequest, User loginUser) {
        ThrowUtils.throwIf(spaceUserQueryRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NO_AUTH_ERROR, "未登录");
        Long spaceId = spaceUserQueryRequest.getSpaceId();
        ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR, "空间 id 不能为空");
        Space space = spaceMapper.selectById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        // 成员列表对团队内成员开放；非成员只有系统管理员能看
        boolean isMember = this.getSpaceUser(spaceId, loginUser.getId()) != null;
        boolean isCreator = Objects.equals(space.getUserId(), loginUser.getId());
        ThrowUtils.throwIf(!isMember && !isCreator && !userService.isAdmin(loginUser),
                ErrorCode.NO_AUTH_ERROR, "无权限查看该空间成员");
        List<SpaceUser> spaceUserList = this.list(getQueryWrapper(spaceUserQueryRequest));
        if (CollUtil.isEmpty(spaceUserList)) {
            return Collections.emptyList();
        }
        // 批量查用户信息，避免 N+1
        Set<Long> userIdSet = spaceUserList.stream().map(SpaceUser::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        SpaceVO spaceVO = SpaceVO.entityToVo(space);
        return spaceUserList.stream().map(spaceUser -> {
            SpaceUserVO spaceUserVO = SpaceUserVO.objToVo(spaceUser);
            List<User> userList = userIdUserListMap.get(spaceUser.getUserId());
            spaceUserVO.setUser(userService.getUserVO(CollUtil.isEmpty(userList) ? null : userList.get(0)));
            spaceUserVO.setSpace(spaceVO);
            return spaceUserVO;
        }).collect(Collectors.toList());
    }

    @Override
    public List<SpaceUserVO> listMyTeamSpace(User loginUser) {
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NO_AUTH_ERROR, "未登录");
        List<SpaceUser> spaceUserList = this.list(new QueryWrapper<SpaceUser>()
                .eq("userId", loginUser.getId()));
        if (CollUtil.isEmpty(spaceUserList)) {
            return Collections.emptyList();
        }
        // 批量查空间信息，成员表只关联团队空间，但空间可能被删，查不到的要跳过
        Set<Long> spaceIdSet = spaceUserList.stream().map(SpaceUser::getSpaceId).collect(Collectors.toSet());
        Map<Long, List<Space>> spaceIdSpaceListMap = spaceMapper.selectBatchIds(spaceIdSet).stream()
                .collect(Collectors.groupingBy(Space::getId));
        // 用户信息即当前登录用户，逐条填充即可，无需再查库
        return spaceUserList.stream().map(spaceUser -> {
            List<Space> spaceList = spaceIdSpaceListMap.get(spaceUser.getSpaceId());
            if (CollUtil.isEmpty(spaceList)) {
                return null;
            }
            Space space = spaceList.get(0);
            // 排除自己创建的团队空间：创建者已在「我创建的空间」里看到，这里再列一次会重复
            if (Objects.equals(space.getUserId(), loginUser.getId())) {
                return null;
            }
            SpaceUserVO spaceUserVO = SpaceUserVO.objToVo(spaceUser);
            spaceUserVO.setUser(userService.getUserVO(loginUser));
            spaceUserVO.setSpace(SpaceVO.entityToVo(space));
            return spaceUserVO;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    @Override
    public void validSpaceUser(SpaceUser spaceUser, boolean add) {
        ThrowUtils.throwIf(spaceUser == null, ErrorCode.PARAMS_ERROR);
        Long spaceId = spaceUser.getSpaceId();
        Long userId = spaceUser.getUserId();
        String spaceRole = spaceUser.getSpaceRole();
        if (add) {
            ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR, "空间 id 不能为空");
            ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "用户 id 不能为空");
            ThrowUtils.throwIf(StrUtil.isBlank(spaceRole), ErrorCode.PARAMS_ERROR, "空间角色不能为空");
        }
        if (ObjUtil.isNotEmpty(spaceId)) {
            Space space = spaceMapper.selectById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        }
        if (ObjUtil.isNotEmpty(userId)) {
            User user = userService.getById(userId);
            ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }
        if (StrUtil.isNotBlank(spaceRole)) {
            SpaceRoleEnum spaceRoleEnum = SpaceRoleEnum.getEnumByValue(spaceRole);
            ThrowUtils.throwIf(spaceRoleEnum == null, ErrorCode.PARAMS_ERROR, "空间角色不存在");
        }
    }

    @Override
    public QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest) {
        QueryWrapper<SpaceUser> queryWrapper = new QueryWrapper<>();
        if (spaceUserQueryRequest == null) {
            return queryWrapper;
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceUserQueryRequest.getId()), "id", spaceUserQueryRequest.getId());
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceUserQueryRequest.getSpaceId()), "spaceId", spaceUserQueryRequest.getSpaceId());
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceUserQueryRequest.getUserId()), "userId", spaceUserQueryRequest.getUserId());
        queryWrapper.eq(StrUtil.isNotBlank(spaceUserQueryRequest.getSpaceRole()), "spaceRole", spaceUserQueryRequest.getSpaceRole());
        return queryWrapper;
    }

    @Override
    public SpaceUserVO getSpaceUserVO(SpaceUser spaceUser) {
        SpaceUserVO spaceUserVO = SpaceUserVO.objToVo(spaceUser);
        if (spaceUserVO == null) {
            return null;
        }
        Long userId = spaceUser.getUserId();
        if (userId != null && userId > 0) {
            spaceUserVO.setUser(userService.getUserVO(userService.getById(userId)));
        }
        Long spaceId = spaceUser.getSpaceId();
        if (spaceId != null && spaceId > 0) {
            Space space = spaceMapper.selectById(spaceId);
            spaceUserVO.setSpace(SpaceVO.entityToVo(space));
        }
        return spaceUserVO;
    }

    @Override
    public SpaceUser getSpaceUser(Long spaceId, Long userId) {
        if (spaceId == null || userId == null) {
            return null;
        }
        return this.getOne(new QueryWrapper<SpaceUser>()
                .eq("spaceId", spaceId)
                .eq("userId", userId));
    }

    /**
     * 是否为空间成员管理者：空间创建者、团队管理员或系统管理员。
     * <p>
     * 这里直接查 space_user 表判断角色，而不是用 {@code StpUtil.hasPermission} 拼权限码：
     * 权限码由 {@code StpInterfaceImpl} 依据请求上下文动态计算，在 service 内部调用时
     * 拿不到可靠的 spaceId 来源，容易算出空权限把正常操作误判成越权。
     * 入口处的角色权限拦截交给 Controller 上的 {@code @SaSpaceCheckPermission}。
     */
    private boolean canManageMember(Space space, User loginUser) {
        if (Objects.equals(space.getUserId(), loginUser.getId())) {
            return true;
        }
        if (userService.isAdmin(loginUser)) {
            return true;
        }
        // 团队管理员可管理成员
        SpaceUser spaceUser = getSpaceUser(space.getId(), loginUser.getId());
        return spaceUser != null && SpaceRoleEnum.ADMIN.getValue().equals(spaceUser.getSpaceRole());
    }
}
