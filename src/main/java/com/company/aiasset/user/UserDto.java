package com.company.aiasset.user;

import java.time.Instant;
import java.util.UUID;

public record UserDto(
    UUID id,
    String username,
    String displayName,
    String email,
    User.Role role,
    User.Status status,
    UUID teamId,
    boolean mustChangePassword,
    Instant lastLoginAt,
    Instant createdAt
) {
    public static UserDto from(User user) {
        return new UserDto(
            user.getId(),
            user.getUsername(),
            user.getDisplayName(),
            user.getEmail(),
            user.getRole(),
            user.getStatus(),
            user.getTeamId(),
            user.isMustChangePassword(),
            user.getLastLoginAt(),
            user.getCreatedAt()
        );
    }
}
