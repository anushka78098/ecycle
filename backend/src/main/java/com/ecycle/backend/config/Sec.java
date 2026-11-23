package com.ecycle.backend.config;

import com.ecycle.backend.exception.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class Sec {
    private Sec() {}
    public static AuthUser me() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof AuthUser u)) throw ApiException.unauthorized("Authentication required");
        return u;
    }
}
