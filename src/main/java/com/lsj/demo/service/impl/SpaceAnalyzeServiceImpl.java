package com.lsj.demo.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lsj.demo.common.SpacePermissionUtils;
import com.lsj.demo.exception.BusinessException;
import com.lsj.demo.exception.ErrorCode;
import com.lsj.demo.exception.ThrowUtils;
import com.lsj.demo.mapper.PictureMapper;
import com.lsj.demo.mapper.SpaceMapper;
import com.lsj.demo.model.dto.space.SpaceAnalyzeRequest;
import com.lsj.demo.model.entity.Picture;
import com.lsj.demo.model.entity.Space;
import com.lsj.demo.model.entity.User;
import com.lsj.demo.model.vo.space.SpaceCategoryAnalyzeVO;
import com.lsj.demo.model.vo.space.SpaceRankAnalyzeVO;
import com.lsj.demo.model.vo.space.SpaceSizeAnalyzeVO;
import com.lsj.demo.model.vo.space.SpaceTagAnalyzeVO;
import com.lsj.demo.model.vo.space.SpaceTrendAnalyzeVO;
import com.lsj.demo.model.vo.space.SpaceUsageAnalyzeVO;
import com.lsj.demo.service.SpaceAnalyzeService;
import com.lsj.demo.service.SpaceUserService;
import com.lsj.demo.service.UserService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 空间图库分析服务实现
 * <p>
 * 所有聚合都在数据库侧完成（count / sum / group by），
 * 只有标签解析因为 tags 是 JSON 数组字符串、需要拆分成多个标签，放在了应用侧统计。
 * 分析范围由 spaceId 决定：为空表示全系统（公共图库 + 所有空间，仅管理员），不为空表示单个空间。
 *
 * @author lsj
 */
@Slf4j
@Service
@AllArgsConstructor
public class SpaceAnalyzeServiceImpl implements SpaceAnalyzeService {

    private static final long KB = 1024L;
    private static final long MB = 1024L * 1024L;

    /** 词云、排行等列表的默认返回条数与上限 */
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    /** 排行榜默认取前 N 个空间 */
    private static final int DEFAULT_TOP_N = 10;

    private final PictureMapper pictureMapper;

    private final SpaceMapper spaceMapper;

    private final UserService userService;

    /**
     * 空间成员服务：团队空间的分析权限对成员开放
     */
    private final SpaceUserService spaceUserService;

    @Override
    public SpaceUsageAnalyzeVO usage(SpaceAnalyzeRequest analyzeRequest, User loginUser) {
        checkAnalyzeAuth(analyzeRequest, loginUser);
        Long spaceId = analyzeRequest.getSpaceId();
        SpaceUsageAnalyzeVO vo = new SpaceUsageAnalyzeVO();
        vo.setAllSpace(spaceId == null);

        if (spaceId != null) {
            // 单个空间：用量字段由上传 / 删除时维护，直接读空间记录即可
            Space space = spaceMapper.selectById(spaceId);
            vo.setUsedSize(space.getTotalSize());
            vo.setMaxSize(space.getMaxSize());
            vo.setUsedCount(space.getTotalCount());
            vo.setMaxCount(space.getMaxCount());
        } else {
            // 全系统：已用量统计所有图片（公共图库 + 所有空间），配额取各空间配额之和
            QueryWrapper<Picture> pictureWrapper = new QueryWrapper<>();
            pictureWrapper.select("ifnull(sum(picSize), 0) as usedSize", "count(*) as usedCount");
            List<Map<String, Object>> rows = pictureMapper.selectMaps(pictureWrapper);
            Map<String, Object> row = rows.isEmpty() ? Collections.emptyMap() : rows.get(0);

            QueryWrapper<Space> spaceWrapper = new QueryWrapper<>();
            spaceWrapper.select("ifnull(sum(maxSize), 0) as maxSize", "ifnull(sum(maxCount), 0) as maxCount");
            List<Map<String, Object>> spaceRows = spaceMapper.selectMaps(spaceWrapper);
            Map<String, Object> spaceRow = spaceRows.isEmpty() ? Collections.emptyMap() : spaceRows.get(0);

            vo.setUsedSize(toLong(row.get("usedSize")));
            vo.setMaxSize(toLong(spaceRow.get("maxSize")));
            vo.setUsedCount(toLong(row.get("usedCount")));
            vo.setMaxCount(toLong(spaceRow.get("maxCount")));
        }
        vo.setSizeRatio(ratio(vo.getUsedSize(), vo.getMaxSize()));
        vo.setCountRatio(ratio(vo.getUsedCount(), vo.getMaxCount()));
        return vo;
    }

