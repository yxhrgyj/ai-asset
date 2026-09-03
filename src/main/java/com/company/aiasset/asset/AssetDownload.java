package com.company.aiasset.asset;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * 资产下载记录（审计用）。
 */
@Entity
@Table(name = "asset_downloads")
public class AssetDownload {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "asset_id", nullable = false, columnDefinition = "uuid")
    private UUID assetId;

    @Column(name = "version_id", columnDefinition = "uuid")
    private UUID versionId;

    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "downloaded_at", nullable = false, insertable = false, updatable = false)
    private Instant downloadedAt;

    public UUID getId() { return id; }

    public UUID getAssetId() { return assetId; }
    public void setAssetId(UUID assetId) { this.assetId = assetId; }

    public UUID getVersionId() { return versionId; }
    public void setVersionId(UUID versionId) { this.versionId = versionId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public Instant getDownloadedAt() { return downloadedAt; }
}
