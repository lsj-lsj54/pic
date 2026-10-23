package com.lsj.demo.manager.auth;

import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import com.lsj.demo.exception.BusinessException;
import com.lsj.demo.exception.ErrorCode;
import com.lsj.demo.manager.auth.model.SpaceUserAuthContext;
import com.lsj.demo.model.constant.SpaceUserPermissionConstant;
import com.lsj.demo.model.entity.Picture;
import com.lsj.demo.model.entity.Space;
import com.lsj.demo.model.entity.SpaceUser;
import com.lsj.demo.model.entity.User;
import com.lsj.demo.model.enums.SpaceRoleEnum;
import com.lsj.demo.model.enums.SpaceTypeEnum;
import com.lsj.demo.model.enums.UserRoleEnum;
import com.lsj.demo.service.PictureService;
import com.lsj.demo.service.SpaceService;
import com.lsj.demo.service.SpaceUserService;
import com.lsj.demo.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.ServletUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.Collections;

import static com.lsj.demo.model.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 自定义权限加载接口实现类
 */
@Slf4j
@Component    // 保证此类被 SpringBoot 扫描，完成 Sa-Token 的自定义权限验证扩展
public class StpInterfaceImpl implements StpInterface {

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;
    @Resource
    private SpaceUserService spaceUserService;
    @Resource
    private PictureService pictureService;
    @Resource
    private UserService userService;
    @Resource
    private SpaceService spaceService;

