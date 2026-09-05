package com.company.aiasset.asset;

import com.company.aiasset.statistics.StatisticsController;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetRepository extends JpaRepository<Asset, UUID> {

    Optional<Asset> findBySlug(String slug);

    boolean existsBySlug(String slug);

    long countByArchivedFalse();

    /**
     * 查询活跃用户（按创建的资产数量和下载数排序）
     */
    @Query("""
        SELECT new com.company.aiasset.statistics.StatisticsController$ActiveUserStats(
            CAST(u.id AS string),
            u.username,
            u.displayName,
            COUNT(DISTINCT a.id),
            SUM(a.downloadCount)
        )
        FROM Asset a
        JOIN User u ON a.ownerUserId = u.id
        WHERE a.updatedAt >= :since
        GROUP BY u.id, u.username, u.displayName
        ORDER BY COUNT(DISTINCT a.id) DESC, SUM(a.downloadCount) DESC
        LIMIT :limit
    """)
    List<StatisticsController.ActiveUserStats> findActiveUsers(
            @Param("since") Instant since,
            @Param("limit") int limit
    );

    /**
     * 列表与搜索合一：三个筛选条件都可为 null 表示不限制。
     *
     * 搜索用 pg_trgm 的 `%` 相似度运算符而非 LIKE，理由见方案文档 5.4——
     * 没装中文分词插件，靠三元组相似度做模糊匹配。V6 建的 GIN 索引在此生效。
     * `%` 需要 pg_trgm.similarity_threshold（默认 0.3）配合，短查询词命中率偏低，
     * 所以再并上一个 ILIKE 兜底：搜"登录"这种两字词时 trigram 常常不达阈值。
     *
     * archived 参数：控制是否显示已归档资产，不能为 null（Controller 层会设置默认值 false）。
     */
    @Query(value = """
            SELECT * FROM assets a
            WHERE a.archived = :archived
              AND (:type     IS NULL OR a.type = :type)
              AND (:scope    IS NULL OR a.scope = :scope)
              AND (:tag      IS NULL OR :tag = ANY(a.tags))
              AND (
                    :q IS NULL
                 OR a.name %> :q
                 OR a.summary %> :q
                 OR a.name    ILIKE '%' || :q || '%'
                 OR a.summary ILIKE '%' || :q || '%'
              )
            ORDER BY
              CASE WHEN :q IS NULL THEN 0
                   ELSE GREATEST(
                          similarity(a.name, :q),
                          similarity(coalesce(a.summary, ''), :q)
                        )
              END DESC,
              a.updated_at DESC
            """,
            countQuery = """
            SELECT count(*) FROM assets a
            WHERE a.archived = :archived
              AND (:type     IS NULL OR a.type = :type)
              AND (:scope    IS NULL OR a.scope = :scope)
              AND (:tag      IS NULL OR :tag = ANY(a.tags))
              AND (
                    :q IS NULL
                 OR a.name %> :q
                 OR a.summary %> :q
                 OR a.name    ILIKE '%' || :q || '%'
                 OR a.summary ILIKE '%' || :q || '%'
              )
            """,
            nativeQuery = true)
    Page<Asset> search(@Param("q") String q,
                       @Param("type") String type,
                       @Param("scope") String scope,
                       @Param("tag") String tag,
                       @Param("archived") Boolean archived,
                       Pageable pageable);

    /**
     * 查找指定类型和作用域的未归档资产
     */
    List<Asset> findByTypeAndScopeAndArchivedFalse(Asset.Type type, Asset.Scope scope);
}
