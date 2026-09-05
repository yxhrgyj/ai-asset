package com.company.aiasset.team;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {

    /**
     * 查找用户所在的所有团队 ID
     */
    @Query("SELECT tm.teamId FROM TeamMember tm WHERE tm.userId = :userId")
    List<UUID> findTeamIdsByUserId(@Param("userId") UUID userId);

    /**
     * 检查用户是否是团队成员
     */
    boolean existsByTeamIdAndUserId(UUID teamId, UUID userId);

    /**
     * 查找团队的所有成员
     */
    List<TeamMember> findByTeamId(UUID teamId);

    /**
     * 删除团队成员
     */
    void deleteByTeamIdAndUserId(UUID teamId, UUID userId);
}
