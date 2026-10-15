package com.lsj.demo.service;

import com.lsj.demo.model.dto.space.SpaceAnalyzeRequest;
import com.lsj.demo.model.entity.User;
import com.lsj.demo.model.vo.space.SpaceCategoryAnalyzeVO;
import com.lsj.demo.model.vo.space.SpaceRankAnalyzeVO;
import com.lsj.demo.model.vo.space.SpaceSizeAnalyzeVO;
import com.lsj.demo.model.vo.space.SpaceTagAnalyzeVO;
import com.lsj.demo.model.vo.space.SpaceTrendAnalyzeVO;
import com.lsj.demo.model.vo.space.SpaceUsageAnalyzeVO;

import java.util.List;

/**
 * 空间图库分析服务
 * <p>
 * 统一负责分析接口的权限校验：spaceId 为空时仅管理员可分析全部空间，
 * 不为空时仅空间创建者（或管理员）可分析自己的空间。
 *
 * @author lsj
 */
public interface SpaceAnalyzeService {

    /**
     * 空间资源使用分析（已用/配额比例，驱动仪表盘）
     */
    SpaceUsageAnalyzeVO usage(SpaceAnalyzeRequest analyzeRequest, User loginUser);

    /**
     * 图片分类分析（各分类的数量与总大小，驱动分组条形图）
     */
    List<SpaceCategoryAnalyzeVO> category(SpaceAnalyzeRequest analyzeRequest, User loginUser);

    /**
     * 图片标签分析（标签关联图片数，驱动词云图）
     */
    List<SpaceTagAnalyzeVO> tag(SpaceAnalyzeRequest analyzeRequest, User loginUser);

    /**
     * 图片大小分段分析（< 100 KB / 100 KB-1 MB / > 1 MB，驱动饼图）
     */
    List<SpaceSizeAnalyzeVO> size(SpaceAnalyzeRequest analyzeRequest, User loginUser);

    /**
     * 上传行为分析（按天 / 周 / 月的上传数量趋势，驱动折线图）
     */
    List<SpaceTrendAnalyzeVO> trend(SpaceAnalyzeRequest analyzeRequest, User loginUser);

    /**
     * 空间使用排行（按已用容量排序的前 N 个空间，驱动柱状图，仅管理员）
     */
    List<SpaceRankAnalyzeVO> rank(SpaceAnalyzeRequest analyzeRequest, User loginUser);
}
