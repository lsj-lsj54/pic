package com.lsj.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lsj.demo.exception.BusinessException;
import com.lsj.demo.mapper.PictureMapper;
import com.lsj.demo.mapper.SpaceMapper;
import com.lsj.demo.model.dto.space.SpaceAnalyzeRequest;
import com.lsj.demo.model.entity.Picture;
import com.lsj.demo.model.entity.Space;
import com.lsj.demo.model.entity.User;
import com.lsj.demo.model.vo.space.SpaceCategoryAnalyzeVO;
import com.lsj.demo.model.vo.space.SpaceSizeAnalyzeVO;
import com.lsj.demo.model.vo.space.SpaceTagAnalyzeVO;
import com.lsj.demo.model.vo.space.SpaceTrendAnalyzeVO;
import com.lsj.demo.model.vo.space.SpaceUsageAnalyzeVO;
import com.lsj.demo.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 空间分析服务测试
 * <p>
 * 重点防回归：分析某个空间时，spaceId 必须拼进图片查询条件。
 * 曾经漏拼过这一步，导致「分析自己的空间」实际统计的是全系统数据（数据错误 + 越权泄露）。
 */
class SpaceAnalyzeServiceImplTest {

    /** 被测空间 */
    private static final long SPACE_ID = 10L;
    /** 空间创建者（也是登录用户） */
    private static final long OWNER_ID = 1L;

    private PictureMapper pictureMapper;

    private SpaceMapper spaceMapper;

    private UserService userService;

    private SpaceAnalyzeServiceImpl analyzeService;

    private User owner;

    @BeforeEach
    void setUp() {
        pictureMapper = mock(PictureMapper.class);
        spaceMapper = mock(SpaceMapper.class);
        userService = mock(UserService.class);

        owner = new User();
        owner.setId(OWNER_ID);
        owner.setUserRole("user");

        Space space = new Space();
        space.setId(SPACE_ID);
        space.setUserId(OWNER_ID);
        space.setSpaceName("旅行照片");
        space.setMaxSize(104857600L);
        space.setMaxCount(100L);
        space.setTotalSize(2048L);
        space.setTotalCount(2L);
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(space);

        when(userService.isAdmin(any(User.class))).thenReturn(false);
        when(pictureMapper.selectMaps(any(Wrapper.class))).thenReturn(new ArrayList<>());
        when(pictureMapper.selectObjs(any(Wrapper.class))).thenReturn(new ArrayList<>());

        analyzeService = new SpaceAnalyzeServiceImpl(pictureMapper, spaceMapper, userService);
    }

    /** 取出传给 mapper 的查询条件 SQL（WHERE / GROUP BY / ORDER BY） */
    private String captureSql() {
        ArgumentCaptor<Wrapper<Picture>> captor = ArgumentCaptor.forClass(Wrapper.class);
        org.mockito.Mockito.verify(pictureMapper, org.mockito.Mockito.atLeastOnce()).selectMaps(captor.capture());
        return captor.getValue().getSqlSegment();
    }

    /** 取出查询的 SELECT 字段段（getSqlSegment 不含 select 部分） */
    private String captureSelect() {
        ArgumentCaptor<Wrapper<Picture>> captor = ArgumentCaptor.forClass(Wrapper.class);
        org.mockito.Mockito.verify(pictureMapper, org.mockito.Mockito.atLeastOnce()).selectMaps(captor.capture());
        return captor.getValue().getSqlSelect();
    }

    /** 取出传给 mapper 的 selectObjs 查询条件 SQL */
    private String captureObjsSql() {
        ArgumentCaptor<Wrapper<Picture>> captor = ArgumentCaptor.forClass(Wrapper.class);
        org.mockito.Mockito.verify(pictureMapper, org.mockito.Mockito.atLeastOnce()).selectObjs(captor.capture());
        return captor.getValue().getSqlSegment();
    }

    @Test
    @DisplayName("分类分析：指定空间时必须带上 spaceId 条件")
    void categoryShouldFilterBySpaceId() {
        SpaceAnalyzeRequest request = new SpaceAnalyzeRequest();
        request.setSpaceId(SPACE_ID);

        analyzeService.category(request, owner);

        assertTrue(captureSql().contains("spaceId"),
                "分类分析缺少空间范围条件: " + captureSql());
    }

