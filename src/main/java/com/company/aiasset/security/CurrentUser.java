package com.company.aiasset.security;

import com.company.aiasset.user.User;

/**
 * 控制器方法参数，由 CurrentUserResolver 注入。
 * 用类型而非 @AuthenticationPrincipal 是为了让签名自解释。
 */
public record CurrentUser(User user) {

    public boolean hasRole(User.Role role) {
        return user.getRole() == role;
    }

    public boolean canApprove() {
        return user.getRole() == User.Role.APPROVER || user.getRole() == User.Role.ADMIN;
    }

    public boolean canAuthor() {
        return user.getRole() != User.Role.USER;
    }
}
