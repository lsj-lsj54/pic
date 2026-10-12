package com.lsj.demo.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 批量抓取图片请求
 *
 * @author lsj
 */
@Data
public class PictureUploadByBatchRequest implements Serializable {

    /**
     * 搜索词
     */
    private String searchText;

    /**
     * 名称前缀，不传时使用搜索词
     */
    private String namePrefix;

    /**
     * 抓取数量，默认 10，上限 30
     */
    private Integer count = 10;

    private static final long serialVersionUID = 1L;
}