    @Test
    @DisplayName("标签分析：指定空间时必须带上 spaceId 条件")
    void tagShouldFilterBySpaceId() {
        SpaceAnalyzeRequest request = new SpaceAnalyzeRequest();
        request.setSpaceId(SPACE_ID);

        analyzeService.tag(request, owner);

        assertTrue(captureObjsSql().contains("spaceId"),
                "标签分析缺少空间范围条件: " + captureObjsSql());
    }

    @Test
    @DisplayName("大小分析：指定空间时必须带上 spaceId 条件")
    void sizeShouldFilterBySpaceId() {
        SpaceAnalyzeRequest request = new SpaceAnalyzeRequest();
        request.setSpaceId(SPACE_ID);

        analyzeService.size(request, owner);

        assertTrue(captureObjsSql().contains("spaceId"),
                "大小分析缺少空间范围条件: " + captureObjsSql());
    }

    @Test
    @DisplayName("上传趋势：指定空间时带上空间条件，并且维度影响时间格式")
    void trendShouldFilterBySpaceIdAndHonorDimension() {
        SpaceAnalyzeRequest request = new SpaceAnalyzeRequest();
        request.setSpaceId(SPACE_ID);
        request.setTimeDimension("month");

        analyzeService.trend(request, owner);

        String sql = captureSql();
        assertTrue(sql.contains("spaceId"), "趋势分析缺少空间范围条件: " + sql);
        assertTrue(captureSelect().contains("%Y-%m"), "按月维度应使用 %Y-%m 格式: " + captureSelect());
    }

    @Test
    @DisplayName("管理员全空间分析：不应带上 spaceId 条件（统计范围是全系统）")
    void allSpaceAnalyzeShouldNotFilterBySpaceId() {
        User admin = new User();
        admin.setId(99L);
        admin.setUserRole("admin");
        when(userService.isAdmin(admin)).thenReturn(true);

        SpaceAnalyzeRequest request = new SpaceAnalyzeRequest();
        request.setSpaceId(null);

        analyzeService.category(request, admin);

        assertFalse(captureSql().contains("spaceId"),
                "全空间分析不该带空间条件: " + captureSql());
    }

    @Test
    @DisplayName("普通用户不带 spaceId 做全空间分析应被拒绝")
    void normalUserShouldNotAnalyzeAllSpace() {
        SpaceAnalyzeRequest request = new SpaceAnalyzeRequest();
        request.setSpaceId(null);

        assertThrows(BusinessException.class, () -> analyzeService.category(request, owner));
    }

    @Test
    @DisplayName("排行仅管理员可用：服务层也要拦住普通用户")
    void rankShouldRejectNonAdmin() {
        SpaceAnalyzeRequest request = new SpaceAnalyzeRequest();
        assertThrows(BusinessException.class, () -> analyzeService.rank(request, owner));
    }

    @Test
    @DisplayName("未登录应被拒绝")
    void notLoginShouldBeRejected() {
        SpaceAnalyzeRequest request = new SpaceAnalyzeRequest();
        assertThrows(BusinessException.class, () -> analyzeService.usage(request, null));
    }

    @Test
    @DisplayName("单空间用量分析：直接读空间记录的用量与配额")
    void usageShouldReadSpaceQuota() {
        SpaceAnalyzeRequest request = new SpaceAnalyzeRequest();
        request.setSpaceId(SPACE_ID);

        SpaceUsageAnalyzeVO vo = analyzeService.usage(request, owner);

        assertEquals(2048L, vo.getUsedSize());
        assertEquals(104857600L, vo.getMaxSize());
        assertEquals(2L, vo.getUsedCount());
        assertEquals(100L, vo.getMaxCount());
        assertEquals(Boolean.FALSE, vo.getAllSpace());
        // 2048 / 104857600 ≈ 0.0%
        assertEquals(0.0, vo.getSizeRatio());
    }

    @Test
    @DisplayName("分类分析：空分类与 null 分类都归入「未分类」")
    void categoryShouldConvertBlankToUncategorized() {
        Map<String, Object> row = new HashMap<>();
        row.put("category", "未分类");
        row.put("count", 3L);
        row.put("totalSize", 1024L);
        when(pictureMapper.selectMaps(any(Wrapper.class))).thenReturn(Collections.singletonList(row));

        SpaceAnalyzeRequest request = new SpaceAnalyzeRequest();
        request.setSpaceId(SPACE_ID);
        List<SpaceCategoryAnalyzeVO> result = analyzeService.category(request, owner);

        assertEquals(1, result.size());
        assertEquals("未分类", result.get(0).getCategory());
        assertEquals(3L, result.get(0).getCount());
        assertTrue(captureSql().contains("category"), "分类统计应基于 category 列");
    }

