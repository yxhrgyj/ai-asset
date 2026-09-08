package com.company.aiasset.asset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetVersionRepository extends JpaRepository<AssetVersion, UUID> {

    List<AssetVersion> findByAssetIdOrderByVersionNoDesc(UUID assetId);

    /**
     * 统计指定状态的版本数量
     */
    @Query("SELECT COUNT(v) FROM AssetVersion v WHERE CAST(v.status AS string) = :status")
    long countByStatus(@Param("status") String status);

    /**
     * 当前开放（可编辑）版本。asset_versions_single_open_uk 保证最多一条，
     * 因此这里可以安全返回 Optional 而不是 List。行锁串行化发布、正文和附件修改。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
           select v from AssetVersion v
           where v.assetId = :assetId
             and v.status = com.company.aiasset.asset.AssetVersion$Status.DRAFT
           """)
    Optional<AssetVersion> findOpenVersion(@Param("assetId") UUID assetId);

    /** 最新已发布版本，详情页默认展示这个。 */
    @Query("""
           select v from AssetVersion v
           where v.assetId = :assetId
             and v.status = com.company.aiasset.asset.AssetVersion$Status.PUBLISHED
           order by v.versionNo desc
           limit 1
           """)
    Optional<AssetVersion> findLatestPublished(@Param("assetId") UUID assetId);

    @Query("select coalesce(max(v.versionNo), 0) from AssetVersion v where v.assetId = :assetId")
    int maxVersionNo(@Param("assetId") UUID assetId);

    /**
     * 查找资产的所有版本，按创建时间倒序
     */
    List<AssetVersion> findByAssetIdOrderByCreatedAtDesc(UUID assetId);
}
