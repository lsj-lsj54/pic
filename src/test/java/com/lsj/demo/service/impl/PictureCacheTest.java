package com.lsj.demo.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lsj.demo.model.dto.picture.PictureQueryRequest;
import com.lsj.demo.model.vo.PictureVO;
import com.lsj.demo.model.vo.UserVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 图片列表缓存的序列化与缓存 key 测试
 * <p>
 * 都在访问 Redis 之前完成，因此不需要启动 Redis。
 * 重点覆盖泛型反序列化：Page&lt;PictureVO&gt; 用 toBean(x, Page.class) 还原会丢掉
 * records 的泛型信息，拿到的是 JSONObject 列表，只有在取值时才抛异常，很容易漏测。
 */
class PictureCacheTest {

    private final PictureServiceImpl service = new PictureServiceImpl();

    /* ------------------------------ 反射调用私有方法 ------------------------------ */

    @SuppressWarnings("unchecked")
    private String serializePage(Page<PictureVO> page) throws Exception {
        Method method = PictureServiceImpl.class.getDeclaredMethod("serializePage", Page.class);
        method.setAccessible(true);
        return (String) method.invoke(service, page);
    }

    private Page<PictureVO> parseCachedPage(String json) throws Exception {
        Method method = PictureServiceImpl.class.getDeclaredMethod("parseCachedPage", String.class);
        method.setAccessible(true);
        return (Page<PictureVO>) method.invoke(service, json);
    }

    private String buildCacheCondition(PictureQueryRequest request) throws Exception {
        Method method = PictureServiceImpl.class.getDeclaredMethod(
                "buildCacheCondition", PictureQueryRequest.class);
        method.setAccessible(true);
        return (String) method.invoke(service, request);
    }

    /** 造一条带嵌套用户信息的分页数据，尽量贴近真实返回 */
    private Page<PictureVO> buildPage() {
        UserVO userVO = new UserVO();
        userVO.setId(1L);
        userVO.setUserAccount("lsj");
        userVO.setUserName("吕世杰");

        PictureVO pictureVO = new PictureVO();
        pictureVO.setId(2109567101075230721L);
        pictureVO.setName("测试图片");
        pictureVO.setUrl("https://example.com/a.jpg");
        pictureVO.setIntroduction("简介");
        pictureVO.setTags(Arrays.asList("风景", "海边"));
        pictureVO.setCategory("风景");
        pictureVO.setPicSize(84293L);
        pictureVO.setPicWidth(800);
        pictureVO.setPicHeight(600);
        pictureVO.setPicScale(1.33);
        pictureVO.setPicFormat("jpeg");
        pictureVO.setUserId(1L);
        pictureVO.setReviewStatus(1);
        pictureVO.setUser(userVO);

        Page<PictureVO> page = new Page<>(2L, 12L, 25L);
        page.setRecords(new ArrayList<>(Arrays.asList(pictureVO)));
        return page;
    }

    /* ------------------------------ 序列化往返 ------------------------------ */

    @Test
    @DisplayName("分页对象应能完整往返序列化")
    void shouldRoundTripPage() throws Exception {
        Page<PictureVO> original = buildPage();
        Page<PictureVO> restored = parseCachedPage(serializePage(original));

        assertEquals(original.getTotal(), restored.getTotal(), "total 丢失");
        assertEquals(original.getSize(), restored.getSize(), "size 丢失");
        assertEquals(original.getCurrent(), restored.getCurrent(), "current 丢失");
        assertEquals(1, restored.getRecords().size(), "records 数量不对");
    }

    @Test
    @DisplayName("records 里的元素必须是 PictureVO，而不是 JSONObject")
    void recordsShouldBeTypedAsPictureVO() throws Exception {
        Page<PictureVO> restored = parseCachedPage(serializePage(buildPage()));
        Object first = restored.getRecords().get(0);

        // 这一条是核心：用错反序列化方式的话这里会是 JSONObject
        assertInstanceOf(PictureVO.class, first,
                "records 元素类型不是 PictureVO，说明泛型信息丢了");
        // 取字段不应抛异常
        PictureVO vo = (PictureVO) first;
        assertEquals("测试图片", vo.getName());
        assertEquals(2109567101075230721L, vo.getId());
        assertEquals("jpeg", vo.getPicFormat());
    }

    @Test
    @DisplayName("嵌套的用户信息与标签列表也应保留")
    void nestedFieldsShouldSurvive() throws Exception {
        Page<PictureVO> restored = parseCachedPage(serializePage(buildPage()));
        PictureVO vo = restored.getRecords().get(0);

        assertEquals(2, vo.getTags().size(), "标签列表丢失");
        assertTrue(vo.getTags().contains("风景"));
        assertEquals("lsj", vo.getUser().getUserAccount(), "嵌套用户信息丢失");
    }

