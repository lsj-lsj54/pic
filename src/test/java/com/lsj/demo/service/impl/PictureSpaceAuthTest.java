package com.lsj.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lsj.demo.conifg.CosClientConfig;
import com.lsj.demo.exception.BusinessException;
import com.lsj.demo.exception.ErrorCode;
import com.lsj.demo.manager.CosManager;
import com.lsj.demo.manager.factory.UploadFactory;
import com.lsj.demo.manager.uplode.FilePictureUpload;
import com.lsj.demo.manager.uplode.PictureUploadTemplate;
import com.lsj.demo.model.dto.picture.PictureQueryRequest;
import com.lsj.demo.model.dto.picture.PictureUploadRequest;
import com.lsj.demo.model.entity.Picture;
import com.lsj.demo.model.entity.Space;
import com.lsj.demo.model.entity.User;
import com.lsj.demo.model.enums.FileUploadEnum;
import com.lsj.demo.model.enums.PictureReviewStatusEnum;
import com.lsj.demo.model.vo.PictureVO;
import com.lsj.demo.service.SpaceService;
import com.lsj.demo.service.UserService;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.CIUploadResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.OriginalInfo;
import com.qcloud.cos.model.ciModel.persistence.ProcessResults;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 图片操作的空间校验测试
 * <p>
 * 项目里 spaceId 为空表示公共空间（所有人可见），不为空表示私有空间（只有空间创建者和管理员可见、可操作）。
 * 这里覆盖「查看」和「上传」两条链路，确保别人空间里的图片既看不到也传不进去。
 */
class PictureSpaceAuthTest {

    private static final long OWNER_ID = 1L;
    private static final long OTHER_ID = 2L;
    private static final long SPACE_ID = 10L;

    private final UserService userService = mock(UserService.class);
    private final SpaceService spaceService = mock(SpaceService.class);
    private final UploadFactory uploadFactory = mock(UploadFactory.class);
    private final CosManager cosManager = mock(CosManager.class);

    private PictureServiceImpl newService() throws Exception {
        PictureServiceImpl service = spy(new PictureServiceImpl());
        setField(service, "userService", userService);
        setField(service, "spaceService", spaceService);
        setField(service, "uploadFactory", uploadFactory);
        setField(service, "cosManager", cosManager);
        return service;
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = PictureServiceImpl.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
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
        space.setMaxCount(100L);
        // 上限给得足够大，需要触发「容量不足」的用例再单独调小
        space.setMaxSize(10_000_000L);
        space.setTotalCount(0L);
        space.setTotalSize(0L);
        return space;
    }

    private Picture picture(long id, Long userId, Long spaceId) {
        Picture picture = new Picture();
        picture.setId(id);
        picture.setUserId(userId);
        picture.setSpaceId(spaceId);
        picture.setUrl("https://example.com/a.jpg");
        return picture;
    }

    /* ------------------------------ 查看单张图片 ------------------------------ */

    @Test
    @DisplayName("公共空间的图片未登录也能查看")
    void publicPictureShouldBeVisibleWithoutLogin() throws Exception {
        PictureServiceImpl service = newService();
        // 未登录时 getLoginUserOrNull 返回 null
        when(userService.getLoginUserOrNull(null)).thenReturn(null);

        PictureVO pictureVO = service.getPictureVO(picture(100L, OTHER_ID, null), null);

        assertNotNull(pictureVO, "公共图库必须允许未登录浏览");
    }

