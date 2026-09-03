package com.company.aiasset.security;

import com.company.aiasset.user.User;
import com.company.aiasset.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    static final String SESSION_USER_ID = "USER_ID";

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AuthController(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record ChangePasswordRequest(@NotBlank String currentPassword,
                                        @NotBlank String newPassword) {}

    @PostMapping("/login")
    @Transactional
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        Optional<User> found = users.findByUsernameIgnoreCase(req.username());

        // 用户不存在与口令错误返回同一响应，避免暴露账号是否存在。
        // 注意：本地阶段没有登录限流，公网部署前必须补上（方案文档 5.5 档二）。
        if (found.isEmpty()
                || found.get().getStatus() == User.Status.DISABLED
                || !encoder.matches(req.password(), found.get().getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of("message", "用户名或口令不正确"));
        }

        User user = found.get();
        user.setLastLoginAt(Instant.now());

        // 防会话固定攻击：登录成功后换一个新会话 ID。
        HttpSession old = http.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        http.getSession(true).setAttribute(SESSION_USER_ID, user.getId());

        return ResponseEntity.ok(toDto(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        HttpSession session = http.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(CurrentUser current) {
        return ResponseEntity.ok(toDto(current.user()));
    }

    @PostMapping("/change-password")
    @Transactional
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest req,
                                            CurrentUser current) {
        User user = current.user();
        if (!encoder.matches(req.currentPassword(), user.getPasswordHash())) {
            return ResponseEntity.status(400).body(Map.of("message", "原口令不正确"));
        }
        if (req.newPassword().length() < 10) {
            return ResponseEntity.badRequest().body(Map.of("message", "新口令至少 10 位"));
        }
        user.setPasswordHash(encoder.encode(req.newPassword()));
        user.setMustChangePassword(false);
        users.save(user);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> toDto(User u) {
        return Map.of(
                "id", u.getId(),
                "username", u.getUsername(),
                "displayName", u.getDisplayName(),
                "role", u.getRole().name(),
                "mustChangePassword", u.isMustChangePassword()
        );
    }
}
