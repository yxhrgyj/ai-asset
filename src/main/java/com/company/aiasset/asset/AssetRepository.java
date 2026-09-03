package com.company.aiasset.asset;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AssetRepository extends JpaRepository<Asset, UUID> {

    Optional<Asset> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /**
     * 列表与搜索合一：三个筛选条件都可为 null 表示不限制。
     *
     * 搜索用 pg_trgm 的 `%` 相似度运算符而非 LIKE，理由见方案文档 5.4——
     * 没装中文分词插件，靠三元组相似度做模糊匹配。V6 建的 GIN 索引在此生效。
     * `%` 需要 pg_trgm.similarity_threshold（默认 0.3）配合，短查询词命中率偏低，
     * 所以再并上一个 ILIKE 兜底：搜"登录"这种两字词时 trigram 常常不达阈值。
     */
    @Query(value = """
            SELECT * FROM assets a
            WHERE a.archived = false
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
            WHERE a.archived = false
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
                       Pageable pageable);
}
