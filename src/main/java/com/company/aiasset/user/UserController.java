package com.company.aiasset.user;

import com.company.aiasset.security.CurrentUser;
import com.company.aiasset.team.TeamRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserRepository userRepository,
                         TeamRepository teamRepository,
                         PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public List<UserDto> list(CurrentUser currentUser) {
        if (!currentUser.hasRole(User.Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可查看用户列表");
        }
        return userRepository.findAll().stream()
                .map(UserDto::from)
                .toList();
    }

    @GetMapping("/{id}")
    public UserDto get(@PathVariable UUID id, CurrentUser currentUser) {
        if (!currentUser.hasRole(User.Role.ADMIN) && !currentUser.user().getId().equals(id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅能查看自己或需要管理员权限");
        }
        return userRepository.findById(id)
                .map(UserDto::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public UserDto create(@RequestBody CreateUserRequest req, CurrentUser currentUser) {
        if (!currentUser.hasRole(User.Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可创建用户");
        }

        if (userRepository.existsByUsernameIgnoreCase(req.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }

        if (req.teamId() != null && !teamRepository.existsById(req.teamId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "团队不存在");
        }

        String hash = passwordEncoder.encode(req.password());
        User user = new User(req.username(), req.displayName(), hash, req.role());
        user.setTeamId(req.teamId());
        user.setEmail(req.email());

        return UserDto.from(userRepository.save(user));
    }

    @PutMapping("/{id}")
    public UserDto update(@PathVariable UUID id, @RequestBody UpdateUserRequest req, CurrentUser currentUser) {
        if (!currentUser.hasRole(User.Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可修改用户");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (req.teamId() != null && !teamRepository.existsById(req.teamId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "团队不存在");
        }

        user.setDisplayName(req.displayName());
        user.setEmail(req.email());
        user.setRole(req.role());
        user.setTeamId(req.teamId());
        user.setStatus(req.status());

        return UserDto.from(userRepository.save(user));
    }

    @PostMapping("/{id}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable UUID id, @RequestBody ResetPasswordRequest req, CurrentUser currentUser) {
        if (!currentUser.hasRole(User.Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可重置密码");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        String hash = passwordEncoder.encode(req.newPassword());
        user.setPasswordHash(hash);
        user.setMustChangePassword(true);
        userRepository.save(user);
    }

    public record CreateUserRequest(
        String username,
        String displayName,
        String email,
        String password,
        User.Role role,
        UUID teamId
    ) {}

    public record UpdateUserRequest(
        String displayName,
        String email,
        User.Role role,
        User.Status status,
        UUID teamId
    ) {}

    public record ResetPasswordRequest(String newPassword) {}
}
