package com.company.aiasset.team;

import java.time.Instant;
import java.util.UUID;

public record TeamDto(
    UUID id,
    String name,
    Instant createdAt
) {
    public static TeamDto from(Team team) {
        return new TeamDto(
            team.getId(),
            team.getName(),
            team.getCreatedAt()
        );
    }
}
