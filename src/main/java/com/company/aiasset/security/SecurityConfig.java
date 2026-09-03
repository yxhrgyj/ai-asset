package com.company.aiasset.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import com.company.aiasset.user.UserRepository;

@Configuration
public class SecurityConfig {

    private final UserRepository users;

    public SecurityConfig(UserRepository users) {
        this.users = users;
    }

    /**
     * bcrypt。Spring Security 的默认选择，也是方案文档 5.5「档一」要求现在就做对的项。
     * 若先存明文再迁移，需要让全公司改一次口令。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 必须在鉴权判定之前把会话还原成 Authentication，否则 authenticated() 恒为 false。
            //
            // 注册位置是 AnonymousAuthenticationFilter 之前，不是 AuthorizationFilter 之前：
            // 后者位于匿名过滤器下游，等我们执行时 SecurityContext 里已经被塞了一个
            // AnonymousAuthenticationToken，"没有 Authentication 才还原"的判断永不成立，
            // 结果带着有效会话 Cookie 访问 /api/** 一样得 401。
            .addFilterBefore(new SessionAuthFilter(users), AnonymousAuthenticationFilter.class)
            // 前后端分离 + 会话 Cookie。CSRF 在本地阶段关闭以便用 curl 调试；
            // 上公网前应启用并让前端带 XSRF-TOKEN，见方案文档 5.5 档二。
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login", "/api/auth/logout").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            )
            // 未登录时返回 401 而非重定向登录页——前端是 SPA，需要状态码。
            .exceptionHandling(e -> e
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable());

        return http.build();
    }
}
