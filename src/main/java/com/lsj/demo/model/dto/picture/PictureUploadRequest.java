package com.lsj.demo.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 图片上传请求
 *
 * @author lsj
 */
@Data
public class PictureUploadRequest implements Serializable {

    /**
     * 图片 id（用于修改）
     */
    private Long id;

    /**
     * 图片地址（通过 URL 上传时必填，与文件上传二选一）
     */
    private String pictureUrl;

    /**
     * 上传方式：file-本地文件；url-远程地址。
     * 不传时按「有 pictureUrl 走 url，否则走 file」自动判断
     */
    private String uploadType;

    private String picName;

    /**
     * 空间 id
     * <p>
     * 为空表示上传到公共空间；不为空表示上传到该私有空间，
     * 只允许空间创建者（或管理员）上传，并会校验空间容量。
     */
    private Long spaceId;

    private static final long serialVersionUID = 1L;
}
