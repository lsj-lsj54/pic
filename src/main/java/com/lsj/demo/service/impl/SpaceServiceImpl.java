package com.lsj.demo.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.lsj.demo.common.SpacePermissionUtils;
import com.lsj.demo.exception.BusinessException;
import com.lsj.demo.exception.ErrorCode;
import com.lsj.demo.exception.ThrowUtils;
import com.lsj.demo.mapper.SpaceMapper;
import com.lsj.demo.model.dto.space.SpaceAddRequest;
import com.lsj.demo.model.dto.space.SpaceDeleteRequest;
import com.lsj.demo.model.dto.space.SpaceQueryRequest;
import com.lsj.demo.model.entity.Picture;
import com.lsj.demo.model.entity.Space;
import com.lsj.demo.model.entity.User;
import com.lsj.demo.model.enums.SpaceLevelEnum;
import com.lsj.demo.model.vo.SpaceVO;
import com.lsj.demo.service.PictureService;
import com.lsj.demo.service.SpaceService;
import com.lsj.demo.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.CollectionUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


/**
 * @author lsj
 * @description 针对表【space(空间)】的数据库操作Service实现
 * @createDate 2026-10-13 23:36:58
 */
@Service
@Slf4j
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space>
        implements SpaceService {

    private final UserService userService;

    private final TransactionTemplate transactionTemplate;

    /**
     * 删除空间时要连带清理空间下的图片，所以依赖图片服务。
     * <p>
     * 图片服务为了做空间权限校验又反过来依赖空间服务，两个 Bean 互相依赖，
     * 这里必须用 @Lazy 打断（Spring Boot 2.6 起默认禁止循环依赖，不加会启动失败）。
     */
    @Autowired
    @Lazy
    private PictureService pictureService;

    /**
     * 按用户加锁：同一个账号并发创建空间时，防止插入两条同名空间
     */
    private final Map<Long, Object> lockmap = new ConcurrentHashMap<>();

    public SpaceServiceImpl(UserService userService, TransactionTemplate transactionTemplate) {
        this.userService = userService;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void validSpace(Space space, boolean add) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);
        // 要创建
        if (add) {
            if (StrUtil.isBlank(spaceName)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称不能为空");
            }
            if (spaceLevel == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不能为空");
            }
        }
        // 修改数据时，如果要改空间级别
        if (spaceLevel != null && spaceLevelEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不存在");
        }
        if (StrUtil.isNotBlank(spaceName) && spaceName.length() > 30) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称过长");
        }
    }


    @Override
    public void fillSpaceBySpaceLevel(Space space) {
        // 根据空间级别，自动填充限额
        //如果space自带的话就用自带的
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());
        if (spaceLevelEnum != null) {
            long maxSize = spaceLevelEnum.getMaxSize();
            if (space.getMaxSize() == null) {
                space.setMaxSize(maxSize);
            }
            long maxCount = spaceLevelEnum.getMaxCount();
            if (space.getMaxCount() == null) {
                space.setMaxCount(maxCount);
            }
        }
    }


    @Override
    public long addSpace(SpaceAddRequest spaceAddRequest, User loginUser, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceAddRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NO_AUTH_ERROR);
        Space space = new Space();
        BeanUtils.copyProperties(spaceAddRequest, space);
        // 空间归创建者所有，userId 必须在这里落上，否则数据库的 userId 非空约束会直接插入失败
        space.setUserId(loginUser.getId());
        validSpace(space, true);
        fillSpaceBySpaceLevel(space);
        // 普通用户只能创建普通版空间：专业版、旗舰版必须由管理员创建
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());
        if (!userService.isAdmin(loginUser) && !SpaceLevelEnum.COMMON.equals(spaceLevelEnum)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "非管理员只能创建普通空间");
        }
        // 同一用户串行创建，避免并发提交绕过去重校验
        Object lock = lockmap.computeIfAbsent(loginUser.getId(), k -> new Object());
        synchronized (lock) {
            try {
                Long newSpaceId = transactionTemplate.execute(status -> {
                    // 校验该用户是否已经有同名同级别的空间
                    SpaceQueryRequest spaceQueryRequest = new SpaceQueryRequest();
                    spaceQueryRequest.setUserId(loginUser.getId());
                    spaceQueryRequest.setSpaceName(space.getSpaceName());
                    spaceQueryRequest.setSpaceLevel(space.getSpaceLevel());
                    Space exit = this.getOne(getQueryWrapper(spaceQueryRequest));
                    ThrowUtils.throwIf(exit != null, ErrorCode.PARAMS_ERROR, "用户空间已经存在");
                    ThrowUtils.throwIf(!save(space), ErrorCode.SYSTEM_ERROR, "数据库插入失败");
                    return space.getId();
                });
                return Optional.ofNullable(newSpaceId).orElse(-1L);
            } finally {
                lockmap.remove(loginUser.getId());
            }
        }
    }


    @Override
    public SpaceVO getSpaceVO(Space space, HttpServletRequest request) {
        // 实体转封装类统一走 VO 的工厂方法
        SpaceVO spaceVO = SpaceVO.entityToVo(space);
        if (spaceVO == null) {
            return null;
        }
        // 关联查询创建人信息，前端列表要展示空间归属
        Long userId = space.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            spaceVO.setUser(userService.getUserVO(user));
        }
        return spaceVO;
    }

    @Override
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request) {
        ThrowUtils.throwIf(spacePage == null, ErrorCode.PARAMS_ERROR);
        List<Space> spaceList = spacePage.getRecords();
        Page<SpaceVO> spaceVOPage = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());
        if (CollUtil.isEmpty(spaceList)) {
            return spaceVOPage;
        }
        // 实体列表 => 封装对象列表
        List<SpaceVO> spaceVOList = spaceList.stream().map(SpaceVO::entityToVo).collect(Collectors.toList());
        // 1. 批量查询创建人信息，避免逐条查询造成 N+1
        Set<Long> userIdSet = spaceList.stream().map(Space::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userIdSet.isEmpty() ? Collections.emptyMap()
                : userService.listByIds(userIdSet).stream().collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        spaceVOList.forEach(spaceVO -> {
            List<User> userList = userIdUserListMap.get(spaceVO.getUserId());
            spaceVO.setUser(userService.getUserVO(CollUtil.isEmpty(userList) ? null : userList.get(0)));
        });
        spaceVOPage.setRecords(spaceVOList);
        return spaceVOPage;
    }

    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {

        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        if (spaceQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = spaceQueryRequest.getId();
        Long userId = spaceQueryRequest.getUserId();
        String spaceName = spaceQueryRequest.getSpaceName();
        Integer spaceLevel = spaceQueryRequest.getSpaceLevel();
        String sortField = spaceQueryRequest.getSortField();
        String sortOrder = spaceQueryRequest.getSortOrder();

        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        // 空间名用精确匹配：创建空间时的重名校验也走这里，模糊匹配会把「空间a」和「空间ab」判成重复
        queryWrapper.eq(StrUtil.isNotBlank(spaceName), "spaceName", spaceName);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceLevel), "spaceLevel", spaceLevel);
        // 排序：常量写在前面，sortOrder 为 null 时不会抛 NPE
        queryWrapper.orderBy(StrUtil.isNotBlank(sortField), "ascend".equals(sortOrder), sortField);

        return queryWrapper;
    }

    @Override
    public boolean deleteSpace(SpaceDeleteRequest spaceDeleteRequest, User loginUser) {
        ThrowUtils.throwIf(spaceDeleteRequest == null || spaceDeleteRequest.getId() == null,
                ErrorCode.PARAMS_ERROR, "空间 id 不能为空");
        final Long spaceId = spaceDeleteRequest.getId();
        // 权限以库里的数据为准：请求体里的 userId 是前端传的，不能拿它判断归属
        Space space = this.getById(spaceId);
        SpacePermissionUtils.checkSpaceAuth(space, loginUser, userService);
        transactionTemplate.execute((status -> {
            // 删除空间
            ThrowUtils.throwIf(!removeById(spaceId), ErrorCode.OPERATION_ERROR, "删除失败");
            // 删除相关图片：先删库，再清对象存储
            List<Picture> pictures = pictureService.getListPictureBySpaceId(space);
            if (!CollectionUtils.isEmpty(pictures)) {
                pictureService.deleteBatchIds(pictures.stream().map(Picture::getId).collect(Collectors.toList()));
                pictures.forEach(pictureService::clearPictureFile);
            }
            return null;
        }));
        return true;
    }

    @Override
    public void updateSpaceUsage(Long spaceId, long countDelta, long sizeDelta) {
        if (spaceId == null || (countDelta == 0 && sizeDelta == 0)) {
            return;
        }
        // 用 SQL 自增/自减，避免「先查询再更新」在并发上传时相互覆盖
        boolean updated = this.lambdaUpdate()
                .eq(Space::getId, spaceId)
                .setSql(countDelta != 0, "totalCount = totalCount + (" + countDelta + ")")
                .setSql(sizeDelta != 0, "totalSize = totalSize + (" + sizeDelta + ")")
                .update();
        if (!updated) {
            // 空间可能已被删除，属于可接受情况，不影响图片本身的增删
            log.warn("空间容量更新失败，空间可能已被删除，spaceId = {}", spaceId);
        }
    }


}
