package com.company.aiasset.rule;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * 规则条目。一个 RULE 类型资产包含多条规则，每条规则独立存储。
 * 合并与重复检查作用在条目级而非资产级。
 */
@Entity
@Table(name = "rule_items")
public class RuleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "asset_version_id", nullable = false)
    private UUID assetVersionId;

    @Column(name = "rule_key", nullable = false)
    private String ruleKey;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleLevel level;

    @Column(name = "path_globs", columnDefinition = "TEXT[]")
    private String[] pathGlobs = new String[0];

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public enum RuleLevel {
        REQUIRED,
        RECOMMENDED
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAssetVersionId() {
        return assetVersionId;
    }

    public void setAssetVersionId(UUID assetVersionId) {
        this.assetVersionId = assetVersionId;
    }

    public String getRuleKey() {
        return ruleKey;
    }

    public void setRuleKey(String ruleKey) {
        this.ruleKey = ruleKey;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public RuleLevel getLevel() {
        return level;
    }

    public void setLevel(RuleLevel level) {
        this.level = level;
    }

    public String[] getPathGlobs() {
        return pathGlobs;
    }

    public void setPathGlobs(String[] pathGlobs) {
        this.pathGlobs = pathGlobs;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
