package com.lsj.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lsj.demo.model.dto.picture.PictureQueryRequest;
import com.lsj.demo.model.dto.picture.PictureReviewRequest;
import com.lsj.demo.model.dto.picture.PictureUploadByBatchRequest;
import com.lsj.demo.model.dto.picture.PictureUploadRequest;
import com.lsj.demo.model.entity.Picture;
import com.lsj.demo.model.entity.Space;
import com.lsj.demo.model.entity.User;
import com.lsj.demo.model.vo.PictureVO;

import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author lsj
* @description 针对表【picture(图片)】的数据库操作Service
* @createDate 2026-10-12 10:48:24
*/
public interface PictureService extends IService<Picture> {
    /**
     * 上传图片（本地文件）
     *
     * @param multipartFile        上传的图片文件
     * @param pictureUploadRequest 图片上传请求，id 不为空时表示重新上传
     * @param loginUser            登录的用户
     * @return 图片封装类
     */
    PictureVO uploadPicture(MultipartFile multipartFile,
                            PictureUploadRequest pictureUploadRequest,
                            User loginUser);

    /**
     * 上传图片（通过 URL）
     * <p>
     * 会把远程图片下载后转存到对象存储，其余流程与本地文件上传一致。
     *
     * @param pictureUploadRequest 图片上传请求，必须携带 pictureUrl
     * @param loginUser            登录的用户
     * @return 图片封装类
     */
    PictureVO uploadPictureByUrl(PictureUploadRequest pictureUploadRequest,
                                 User loginUser);

    /**
     * 获取查询的 queryWrapper
     *
     * @param pictureQueryRequest 图片请求类
     * @return 可用来查询的 queryWrapper
     */
    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);

    /**
     * 获取单个图片的 VO 对象
     *
     * @param picture picture 对象
     * @param request request 请求
     * @return 对应图片的 VO
     */
    PictureVO getPictureVO(Picture picture, HttpServletRequest request);

    /**
     * 分页获取图片 VO 对象
     *
     * @param picturePage page 对象
     * @param request     request 请求
     * @return 分页的 VO
     */
    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request);

    /**
     * 分页获取图片 VO 对象
     * <p>
     * 空间规则：
     * <ul>
     *     <li>spaceId 为空：只返回公共空间的图片，且只返回已过审的图片，任何人可看</li>
     *     <li>spaceId 不为空：只允许该空间的创建者（或管理员）查询，能看到空间内全部图片</li>
     * </ul>
     *
     * @param pictureQueryRequest 图片查询请求
     * @param request             request 请求
     * @return 分页的 VO
     */
    Page<PictureVO> listPictureVOByPage(PictureQueryRequest pictureQueryRequest,
                                       HttpServletRequest request);

    /**
     * 分页获取图片 VO 对象（带多级缓存）
     * <p>
     * Caffeine（本地）→ Redis（分布式）→ 数据库，逐级回填。
     * 缓存的是所有人共享的公共空间数据，因此只对 spaceId 为空（公共空间）的查询生效；
     * 查私有空间时校验完权限直接走数据库，不读也不写缓存，避免把别人的私有图片缓存出去。
     *
     * @param pictureQueryRequest 图片查询请求
     * @param request             request 请求
     * @return 分页的 VO
     */
    Page<PictureVO> listPictureVOByPageWithCache(PictureQueryRequest pictureQueryRequest,
                                                HttpServletRequest request);

    /**
     * 清除图片列表缓存
     * <p>
     * 图片增删改、审核之后都要调用，否则首页会一直返回旧数据。
     * 目前清的是本机 Caffeine；Redis 侧靠 TTL 自然过期，详见实现类注释。
     */
    void clearPictureCache();

    /**
     * 校验参数
     *
     * @param picture 需要校验的 picture 对象
     */
    void validPicture(Picture picture);
    /**
     * 图片审核
     *
     * @param pictureReviewRequest 图片审核请求
     * @param loginUser            当前登录的 user
     */
    void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser);

    /**
     * 填充审核参数，方便其他方法使用
     *
     * @param picture   picture
     * @param loginUser 登录的用户
     */
    void fillReviewParams(Picture picture, User loginUser);

    /**
     * 批量抓取和创建图片
     *
     * @param pictureUploadByBatchRequest
     * @param loginUser
     * @return 成功创建的图片数
     */
    Integer uploadPictureByBatch(
            PictureUploadByBatchRequest pictureUploadByBatchRequest,
            User loginUser
    );

    /**
     * 清理图片在对象存储中的文件（原图 + 缩略图）
     * <p>
     * 被其他记录引用的图片会被跳过，不会误删公用文件。
     * 删除失败只记日志，不影响调用方。
     *
     * @param oldPicture 已删除的图片记录
     */
    void clearPictureFile(Picture oldPicture);

    /**
     * 删除图片（数据库记录 + 对象存储文件）
     * <p>
     * 先删数据库记录，再清理对象存储，避免对象存储异常导致请求失败。
     * 删除前会做空间校验：公共空间的图片仅上传者本人可删，
     * 私有空间的图片仅空间创建者（或管理员）可删。
     *
     * @param pictureId 图片 id
     * @param loginUser 当前登录用户
     */
    void deletePicture(long pictureId, User loginUser);

    List<Picture> getListPictureBySpaceId(Space space);

    void deleteBatchIds(List<Long> list);
}
