package com.lsj.demo.model.vo.space;

import lombok.Data;

import java.io.Serializable;

/**
 * 空间资源使用分析（仪表盘）
 * <p>
 * usedSize / maxSize、usedCount / maxCount 的占比分别驱动两个仪表盘；
 * 分析全部空间时各字段是所有空间的总和。
 *
 * @author lsj
 */
@Data
public class SpaceUsageAnalyzeVO implements Serializable {

    /**
     * 已使用容量（字节）
     */
    private Long usedSize;

    /**
     * 容量配额（字节），全部空间时为各空间配额之和
     */
    private Long maxSize;

    /**
     * 已使用图片数量
     */
    private Long usedCount;

    /**
     * 数量配额，全部空间时为各空间数量配额之和
     */
    private Long maxCount;

    /**
     * 容量使用比例 0~100
     */
    private Double sizeRatio;

    /**
     * 数量使用比例 0~100
     */
    private Double countRatio;

    /**
     * 是否为全空间（汇总）分析
     */
    private Boolean allSpace;

    private static final long serialVersionUID = 1L;
}
