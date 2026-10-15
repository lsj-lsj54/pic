package com.lsj.demo.model.vo.space;

import lombok.Data;

import java.io.Serializable;

/**
 * 空间图片大小分析（饼图）
 *
 * @author lsj
 */
@Data
public class SpaceSizeAnalyzeVO implements Serializable {

    /**
     * 大小区间描述，例如「< 100 KB」
     */
    private String sizeRange;

    /**
     * 该区间内的图片数量
     */
    private Long count;

    private static final long serialVersionUID = 1L;
}
