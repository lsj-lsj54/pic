package com.lsj.demo.model.vo;

import com.lsj.demo.model.entity.Picture;
import com.lsj.demo.model.entity.Space;
import com.lsj.demo.model.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VO 与实体互转的测试
 * <p>
 * voToEntity / entityToVo 是各 service 里转换的统一入口，
 * 一旦字段映射写错（尤其是 tags 的 JSON 转换、缩略图回退），
 * 会出现「列表能查出来但字段是空的」这种很难定位的问题。
 */
class VoConvertTest {

    /* ------------------------------ SpaceVO ------------------------------ */

    @Test
    @DisplayName("SpaceVO 与实体应能双向转换")
    void spaceVoShouldRoundTrip() {
        Space space = new Space();
        space.setId(10L);
        space.setSpaceName("我的空间");
        space.setSpaceLevel(1);
        space.setMaxSize(1024L);
        space.setMaxCount(100L);
        space.setTotalSize(512L);
        space.setTotalCount(3L);
        space.setUserId(7L);

        SpaceVO spaceVO = SpaceVO.entityToVo(space);
        assertNotNull(spaceVO);
        assertEquals("我的空间", spaceVO.getSpaceName());
        assertEquals(1, spaceVO.getSpaceLevel());
        assertEquals(7L, spaceVO.getUserId());

        Space back = SpaceVO.voToEntity(spaceVO);
        assertNotNull(back);
        assertEquals(space.getId(), back.getId());
        assertEquals(space.getSpaceName(), back.getSpaceName());
        assertEquals(space.getSpaceLevel(), back.getSpaceLevel());
        assertEquals(space.getMaxSize(), back.getMaxSize());
        assertEquals(space.getMaxCount(), back.getMaxCount());
        assertEquals(space.getTotalSize(), back.getTotalSize());
        assertEquals(space.getTotalCount(), back.getTotalCount());
        assertEquals(space.getUserId(), back.getUserId());
    }

    @Test
    @DisplayName("SpaceVO 转换遇到 null 应返回 null，而不是抛异常")
    void spaceVoShouldHandleNull() {
        assertNull(SpaceVO.entityToVo(null));
        assertNull(SpaceVO.voToEntity(null));
    }

    /* ------------------------------ PictureVO ------------------------------ */

    @Test
    @DisplayName("PictureVO 转换应把 tags 在 JSON 字符串与列表之间互转")
    void pictureVoShouldConvertTags() {
        Picture picture = new Picture();
        picture.setId(100L);
        picture.setName("海边");
        picture.setUrl("https://example.com/a.jpg");
        picture.setThumbnailUrl("https://example.com/a.webp");
        picture.setTags("[\"风景\",\"海边\"]");
        picture.setSpaceId(10L);
        picture.setUserId(7L);

        PictureVO pictureVO = PictureVO.entityToVo(picture);
        assertNotNull(pictureVO);
        assertEquals(Arrays.asList("风景", "海边"), pictureVO.getTags());
        assertEquals(10L, pictureVO.getSpaceId(), "空间归属必须传出去，否则前端分不清公共图库和私有空间");

        Picture back = PictureVO.voToEntity(pictureVO);
        assertNotNull(back);
        assertEquals("[\"风景\",\"海边\"]", back.getTags(), "tags 必须还原成 JSON 字符串再入库");
        assertEquals(Long.valueOf(10L), back.getSpaceId());
    }

    @Test
    @DisplayName("历史数据没有缩略图时应回退成原图，避免前端裂图")
    void pictureVoShouldFallbackThumbnail() {
        Picture picture = new Picture();
        picture.setUrl("https://example.com/a.jpg");

        PictureVO pictureVO = PictureVO.entityToVo(picture);
        assertNotNull(pictureVO);
        assertEquals("https://example.com/a.jpg", pictureVO.getThumbnailUrl());
    }

    @Test
    @DisplayName("PictureVO 转换遇到 null 应返回 null")
    void pictureVoShouldHandleNull() {
        assertNull(PictureVO.entityToVo(null));
        assertNull(PictureVO.voToEntity(null));
    }

    /* ------------------------------ UserVO / LoginUserVO ------------------------------ */

    @Test
    @DisplayName("UserVO 与实体应能双向转换，且不带上密码")
    void userVoShouldRoundTripWithoutPassword() {
        User user = new User();
        user.setId(1L);
        user.setUserAccount("lsj");
        user.setUserName("吕世杰");
        user.setUserRole("admin");
        user.setUserPassword("加密后的密码");
        user.setCreateTime(new Date(0L));

        UserVO userVO = UserVO.entityToVo(user);
        assertNotNull(userVO);
        assertEquals("lsj", userVO.getUserAccount());
        assertEquals("admin", userVO.getUserRole());

        User back = UserVO.voToEntity(userVO);
        assertNotNull(back);
        assertEquals(user.getId(), back.getId());
        assertEquals(user.getUserAccount(), back.getUserAccount());
        // VO 里没有密码字段，回写时不能凭空造出一个密码
        assertNull(back.getUserPassword());
    }

    @Test
    @DisplayName("LoginUserVO 与实体应能双向转换，且不带上密码")
    void loginUserVoShouldRoundTripWithoutPassword() {
        User user = new User();
        user.setId(1L);
        user.setUserAccount("lsj");
        user.setUserPassword("加密后的密码");

        LoginUserVO loginUserVO = LoginUserVO.entityToVo(user);
        assertNotNull(loginUserVO);
        assertEquals("lsj", loginUserVO.getUserAccount());

        User back = LoginUserVO.voToEntity(loginUserVO);
        assertNotNull(back);
        assertNull(back.getUserPassword());
        assertEquals(Long.valueOf(1L), back.getId());
    }

    @Test
    @DisplayName("UserVO / LoginUserVO 转换遇到 null 应返回 null")
    void userVoShouldHandleNull() {
        assertNull(UserVO.entityToVo(null));
        assertNull(UserVO.voToEntity(null));
        assertNull(LoginUserVO.entityToVo(null));
        assertNull(LoginUserVO.voToEntity(null));
    }

    @Test
    @DisplayName("属性为 null 时转换不应抛异常")
    void nullFieldsShouldNotThrow() {
        assertTrue(SpaceVO.entityToVo(new Space()) != null);
        assertTrue(PictureVO.entityToVo(new Picture()) != null);
    }
}
