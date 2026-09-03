package com.company.aiasset.asset;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * 资产的一个版本。正文与附件都属于版本，不属于资产本体。
 *
 * 两条数据库层规则决定了这里能做什么：
 * - asset_versions_single_open_uk：每个资产最多一个 DRAFT/PENDING 版本，
 *   所以"新建草稿"必须先查是否已有开放版本，否则插入会撞唯一索引。
 * - trg_asset_versions_immutable：PUBLISHED/DEPRECATED/WITHDRAWN 的正文、
 *   版本号、作者、发布时间都改不动，要改内容只能发新版本。
 */
@Entity
@Table(name = "asset_versions")
public class AssetVersion {

    /** 与 asset_versions_status_check 一致。v1 只用到 DRAFT 与 PUBLISHED，其余为第 3 步预留。 */
    public enum Status { DRAFT, PENDING, REJECTED, PUBLISHED, DEPRECATED, WITHDRAWN }

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "asset_id", nullable = false, columnDefinition = "uuid")
    private UUID assetId;

    /** 从 1 开始，同一资产内唯一（asset_versions_no_uk）。 */
    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;

    /** Markdown 原文。渲染在前端做，后端不存 HTML。 */
    @Column(columnDefinition = "text")
    private String body;

    /** 发布时必填（asset_versions_published_needs_hash），用于校验内容是否被改动过。 */
    @Column(name = "content_hash")
    private String contentHash;

    private String changelog;

    @Column(name = "created_by", nullable = false, columnDefinition = "uuid")
    private UUID createdBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    /** 已发布的版本不可再改内容，交由数据库触发器强制；此处仅用于界面与接口的前置判断。 */
    public boolean isFrozen() {
        return status == Status.PUBLISHED
                || status == Status.DEPRECATED
                || status == Status.WITHDRAWN;
    }

    public UUID getId() { return id; }

    public UUID getAssetId() { return assetId; }
    public void setAssetId(UUID assetId) { this.assetId = assetId; }

    public int getVersionNo() { return versionNo; }
    public void setVersionNo(int versionNo) { this.versionNo = versionNo; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    public String getChangelog() { return changelog; }
    public void setChangelog(String changelog) { this.changelog = changelog; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}
