package com.lsj.demo.service;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lsj.demo.model.dto.space.SpaceAddRequest;
import com.lsj.demo.model.dto.space.SpaceDeleteRequest;
import com.lsj.demo.model.dto.space.SpaceQueryRequest;
import com.lsj.demo.model.entity.Picture;
import com.lsj.demo.model.entity.Space;
import com.lsj.demo.model.entity.User;
import com.lsj.demo.model.vo.SpaceVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author lsj
 * @description 针对表【space(空间)】的数据库操作Service
 * @createDate 2026-10-13 23:36:58
 */
public interface SpaceService extends IService<Space> {





    /**
     * 校验空间
     *
     * @param space space
     * @param add   是否为创建时检验
     */

    void validSpace(Space space, boolean add);

    long addSpace(SpaceAddRequest spaceAddRequest, User loginUser, HttpServletRequest request);

    /**
     * 获取空间包装类（单条）
     *
     * @param space
     * @param request
     * @return
     */

    SpaceVO getSpaceVO(Space space, HttpServletRequest request);

    /**
     * 获取空间包装类（分页）
     *
     * @param spacePage
     * @param request
     * @return
     */

    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request);

    /**
     * 获取查询对象
     *
     * @param spaceQueryRequest
     * @return
     */

    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);

    /**
     * 根据空间级别填充空间对象
     */

    void fillSpaceBySpaceLevel(Space space);

    /**
     * 删除空间
     *
     * @param spaceDeleteRequest spaceId
     * @param loginUser 登录用户
     * @return boolean true 删除成功 、false 删除失败
     */
    boolean deleteSpace(SpaceDeleteRequest spaceDeleteRequest, User loginUser);

    /**
     * 增减空间已用容量与图片数
     * <p>
     * 图片上传、删除后必须调用，否则空间的 totalSize / totalCount 永远停在 0，
     * 容量校验也就形同虚设。用 SQL 自增自减而不是「查出来再更新」，
     * 避免同一个空间并发上传时互相覆盖。
     *
     * @param spaceId    空间 id
     * @param countDelta 图片数量增量，可为负数
     * @param sizeDelta  图片总大小增量（字节），可为负数
     */
    void updateSpaceUsage(Long spaceId, long countDelta, long sizeDelta);

}
