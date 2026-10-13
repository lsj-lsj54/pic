package com.lsj.demo.service.impl;

import com.lsj.demo.exception.BusinessException;
import com.lsj.demo.manager.CosManager;
import com.lsj.demo.model.entity.Picture;
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
 * 删除是「删库 + 清缓存 + 清对象存储」三件事，
 * 这里验证三件事都做了、顺序正确、且异常场景有合理行为。
 */
class PictureDeleteTest {

    /**
     * 构造一个假的 Service：不连数据库，也不真的删对象存储
     *
     * @param existing   getById 返回的记录，null 表示不存在
     * @param removeById removeById 的返回值
     */
    private PictureServiceImpl buildService(Picture existing, boolean removeById) throws Exception {
        PictureServiceImpl service = spy(new PictureServiceImpl());
        doReturn(existing).when(service).getById(anyLong());
        doReturn(removeById).when(service).removeById(anyLong());
        // 清理对象存储这一步单独验证，这里替换掉避免真的调用外部服务
        org.mockito.Mockito.doNothing().when(service).clearPictureFile(any(Picture.class));

        // 注入 async 方法内部用到的 cosManager（部分场景会走到）
        CosManager cosManager = mock(CosManager.class);
        setField(service, "cosManager", cosManager);
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
        picture.setUrl("https://bucket.cos.ap-guangzhou.myqcloud.com/public/1/a.jpg");
        picture.setThumbnailUrl("https://bucket.cos.ap-guangzhou.myqcloud.com/public/1/a.webp");
        return picture;
    }

    @Test
    @DisplayName("删除应同时删库、清缓存、清对象存储")
    void shouldDeleteRecordAndFileAndCache() throws Exception {
        Picture existing = picture(100L);
        PictureServiceImpl service = buildService(existing, true);

        service.deletePicture(100L);

        verify(service).removeById(100L);
        verify(service).clearPictureCache();
        verify(service).clearPictureFile(existing);
    }

    @Test
    @DisplayName("应先删库再清对象存储（顺序反了会留下打不开的记录）")
    void shouldDeleteRecordBeforeFile() throws Exception {
        Picture existing = picture(100L);
        PictureServiceImpl service = buildService(existing, true);

        service.deletePicture(100L);

        InOrder inOrder = inOrder(service);
        inOrder.verify(service).removeById(100L);
        inOrder.verify(service).clearPictureFile(existing);
    }

    @Test
    @DisplayName("图片不存在时应报错，且不做任何删除动作")
    void shouldFailWhenPictureNotFound() throws Exception {
        PictureServiceImpl service = buildService(null, true);

        assertThrows(BusinessException.class, () -> service.deletePicture(100L));

        verify(service, never()).removeById(anyLong());
        verify(service, never()).clearPictureFile(any(Picture.class));
    }

    @Test
    @DisplayName("删库失败时不应继续清理对象存储")
    void shouldNotClearFileWhenRemoveFails() throws Exception {
        Picture existing = picture(100L);
        PictureServiceImpl service = buildService(existing, false);

        assertThrows(BusinessException.class, () -> service.deletePicture(100L));

        verify(service, never()).clearPictureFile(any(Picture.class));
    }

    @Test
    @DisplayName("清缓存要在删库之后，避免清完又被读回旧数据")
    void shouldClearCacheAfterRemove() throws Exception {
        Picture existing = picture(100L);
        PictureServiceImpl service = buildService(existing, true);

        service.deletePicture(100L);

        InOrder inOrder = inOrder(service);
        inOrder.verify(service).removeById(100L);
        inOrder.verify(service).clearPictureCache();
    }
}
