package com.company.aiasset.asset;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * 资产本体：只存不随版本变化的元数据。正文与附件挂在 {@link AssetVersion} 上。
 *
 * 字段与 V2 的 CHECK 约束一一对应，枚举值改名要同时改迁移脚本，
 * 否则插入时会被数据库拒绝而不是在 Java 层报错。
 */
@Entity
@Table(name = "assets")
public class Asset {

    /** 与 assets_type_check 一致。 */
    public enum Type { RULE, SKILL, DOCUMENT }

    /** 与 assets_scope_check 一致。 */
    public enum Scope { ORGANIZATION, TECH_STACK, PROJECT }

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(nullable = false)
    private String name;

    /** URL 友好标识，全库唯一（assets_slug_key）。 */
    @Column(nullable = false)
    private String slug;

    private String summary;

    private String category;

    /**
     * text[] 而非关联表：v1 的标签只用于筛选，不需要维护标签本身。
     * 需要重命名标签或统计使用量时再拆表。
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private String[] tags = new String[0];

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Scope scope = Scope.ORGANIZATION;

    /** assets_tech_stack_pairing：当且仅当 scope=TECH_STACK 时非空。 */
    @Column(name = "tech_stack")
    private String techStack;

    @Column(name = "owner_user_id", nullable = false, columnDefinition = "uuid")
    private UUID ownerUserId;

    @Column(name = "team_id", columnDefinition = "uuid")
    private UUID teamId;

    /** 归档代替删除：资产可能已被下载引用，物理删除会让追溯断链。 */
    @Column(nullable = false)
    private boolean archived = false;

    /** 下载次数统计。 */
    @Column(name = "download_count", nullable = false)
    private int downloadCount = 0;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String[] getTags() { return tags; }
    public void setTags(String[] tags) { this.tags = tags == null ? new String[0] : tags; }

    public Scope getScope() { return scope; }
    public void setScope(Scope scope) { this.scope = scope; }

    public String getTechStack() { return techStack; }
    public void setTechStack(String techStack) { this.techStack = techStack; }

    public UUID getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(UUID ownerUserId) { this.ownerUserId = ownerUserId; }

    public UUID getTeamId() { return teamId; }
    public void setTeamId(UUID teamId) { this.teamId = teamId; }

    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }

    public int getDownloadCount() { return downloadCount; }
    public void setDownloadCount(int downloadCount) { this.downloadCount = downloadCount; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}