    @Test
    @DisplayName("大小分析：三个区间始终返回，便于前端画饼图")
    void sizeShouldAlwaysReturnThreeBuckets() {
        when(pictureMapper.selectObjs(any(Wrapper.class)))
                .thenReturn(new ArrayList<Object>(Arrays.asList(1024L, 500L * 1024, 2L * 1024 * 1024)));

        SpaceAnalyzeRequest request = new SpaceAnalyzeRequest();
        request.setSpaceId(SPACE_ID);
        List<SpaceSizeAnalyzeVO> result = analyzeService.size(request, owner);

        assertEquals(3, result.size());
        assertEquals("< 100 KB", result.get(0).getSizeRange());
        assertEquals(1L, result.get(0).getCount());
        assertEquals(1L, result.get(1).getCount());
        assertEquals(1L, result.get(2).getCount());
    }

    @Test
    @DisplayName("标签分析：按关联数量倒序并受 limit 限制")
    void tagShouldSortByCountDescAndLimit() {
        when(pictureMapper.selectObjs(any(Wrapper.class)))
                .thenReturn(new ArrayList<Object>(Arrays.asList(
                        "[\"风景\",\"海\"]", "[\"风景\"]", "[\"人物\"]", "[\"风景\"]")));

        SpaceAnalyzeRequest request = new SpaceAnalyzeRequest();
        request.setSpaceId(SPACE_ID);
        request.setLimit(2);
        List<SpaceTagAnalyzeVO> result = analyzeService.tag(request, owner);

        // 「风景」出现 3 次应排第一；「海」「人物」同为 1 次，顺序取决于插入顺序不做要求
        assertEquals(2, result.size());
        assertEquals("风景", result.get(0).getTag());
        assertEquals(3L, result.get(0).getCount().longValue());
        assertEquals(1L, result.get(1).getCount().longValue());
        assertTrue(Arrays.asList("海", "人物").contains(result.get(1).getTag()));
    }

    @Test
    @DisplayName("标签分析：脏数据不应让整个接口失败")
    void tagShouldSkipBrokenJson() {
        when(pictureMapper.selectObjs(any(Wrapper.class)))
                .thenReturn(new ArrayList<Object>(Arrays.asList("not-json", "[\"风景\"]")));

        SpaceAnalyzeRequest request = new SpaceAnalyzeRequest();
        request.setSpaceId(SPACE_ID);
        List<SpaceTagAnalyzeVO> result = analyzeService.tag(request, owner);

        assertEquals(1, result.size());
        assertEquals("风景", result.get(0).getTag());
    }

    @Test
    @DisplayName("上传趋势：非法时间维度应报参数错误")
    void trendShouldRejectUnknownDimension() {
        SpaceAnalyzeRequest request = new SpaceAnalyzeRequest();
        request.setSpaceId(SPACE_ID);
        request.setTimeDimension("year");

        assertThrows(BusinessException.class, () -> analyzeService.trend(request, owner));
    }

    @Test
    @DisplayName("上传趋势：维度为空时按天统计")
    void trendShouldDefaultToDay() {
        SpaceAnalyzeRequest request = new SpaceAnalyzeRequest();
        request.setSpaceId(SPACE_ID);

        analyzeService.trend(request, owner);

        assertTrue(captureSelect().contains("%Y-%m-%d"), "默认应按天统计: " + captureSelect());
    }

    @Test
    @DisplayName("上传趋势：按周统计使用 %Y-%u 格式")
    void trendShouldSupportWeek() {
        SpaceAnalyzeRequest request = new SpaceAnalyzeRequest();
        request.setSpaceId(SPACE_ID);
        request.setTimeDimension("week");

        List<SpaceTrendAnalyzeVO> result = analyzeService.trend(request, owner);

        assertTrue(result.isEmpty());
        assertTrue(captureSelect().contains("%Y-%u"), "按周应使用 %Y-%u 格式: " + captureSelect());
    }
}
