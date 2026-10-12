package com.lsj.demo;

import com.lsj.demo.manager.factory.UploadFactory;
import com.lsj.demo.manager.uplode.FilePictureUpload;
import com.lsj.demo.manager.uplode.UrlPictureUpload;
import com.lsj.demo.model.enums.FileUploadEnum;
import com.lsj.demo.model.enums.UserRoleEnum;
import com.lsj.demo.service.PictureService;
import com.lsj.demo.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 应用上下文测试
 * <p>
 * 不只是「能启动」，还要验证关键装配真的成立 —— 尤其是上传工厂：
 * 它靠 @Component("file") / @Component("url") 注册实现，
 * 一旦元注解解析或 bean 名称有问题，上传功能会整体失效，但容器仍能正常启动。
 */
@SpringBootTest
class DemoApplicationTests {

    @Autowired
    private UploadFactory uploadFactory;

    @Autowired
    private UserService userService;

    @Autowired
    private PictureService pictureService;

    @Test
    void contextLoads() {
        assertNotNull(userService, "UserService 未装配");
        assertNotNull(pictureService, "PictureService 未装配");
    }

    @Test
    void uploadFactoryShouldBeReadyInRealContext() {
        assertNotNull(uploadFactory, "UploadFactory 未装配");

        // 两种上传方式都必须能在真实容器里查到，否则上传功能整体不可用
        assertSame(FilePictureUpload.class,
                uploadFactory.getUploadTemplate(FileUploadEnum.FILE_TYPE).getClass(),
                "file 上传实现未注册");
        assertSame(UrlPictureUpload.class,
                uploadFactory.getUploadTemplate(FileUploadEnum.URL_TYPE).getClass(),
                "url 上传实现未注册");
    }

    @Test
    void userConstantAndEnumShouldStayConsistent() {
        // 枚举值要和后端常量、前端约定保持一致
        assertEquals("user", UserRoleEnum.USER.getValue());
        assertEquals("admin", UserRoleEnum.ADMIN.getValue());
        assertEquals("file", FileUploadEnum.FILE_TYPE);
        assertEquals("url", FileUploadEnum.URL_TYPE);
    }
}
