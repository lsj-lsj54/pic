package com.lsj.demo.service.impl;

import com.lsj.demo.exception.BusinessException;
import com.lsj.demo.manager.CosManager;
import com.lsj.demo.model.entity.Picture;
import com.lsj.demo.model.entity.Space;
import com.lsj.demo.model.entity.User;
import com.lsj.demo.service.SpaceService;
import com.lsj.demo.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * 删除图片流程的测试
 * <p>
 * 删除是「空间校验 + 删库 + 释放空间容量 + 清缓存 + 清对象存储」几件事，
 * 这里验证权限拦得住、该做的都做了、顺序正确、且异常场景有合理行为。
 */
class PictureDeleteTest {

    private static final long OWNER_ID = 1L;

    /**
     * 构造一个假的 Service：不连数据库，也不真的删对象存储
     *
     * @param existing    getById 返回的记录，null 表示不存在
     * @param removeById  removeById 的返回值
     * @param userService 用户服务（判断管理员），传 null 表示走不到这一步
     * @param spaceService 空间服务，传 null 表示图片不在私有空间里
     */
    private PictureServiceImpl buildService(Picture existing, boolean removeById,
                                            UserService userService, SpaceService spaceService) throws Exception {
        PictureServiceImpl service = spy(new PictureServiceImpl());
        doReturn(existing).when(service).getById(anyLong());
        doReturn(removeById).when(service).removeById(anyLong());
        // 清理对象存储这一步单独验证，这里替换掉避免真的调用外部服务
        org.mockito.Mockito.doNothing().when(service).clearPictureFile(any(Picture.class));

        // 注入 async 方法内部用到的 cosManager（部分场景会走到）
        CosManager cosManager = mock(CosManager.class);
        setField(service, "cosManager", cosManager);
        if (userService != null) {
            setField(service, "userService", userService);
        }
        if (spaceService != null) {
            setField(service, "spaceService", spaceService);
        }
        return service;
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = PictureServiceImpl.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Picture picture(long id) {
        Picture picture = new Picture();
        picture.setId(id);
        picture.setUserId(OWNER_ID);
        picture.setUrl("https://bucket.cos.ap-guangzhou.myqcloud.com/public/1/a.jpg");
        picture.setThumbnailUrl("https://bucket.cos.ap-guangzhou.myqcloud.com/public/1/a.webp");
        return picture;
    }

    private User user(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private Space space(long id, long ownerId) {
        Space space = new Space();
        space.setId(id);
        space.setUserId(ownerId);
        return space;
    }

    @Test
    @DisplayName("删除应同时删库、清缓存、清对象存储")
    void shouldDeleteRecordAndFileAndCache() throws Exception {
        Picture existing = picture(100L);
        PictureServiceImpl service = buildService(existing, true, null, null);

        service.deletePicture(100L, user(OWNER_ID));

        verify(service).removeById(100L);
        verify(service).clearPictureCache();
        verify(service).clearPictureFile(existing);
    }

    @Test
    @DisplayName("应先删库再清对象存储（顺序反了会留下打不开的记录）")
    void shouldDeleteRecordBeforeFile() throws Exception {
        Picture existing = picture(100L);
        PictureServiceImpl service = buildService(existing, true, null, null);

        service.deletePicture(100L, user(OWNER_ID));

        InOrder inOrder = inOrder(service);
        inOrder.verify(service).removeById(100L);
        inOrder.verify(service).clearPictureFile(existing);
    }

    @Test
    @DisplayName("图片不存在时应报错，且不做任何删除动作")
    void shouldFailWhenPictureNotFound() throws Exception {
        PictureServiceImpl service = buildService(null, true, null, null);

        assertThrows(BusinessException.class, () -> service.deletePicture(100L, user(OWNER_ID)));

        verify(service, never()).removeById(anyLong());
        verify(service, never()).clearPictureFile(any(Picture.class));
    }

    @Test
    @DisplayName("删库失败时不应继续清理对象存储")
    void shouldNotClearFileWhenRemoveFails() throws Exception {
        Picture existing = picture(100L);
        PictureServiceImpl service = buildService(existing, false, null, null);

        assertThrows(BusinessException.class, () -> service.deletePicture(100L, user(OWNER_ID)));

        verify(service, never()).clearPictureFile(any(Picture.class));
    }

    @Test
    @DisplayName("清缓存要在删库之后，避免清完又被读回旧数据")
    void shouldClearCacheAfterRemove() throws Exception {
        Picture existing = picture(100L);
        PictureServiceImpl service = buildService(existing, true, null, null);

        service.deletePicture(100L, user(OWNER_ID));

        InOrder inOrder = inOrder(service);
        inOrder.verify(service).removeById(100L);
        inOrder.verify(service).clearPictureCache();
    }

    @Test
    @DisplayName("公共空间里别人的图片不能删")
    void shouldRejectDeletingOthersPictureInPublicSpace() throws Exception {
        Picture existing = picture(100L);
        existing.setUserId(2L);
        UserService userService = mock(UserService.class);
        PictureServiceImpl service = buildService(existing, true, userService, mock(SpaceService.class));

        assertThrows(BusinessException.class, () -> service.deletePicture(100L, user(OWNER_ID)));

        verify(service, never()).removeById(anyLong());
    }

    @Test
    @DisplayName("别人私有空间里的图片不能删")
    void shouldRejectDeletingPictureInOthersSpace() throws Exception {
        Picture existing = picture(100L);
        // 图片挂在 id=10 的空间下，但空间所有者是别人
        existing.setSpaceId(10L);
        UserService userService = mock(UserService.class);
        SpaceService spaceService = mock(SpaceService.class);
        doReturn(space(10L, 2L)).when(spaceService).getById(10L);
        PictureServiceImpl service = buildService(existing, true, userService, spaceService);

        assertThrows(BusinessException.class, () -> service.deletePicture(100L, user(OWNER_ID)));

        verify(service, never()).removeById(anyLong());
    }

    @Test
    @DisplayName("删除自己空间里的图片要释放空间容量")
    void shouldReleaseSpaceUsageWhenDeletingPictureInSpace() throws Exception {
        Picture existing = picture(100L);
        existing.setSpaceId(10L);
        existing.setPicSize(1024L);
        UserService userService = mock(UserService.class);
        SpaceService spaceService = mock(SpaceService.class);
        doReturn(space(10L, OWNER_ID)).when(spaceService).getById(10L);
        PictureServiceImpl service = buildService(existing, true, userService, spaceService);

        service.deletePicture(100L, user(OWNER_ID));

        verify(spaceService).updateSpaceUsage(10L, -1L, -1024L);
    }

    @Test
    @DisplayName("公共空间的图片不涉及空间容量，不应调用空间用量更新")
    void shouldNotTouchSpaceUsageForPublicPicture() throws Exception {
        Picture existing = picture(100L);
        SpaceService spaceService = mock(SpaceService.class);
        PictureServiceImpl service = buildService(existing, true, null, spaceService);

        service.deletePicture(100L, user(OWNER_ID));

        verify(spaceService, never()).updateSpaceUsage(any(), anyLong(), anyLong());
    }
}
