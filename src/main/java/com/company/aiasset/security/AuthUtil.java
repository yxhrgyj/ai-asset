package com.company.aiasset.security;

import com.company.aiasset.user.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 认证工具类，用于获取当前登录用户信息
 */
@Component
public class AuthUtil {

    /**
     * 获取当前登录用户的 ID
     * @throws IllegalStateException 如果用户未登录
     */
    public UUID getCurrentUserId() {
        User user = getCurrentUser();
        return user.getId();
    }

    /**
     * 获取当前登录用户
     * @throws IllegalStateException 如果用户未登录
     */
    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof User)) {
            throw new IllegalStateException("用户未登录");
        }

        return (User) auth.getPrincipal();
    }
}
