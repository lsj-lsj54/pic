package com.lsj.demo.model.vo.space;

import lombok.Data;

import java.io.Serializable;

/**
 * 空间图片分类分析（分组条形图：数量 + 总大小）
 *
 * @author lsj
 */
@Data
public class SpaceCategoryAnalyzeVO implements Serializable {

    /**
     * 分类名称，图片未设置分类时为「未分类」
     */
    private String category;

    /**
     * 该分类下的图片数量
     */
    private Long count;

    /**
     * 该分类下的图片总大小（字节）
     */
    private Long totalSize;

    private static final long serialVersionUID = 1L;
}
