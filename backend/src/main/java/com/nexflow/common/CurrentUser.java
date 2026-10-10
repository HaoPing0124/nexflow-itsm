package com.nexflow.common;

import com.nexflow.user.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static UserPrincipal get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        return null;
    }

    public static Long id() {
        UserPrincipal principal = get();
        return principal != null ? principal.getUserId() : null;
    }

    public static String username() {
        UserPrincipal principal = get();
        return principal != null ? principal.getUsername() : null;
    }

    public static boolean hasRole(String code) {
        UserPrincipal principal = get();
        return principal != null && principal.hasRole(code);
    }

    public static boolean hasPermission(String code) {
        UserPrincipal principal = get();
        return principal != null && principal.hasPermission(code);
    }

    public static void requirePermission(String code) {
        if (!hasPermission(code)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少权限: " + code);
        }
    }
}
