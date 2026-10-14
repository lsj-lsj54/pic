package com.lsj.demo.common;

import com.lsj.demo.exception.BusinessException;
import com.lsj.demo.exception.ErrorCode;
import com.lsj.demo.model.entity.Space;
import com.lsj.demo.model.entity.User;
import com.lsj.demo.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 空间权限校验的测试
 * <p>
 * 规则：私有空间只有空间创建者和管理员能看、能动，别人一律拒绝。
 * 这条规则是「不能看别人空间」的最后一道防线，必须逐个分支覆盖。
 */
class SpacePermissionUtilsTest {

    private static final long OWNER_ID = 1L;
    private static final long OTHER_ID = 2L;
    private static final long SPACE_ID = 10L;

    private User user(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private Space space(long ownerId) {
        Space space = new Space();
        space.setId(SPACE_ID);
        space.setUserId(ownerId);
        return space;
    }

    private UserService userService(boolean admin) {
        UserService userService = mock(UserService.class);
        when(userService.isAdmin(any(User.class))).thenReturn(admin);
        return userService;
    }

    @Test
    @DisplayName("空间创建者可以操作自己的空间")
    void ownerShouldPass() {
        assertDoesNotThrow(() -> SpacePermissionUtils.checkSpaceAuth(
                space(OWNER_ID), user(OWNER_ID), userService(false)));
    }

    @Test
    @DisplayName("管理员可以操作任意空间")
    void adminShouldPass() {
        assertDoesNotThrow(() -> SpacePermissionUtils.checkSpaceAuth(
                space(OWNER_ID), user(OTHER_ID), userService(true)));
    }

    @Test
    @DisplayName("别人不能操作我的空间")
    void otherUserShouldBeRejected() {
        BusinessException e = assertThrows(BusinessException.class, () -> SpacePermissionUtils.checkSpaceAuth(
                space(OWNER_ID), user(OTHER_ID), userService(false)));
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), e.getCode());
    }

    @Test
    @DisplayName("别人不能查看我的空间")
    void otherUserShouldNotView() {
        BusinessException e = assertThrows(BusinessException.class, () -> SpacePermissionUtils.checkSpaceViewAuth(
                space(OWNER_ID), user(OTHER_ID), userService(false)));
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), e.getCode());
    }

    @Test
    @DisplayName("空间创建者可以查看自己的空间")
    void ownerShouldView() {
        assertDoesNotThrow(() -> SpacePermissionUtils.checkSpaceViewAuth(
                space(OWNER_ID), user(OWNER_ID), userService(false)));
    }

    @Test
    @DisplayName("未登录不能查看或操作空间")
    void anonymousShouldBeRejected() {
        BusinessException e = assertThrows(BusinessException.class, () -> SpacePermissionUtils.checkSpaceViewAuth(
                space(OWNER_ID), null, userService(false)));
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), e.getCode());
    }

    @Test
    @DisplayName("空间不存在时应报「数据不存在」而不是无权限")
    void missingSpaceShouldReportNotFound() {
        BusinessException e = assertThrows(BusinessException.class, () -> SpacePermissionUtils.checkSpaceAuth(
                null, user(OWNER_ID), userService(false)));
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), e.getCode());
    }
}
