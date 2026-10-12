package com.lsj.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lsj.demo.model.dto.picture.PictureQueryRequest;
import com.lsj.demo.model.dto.picture.PictureReviewRequest;
import com.lsj.demo.model.dto.picture.PictureUploadRequest;
import com.lsj.demo.model.entity.Picture;
import com.lsj.demo.model.entity.User;
import com.lsj.demo.model.vo.PictureVO;

import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

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

}