    @Override
    public List<SpaceCategoryAnalyzeVO> category(SpaceAnalyzeRequest analyzeRequest, User loginUser) {
        checkAnalyzeAuth(analyzeRequest, loginUser);

        QueryWrapper<Picture> wrapper = new QueryWrapper<>();
        wrapper.select("if(category is null or category = '', '未分类', category) as category",
                        "count(*) as count",
                        "ifnull(sum(picSize), 0) as totalSize")
                .groupBy("category")
                .orderByDesc("count");
        applySpaceScope(wrapper, analyzeRequest.getSpaceId());
        return pictureMapper.selectMaps(wrapper).stream()
                .map(row -> {
                    SpaceCategoryAnalyzeVO vo = new SpaceCategoryAnalyzeVO();
                    vo.setCategory(String.valueOf(row.get("category")));
                    vo.setCount(toLong(row.get("count")));
                    vo.setTotalSize(toLong(row.get("totalSize")));
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<SpaceTagAnalyzeVO> tag(SpaceAnalyzeRequest analyzeRequest, User loginUser) {
        checkAnalyzeAuth(analyzeRequest, loginUser);
        int limit = normalizeLimit(analyzeRequest.getLimit());

        // tags 是 JSON 数组字符串，数据库侧拆分不划算，只取出 tags 列在应用侧统计
        QueryWrapper<Picture> wrapper = new QueryWrapper<>();
        wrapper.select("tags").isNotNull("tags").ne("tags", "");
        applySpaceScope(wrapper, analyzeRequest.getSpaceId());
        Map<String, Long> tagCounts = new LinkedHashMap<>();
        for (Object tagsObj : pictureMapper.selectObjs(wrapper)) {
            String tagsStr = tagsObj == null ? null : String.valueOf(tagsObj);
            if (StrUtil.isBlank(tagsStr)) {
                continue;
            }
            try {
                JSONArray tags = JSONUtil.parseArray(tagsStr);
                for (Object tag : tags) {
                    String tagText = StrUtil.trimToEmpty(String.valueOf(tag));
                    if (StrUtil.isNotEmpty(tagText)) {
                        tagCounts.merge(tagText, 1L, Long::sum);
                    }
                }
            } catch (Exception e) {
                // 个别脏数据解析失败只影响它自己，不能让整个分析接口挂掉
                log.warn("图片标签解析失败，已跳过: {}", tagsStr);
            }
        }
        return tagCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> {
                    SpaceTagAnalyzeVO vo = new SpaceTagAnalyzeVO();
                    vo.setTag(entry.getKey());
                    vo.setCount(entry.getValue());
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<SpaceSizeAnalyzeVO> size(SpaceAnalyzeRequest analyzeRequest, User loginUser) {
        checkAnalyzeAuth(analyzeRequest, loginUser);

        QueryWrapper<Picture> wrapper = new QueryWrapper<>();
        wrapper.select("picSize");
        applySpaceScope(wrapper, analyzeRequest.getSpaceId());
        long small = 0;
        long medium = 0;
        long large = 0;
        for (Object sizeObj : pictureMapper.selectObjs(wrapper)) {
            long picSize = sizeObj == null ? 0 : ((Number) sizeObj).longValue();
            if (picSize < 100 * KB) {
                small++;
            } else if (picSize <= MB) {
                medium++;
            } else {
                large++;
            }
        }
        List<SpaceSizeAnalyzeVO> result = new ArrayList<>();
        result.add(buildSizeVO("< 100 KB", small));
        result.add(buildSizeVO("100 KB - 1 MB", medium));
        result.add(buildSizeVO("> 1 MB", large));
        return result;
    }

    @Override
    public List<SpaceTrendAnalyzeVO> trend(SpaceAnalyzeRequest analyzeRequest, User loginUser) {
        checkAnalyzeAuth(analyzeRequest, loginUser);
        String dimension = StrUtil.blankToDefault(analyzeRequest.getTimeDimension(), "day");
        ThrowUtils.throwIf(!"day".equals(dimension) && !"week".equals(dimension) && !"month".equals(dimension),
                ErrorCode.PARAMS_ERROR, "时间维度仅支持 day / week / month");
        // MySQL 格式：%Y-%m-%d 按天；%Y-%u 按周（周一起算）；%Y-%m 按月
        String dateFormat;
        if ("week".equals(dimension)) {
            dateFormat = "'%Y-%u'";
        } else if ("month".equals(dimension)) {
            dateFormat = "'%Y-%m'";
        } else {
            dateFormat = "'%Y-%m-%d'";
        }

        QueryWrapper<Picture> wrapper = new QueryWrapper<>();
        wrapper.select("DATE_FORMAT(createTime, " + dateFormat + ") as date",
                        "count(*) as count")
                .groupBy("date")
                .orderByAsc("date");
        applySpaceScope(wrapper, analyzeRequest.getSpaceId());
        return pictureMapper.selectMaps(wrapper).stream()
                .map(row -> {
                    SpaceTrendAnalyzeVO vo = new SpaceTrendAnalyzeVO();
                    vo.setDate(String.valueOf(row.get("date")));
                    vo.setCount(toLong(row.get("count")));
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<SpaceRankAnalyzeVO> rank(SpaceAnalyzeRequest analyzeRequest, User loginUser) {
        // 排行是管理员视角的全局统计：控制器上有 @AuthCheck，这里再兜一道，避免服务被直接调用绕过鉴权
        ThrowUtils.throwIf(analyzeRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null,
                ErrorCode.NOT_LOGIN_ERROR, "未登录");
        ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR, "仅管理员可查看空间排行");

        int topN = analyzeRequest.getLimit() == null ? DEFAULT_TOP_N
                : Math.min(Math.max(analyzeRequest.getLimit(), 1), MAX_LIMIT);

        // 全系统已用容量作为占比分母（包含公共图库）
        QueryWrapper<Picture> totalWrapper = new QueryWrapper<>();
        totalWrapper.select("ifnull(sum(picSize), 0) as total");
        long totalUsed = toLong(pictureMapper.selectMaps(totalWrapper)
                .stream().findFirst().map(row -> row.get("total")).orElse(0L));

        QueryWrapper<Space> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("totalSize").last("limit " + topN);
        return spaceMapper.selectList(wrapper).stream()
                .map(space -> {
                    SpaceRankAnalyzeVO vo = new SpaceRankAnalyzeVO();
                    vo.setSpaceId(space.getId());
                    vo.setSpaceName(space.getSpaceName());
                    vo.setTotalSize(space.getTotalSize());
                    vo.setTotalCount(space.getTotalCount());
                    vo.setSizeRatio(totalUsed > 0
                            ? Math.round(space.getTotalSize() * 1000.0 / totalUsed) / 10.0 : 0.0);
                    return vo;
                })
                .collect(Collectors.toList());
    }

    /**
     * 把分析范围拼进图片查询条件
     * <p>
     * spaceId 为空表示全系统（公共图库 + 所有空间），不加过滤；
     * 不为空时只统计该空间内的图片。
     * <p>
     * 注意：这一步必须拼在条件里，漏掉会让「分析某个空间」实际返回全系统数据，
     * 既是数据错误，也会把别人的空间数据泄露给空间创建者。
     */
    private void applySpaceScope(QueryWrapper<Picture> wrapper, Long spaceId) {
        if (spaceId != null) {
            wrapper.eq("spaceId", spaceId);
        }
    }

    /**
     * 分析权限校验：指定空间仅创建者、团队成员（或管理员）可看，全部空间仅管理员
     */
    private void checkAnalyzeAuth(SpaceAnalyzeRequest analyzeRequest, User loginUser) {
        ThrowUtils.throwIf(analyzeRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null,
                ErrorCode.NOT_LOGIN_ERROR, "未登录");
        if (analyzeRequest.getSpaceId() != null) {
            Space space = spaceMapper.selectById(analyzeRequest.getSpaceId());
            SpacePermissionUtils.checkSpaceViewAuth(space, loginUser, userService, spaceUserService);
        } else if (!userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "仅管理员可分析全部空间");
        }
    }

    private SpaceSizeAnalyzeVO buildSizeVO(String range, long count) {
        SpaceSizeAnalyzeVO vo = new SpaceSizeAnalyzeVO();
        vo.setSizeRange(range);
        vo.setCount(count);
        return vo;
    }

    /**
     * 返回条数归一化：默认 20，范围 1~100
     */
    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.min(Math.max(limit, 1), MAX_LIMIT);
    }

    /**
     * 占比 0~100，保留 1 位小数；配额为空或 0 时返回 0
     */
    private Double ratio(Long used, Long max) {
        if (used == null || max == null || max <= 0) {
            return 0.0;
        }
        return Math.min(Math.round(used * 1000.0 / max) / 10.0, 100.0);
    }

    private Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        return value instanceof Number ? ((Number) value).longValue()
                : Long.parseLong(String.valueOf(value));
    }
}
