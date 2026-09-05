package com.company.aiasset.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    /**
     * 根据 slug 查找项目
     */
    Optional<Project> findBySlug(String slug);

    /**
     * 检查 slug 是否已存在
     */
    boolean existsBySlug(String slug);

    /**
     * 查找用户拥有的所有项目（未归档）
     */
    List<Project> findByOwnerUserIdAndArchivedFalseOrderByCreatedAtDesc(UUID ownerUserId);

    /**
     * 查找团队的所有项目（未归档）
     */
    List<Project> findByTeamIdAndArchivedFalseOrderByCreatedAtDesc(UUID teamId);

    /**
     * 查找用户可见的所有项目：用户拥有的 + 用户所在团队的
     */
    @Query("SELECT p FROM Project p WHERE p.archived = false AND " +
           "(p.ownerUserId = :userId OR p.teamId IN :teamIds) " +
           "ORDER BY p.createdAt DESC")
    List<Project> findVisibleProjects(@Param("userId") UUID userId, @Param("teamIds") List<UUID> teamIds);

    @Query("SELECT p FROM Project p WHERE p.archived = false AND p.ownerUserId = :userId " +
           "ORDER BY p.createdAt DESC")
    List<Project> findByOwnerUserId(@Param("userId") UUID userId);
}
