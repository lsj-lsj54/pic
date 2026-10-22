package com.lsj.demo.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.core.util.StrUtil;
import com.lsj.demo.annotation.AuthCheck;
import com.lsj.demo.common.BaseResponse;
import com.lsj.demo.common.ResultUtils;
import com.lsj.demo.common.SpacePermissionUtils;
import com.lsj.demo.exception.BusinessException;
import com.lsj.demo.exception.ErrorCode;
import com.lsj.demo.exception.ThrowUtils;
import com.lsj.demo.model.constant.UserConstant;
import com.lsj.demo.model.dto.space.SpaceAddRequest;
import com.lsj.demo.model.dto.space.SpaceDeleteRequest;
import com.lsj.demo.model.dto.space.SpaceEditRequest;
import com.lsj.demo.model.dto.space.SpaceQueryRequest;
import com.lsj.demo.model.dto.space.SpaceUpdateRequest;
import com.lsj.demo.model.entity.Space;
import com.lsj.demo.model.entity.User;
import com.lsj.demo.model.enums.SpaceLevelEnum;
import com.lsj.demo.model.vo.SpaceVO;
import com.lsj.demo.service.SpaceService;
import com.lsj.demo.service.SpaceUserService;
import com.lsj.demo.service.UserService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 空间模块接口
 * <p>
 * 空间是用户的私有容器：只有空间创建者（或管理员）能查看、修改、删除自己的空间，
 * 空间内的图片同样只对创建者可见，相关规则由 {@link SpacePermissionUtils} 统一负责。
 *
 * @author lsj
 */
@Slf4j
@RestController
@RequestMapping("/space")
@AllArgsConstructor
public class SpaceController {

    private final SpaceService spaceService;

    private final UserService userService;

    /**
     * 空间成员服务：团队空间的查看、编辑权限按成员角色判定
     */
    private final SpaceUserService spaceUserService;

    /**
     * 创建空间
     * <p>
     * 普通用户只能创建普通版空间，专业版 / 旗舰版需要管理员身份。
     *
     * @param spaceAddRequest 空间创建请求
     * @param request         request 请求
     * @return 新空间 id
     */
    @PostMapping("/add")
    public BaseResponse<Long> addSpace(@RequestBody SpaceAddRequest spaceAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceAddRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        long newSpaceId = spaceService.addSpace(spaceAddRequest, loginUser, request);
        ThrowUtils.throwIf(newSpaceId <= 0, ErrorCode.OPERATION_ERROR, "创建空间失败");
        return ResultUtils.success(newSpaceId);
    }

