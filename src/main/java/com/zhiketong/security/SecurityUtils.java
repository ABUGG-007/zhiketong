package com.zhiketong.security;

import com.zhiketong.common.BusinessException;
import com.zhiketong.common.ResultCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文工具：从 SecurityContext 中获取当前登录用户。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 获取当前登录用户，未登录返回 null。
     */
    public static LoginUser getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser)) {
            return null;
        }
        return (LoginUser) authentication.getPrincipal();
    }

    /**
     * 获取当前登录用户，未登录抛出 401。
     */
    public static LoginUser requireLogin() {
        LoginUser loginUser = getLoginUser();
        if (loginUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return loginUser;
    }

    /**
     * 获取当前用户 ID，未登录返回 null。
     */
    public static Long getUserId() {
        LoginUser loginUser = getLoginUser();
        return loginUser == null ? null : loginUser.getUserId();
    }

    /**
     * 获取当前用户角色编码，未登录返回 null。
     */
    public static String getRoleCode() {
        LoginUser loginUser = getLoginUser();
        return loginUser == null ? null : loginUser.getRoleCode();
    }

    /**
     * 判断当前用户是否拥有指定角色。
     */
    public static boolean hasRole(String roleCode) {
        return roleCode != null && roleCode.equals(getRoleCode());
    }
}
