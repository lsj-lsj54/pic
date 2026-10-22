package com.lsj.demo.controller;

import com.lsj.demo.annotation.SaCheckSpaceRole;
import com.lsj.demo.common.BaseResponse;
import com.lsj.demo.common.DeleteRequest;
import com.lsj.demo.common.ResultUtils;
import com.lsj.demo.exception.ErrorCode;
import com.lsj.demo.exception.ThrowUtils;
import com.lsj.demo.model.dto.spaceuser.SpaceUserAddRequest;
import com.lsj.demo.model.dto.spaceuser.SpaceUserEditRequest;
import com.lsj.demo.model.dto.spaceuser.SpaceUserQueryRequest;
import com.lsj.demo.model.entity.User;
import com.lsj.demo.model.vo.SpaceUserVO;
import com.lsj.demo.service.SpaceUserService;
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
 * 团队成员模块接口
 * <p>
 * 团队空间（spaceType = 1）的成员管理：邀请、移除、调整角色、查看成员列表，
 * 以及查询「我加入的团队空间」。成员权限统一由 Sa-Token 动态权限码校验
 * （见 {@code SaTokenStpInterfaceImpl}），具体规则见
 * {@link com.lsj.demo.service.SpaceUserService} 各方法注释。
 *
 * @author lsj
 */
@Slf4j
@RestController
@RequestMapping("/space/user")
@AllArgsConstructor
public class SpaceUserController {

    private final SpaceUserService spaceUserService;

    private final UserService userService;

    /**
     * 邀请用户加入团队空间
     * <p>
     * 权限：空间创建者、团队管理员或系统管理员（通过 Sa-Token 权限码 space:admin:{spaceId} 校验）。
     *
     * @param spaceUserAddRequest 邀请请求（spaceId / userId / spaceRole，角色缺省为 viewer）
     * @param request             request 请求
     * @return 新成员记录 id
     */
    @PostMapping("/add")
    @SaCheckSpaceRole(spaceIdField = "spaceId", anyRole = {"admin"})
    public BaseResponse<Long> addSpaceUser(@RequestBody SpaceUserAddRequest spaceUserAddRequest,
                                           HttpServletRequest request) {
        ThrowUtils.throwIf(spaceUserAddRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        long id = spaceUserService.addSpaceUser(spaceUserAddRequest, loginUser);
        return ResultUtils.success(id);
    }

    /**
     * 移除团队成员（成员也可用于退出团队）
     *
     * @param deleteRequest 成员记录 id
     * @param request       request 请求
     * @return 是否成功
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteSpaceUser(@RequestBody DeleteRequest deleteRequest,
                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        boolean result = spaceUserService.deleteSpaceUser(deleteRequest.getId(), loginUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "移除成员失败");
        return ResultUtils.success(true);
    }

    /**
     * 调整团队成员角色
     *
     * @param spaceUserEditRequest 编辑请求（id / spaceRole）
     * @param request              request 请求
     * @return 是否成功
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editSpaceUser(@RequestBody SpaceUserEditRequest spaceUserEditRequest,
                                               HttpServletRequest request) {
        ThrowUtils.throwIf(spaceUserEditRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        boolean result = spaceUserService.editSpaceUser(spaceUserEditRequest, loginUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "调整角色失败");
        return ResultUtils.success(true);
    }

    /**
     * 查询某个空间的成员列表
     *
     * @param spaceUserQueryRequest 查询请求（spaceId 必填，可按角色筛选）
     * @param request               request 请求
     * @return 成员封装列表（含用户信息）
     */
    @PostMapping("/list")
    public BaseResponse<List<SpaceUserVO>> listSpaceUser(@RequestBody SpaceUserQueryRequest spaceUserQueryRequest,
                                                         HttpServletRequest request) {
        ThrowUtils.throwIf(spaceUserQueryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(spaceUserService.listSpaceUserVO(spaceUserQueryRequest, loginUser));
    }

    /**
     * 查询当前用户加入的团队空间列表
     *
     * @param request request 请求
     * @return 我加入的团队空间（含空间信息与我的角色）
     */
    @PostMapping("/list/my")
    public BaseResponse<List<SpaceUserVO>> listMyTeamSpace(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(spaceUserService.listMyTeamSpace(loginUser));
    }
}