    /**
     * 更新空间（仅管理员可用）
     *
     * @param spaceUpdateRequest 空间更新请求
     * @return Boolean true 是成功
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateSpace(@RequestBody SpaceUpdateRequest spaceUpdateRequest) {
        if (spaceUpdateRequest == null || spaceUpdateRequest.getId() == null
                || spaceUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 将实体类和 DTO 进行转换
        Space space = new Space();
        BeanUtils.copyProperties(spaceUpdateRequest, space);
        // 自动填充数据
        spaceService.fillSpaceBySpaceLevel(space);
        // 数据校验
        spaceService.validSpace(space, false);
        // 判断是否存在
        long id = spaceUpdateRequest.getId();
        Space oldSpace = spaceService.getById(id);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR);
        // 操作数据库
        boolean result = spaceService.updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 删除空间（仅空间创建者或管理员可用）
     * <p>
     * 空间内的图片会被一并删除（含对象存储中的文件）。
     *
     * @param spaceDeleteRequest 空间删除请求
     * @param request            request 请求
     * @return 是否删除成功
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteSpace(@RequestBody SpaceDeleteRequest spaceDeleteRequest,
                                             HttpServletRequest request) {
        ThrowUtils.throwIf(spaceDeleteRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(!spaceService.deleteSpace(spaceDeleteRequest, loginUser),
                ErrorCode.SYSTEM_ERROR, "删除失败");
        return ResultUtils.success(true);
    }

    /**
     * 编辑空间名称（仅空间创建者或管理员可用）
     *
     * @param spaceEditRequest 空间编辑请求
     * @param request          request 请求
     * @return 是否编辑成功
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editSpace(@RequestBody SpaceEditRequest spaceEditRequest,
                                           HttpServletRequest request) {
        if (spaceEditRequest == null || spaceEditRequest.getId() == null || spaceEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 改名不允许把名称留空：validSpace 只在创建时校验名称非空
        ThrowUtils.throwIf(StrUtil.isBlank(spaceEditRequest.getSpaceName()),
                ErrorCode.PARAMS_ERROR, "空间名称不能为空");
        User loginUser = userService.getLoginUser(request);
        Space oldSpace = spaceService.getById(spaceEditRequest.getId());
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        // 私有空间只能改自己的；团队空间 admin / editor 成员也可以改名
        SpacePermissionUtils.checkSpaceAuth(oldSpace, loginUser, userService, spaceUserService);
        Space space = new Space();
        space.setId(spaceEditRequest.getId());
        space.setSpaceName(spaceEditRequest.getSpaceName());
        space.setEditTime(new Date());
        spaceService.validSpace(space, false);
        boolean result = spaceService.updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取空间实体（仅管理员可用）
     *
     * @param id 空间 id
     * @return 空间实体
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Space> getSpaceById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(space);
    }

    /**
     * 根据 id 获取空间封装类（仅空间创建者或管理员可用）
     *
     * @param id      空间 id
     * @param request request 请求
     * @return 空间封装类
     */
    @GetMapping("/get/vo")
    public BaseResponse<SpaceVO> getSpaceVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        User loginUser = userService.getLoginUser(request);
        // 不能查看别人的私有空间；团队空间成员可查看
        SpacePermissionUtils.checkSpaceViewAuth(space, loginUser, userService, spaceUserService);
        return ResultUtils.success(spaceService.getSpaceVO(space, request));
    }

    /**
     * 分页获取空间实体列表（仅管理员可用）
     *
     * @param spaceQueryRequest 空间查询请求
     * @return 空间分页数据
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Space>> listSpaceByPage(@RequestBody SpaceQueryRequest spaceQueryRequest) {
        ThrowUtils.throwIf(spaceQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long current = spaceQueryRequest.getCurrent();
        long size = spaceQueryRequest.getPageSize();
        Page<Space> spacePage = spaceService.page(new Page<>(current, size),
                spaceService.getQueryWrapper(spaceQueryRequest));
        return ResultUtils.success(spacePage);
    }

    /**
     * 分页获取空间封装列表（仅管理员可用）
     *
     * @param spaceQueryRequest 空间查询请求
     * @param request           request 请求
     * @return 空间封装分页数据
     */
    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<SpaceVO>> listSpaceVOByPage(@RequestBody SpaceQueryRequest spaceQueryRequest,
                                                         HttpServletRequest request) {
        ThrowUtils.throwIf(spaceQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long current = spaceQueryRequest.getCurrent();
        long size = spaceQueryRequest.getPageSize();
        Page<Space> spacePage = spaceService.page(new Page<>(current, size),
                spaceService.getQueryWrapper(spaceQueryRequest));
        return ResultUtils.success(spaceService.getSpaceVOPage(spacePage, request));
    }

    /**
     * 分页获取当前用户自己的空间列表
     * <p>
     * 用户 id 由后端从登录态写入，前端传什么都不作数，避免查到别人的空间。
     *
     * @param spaceQueryRequest 空间查询请求
     * @param request           request 请求
     * @return 空间封装分页数据
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<SpaceVO>> listMySpaceVOByPage(@RequestBody SpaceQueryRequest spaceQueryRequest,
                                                           HttpServletRequest request) {
        ThrowUtils.throwIf(spaceQueryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        spaceQueryRequest.setUserId(loginUser.getId());
        long current = spaceQueryRequest.getCurrent();
        long size = spaceQueryRequest.getPageSize();
        Page<Space> spacePage = spaceService.page(new Page<>(current, size),
                spaceService.getQueryWrapper(spaceQueryRequest));
        return ResultUtils.success(spaceService.getSpaceVOPage(spacePage, request));
    }

    /**
     * 获取空间级别字典（创建空间时给前端下拉框用）
     *
     * @return 级别列表：text / value / maxSize / maxCount
     */
    @GetMapping("/list/level")
    public BaseResponse<List<Map<String, Object>>> listSpaceLevel() {
        List<Map<String, Object>> levelList = Arrays.stream(SpaceLevelEnum.values())
                .map(spaceLevelEnum -> {
                    Map<String, Object> map = new HashMap<>(4);
                    map.put("text", spaceLevelEnum.getText());
                    map.put("value", spaceLevelEnum.getValue());
                    map.put("maxSize", spaceLevelEnum.getMaxSize());
                    map.put("maxCount", spaceLevelEnum.getMaxCount());
                    return map;
                })
                .collect(Collectors.toList());
        return ResultUtils.success(levelList);
    }
}