    /**
     * 返回一个账号所拥有的权限码集合
     */
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 判断 loginType，仅对类型为 "space" 进行权限校验
        if (!StpKit.SPACE_TYPE.equals(loginType)) {
            return new ArrayList<>();
        }
        // 管理员权限，表示权限校验通过
        List<String> ADMIN_PERMISSIONS = spaceUserAuthManager.getPermissionsByRole(SpaceRoleEnum.ADMIN.getValue());
        // 获取上下文对象
        SpaceUserAuthContext authContext = getAuthContextByRequest();
        // 如果所有字段都为空，表示查询公共图库，可以通过
        if (isAllFieldsNull(authContext)) {
            return ADMIN_PERMISSIONS;
        }
        // 获取 userId
        User loginUser = (User) StpKit.SPACE.getSessionByLoginId(loginId).get(USER_LOGIN_STATE);
        if (loginUser == null) {
            // 会话里没有用户信息（例如上线前签发的旧 token），退化为查库，避免直接判未登录
            loginUser = userService.getById(Long.valueOf(loginId.toString()));
        }
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "用户未登录");
        }
        Long userId = loginUser.getId();
        // 优先从上下文中获取 SpaceUser 对象
        SpaceUser spaceUser = authContext.getSpaceUser();
        if (spaceUser != null) {
            return spaceUserAuthManager.getPermissionsByRole(spaceUser.getSpaceRole());
        }
        // 如果有 spaceUserId，必然是团队空间，通过数据库查询 SpaceUser 对象
        Long spaceUserId = authContext.getSpaceUserId();
        if (spaceUserId != null) {
            spaceUser = spaceUserService.getById(spaceUserId);
            if (spaceUser == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到空间用户信息");
            }
            // 取出当前登录用户对应的 spaceUser
            SpaceUser loginSpaceUser = spaceUserService.lambdaQuery()
                    .eq(SpaceUser::getSpaceId, spaceUser.getSpaceId())
                    .eq(SpaceUser::getUserId, userId)
                    .one();
            if (loginSpaceUser == null) {
                return new ArrayList<>();
            }
            // 这里会导致管理员在私有空间没有权限，可以再查一次库处理
            return spaceUserAuthManager.getPermissionsByRole(loginSpaceUser.getSpaceRole());
        }
        // 如果没有 spaceUserId，尝试通过 spaceId 或 pictureId 获取 Space 对象并处理
        Long spaceId = authContext.getSpaceId();
        if (spaceId == null) {
            // 如果没有 spaceId，通过 pictureId 获取 Picture 对象和 Space 对象
            Long pictureId = authContext.getPictureId();
            // 图片 id 也没有，则默认通过权限校验
            if (pictureId == null) {
                return ADMIN_PERMISSIONS;
            }
            Picture picture = pictureService.lambdaQuery()
                    .eq(Picture::getId, pictureId)
                    .select(Picture::getId, Picture::getSpaceId, Picture::getUserId)
                    .one();
            if (picture == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到图片信息");
            }
            spaceId = picture.getSpaceId();
            // 公共图库，仅本人或管理员可操作
            if (spaceId == null) {
                if (picture.getUserId().equals(userId) || userService.isAdmin(loginUser)) {
                    return ADMIN_PERMISSIONS;
                } else {
                    // 不是自己的图片，仅可查看
                    return Collections.singletonList(SpaceUserPermissionConstant.PICTURE_VIEW);
                }
            }
        }
        // 获取 Space 对象
        Space space = spaceService.getById(spaceId);
        if (space == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到空间信息");
        }
        // 根据 Space 类型判断权限
        if (space.getSpaceType() == SpaceTypeEnum.PRIVATE.getValue()) {
            // 私有空间，仅本人或管理员有权限
            if (space.getUserId().equals(userId) || userService.isAdmin(loginUser)) {
                return ADMIN_PERMISSIONS;
            } else {
                return new ArrayList<>();
            }
        } else {
            // 团队空间，查询 SpaceUser 并获取角色和权限
            spaceUser = spaceUserService.lambdaQuery()
                    .eq(SpaceUser::getSpaceId, spaceId)
                    .eq(SpaceUser::getUserId, userId)
                    .one();
            if (spaceUser == null) {
                return new ArrayList<>();
            }
            return spaceUserAuthManager.getPermissionsByRole(spaceUser.getSpaceRole());
        }
    }


    private boolean isAllFieldsNull(Object object) {
        if (object == null) {
            return true; // 对象本身为空
        }
        // 获取所有字段并判断是否所有字段都为空
        return Arrays.stream(ReflectUtil.getFields(object.getClass()))
                // 获取字段值
                .map(field -> ReflectUtil.getFieldValue(object, field))
                // 检查是否所有字段都为空
                .allMatch(ObjectUtil::isEmpty);
    }


    /**
     * 返回一个账号所拥有的角色集合（全局角色，与空间角色无关）
     * <p>
     * 不能像模板代码那样无条件返回 admin：{@code @SaCheckPermission(orRole = "admin")}
     * 一旦用上，任何人都能靠 orRole 绕过权限码校验。
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        // space 体系只做权限码校验，不提供全局角色
        if (StpKit.SPACE_TYPE.equals(loginType) || loginId == null) {
            return new ArrayList<>();
        }
        User loginUser = userService.getById(Long.valueOf(loginId.toString()));
        if (loginUser == null || !UserRoleEnum.ADMIN.getValue().equals(loginUser.getUserRole())) {
            return new ArrayList<>();
        }
        return Collections.singletonList(UserRoleEnum.ADMIN.getValue());
    }

    @Value("${server.servlet.context-path}")
    private String contextPath;

    /**
     * 从请求中获取上下文对象（把请求里的 id 解析成空间 / 图片 / 成员 id）
     * <p>
     * 不同接口的 {@code id} 含义不同，靠路径前缀区分。注意成员接口的路径是
     * {@code /space/user/**}，它的 id 是成员记录 id，必须在 {@code /space/} 的规则之前判断，
     * 否则会被当成空间 id 去算权限，导致改角色、退团队判到错误的空间上。
     */
    private SpaceUserAuthContext getAuthContextByRequest() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        SpaceUserAuthContext authRequest = parseAuthContext(request);
        // 根据请求路径区分 id 字段的含义
        Long id = authRequest.getId();
        if (ObjUtil.isNull(id)) {
            return authRequest;
        }
        String requestUri = request.getRequestURI();
        String partUri = StrUtil.removePrefix(requestUri, StrUtil.nullToEmpty(contextPath) + "/");
        if (StrUtil.startWith(partUri, "space/user")) {
            // 成员管理：id 是 space_user 记录 id
            authRequest.setSpaceUserId(id);
        } else if (StrUtil.startWith(partUri, "space")) {
            authRequest.setSpaceId(id);
        } else if (StrUtil.startWith(partUri, "picture")) {
            authRequest.setPictureId(id);
        }
        return authRequest;
    }

    /**
     * 按 Content-Type 从请求体或查询参数里解析上下文
     * <p>
     * 两个易踩的点：
     * 1. {@code application/json;charset=UTF-8} 这类带参数的内容类型，要按前缀匹配，
     *    否则会走到参数分支，拿不到 body 里的 spaceId；
     * 2. 允许请求体为空（例如无参 POST），空 body 或非法 JSON 时返回空上下文，
     *    不能让 JSON 解析异常把接口打成 500。
     */
    private SpaceUserAuthContext parseAuthContext(HttpServletRequest request) {
        String contentType = request.getHeader(Header.CONTENT_TYPE.getValue());
        if (StrUtil.startWithIgnoreCase(contentType, ContentType.JSON.getValue())) {
            String body = ServletUtil.getBody(request);
            if (StrUtil.isBlank(body)) {
                return new SpaceUserAuthContext();
            }
            try {
                SpaceUserAuthContext authRequest = JSONUtil.toBean(body, SpaceUserAuthContext.class);
                return authRequest == null ? new SpaceUserAuthContext() : authRequest;
            } catch (Exception e) {
                log.warn("解析空间鉴权上下文失败，按无空间上下文处理：{}", e.getMessage());
                return new SpaceUserAuthContext();
            }
        }
        Map<String, String> paramMap = ServletUtil.getParamMap(request);
        SpaceUserAuthContext authRequest = BeanUtil.toBean(paramMap, SpaceUserAuthContext.class);
        return authRequest == null ? new SpaceUserAuthContext() : authRequest;
    }

}