    @Test
    @DisplayName("别人私有空间里的图片不能查看")
    void privatePictureShouldBeHiddenFromOthers() throws Exception {
        PictureServiceImpl service = newService();
        doReturn(space(SPACE_ID, OWNER_ID)).when(spaceService).getById(SPACE_ID);
        when(userService.getLoginUserOrNull(null)).thenReturn(user(OTHER_ID));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.getPictureVO(picture(100L, OWNER_ID, SPACE_ID), null));
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), e.getCode());
    }

    @Test
    @DisplayName("私有空间的图片未登录不能查看")
    void privatePictureShouldBeHiddenFromAnonymous() throws Exception {
        PictureServiceImpl service = newService();
        doReturn(space(SPACE_ID, OWNER_ID)).when(spaceService).getById(SPACE_ID);
        when(userService.getLoginUserOrNull(null)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> service.getPictureVO(picture(100L, OWNER_ID, SPACE_ID), null));
    }

    @Test
    @DisplayName("空间创建者可以查看自己空间里的图片")
    void privatePictureShouldBeVisibleToOwner() throws Exception {
        PictureServiceImpl service = newService();
        doReturn(space(SPACE_ID, OWNER_ID)).when(spaceService).getById(SPACE_ID);
        when(userService.getLoginUserOrNull(null)).thenReturn(user(OWNER_ID));

        PictureVO pictureVO = service.getPictureVO(picture(100L, OWNER_ID, SPACE_ID), null);

        assertNotNull(pictureVO);
        assertEquals(Long.valueOf(SPACE_ID), pictureVO.getSpaceId());
    }

    /* ------------------------------ 分页查询 ------------------------------ */

    @Test
    @DisplayName("不传 spaceId 时只查公共空间，且强制只看已过审图片")
    void listWithoutSpaceShouldOnlyQueryPublicSpace() throws Exception {
        PictureServiceImpl service = newService();
        doReturn(new Page<Picture>()).when(service).page(any(), any());
        PictureQueryRequest request = new PictureQueryRequest();

        service.listPictureVOByPage(request, null);

        assertEquals(PictureReviewStatusEnum.PASS.getValue(), request.getReviewStatus(),
                "公共图库必须只看已过审的图片");
        ArgumentCaptor<Wrapper<Picture>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(service).page(any(), captor.capture());
        String sql = captor.getValue().getSqlSegment().toLowerCase();
        assertTrue(sql.contains("spaceid is null"),
                "没有限定只查公共空间，会把别人私有空间里的图片一起查出来: " + sql);
    }

    @Test
    @DisplayName("查询别人空间应被拒绝")
    void listOthersSpaceShouldBeRejected() throws Exception {
        PictureServiceImpl service = newService();
        doReturn(space(SPACE_ID, OWNER_ID)).when(spaceService).getById(SPACE_ID);
        when(userService.getLoginUserOrNull(null)).thenReturn(user(OTHER_ID));

        PictureQueryRequest request = new PictureQueryRequest();
        request.setSpaceId(SPACE_ID);

        assertThrows(BusinessException.class, () -> service.listPictureVOByPage(request, null));
        verify(service, never()).page(any(), any());
    }

    @Test
    @DisplayName("空间创建者可以查询自己空间里的图片")
    void listOwnSpaceShouldPass() throws Exception {
        PictureServiceImpl service = newService();
        doReturn(space(SPACE_ID, OWNER_ID)).when(spaceService).getById(SPACE_ID);
        when(userService.getLoginUserOrNull(null)).thenReturn(user(OWNER_ID));
        doReturn(new Page<Picture>()).when(service).page(any(), any());

        PictureQueryRequest request = new PictureQueryRequest();
        request.setSpaceId(SPACE_ID);

        service.listPictureVOByPage(request, null);

        // 私有空间里能看到自己的待审核图片，所以不能强制改成已过审
        assertNull(request.getReviewStatus());
        verify(service).page(any(), any());
    }

    /* ------------------------------ 上传 ------------------------------ */

    private static final String HOST = "https://test-bucket.cos.ap-guangzhou.myqcloud.com";

    /**
     * 用真实的上传实现来跑上传流程
     * <p>
     * {@code uploadPicture} 是模板方法且被声明为 final，Mockito 没法打桩，
     * 所以这里只把对象存储与配置换成假的，模板流程本身照常执行。
     */
    private void useRealUploadTemplate() throws Exception {
        FilePictureUpload template = new FilePictureUpload();
        when(cosManager.putPictureObject(anyString(), any())).thenReturn(buildPutObjectResult());

        CosClientConfig config = new CosClientConfig();
        config.setHost(HOST);
        setTemplateField(template, "cosManager", cosManager);
        setTemplateField(template, "cosClientConfig", config);

        when(uploadFactory.getUploadTemplate(FileUploadEnum.FILE_TYPE)).thenReturn(template);
    }

    private void setTemplateField(Object target, String name, Object value) throws Exception {
        Field field = PictureUploadTemplate.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    /** 构造对象存储的返回：原图信息 + 一张 30000 字节的压缩图 */
    private PutObjectResult buildPutObjectResult() {
        ImageInfo imageInfo = new ImageInfo();
        imageInfo.setWidth(800);
        imageInfo.setHeight(600);
        imageInfo.setFormat("png");

        OriginalInfo originalInfo = new OriginalInfo();
        originalInfo.setImageInfo(imageInfo);

        CIObject compressed = new CIObject();
        compressed.setKey("/public/1/compressed.webp");
        compressed.setFormat("webp");
        compressed.setWidth(800);
        compressed.setHeight(600);
        compressed.setSize(30000);

        ProcessResults processResults = new ProcessResults();
        processResults.setObjectList(new ArrayList<>(Arrays.asList(compressed)));

        CIUploadResult ciUploadResult = new CIUploadResult();
        ciUploadResult.setOriginalInfo(originalInfo);
        ciUploadResult.setProcessResults(processResults);

        PutObjectResult result = new PutObjectResult();
        result.setCiUploadResult(ciUploadResult);
        return result;
    }

    private MockMultipartFile pngFile() {
        return new MockMultipartFile("file", "photo.png", "image/png", new byte[1024]);
    }

    @Test
    @DisplayName("不能往别人的空间里传图")
    void uploadToOthersSpaceShouldBeRejected() throws Exception {
        PictureServiceImpl service = newService();
        doReturn(space(SPACE_ID, OWNER_ID)).when(spaceService).getById(SPACE_ID);

        PictureUploadRequest request = new PictureUploadRequest();
        request.setSpaceId(SPACE_ID);

        assertThrows(BusinessException.class,
                () -> service.uploadPicture(pngFile(), request, user(OTHER_ID)));
        // 权限没通过时不该真的去上传文件
        verify(uploadFactory, never()).getUploadTemplate(anyString());
    }

    @Test
    @DisplayName("空间图片数量已满时应拒绝上传，且不上传文件")
    void uploadShouldBeRejectedWhenSpaceCountIsFull() throws Exception {
        PictureServiceImpl service = newService();
        Space space = space(SPACE_ID, OWNER_ID);
        // 空间只允许 1 张，且已经用满了
        space.setMaxCount(1L);
        space.setTotalCount(1L);
        doReturn(space).when(spaceService).getById(SPACE_ID);

        PictureUploadRequest request = new PictureUploadRequest();
        request.setSpaceId(SPACE_ID);

        assertThrows(BusinessException.class,
                () -> service.uploadPicture(pngFile(), request, user(OWNER_ID)));

        // 数量校验在上传之前，放不下就没必要再去传文件
        verify(uploadFactory, never()).getUploadTemplate(anyString());
        verify(service, never()).saveOrUpdate(any(Picture.class));
    }

    @Test
    @DisplayName("空间容量不足时应拒绝上传，并清掉刚上传的文件")
    void uploadShouldBeRejectedWhenSpaceSizeIsFull() throws Exception {
        PictureServiceImpl service = newService();
        Space space = space(SPACE_ID, OWNER_ID);
        // 只允许 1000 字节，而上传结果有 30000 字节
        space.setMaxSize(1000L);
        doReturn(space).when(spaceService).getById(SPACE_ID);
        useRealUploadTemplate();

        PictureUploadRequest request = new PictureUploadRequest();
        request.setSpaceId(SPACE_ID);

        assertThrows(BusinessException.class,
                () -> service.uploadPicture(pngFile(), request, user(OWNER_ID)));

        // 没能入库的文件必须从对象存储里删掉（原图 + 压缩图），避免留下没有记录引用的垃圾
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(cosManager, times(2)).deleteObjectByUrl(urlCaptor.capture());
        assertTrue(urlCaptor.getAllValues().get(0).startsWith(HOST + "/public/1/"),
                "清理的不是刚上传的原图: " + urlCaptor.getAllValues().get(0));
        verify(service, never()).saveOrUpdate(any(Picture.class));
    }

    @Test
    @DisplayName("上传成功后应把图片归到空间并累加空间用量")
    void uploadShouldIncreaseSpaceUsage() throws Exception {
        PictureServiceImpl service = newService();
        doReturn(space(SPACE_ID, OWNER_ID)).when(spaceService).getById(SPACE_ID);
        useRealUploadTemplate();
        doReturn(true).when(service).saveOrUpdate(any(Picture.class));

        PictureUploadRequest request = new PictureUploadRequest();
        request.setSpaceId(SPACE_ID);

        PictureVO pictureVO = service.uploadPicture(pngFile(), request, user(OWNER_ID));

        assertNotNull(pictureVO);
        assertEquals(Long.valueOf(SPACE_ID), pictureVO.getSpaceId(), "图片没有归到目标空间");
        // 新增 1 张图、占用 30000 字节
        verify(spaceService).updateSpaceUsage(SPACE_ID, 1L, 30000L);
    }

    @Test
    @DisplayName("上传到公共空间不应影响任何空间用量")
    void uploadToPublicSpaceShouldNotTouchUsage() throws Exception {
        PictureServiceImpl service = newService();
        useRealUploadTemplate();
        doReturn(true).when(service).saveOrUpdate(any(Picture.class));

        PictureUploadRequest request = new PictureUploadRequest();

        PictureVO pictureVO = service.uploadPicture(pngFile(), request, user(OWNER_ID));

        assertNotNull(pictureVO);
        assertNull(pictureVO.getSpaceId());
        verify(spaceService, never()).updateSpaceUsage(anyLong(), anyLong(), anyLong());
    }
}