    @Test
    @DisplayName("对照组：用 Page.class 反序列化会丢掉泛型（这正是不能用它的原因）")
    void toBeanWithRawPageClassLosesGenericType() throws Exception {
        String json = serializePage(buildPage());

        Page<?> raw = cn.hutool.json.JSONUtil.toBean(json, Page.class);
        Object first = raw.getRecords().get(0);

        // 记录下真实行为：元素不是 PictureVO 而是 JSONObject，
        // 后续 (PictureVO) first 或取值时才炸，属于很难定位的静默问题
        assertTrue(!(first instanceof PictureVO),
                "如果这里已经是 PictureVO，说明 Hutool 行为变了，可以简化 parseCachedPage");
    }

    /* ------------------------------ 缓存 key 稳定性 ------------------------------ */

    @Test
    @DisplayName("相同条件的查询应生成相同的缓存条件串")
    void sameConditionShouldProduceSameKey() throws Exception {
        PictureQueryRequest a = baseRequest();
        PictureQueryRequest b = baseRequest();
        assertEquals(buildCacheCondition(a), buildCacheCondition(b));
    }

    @Test
    @DisplayName("标签顺序不同不应视作不同查询")
    void tagOrderShouldNotMatter() throws Exception {
        PictureQueryRequest a = baseRequest();
        a.setTags(Arrays.asList("风景", "海边"));
        PictureQueryRequest b = baseRequest();
        b.setTags(Arrays.asList("海边", "风景"));

        assertEquals(buildCacheCondition(a), buildCacheCondition(b),
                "标签顺序不同生成了不同的 key，会白白多存一份缓存");
    }

    @Test
    @DisplayName("排序方向大小写不同不应视作不同查询")
    void sortOrderCaseShouldNotMatter() throws Exception {
        PictureQueryRequest a = baseRequest();
        a.setSortOrder("ASC");
        PictureQueryRequest b = baseRequest();
        b.setSortOrder("asc");

        assertEquals(buildCacheCondition(a), buildCacheCondition(b));
    }

    @Test
    @DisplayName("页码、搜索词、分类不同应生成不同的 key")
    void differentConditionShouldProduceDifferentKey() throws Exception {
        PictureQueryRequest base = baseRequest();

        PictureQueryRequest otherPage = baseRequest();
        otherPage.setCurrent(2);

        PictureQueryRequest otherSearch = baseRequest();
        otherSearch.setSearchText("海");

        PictureQueryRequest otherCategory = baseRequest();
        otherCategory.setCategory("人物");

        String baseKey = buildCacheCondition(base);
        assertNotEquals(baseKey, buildCacheCondition(otherPage), "页码没有参与 key");
        assertNotEquals(baseKey, buildCacheCondition(otherSearch), "搜索词没有参与 key");
        assertNotEquals(baseKey, buildCacheCondition(otherCategory), "分类没有参与 key");
    }

    @Test
    @DisplayName("字段为 null 时应能正常拼装，不应抛异常")
    void nullFieldsShouldNotThrow() throws Exception {
        PictureQueryRequest request = new PictureQueryRequest();
        request.setCurrent(1);
        request.setPageSize(12);
        request.setSortOrder(null);
        request.setTags(null);
        // 不抛异常且包含 null 的占位即可
        String condition = buildCacheCondition(request);
        assertTrue(condition.contains("current=1"), condition);
        assertTrue(condition.contains("tags") == false, "tags 为空时不该出现在 key 里");
    }

    /** 构造一个字段齐全的基础查询请求 */
    private PictureQueryRequest baseRequest() {
        PictureQueryRequest request = new PictureQueryRequest();
        request.setCurrent(1);
        request.setPageSize(12);
        request.setReviewStatus(1);
        request.setSortField("createTime");
        request.setSortOrder("descend");
        return request;
    }

    /* ------------------------------ 清缓存 ------------------------------ */

    @Test
    @DisplayName("清缓存方法应可重复调用且不抛异常")
    void clearCacheShouldBeIdempotent() throws Exception {
        service.clearPictureCache();
        service.clearPictureCache();

        // 清完再查本地缓存应为空，确认确实清掉了
        java.lang.reflect.Field field = PictureServiceImpl.class.getDeclaredField("LOCAL_CACHE");
        field.setAccessible(true);
        com.github.benmanes.caffeine.cache.Cache<?, ?> cache =
                (com.github.benmanes.caffeine.cache.Cache<?, ?>) field.get(service);
        assertEquals(0, cache.estimatedSize(), "clearPictureCache 没有真正清空本地缓存");
    }
}
