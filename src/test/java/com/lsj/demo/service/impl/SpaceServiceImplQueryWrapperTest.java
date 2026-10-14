package com.lsj.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lsj.demo.model.dto.space.SpaceQueryRequest;
import com.lsj.demo.model.entity.Space;
import com.lsj.demo.model.entity.User;
import com.lsj.demo.model.vo.SpaceVO;
import com.lsj.demo.model.vo.UserVO;
import com.lsj.demo.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 空间查询条件构造与 VO 转换的测试
 * <p>
 * 主要防回归：
 * 1. 排序方向比较必须写成「常量在前」，否则 sortOrder 为 null 时会抛 NPE；
 * 2. 空间级别、用户 id 等条件必须真的进了 SQL，否则筛选条件会被静默忽略；
 * 3. getSpaceVO / getSpaceVOPage 以前直接返回 null，接口等于不可用。
 */
class SpaceServiceImplQueryWrapperTest {

    private final UserService userService = mock(UserService.class);

    /** TransactionTemplate 只在真正写库时才会用到，构造查询条件不需要 */
    private final SpaceServiceImpl spaceService = new SpaceServiceImpl(userService, null);

    private Space space(long id, String name, Long userId) {
        Space space = new Space();
        space.setId(id);
        space.setSpaceName(name);
        space.setSpaceLevel(0);
        space.setUserId(userId);
        return space;
    }

    /* ------------------------------ 查询条件 ------------------------------ */

    @Test
    @DisplayName("sortOrder 为 null 时不应抛异常")
    void nullSortOrderShouldNotThrow() {
        SpaceQueryRequest request = new SpaceQueryRequest();
        request.setSortField("createTime");
        request.setSortOrder(null);
        assertDoesNotThrow(() -> spaceService.getQueryWrapper(request));
    }

    @Test
    @DisplayName("请求为 null 时应返回空条件而不是报错")
    void nullRequestShouldReturnEmptyWrapper() {
        QueryWrapper<Space> wrapper = spaceService.getQueryWrapper(null);
        assertTrue(wrapper.getSqlSegment().isEmpty(),
                "空请求不该带查询条件: " + wrapper.getSqlSegment());
    }

    @Test
    @DisplayName("空间级别应进入查询条件")
    void spaceLevelShouldBeInCondition() {
        SpaceQueryRequest request = new SpaceQueryRequest();
        request.setSpaceLevel(1);

        QueryWrapper<Space> wrapper = spaceService.getQueryWrapper(request);
        String sql = wrapper.getSqlSegment().toLowerCase();
        assertTrue(sql.contains("spacelevel"), "缺少空间级别条件: " + sql);
    }

    @Test
    @DisplayName("用户 id 与空间名称应进入查询条件")
    void userAndNameShouldBeInCondition() {
        SpaceQueryRequest request = new SpaceQueryRequest();
        request.setUserId(7L);
        request.setSpaceName("我的空间");

        QueryWrapper<Space> wrapper = spaceService.getQueryWrapper(request);
        String sql = wrapper.getSqlSegment().toLowerCase();
        assertTrue(sql.contains("userid"), "缺少用户条件: " + sql);
        assertTrue(sql.contains("spacename"), "缺少空间名称条件: " + sql);
    }

    @Test
    @DisplayName("ascend 应生成升序、descend 应生成降序")
    void sortOrderShouldWork() {
        SpaceQueryRequest ascend = new SpaceQueryRequest();
        ascend.setSortField("createTime");
        ascend.setSortOrder("ascend");
        assertTrue(spaceService.getQueryWrapper(ascend).getSqlSegment().toUpperCase().contains("ASC"));

        SpaceQueryRequest descend = new SpaceQueryRequest();
        descend.setSortField("createTime");
        descend.setSortOrder("descend");
        assertTrue(spaceService.getQueryWrapper(descend).getSqlSegment().toUpperCase().contains("DESC"));
    }

    /* ------------------------------ 单条 VO ------------------------------ */

    @Test
    @DisplayName("getSpaceVO 应转出 VO 并带上创建人信息")
    void getSpaceVOShouldReturnVoWithUser() {
        User owner = new User();
        owner.setId(7L);
        owner.setUserAccount("lsj");
        UserVO ownerVO = new UserVO();
        ownerVO.setId(7L);
        ownerVO.setUserAccount("lsj");
        when(userService.getById(7L)).thenReturn(owner);
        when(userService.getUserVO(any(User.class))).thenReturn(ownerVO);

        SpaceVO spaceVO = spaceService.getSpaceVO(space(10L, "我的空间", 7L), null);

        assertNotNull(spaceVO, "getSpaceVO 不能返回 null");
        assertEquals("我的空间", spaceVO.getSpaceName());
        assertEquals(Long.valueOf(10L), spaceVO.getId());
        assertNotNull(spaceVO.getUser(), "缺少创建人信息，前端列表展示不出来");
        assertEquals("lsj", spaceVO.getUser().getUserAccount());
    }

    @Test
    @DisplayName("getSpaceVO 传 null 应返回 null")
    void getSpaceVOShouldHandleNull() {
        assertNull(spaceService.getSpaceVO(null, null));
    }

    /* ------------------------------ 分页 VO ------------------------------ */

    @Test
    @DisplayName("getSpaceVOPage 应转出分页 VO 并保留分页信息")
    void getSpaceVOPageShouldMapRecords() {
        User owner = new User();
        owner.setId(7L);
        UserVO ownerVO = new UserVO();
        ownerVO.setId(7L);
        when(userService.listByIds(any())).thenReturn(Arrays.asList(owner));
        when(userService.getUserVO(any(User.class))).thenReturn(ownerVO);

        List<Space> records = new ArrayList<>(Arrays.asList(
                space(10L, "空间A", 7L), space(11L, "空间B", 7L)));
        Page<Space> spacePage = new Page<>(2L, 10L, 25L);
        spacePage.setRecords(records);

        Page<SpaceVO> voPage = spaceService.getSpaceVOPage(spacePage, null);

        assertNotNull(voPage, "getSpaceVOPage 不能返回 null");
        assertEquals(2L, voPage.getCurrent());
        assertEquals(10L, voPage.getSize());
        assertEquals(25L, voPage.getTotal());
        assertEquals(2, voPage.getRecords().size());
        assertEquals("空间A", voPage.getRecords().get(0).getSpaceName());
        assertNotNull(voPage.getRecords().get(0).getUser());
    }

    @Test
    @DisplayName("getSpaceVOPage 空列表应返回空分页，不报错")
    void getSpaceVOPageShouldHandleEmptyRecords() {
        Page<Space> spacePage = new Page<>(1L, 10L, 0L);
        spacePage.setRecords(new ArrayList<>());

        Page<SpaceVO> voPage = spaceService.getSpaceVOPage(spacePage, null);

        assertNotNull(voPage);
        assertTrue(voPage.getRecords().isEmpty());
    }
}
