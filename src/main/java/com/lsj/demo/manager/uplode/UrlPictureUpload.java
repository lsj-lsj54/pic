package com.lsj.demo.manager.uplode;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import com.lsj.demo.exception.BusinessException;
import com.lsj.demo.exception.ErrorCode;
import com.lsj.demo.exception.ThrowUtils;
import com.lsj.demo.model.enums.FileUploadEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URL;
import java.util.Arrays;

/**
 * URL 上传
 * <p>
 * 把远程图片下载后转存到对象存储，适用于「通过图片地址导入」的场景。
 *
 * @author lsj
 */
@Slf4j
@Component(FileUploadEnum.URL_TYPE)
public class UrlPictureUpload extends PictureUploadTemplate {

    /** URL 探测（HEAD）与下载的超时时间，单位毫秒 */
    private static final int TIMEOUT_MILLIS = 10 * 1000;

    /** 无法从 URL 解析出合法文件名时的兜底名，避免 File.createTempFile 前缀过短报错 */
    private static final String FALLBACK_FILENAME = "picture.jpg";

    /**
     * 校验 URL：合法性、协议、可访问性、类型与体积
     *
     * @param inputSource 图片 URL
     */
    @Override
    protected void validPicture(Object inputSource) {
        String fileUrl = cast(inputSource);

        // 1. 校验 URL 格式与协议
        try {
            URL url = new URL(fileUrl);
            ThrowUtils.throwIf(!Arrays.asList("http", "https").contains(url.getProtocol().toLowerCase()),
                    ErrorCode.PARAMS_ERROR, "仅支持 HTTP 或 HTTPS 协议的文件地址");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件地址格式不正确");
        }

        // 2. 如果 URL 自带文件名，先按后缀拦一道，省掉一次网络请求
        String filenameFromUrl = tryGetFilenameFromUrl(fileUrl);
        if (StrUtil.isNotBlank(filenameFromUrl) && StrUtil.isNotBlank(FileUtil.getSuffix(filenameFromUrl))) {
            validFileFormat(filenameFromUrl);
        }

        // 3. HEAD 探测类型与体积
        HttpResponse response = null;
        try {
            response = HttpRequest.head(fileUrl).timeout(TIMEOUT_MILLIS).execute();
            // 部分站点不支持 HEAD，拿不到信息就交给下载后的校验兜底，不在这里直接失败
            if (response.getStatus() != HttpStatus.HTTP_OK) {
                log.warn("HEAD 探测未返回 200，状态码 = {}，地址 = {}", response.getStatus(), fileUrl);
                return;
            }
            validContentType(response.header("Content-Type"));
            String contentLength = response.header("Content-Length");
            if (StrUtil.isNotBlank(contentLength)) {
                try {
                    validFileSize(Long.parseLong(contentLength.trim()));
                } catch (NumberFormatException e) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小格式错误");
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("HEAD 探测失败，将直接尝试下载，地址 = {}，原因 = {}", fileUrl, e.getMessage());
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * 从 URL 中提取文件名（含后缀），query 参数会被忽略
     *
     * @param inputSource 图片 URL
     * @return 文件名，解析不出时返回兜底名
     */
    @Override
    protected String getOriginFilename(Object inputSource) {
        String filename = tryGetFilenameFromUrl(cast(inputSource));
        if (StrUtil.isBlank(filename)) {
            return FALLBACK_FILENAME;
        }
        // File.createTempFile 要求前缀至少 3 个字符，过短时补上兜底名
        return filename.length() < 3 ? FALLBACK_FILENAME : filename;
    }

    /**
     * 下载远程图片到临时文件
     *
     * @param inputSource 图片 URL
     * @param file        临时文件
     * @throws Exception 下载失败或超出体积上限
     */
    @Override
    protected void processFile(Object inputSource, File file) throws Exception {
        String fileUrl = cast(inputSource);
        HttpResponse response = null;
        try {
            response = HttpRequest.get(fileUrl).timeout(TIMEOUT_MILLIS).execute();
            ThrowUtils.throwIf(!response.isOk(), ErrorCode.OPERATION_ERROR,
                    "下载图片失败，状态码 " + response.getStatus());
            // 边下边校验体积，不能只信任 Content-Length（可能缺省或造假）
            downloadToFile(response.bodyStream(), file, MAX_FILE_SIZE);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * 校验输入源类型
     *
     * @param inputSource 输入源
     * @return 图片 URL
     */
    private String cast(Object inputSource) {
        ThrowUtils.throwIf(!(inputSource instanceof String), ErrorCode.PARAMS_ERROR, "请提供图片地址");
        String fileUrl = ((String) inputSource).trim();
        ThrowUtils.throwIf(StrUtil.isBlank(fileUrl), ErrorCode.PARAMS_ERROR, "文件地址不能为空");
        return fileUrl;
    }

    /**
     * 尽力从 URL 里解析出文件名
     *
     * @param fileUrl 图片 URL
     * @return 文件名，解析不出返回 null
     */
    private String tryGetFilenameFromUrl(String fileUrl) {
        try {
            String path = new URL(fileUrl).getPath();
            if (StrUtil.isBlank(path)) {
                return null;
            }
            String name = path.substring(path.lastIndexOf('/') + 1);
            if (StrUtil.isBlank(name)) {
                return null;
            }
            // 处理文件名里的百分号编码，例如 %E5%9B%BE.png
            return URLUtil.decode(name);
        } catch (Exception e) {
            return null;
        }
    }
}
