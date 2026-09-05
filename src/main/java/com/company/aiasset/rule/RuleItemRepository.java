package com.company.aiasset.rule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RuleItemRepository extends JpaRepository<RuleItem, UUID> {

    /**
     * 查找某个资产版本的所有规则条目，按 sortOrder 排序
     */
    List<RuleItem> findByAssetVersionIdOrderBySortOrder(UUID assetVersionId);

    /**
     * 查找某个资产版本中是否存在指定 ruleKey 的规则
     */
    boolean existsByAssetVersionIdAndRuleKey(UUID assetVersionId, String ruleKey);

    /**
     * 查找多个资产版本的所有规则条目
     */
    @Query("SELECT r FROM RuleItem r WHERE r.assetVersionId IN :versionIds ORDER BY r.sortOrder")
    List<RuleItem> findByAssetVersionIds(@Param("versionIds") List<UUID> versionIds);
}
