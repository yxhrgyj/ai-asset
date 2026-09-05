package com.company.aiasset.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectRuleRepository extends JpaRepository<ProjectRule, UUID> {

    /**
     * 查找项目的所有规则绑定
     */
    List<ProjectRule> findByProjectIdOrderByCreatedAt(UUID projectId);

    /**
     * 检查项目是否已绑定某个资产版本
     */
    boolean existsByProjectIdAndAssetVersionId(UUID projectId, UUID assetVersionId);

    /**
     * 删除项目的某个规则绑定
     */
    void deleteByProjectIdAndAssetVersionId(UUID projectId, UUID assetVersionId);

    /**
     * 查询项目绑定的所有资产版本 ID
     */
    @Query("SELECT pr.assetVersionId FROM ProjectRule pr WHERE pr.projectId = :projectId")
    List<UUID> findAssetVersionIdsByProjectId(@Param("projectId") UUID projectId);
}
