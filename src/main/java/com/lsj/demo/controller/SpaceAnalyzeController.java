package com.lsj.demo.controller;

import com.lsj.demo.annotation.AuthCheck;
import com.lsj.demo.common.BaseResponse;
import com.lsj.demo.common.ResultUtils;
import com.lsj.demo.exception.ErrorCode;
import com.lsj.demo.exception.ThrowUtils;
import com.lsj.demo.model.constant.UserConstant;
import com.lsj.demo.model.dto.space.SpaceAnalyzeRequest;
import com.lsj.demo.model.entity.User;
import com.lsj.demo.model.vo.space.SpaceCategoryAnalyzeVO;
import com.lsj.demo.model.vo.space.SpaceRankAnalyzeVO;
import com.lsj.demo.model.vo.space.SpaceSizeAnalyzeVO;
import com.lsj.demo.model.vo.space.SpaceTagAnalyzeVO;
import com.lsj.demo.model.vo.space.SpaceTrendAnalyzeVO;
import com.lsj.demo.model.vo.space.SpaceUsageAnalyzeVO;
import com.lsj.demo.service.SpaceAnalyzeService;
import com.lsj.demo.service.UserService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 空间图库分析接口
 * <p>
 * 面向两个场景（见需求文档）：
 * 1）用户空间分析：传 spaceId 分析自己的空间（资源用量、分类、标签、大小、上传趋势）；
 * 2）管理员全空间分析：spaceId 不传时分析公共图库 + 全部空间的汇总数据，
 *    并提供空间使用排行；汇总与排行仅管理员可用，服务层会再校验一遍。
 *
 * @author lsj
 */
@Slf4j
@RestController
@RequestMapping("/space/analyze")
@AllArgsConstructor
public class SpaceAnalyzeController {

    private final SpaceAnalyzeService spaceAnalyzeService;

    private final UserService userService;

    /**
     * 空间资源使用分析（仪表盘：容量与数量的已用/配额比例）
     */
    @PostMapping("/usage")
    public BaseResponse<SpaceUsageAnalyzeVO> usageAnalyze(@RequestBody SpaceAnalyzeRequest analyzeRequest,
                                                          HttpServletRequest request) {
        ThrowUtils.throwIf(analyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(spaceAnalyzeService.usage(analyzeRequest, loginUser));
    }

    /**
     * 空间图片分类分析（分组条形图：各分类数量 + 总大小）
     */
    @PostMapping("/category")
    public BaseResponse<List<SpaceCategoryAnalyzeVO>> categoryAnalyze(@RequestBody SpaceAnalyzeRequest analyzeRequest,
                                                                      HttpServletRequest request) {
        ThrowUtils.throwIf(analyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(spaceAnalyzeService.category(analyzeRequest, loginUser));
    }

    /**
     * 空间图片标签分析（词云图：标签关联图片数）
     */
    @PostMapping("/tag")
    public BaseResponse<List<SpaceTagAnalyzeVO>> tagAnalyze(@RequestBody SpaceAnalyzeRequest analyzeRequest,
                                                            HttpServletRequest request) {
        ThrowUtils.throwIf(analyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(spaceAnalyzeService.tag(analyzeRequest, loginUser));
    }

    /**
     * 空间图片大小分析（饼图：< 100 KB / 100 KB-1 MB / > 1 MB）
     */
    @PostMapping("/size")
    public BaseResponse<List<SpaceSizeAnalyzeVO>> sizeAnalyze(@RequestBody SpaceAnalyzeRequest analyzeRequest,
                                                              HttpServletRequest request) {
        ThrowUtils.throwIf(analyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(spaceAnalyzeService.size(analyzeRequest, loginUser));
    }

    /**
     * 上传行为分析（折线图：按天 / 周 / 月的上传数量趋势）
     */
    @PostMapping("/trend")
    public BaseResponse<List<SpaceTrendAnalyzeVO>> trendAnalyze(@RequestBody SpaceAnalyzeRequest analyzeRequest,
                                                                HttpServletRequest request) {
        ThrowUtils.throwIf(analyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(spaceAnalyzeService.trend(analyzeRequest, loginUser));
    }

    /**
     * 空间使用排行（柱状图：按已用容量排序的前 N 个空间，仅管理员）
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/rank")
    public BaseResponse<List<SpaceRankAnalyzeVO>> rankAnalyze(@RequestBody SpaceAnalyzeRequest analyzeRequest,
                                                              HttpServletRequest request) {
        ThrowUtils.throwIf(analyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(spaceAnalyzeService.rank(analyzeRequest, loginUser));
    }
}
