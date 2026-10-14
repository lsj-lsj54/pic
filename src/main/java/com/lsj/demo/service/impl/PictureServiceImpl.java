package com.lsj.demo.service.impl;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.*;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lsj.demo.common.PicturePermissionUtils;
import com.lsj.demo.common.SpacePermissionUtils;
import com.lsj.demo.exception.BusinessException;
import com.lsj.demo.exception.ErrorCode;
import com.lsj.demo.exception.ThrowUtils;
import com.lsj.demo.manager.CosManager;
import com.lsj.demo.manager.factory.UploadFactory;
import com.lsj.demo.manager.uplode.PictureUploadTemplate;
import com.lsj.demo.model.dto.picture.PictureQueryRequest;
import com.lsj.demo.model.dto.picture.PictureReviewRequest;
import com.lsj.demo.model.dto.picture.PictureUploadByBatchRequest;
import com.lsj.demo.model.dto.picture.PictureUploadRequest;
import com.lsj.demo.model.entity.Space;
import com.lsj.demo.model.entity.User;
import com.lsj.demo.mapper.PictureMapper;
import com.lsj.demo.model.dto.file.UploadPictureResult;
import com.lsj.demo.model.entity.Picture;
import com.lsj.demo.model.enums.FileUploadEnum;
import com.lsj.demo.model.enums.PictureReviewStatusEnum;
import com.lsj.demo.model.vo.PictureVO;
import com.lsj.demo.model.vo.UserVO;
import com.lsj.demo.service.PictureService;
import com.lsj.demo.service.SpaceService;
import com.lsj.demo.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author lsj
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2026-10-12 10:48:24
 */
