package com.company.aiasset.asset;

import com.company.aiasset.statistics.StatisticsController;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AssetDownloadRepository extends JpaRepository<AssetDownload, UUID> {

    /**
     * 查询热门资产（按下载次数排序）
     */
    @Query("""
        SELECT new com.company.aiasset.statistics.StatisticsController$PopularAssetStats(
            CAST(a.id AS string),
            a.name,
            CAST(a.type AS string),
            a.slug,
            COUNT(d.id),
            MAX(d.downloadedAt)
        )
        FROM AssetDownload d
        JOIN Asset a ON d.assetId = a.id
        WHERE d.downloadedAt >= :since
        GROUP BY a.id, a.name, a.type, a.slug
        ORDER BY COUNT(d.id) DESC
        LIMIT :limit
    """)
    List<StatisticsController.PopularAssetStats> findPopularAssets(
            @Param("since") Instant since,
            @Param("limit") int limit
    );

    /**
     * 查询最近下载记录
     */
    @Query("""
        SELECT new com.company.aiasset.statistics.StatisticsController$DownloadRecord(
            CAST(a.id AS string),
            a.name,
            CAST(u.id AS string),
            u.username,
            d.downloadedAt
        )
        FROM AssetDownload d
        JOIN Asset a ON d.assetId = a.id
        JOIN User u ON d.userId = u.id
        ORDER BY d.downloadedAt DESC
        LIMIT :limit
    """)
    List<StatisticsController.DownloadRecord> findRecentDownloads(@Param("limit") int limit);
}
