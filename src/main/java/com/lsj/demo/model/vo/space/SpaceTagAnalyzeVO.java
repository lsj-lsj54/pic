package com.lsj.demo.model.vo.space;

import lombok.Data;

import java.io.Serializable;

/**
 * 空间图片标签分析（词云图）
 *
 * @author lsj
 */
@Data
public class SpaceTagAnalyzeVO implements Serializable {

    /**
     * 标签文本
     */
    private String tag;

    /**
     * 关联图片数量
     */
    private Long count;

    private static final long serialVersionUID = 1L;
}
