package com.company.aiasset.asset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetVersionRepository extends JpaRepository<AssetVersion, UUID> {

    List<AssetVersion> findByAssetIdOrderByVersionNoDesc(UUID assetId);

    /**
     * 当前开放（可编辑）版本。asset_versions_single_open_uk 保证最多一条，
     * 因此这里可以安全返回 Optional 而不是 List。
     */
    @Query("""
           select v from AssetVersion v
           where v.assetId = :assetId
             and v.status in (com.company.aiasset.asset.AssetVersion$Status.DRAFT,
                              com.company.aiasset.asset.AssetVersion$Status.PENDING)
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
}
