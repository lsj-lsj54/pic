package com.lsj.demo.manager.factory;

import cn.hutool.core.util.StrUtil;
import com.lsj.demo.exception.ErrorCode;
import com.lsj.demo.exception.ThrowUtils;
import com.lsj.demo.manager.uplode.PictureUploadTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 上传方式工厂
 * <p>
 * 按类型标识（file / url）取出对应的上传实现，新增上传方式时只要实现
 * {@link PictureUploadTemplate} 并用 @Component("类型名") 标注即可，不用改这里。
 *
 * @author lsj
 */
@Slf4j
@Component
public class UploadFactory {

    @Resource
    private List<PictureUploadTemplate> uploadPictureTemplates;

    /** 类型标识 -> 上传实现 */
    private final Map<String, PictureUploadTemplate> uploadFactory = new HashMap<>(10);

    /**
     * 初始化工厂，扫描所有上传实现
     */
    @PostConstruct
    private void init() {
        uploadPictureTemplates.forEach(uploadTemplate -> {
            // 实现类上标的多半是 @Service 或 @Component，
            // 必须用 findAnnotation 解析元注解，直接用 getAnnotation 是取不到的
            Component annotation = AnnotationUtils.findAnnotation(uploadTemplate.getClass(), Component.class);
            if (annotation == null || StrUtil.isBlank(annotation.value())) {
                log.warn("上传实现 {} 未指定 bean 名称，无法按类型查找",
                        uploadTemplate.getClass().getSimpleName());
                return;
            }
            PictureUploadTemplate exists = uploadFactory.put(annotation.value(), uploadTemplate);
            ThrowUtils.throwIf(exists != null, ErrorCode.SYSTEM_ERROR,
                    "上传类型重复：" + annotation.value());
        });
        log.info("已注册的上传方式：{}", uploadFactory.keySet());
    }

    /**
     * 根据类型获取上传实现
     *
     * @param type 类型标识，file / url
     * @return 上传实现
     */
    public PictureUploadTemplate getUploadTemplate(String type) {
        PictureUploadTemplate template = uploadFactory.get(type);
        ThrowUtils.throwIf(template == null, ErrorCode.NOT_FOUND_ERROR,
                "上传类型不存在：" + type);
        return template;
    }

    /**
     * 根据类型获取上传实现
     *
     * @param type 类型标识
     * @return 上传实现
     * @deprecated 命名不够表意，请使用 {@link #getUploadTemplate(String)}
     */
    @Deprecated
    public PictureUploadTemplate getUploadFactory(String type) {
        return getUploadTemplate(type);
    }
}