@Service
@Slf4j
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    /**
     * 批量抓取一次最多允许的图片数，避免一次请求打爆对象存储和数据库
     */
    private static final int MAX_BATCH_COUNT = 30;

    /**
     * 抓取搜索页的超时时间，单位毫秒
     */
    private static final int FETCH_TIMEOUT_MILLIS = 10 * 1000;

    /**
     * 抓取搜索页使用的 UA，部分站点会拒绝空 UA 的请求
     */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    @Resource
    private UserService userService;

    /**
     * 空间服务：图片归属空间后，上传、删除、查看都要做空间校验，并维护空间用量。
     * <p>
     * 空间服务删除空间时会反过来调用本服务清理空间内的图片，
     * 循环依赖由 SpaceServiceImpl 侧的 @Lazy 打断，这里正常注入即可。
     */
    @Resource
    private SpaceService spaceService;


    @Resource
    private UploadFactory uploadFactory;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CosManager cosManager;

    /**
     * 图片列表缓存的 key 前缀
     */
    private static final String CACHE_KEY_PREFIX = "picture:listVO:";

    /**
     * 缓存基础过期时间（秒）
     */
    private static final int CACHE_EXPIRE_BASE_SECONDS = 300;

    /**
     * 在基础过期时间上叠加的随机秒数，避免缓存同时失效
     */
    private static final int CACHE_EXPIRE_JITTER_SECONDS = 300;

    /**
     * 上传图片（本地文件）
     *
     * @param multipartFile        上传的图片文件
     * @param pictureUploadRequest 图片上传请求
     * @param loginUser            当前登录的 user
     * @return picture 的封装对象
     */
    @Override
    public PictureVO uploadPicture(MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest,
                                   User loginUser) {
        ThrowUtils.throwIf(multipartFile == null || multipartFile.isEmpty(),
                ErrorCode.PARAMS_ERROR, "文件不能为空");
        return doUpload(multipartFile, FileUploadEnum.FILE_TYPE, pictureUploadRequest, loginUser);
    }

    /**
     * 上传图片（通过 URL）
     *
     * @param pictureUploadRequest 图片上传请求，必须携带 pictureUrl
     * @param loginUser            当前登录的 user
     * @return picture 的封装对象
     */
    @Override
    public PictureVO uploadPictureByUrl(PictureUploadRequest pictureUploadRequest, User loginUser) {
        ThrowUtils.throwIf(pictureUploadRequest == null
                        || StrUtil.isBlank(pictureUploadRequest.getPictureUrl()),
                ErrorCode.PARAMS_ERROR, "文件地址不能为空");
        String pictureUrl = pictureUploadRequest.getPictureUrl().trim();
        return doUpload(pictureUrl, FileUploadEnum.URL_TYPE, pictureUploadRequest, loginUser);
    }

    /**
     * 上传图片的公共流程
     * <p>
     * 本地文件和 URL 两种方式共用：校验登录态 → 确定新增还是覆盖 → 校验操作权限 →
     * 交给对应模板上传 → 组装入库对象（含审核参数）→ 保存。
     *
     * @param inputSource          输入源：MultipartFile 或图片 URL
     * @param uploadType           上传方式，见 {@link FileUploadEnum}
     * @param pictureUploadRequest 图片上传请求
     * @param loginUser            当前登录的 user
     * @return picture 的封装对象
     */
    private PictureVO doUpload(Object inputSource, String uploadType,
                               PictureUploadRequest pictureUploadRequest, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);

        // 判断是新增还是覆盖已有图片
        Long pictureId = pictureUploadRequest == null ? null : pictureUploadRequest.getId();
        Picture oldPicture = null;
        if (pictureId != null) {
            oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            // 仅本人或管理员可覆盖；图片在私有空间里时还要求是该空间的创建者
            PicturePermissionUtils.checkPictureAuth(oldPicture, loginUser, userService, spaceService);
        }

        // 目标空间：spaceId 为空表示公共空间，不为空表示上传到对应的私有空间
        Long spaceId = pictureUploadRequest == null ? null : pictureUploadRequest.getSpaceId();
        if (spaceId == null && oldPicture != null) {
            // 覆盖上传又没带 spaceId 时，保持图片原有归属，避免把私有空间里的图片意外挪进公共图库
            spaceId = oldPicture.getSpaceId();
        }
        Long oldSpaceId = oldPicture == null ? null : oldPicture.getSpaceId();
        long oldSize = oldPicture == null || oldPicture.getPicSize() == null ? 0L : oldPicture.getPicSize();
        // 空间必须存在且属于当前用户（管理员除外）
        Space space = getAndCheckSpace(spaceId, loginUser);
        // 同一空间内覆盖上传不会新增图片，被覆盖图片占用的容量也可以抵扣
        boolean sameSpace = spaceId != null && spaceId.equals(oldSpaceId);
        long countDelta = spaceId != null && !sameSpace ? 1L : 0L;
        // 数量先校验：空间已经放不下时就不用白跑一趟上传了
        if (space != null) {
            checkSpaceCount(space, countDelta > 0);
        }

        // 按上传方式取出对应实现，按用户 id 划分目录
        String uploadPathPrefix = String.format("public/%s", loginUser.getId());
        PictureUploadTemplate uploadTemplate = uploadFactory.getUploadTemplate(uploadType);
        UploadPictureResult uploadPictureResult = uploadTemplate.uploadPicture(inputSource, uploadPathPrefix);

        // 容量要等拿到真实文件大小才能算，超限就把刚上传的文件删掉，不给对象存储留垃圾
        long newSize = uploadPictureResult.getPicSize() == null ? 0L : uploadPictureResult.getPicSize();
        if (space != null) {
            try {
                checkSpaceSize(space, newSize, sameSpace ? oldSize : 0L);
            } catch (RuntimeException e) {
                deleteUploadedFile(uploadPictureResult);
                throw e;
            }
        }

        // 构造要入库的图片信息
        Picture picture = new Picture();
        picture.setUrl(uploadPictureResult.getUrl());
        // 缩略图：列表页用它，详情页用原图
        picture.setThumbnailUrl(uploadPictureResult.getThumbnailUrl());
        // 允许调用方指定名称（批量抓取时用来生成「搜索词+序号」），未指定则用原始文件名
        String picName = uploadPictureResult.getPicName();
        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picName = pictureUploadRequest.getPicName();
        }
        picture.setName(picName);
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
        picture.setUserId(loginUser.getId());
        // 归属空间：前端据此区分公共图库和私有空间
        picture.setSpaceId(spaceId);
        // 补充审核参数
        fillReviewParams(picture, loginUser);

        // 如果是覆盖，需要补充 id 和编辑时间
        if (pictureId != null) {
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        boolean result = this.saveOrUpdate(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败");
        // 维护空间用量：图片离开旧空间要扣减，进入新空间要累加
        if (oldSpaceId != null && !oldSpaceId.equals(spaceId)) {
            spaceService.updateSpaceUsage(oldSpaceId, -1L, -oldSize);
        }
        if (spaceId != null) {
            long sizeDelta = newSize - (sameSpace ? oldSize : 0L);
            spaceService.updateSpaceUsage(spaceId, countDelta, sizeDelta);
        }
        // 数据变了，首页缓存要失效，否则会一直看到旧列表
        clearPictureCache();
        return PictureVO.entityToVo(picture);
    }

    /**
     * 取出目标空间并校验归属
     *
     * @param spaceId   空间 id，为空表示公共空间
     * @param loginUser 当前登录用户
     * @return 目标空间，spaceId 为空时返回 null
     */
    private Space getAndCheckSpace(Long spaceId, User loginUser) {
        if (spaceId == null) {
            // 公共空间不属于任何人，不需要校验
            return null;
        }
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        // 非管理员只能往自己的空间里传图
        SpacePermissionUtils.checkSpaceAuth(space, loginUser, userService);
        return space;
    }

    /**
     * 校验空间还能不能再放下一张图片
     *
     * @param space          目标空间
     * @param countIncrement 本次上传是否会给空间增加一条图片
     */
    private void checkSpaceCount(Space space, boolean countIncrement) {
        if (!countIncrement) {
            return;
        }
        long maxCount = space.getMaxCount() == null ? 0L : space.getMaxCount();
        long totalCount = space.getTotalCount() == null ? 0L : space.getTotalCount();
        ThrowUtils.throwIf(totalCount + 1 > maxCount, ErrorCode.OPERATION_ERROR,
                "空间图片数量已满，请先清理空间或升级空间");
    }

    /**
     * 校验空间容量是否还放得下这张图片
     *
     * @param space        目标空间
     * @param newSize      本次上传图片的大小（字节）
     * @param releasedSize 被覆盖图片原先占用的容量，覆盖同一空间内的图片时可抵扣
     */
    private void checkSpaceSize(Space space, long newSize, long releasedSize) {
        long maxSize = space.getMaxSize() == null ? 0L : space.getMaxSize();
        long totalSize = space.getTotalSize() == null ? 0L : space.getTotalSize();
        ThrowUtils.throwIf(totalSize - releasedSize + newSize > maxSize, ErrorCode.OPERATION_ERROR,
                "空间容量不足，请先清理空间或升级空间");
    }

    /**
     * 删除刚上传成功、但没能入库的对象存储文件
     * <p>
     * 容量校验失败等场景下用，避免对象存储里堆积没有任何记录引用的垃圾文件。
     *
     * @param uploadPictureResult 上传结果
     */
    private void deleteUploadedFile(UploadPictureResult uploadPictureResult) {
        if (uploadPictureResult == null) {
            return;
        }
        String url = uploadPictureResult.getUrl();
        if (StrUtil.isNotBlank(url)) {
            cosManager.deleteObjectByUrl(url);
        }
        String thumbnailUrl = uploadPictureResult.getThumbnailUrl();
        if (StrUtil.isNotBlank(thumbnailUrl) && !thumbnailUrl.equals(url)) {
            cosManager.deleteObjectByUrl(thumbnailUrl);
        }
    }


    /**
     * 获取查询条件
     *
     * @param pictureQueryRequest 图片查询请求
     * @return 可用来查询的 queryWrapper
     */
    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        Long spaceId = pictureQueryRequest.getSpaceId();

        // 从多字段中搜索
        if (StrUtil.isNotBlank(searchText)) {
            // 需要拼接查询条件
            queryWrapper.and(qw -> qw.like("name", searchText)
                    .or()
                    .like("introduction", searchText)
            );
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);

        // JSON 数组查询
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), "ascend".equals(sortOrder), sortField);
        return queryWrapper;
    }

    /**
     * 获取单个图片的 VO 对象
     *
     * @param picture picture 图片
     * @param request request 请求
     * @return 对应图片的 VO
     */
    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        // 私有空间里的图片只有空间创建者（或管理员）能看，公共空间里的图片所有人可见
        checkPictureViewAuth(picture, request);
        // 实体转封装类
        PictureVO pictureVO = PictureVO.entityToVo(picture);
        // 关联查询用户信息
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        return pictureVO;
    }

    /**
     * 校验当前请求能否查看这张图片
     * <p>
     * 公共空间（spaceId 为空）的图片任何人可看；私有空间的图片只允许空间创建者和管理员看，
     * 未登录或访问别人的空间都直接拒绝。
     *
     * @param picture picture 图片
     * @param request request 请求
     */
    private void checkPictureViewAuth(Picture picture, HttpServletRequest request) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR, "图片不能为空");
        Long spaceId = picture.getSpaceId();
        if (spaceId == null) {
            return;
        }
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        // 未登录时 getLoginUserOrNull 返回 null，checkSpaceViewAuth 会按无权限处理
        SpacePermissionUtils.checkSpaceViewAuth(space, userService.getLoginUserOrNull(request), userService);
    }

    /**
     * 分页查询图片 VO
     * <p>
     * spaceId 为空：只查公共空间、且只查已过审的图片，所有人可见；
     * spaceId 不为空：先校验空间归属，只有空间创建者（或管理员）能查，可看到空间内全部图片。
     *
     * @param pictureQueryRequest 图片查询请求
     * @param request             request 请求
     * @return 分页的 VO
     */
    @Override
    public Page<PictureVO> listPictureVOByPage(PictureQueryRequest pictureQueryRequest,
                                               HttpServletRequest request) {
        ThrowUtils.throwIf(pictureQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR, "每页最多 20 条");
        Long spaceId = pictureQueryRequest.getSpaceId();
        if (spaceId == null) {
            // 公共图库：所有人可见，但只能看到已过审的图片
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        } else {
            // 私有空间：不能查看别人的空间
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            SpacePermissionUtils.checkSpaceViewAuth(space, userService.getLoginUserOrNull(request), userService);
        }
        QueryWrapper<Picture> queryWrapper = this.getQueryWrapper(pictureQueryRequest);
        if (spaceId == null) {
            // 必须显式限定「只查公共空间」，否则会把别人私有空间里的图片一并查出来
            queryWrapper.isNull("spaceId");
        }
        Page<Picture> picturePage = this.page(new Page<>(current, size), queryWrapper);
        return this.getPictureVOPage(picturePage, request);
    }

    /**
     * 分页获取图片封装
     *
     * @param picturePage picture 分页对象
     * @param request     request 请求
     * @return 分页的 VO
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }
        // 对象列表 => 封装对象列表
        List<PictureVO> pictureVOList = pictureList.stream().map(PictureVO::entityToVo).collect(Collectors.toList());
        // 1. 关联查询用户信息
        Set<Long> userIdSet = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.getUserVO(user));
        });
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }

    /**
     * 校验参数
     *
     * @param picture 需要校验的 picture 对象
     */
    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
    }

    /**
     * 图片审核
     *
     * @param pictureReviewRequest 图片审核请求
     * @param loginUser            当前登录的 user
     */
    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        PictureReviewStatusEnum reviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);
        if (id == null || reviewStatusEnum == null || PictureReviewStatusEnum.REVIEWING.equals(reviewStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 判断是否存在
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 已是该状态
        if (oldPicture.getReviewStatus().equals(reviewStatus)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请勿重复审核");
        }
        // 更新审核状态
        Picture updatePicture = new Picture();
        BeanUtils.copyProperties(pictureReviewRequest, updatePicture);
        updatePicture.setReviewerId(loginUser.getId());
        updatePicture.setReviewTime(new Date());
        boolean result = this.updateById(updatePicture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        // 审核结果会影响「已过审」列表，缓存必须失效
        clearPictureCache();
    }

    /**
     * 填充审核参数，方便其他方法使用
     *
     * @param picture   picture
     * @param loginUser 登录的用户
     */
    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        if (userService.isAdmin(loginUser)) {
            // 管理员自动过审
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewMessage("管理员自动过审");
            picture.setReviewTime(new Date());
        } else {
            // 非管理员，创建或编辑都要改为待审核
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
    }

    /**
     * 批量抓取和创建图片
     * <p>
     * 从必应图片搜索页解析出图片直链，再逐个走 URL 上传流程入库。
     * 单个图片失败不影响其余图片，只记录日志并继续。
     *
     * @param pictureUploadByBatchRequest 批量抓取请求
     * @param loginUser                   当前登录的 user
     * @return 成功创建的图片数
     */
    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        String searchText = pictureUploadByBatchRequest.getSearchText();
        ThrowUtils.throwIf(StrUtil.isBlank(searchText), ErrorCode.PARAMS_ERROR, "搜索词不能为空");

        // 名称前缀：没传就用搜索词，保证抓下来的图片名字可读
        // 注意判断方向 —— 写反会导致前缀被赋成空字符串，图片名全部退化成原始文件名
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        if (StrUtil.isBlank(namePrefix)) {
            namePrefix = searchText;
        }

        // 校验抓取数量
        Integer count = pictureUploadByBatchRequest.getCount();
        ThrowUtils.throwIf(count == null || count <= 0, ErrorCode.PARAMS_ERROR, "抓取数量必须大于 0");
        ThrowUtils.throwIf(count > MAX_BATCH_COUNT, ErrorCode.PARAMS_ERROR, "最多 " + MAX_BATCH_COUNT + " 条");

        // 要抓取的地址（只取必应返回的结果片段，比整页 HTML 轻量）
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1",
                URLUtil.encode(searchText));
        Document document = parseSearchPage(fetchUrl);

        Element div = document.getElementsByClass("dgControl").first();
        ThrowUtils.throwIf(ObjUtil.isNull(div), ErrorCode.OPERATION_ERROR, "获取元素失败");

        Elements imgElementList = div.select("img.mimg");
        ThrowUtils.throwIf(imgElementList.isEmpty(), ErrorCode.OPERATION_ERROR,
                "没有解析到图片，可能是搜索词无结果或页面结构变了");

        int uploadCount = 0;
        // 搜索结果里常出现同一张图（同一原图被不同条目引用），去重避免入库一堆重复图片
        Set<String> uploadedUrls = new HashSet<>();
        for (Element imgElement : imgElementList) {
            String fileUrl = resolveImageUrl(imgElement);
            if (StrUtil.isBlank(fileUrl)) {
                log.info("当前链接为空，已跳过");
                continue;
            }
            if (!uploadedUrls.add(fileUrl)) {
                log.info("图片地址重复，已跳过: {}", fileUrl);
                continue;
            }
            // 上传图片
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            pictureUploadRequest.setUploadType(FileUploadEnum.URL_TYPE);
            pictureUploadRequest.setPictureUrl(fileUrl);
            // 名称用「前缀 + 序号」，序号跟着成功数递增，失败时不会跳号
            pictureUploadRequest.setPicName(namePrefix + (uploadCount + 1));
            try {
                PictureVO pictureVO = this.uploadPictureByUrl(pictureUploadRequest, loginUser);
                log.info("图片上传成功, id = {}", pictureVO.getId());
                uploadCount++;
            } catch (Exception e) {
                // 单张失败不影响整体
                log.error("图片上传失败，地址 = {}", fileUrl, e);
                continue;
            }
            if (uploadCount >= count) {
                break;
            }
        }
        return uploadCount;
    }

    /**
     * 拉取并解析搜索页
     * <p>
     * 单独抽出来是为了把「网络请求」与「结果解析」解耦，方便测试替换。
     *
     * @param fetchUrl 搜索页地址
     * @return 解析后的文档（其 baseUri 即 fetchUrl，可用于补全相对地址）
     */
    protected Document parseSearchPage(String fetchUrl) {
        try {
            return Jsoup.connect(fetchUrl)
                    .userAgent(USER_AGENT)
                    .timeout(FETCH_TIMEOUT_MILLIS)
                    .get();
        } catch (IOException e) {
            log.error("获取页面失败，地址 = {}", fetchUrl, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败");
        }
    }

    /**
     * 从搜索结果里解析出可下载的图片地址
     * <p>
     * 优先从结果条目的 m 属性（JSON）里取 murl —— 这是必应最稳定的原图直链位置；
     * 其次取图片节点自身的 murl；再退回 data-src / src。
     * 注意 src 只是站内缩略图（形如 /th?id=OIP.xxx），既没有图片后缀、画质也差，
     * 只能作为最后的兜底。
     *
     * @param imgElement 图片节点（其所属文档带有 baseUri，可解析相对地址）
     * @return 可下载的图片地址，解析不出时返回空串
     */
    private String resolveImageUrl(Element imgElement) {
        String fileUrl = extractMurlFromMeta(imgElement);
        if (StrUtil.isBlank(fileUrl)) {
            fileUrl = imgElement.attr("murl");
        }
        if (StrUtil.isBlank(fileUrl)) {
            fileUrl = imgElement.attr("data-src");
        }
        if (StrUtil.isBlank(fileUrl)) {
            // absUrl 能把 // 开头的协议相对地址和 / 开头的站内地址补全
            fileUrl = imgElement.absUrl("src");
        }
        if (StrUtil.isBlank(fileUrl)) {
            return StrUtil.EMPTY;
        }
        // 协议相对地址补上 https
        if (fileUrl.startsWith("//")) {
            fileUrl = "https:" + fileUrl;
        }
        return fileUrl.trim();
    }

    /**
     * 从结果条目的 m 属性里解析出原图地址
     * <p>
     * 该属性是一段 JSON 字符串，形如 {"murl":"https://...","turl":"https://..."}。
     * 解析失败属于正常情况（不同版本的页面结构不一致），返回空串由调用方兜底。
     *
     * @param imgElement 图片节点
     * @return 原图地址，取不到时返回空串
     */
    private String extractMurlFromMeta(Element imgElement) {
        // m 属性挂在包裹图片的 a.iusc 上
        Element link = imgElement.closest("a.iusc");
        if (link == null) {
            link = imgElement.parent();
        }
        if (link == null) {
            return StrUtil.EMPTY;
        }
        String meta = link.attr("m");
        if (StrUtil.isBlank(meta)) {
            return StrUtil.EMPTY;
        }
        try {
            JSONObject json = JSONUtil.parseObj(meta);
            return StrUtil.nullToEmpty(json.getStr("murl"));
        } catch (Exception e) {
            // m 属性不是合法 JSON 时静默跳过，交给后续兜底
            log.debug("解析 m 属性失败: {}", meta);
            return StrUtil.EMPTY;
        }
    }


    /**
     * 本地缓存（一级缓存）
     * <p>
     * 生命周期跟着应用走，重启即失效；多实例部署时各实例互不可见。
     * 只用来挡住热点查询，真正的跨实例缓存在 Redis。
     */
    private final Cache<String, String> LOCAL_CACHE =
            Caffeine.newBuilder().initialCapacity(1024)
                    .maximumSize(10000L)
                    // 本地缓存比 Redis 稍短，让两级缓存自然错开，避免同时失效
                    .expireAfterWrite(5L, TimeUnit.MINUTES)
                    .build();

    /**
     * 分页查询图片（带多级缓存）
     * <p>
     * 缓存策略：Caffeine（本地）→ Redis（分布式）→ 数据库，逐级回填。
     * <p>
     * 只对公共空间的数据做缓存：这里强制 spaceId 为空、reviewStatus = PASS，
     * 所以缓存内容与用户身份无关，可以放心共用同一个 key。
     * 查私有空间时结果因人而异，不能进公共缓存，校验完权限直接走数据库。
     *
     * @param pictureQueryRequest 图片查询请求
     * @param request             request 请求
     * @return 图片封装类分页数据
     */
    @Override
    public Page<PictureVO> listPictureVOByPageWithCache(PictureQueryRequest pictureQueryRequest,
                                                        HttpServletRequest request) {
        ThrowUtils.throwIf(pictureQueryRequest == null, ErrorCode.PARAMS_ERROR);
        if (pictureQueryRequest.getSpaceId() != null) {
            // 私有空间：不做缓存，交给带空间校验的查询方法
            return this.listPictureVOByPage(pictureQueryRequest, request);
        }
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR, "每页最多 20 条");
        // 普通用户默认只能查看已过审的数据
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());

        // 构建缓存 key：只取影响结果的字段，保证序列化顺序稳定
        String cacheKey = CACHE_KEY_PREFIX + DigestUtils.md5DigestAsHex(
                buildCacheCondition(pictureQueryRequest).getBytes(StandardCharsets.UTF_8));

        // 1. 本地缓存
        String cachedValue = LOCAL_CACHE.getIfPresent(cacheKey);
        if (cachedValue != null) {
            log.debug("命中本地缓存，key = {}", cacheKey);
            return parseCachedPage(cachedValue);
        }

        // 2. Redis 缓存
        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();
        String redisValue = valueOps.get(cacheKey);
        if (redisValue != null) {
            log.debug("命中 Redis 缓存，key = {}", cacheKey);
            // 回填本地缓存，下次就不用走网络了
            LOCAL_CACHE.put(cacheKey, redisValue);
            return parseCachedPage(redisValue);
        }

        // 3. 查数据库
        QueryWrapper<Picture> queryWrapper = this.getQueryWrapper(pictureQueryRequest);
        // 缓存的是公共图库，必须排除私有空间里的图片
        queryWrapper.isNull("spaceId");
        Page<Picture> picturePage = this.page(new Page<>(current, size), queryWrapper);
        Page<PictureVO> pictureVOPage = this.getPictureVOPage(picturePage, request);

        // 4. 回填两级缓存
        String cacheValue = serializePage(pictureVOPage);
        // 5 - 10 分钟随机过期，防止同一时刻大批 key 一起失效造成缓存雪崩
        int cacheExpireTime = CACHE_EXPIRE_BASE_SECONDS + RandomUtil.randomInt(0, CACHE_EXPIRE_JITTER_SECONDS);
        valueOps.set(cacheKey, cacheValue, cacheExpireTime, TimeUnit.SECONDS);
        LOCAL_CACHE.put(cacheKey, cacheValue);

        return pictureVOPage;
    }

    /**
     * 清除本地图片缓存
     * <p>
     * 图片增删改（上传、编辑、删除、审核）后调用，避免首页一直看到旧数据。
     * <p>
     * 注意：这里只清了本机的 Caffeine。Redis 里的数据靠 5~10 分钟的 TTL 自然过期，
     * 单机部署下影响可以接受；如果以后多实例部署、要求写入后立刻一致，
     * 需要在这里把 Redis 中 {@link #CACHE_KEY_PREFIX} 开头的 key 一并删掉。
     */
    @Override
    public void clearPictureCache() {
        LOCAL_CACHE.invalidateAll();
        log.debug("本地图片缓存已清除");
    }

    /**
     * 拼装参与缓存的查询条件
     * <p>
     * 不用 JSON 序列化整个请求对象：字段顺序、null 处理稍有变化就会导致 key 变化，
     * 明明是同一次查询却缓存不命中。这里显式列出真正影响结果的字段。
     *
     * @param queryRequest 查询请求
     * @return 用于计算 md5 的条件串
     */
    private String buildCacheCondition(PictureQueryRequest queryRequest) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("current=").append(queryRequest.getCurrent())
                .append("&size=").append(queryRequest.getPageSize())
                .append("&reviewStatus=").append(queryRequest.getReviewStatus())
                .append("&searchText=").append(StrUtil.nullToEmpty(queryRequest.getSearchText()))
                .append("&category=").append(StrUtil.nullToEmpty(queryRequest.getCategory()))
                .append("&picFormat=").append(StrUtil.nullToEmpty(queryRequest.getPicFormat()))
                .append("&userId=").append(queryRequest.getUserId())
                // 缓存只用于公共空间（spaceId 恒为空），这里列出来是为了条件串与查询语义保持一致
                .append("&spaceId=").append(queryRequest.getSpaceId())
                .append("&name=").append(StrUtil.nullToEmpty(queryRequest.getName()))
                .append("&introduction=").append(StrUtil.nullToEmpty(queryRequest.getIntroduction()))
                .append("&sortField=").append(StrUtil.nullToEmpty(queryRequest.getSortField()))
                // 统一成小写再比较，避免 "ASC" 与 "asc" 生成两个 key
                .append("&sortOrder=").append(StrUtil.nullToEmpty(queryRequest.getSortOrder()).toLowerCase());
        if (CollUtil.isNotEmpty(queryRequest.getTags())) {
            // 标签按字典序排序，顺序不同不应视作不同查询
            sb.append("&tags=").append(queryRequest.getTags().stream()
                    .sorted()
                    .collect(Collectors.joining(",")));
        }
        return sb.toString();
    }

    /**
     * 把分页对象序列化成缓存字符串
     *
     * @param page 分页对象
     * @return JSON 字符串
     */
    private String serializePage(Page<PictureVO> page) {
        return JSONUtil.toJsonStr(page);
    }

    /**
     * 把缓存字符串还原成分页对象
     * <p>
     * 必须用 TypeReference 指定泛型：直接 toBean(xxx, Page.class) 会丢掉
     * records 里的泛型信息，返回的 Page 的 records 是 JSONObject 列表，
     * 遍历时取字段会直接抛异常。
     *
     * @param cachedValue 缓存中的 JSON 字符串
     * @return 分页对象
     */
    private Page<PictureVO> parseCachedPage(String cachedValue) {
        return JSONUtil.toBean(cachedValue, new TypeReference<Page<PictureVO>>() {
        }, false);
    }

    /**
     * 删除图片（数据库记录 + 对象存储文件）
     * <p>
     * 顺序上先删库再清对象存储：对象存储清理失败只会留下无引用的文件，
     * 不影响用户看到的结果；反过来先删文件再删库，一旦删库失败就会出现「记录在、图打不开」。
     * <p>
     * 删除前做空间校验：公共空间的图片仅上传者本人可删，
     * 私有空间的图片仅空间创建者（或管理员）可删。
     *
     * @param pictureId 图片 id
     * @param loginUser 当前登录用户
     */
    @Override
    public void deletePicture(long pictureId, User loginUser) {
        // 先取出记录，删掉之后就查不到 url 了
        Picture oldPicture = this.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        // 不能删别人空间里的图片
        PicturePermissionUtils.checkPictureAuth(oldPicture, loginUser, userService, spaceService);

        boolean result = this.removeById(pictureId);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除失败");

        // 释放空间容量：删掉的图片不再占用该空间
        if (oldPicture.getSpaceId() != null) {
            long picSize = oldPicture.getPicSize() == null ? 0L : oldPicture.getPicSize();
            spaceService.updateSpaceUsage(oldPicture.getSpaceId(), -1L, -picSize);
        }

        // 缓存里的列表还带着这张图，必须清掉
        clearPictureCache();
        // 异步清理对象存储，失败只记日志，不影响本次删除结果
        clearPictureFile(oldPicture);
    }

    /**
     * 清理图片对应的对象存储文件
     * <p>
     * 只有确定这张图片不再被其他记录引用时才删，避免误删公用的文件。
     * 传入的是完整访问地址，由 CosManager 负责反推对象 key ——
     * 直接把 URL 当 key 传给对象存储是删不掉任何东西的。
     * <p>
     * 说明：这里是同步执行的，删除失败会被 CosManager 吞掉并记日志。
     * 之前标的 @Async 实际不生效（deletePicture 内部是 this 调用，绕过了 Spring 代理），
     * 与其留个假注释不如直接说明：删除是低频操作，同步执行更可预期。
     *
     * @param oldPicture 已删除的图片记录
     */
    @Override
    public void clearPictureFile(Picture oldPicture) {
        if (oldPicture == null) {
            return;
        }
        // 判断该图片是否被多条记录使用
        String pictureUrl = oldPicture.getUrl();
        long count = this.lambdaQuery()
                .eq(Picture::getUrl, pictureUrl)
                .count();
        // 有不止一条记录用到了该图片，不清理
        if (count > 1) {
            log.info("该图片仍被 {} 条记录引用，跳过对象存储清理: {}", count, pictureUrl);
            return;
        }
        // 删原图
        cosManager.deleteObjectByUrl(pictureUrl);
        // 删缩略图（历史数据可能没有）
        String thumbnailUrl = oldPicture.getThumbnailUrl();
        if (StrUtil.isNotBlank(thumbnailUrl) && !thumbnailUrl.equals(pictureUrl)) {
            cosManager.deleteObjectByUrl(thumbnailUrl);
        }
    }


    /**
     * 查询某个空间下的全部图片
     * <p>
     * 内部方法，调用方（删除空间等）必须先做空间权限校验，这里不再重复校验。
     * <p>
     * 注意：这里必须用 {@link LambdaQueryWrapper} 而不是把 lambdaQuery() 的结果直接传给 list()。
     * MyBatis-Plus 3.5.9 拼 SQL 时会取 wrapper 的 sqlFirst，而链式 wrapper
     * （AbstractChainWrapper）的 getSqlFirst 直接抛异常，传进去会报
     * 「can not use this method for "getSqlFirst"」。
     *
     * @param space 空间，必须带 id
     * @return 空间下的图片列表
     */
    @Override
    public List<Picture> getListPictureBySpaceId(Space space) {
        ThrowUtils.throwIf(space == null || space.getId() == null, ErrorCode.PARAMS_ERROR, "空间id不可以为空");
        LambdaQueryWrapper<Picture> queryWrapper = Wrappers.lambdaQuery(Picture.class)
                .eq(Picture::getSpaceId, space.getId());
        return list(queryWrapper);
    }

    @Override
    public void deleteBatchIds(List<Long> list) {
        removeByIds(list);
    }
}
