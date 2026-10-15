package com.lsj.demo.model.vo.space;

import lombok.Data;

import java.io.Serializable;

/**
 * 空间使用排行（管理员，柱状图）
 *
 * @author lsj
 */
@Data
public class SpaceRankAnalyzeVO implements Serializable {

    /**
     * 空间 id
     */
    private Long spaceId;

    /**
     * 空间名称
     */
    private String spaceName;

    /**
     * 已用容量（字节）
     */
    private Long totalSize;

    /**
     * 图片数量
     */
    private Long totalCount;

    /**
     * 占全系统已用容量的比例 0~100
     */
    private Double sizeRatio;

    private static final long serialVersionUID = 1L;
}
