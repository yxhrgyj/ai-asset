package com.company.aiasset.security;

import com.company.aiasset.user.User;
import com.company.aiasset.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 把会话中的用户 ID 还原为 Spring Security 的 Authentication。
 *
 * 为什么需要这个：AuthController 手工管理会话属性（没用 formLogin），
 * 因此 SecurityContext 默认是空的，SecurityConfig 里的 authenticated()
 * 判定会全部失败。此过滤器负责这段衔接。
 *
 * 注意此类刻意不加 @Component：加了会被 Spring Boot 自动注册到 servlet
 * 过滤器链，与 SecurityConfig 中的 addFilterBefore 形成重复执行。
 * 由 SecurityConfig 显式构造并注册。
 *
 * 也刻意不清理 SecurityContext：请求结束后的清理由 Spring Security 的
 * SecurityContextHolderFilter 负责，在此处 clear 会与之冲突。
 */
public class SessionAuthFilter extends OncePerRequestFilter {

    private final UserRepository users;

    public SessionAuthFilter(UserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);

        if (session != null && !alreadyAuthenticated()) {
            Object raw = session.getAttribute(AuthController.SESSION_USER_ID);
            if (raw instanceof UUID userId) {
                Optional<User> found = users.findById(userId);

                if (found.isPresent() && found.get().getStatus() == User.Status.ACTIVE) {
                    User user = found.get();
                    var auth = new UsernamePasswordAuthenticationToken(
                            user, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } else {
                    // 账号被禁用（如离职）后，已存在的会话立即失效。
                    session.invalidate();
                }
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * 匿名令牌不算已认证。
     *
     * 只判 {@code getAuthentication() != null} 不够：一旦本过滤器被排到
     * AnonymousAuthenticationFilter 之后，context 里就有个
     * AnonymousAuthenticationToken，会让我们误以为已认证而跳过还原，
     * 表现为带有效会话也访问不了受保护端点。这里显式排除匿名，
     * 使还原逻辑不依赖注册顺序。
     */
    private boolean alreadyAuthenticated() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
    }
}
