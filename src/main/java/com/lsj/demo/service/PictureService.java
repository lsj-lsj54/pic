package com.lsj.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lsj.demo.model.dto.picture.PictureQueryRequest;
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
     * 上传图片
     *
     * @param multipartFile
     * @param pictureUploadRequest
     * @param loginUser
     * @return
     */
    PictureVO uploadPicture(MultipartFile multipartFile,
                            PictureUploadRequest pictureUploadRequest,
                            User loginUser);

    /**
     * 获取查询的 queryWrapper
     * @param pictureQueryRequest 图片请求类
     * @return 可用来查询的 queryWrapper
     */
    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);

    /**
     * 获取单个图片的 VO 对象
     * @param picture picture 对象
     * @param request request 请求
     * @return 对应图片的 VO
     */
    PictureVO getPictureVO(Picture picture, HttpServletRequest request);

    /**
     * 分页获取图片 VO 对象
     * @param picturePage  page 对象
     * @param request request 请求
     * @return 分页的 VO
     */
    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request);
    /**
     * 校验参数
     * @param picture 需要校验的 picture 对象
     */
    void validPicture(Picture picture);

}
