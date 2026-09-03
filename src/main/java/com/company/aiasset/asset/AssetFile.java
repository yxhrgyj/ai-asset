package com.company.aiasset.asset;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * 版本的附件。
 *
 * asset_files_content_location 要求 text_content 与 storage_key 恰有一个非空：
 * 纯文本类附件直接入库便于搜索，二进制走文件系统。v1 上传的都是二进制路径，
 * 但保留 textContent 以便后续把 md/txt 附件纳入全文检索。
 *
 * relative_path 由 asset_files_path_safe 挡掉绝对路径、盘符、`..` 与反斜杠——
 * 这是防路径穿越的最后一道防线，Java 侧仍应先校验再入库。
 */
@Entity
@Table(name = "asset_files")
public class AssetFile {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "asset_version_id", nullable = false, columnDefinition = "uuid")
    private UUID assetVersionId;

    /** 展示用的文件名或相对路径，同一版本内唯一（asset_files_path_uk）。 */
    @Column(name = "relative_path", nullable = false)
    private String relativePath;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "content_hash", nullable = false)
    private String contentHash;

    @Column(name = "text_content", columnDefinition = "text")
    private String textContent;

    /** 文件系统中的存放键，由 FileStorage 生成，不含用户输入的路径成分。 */
    @Column(name = "storage_key")
    private String storageKey;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public UUID getId() { return id; }

    public UUID getAssetVersionId() { return assetVersionId; }
    public void setAssetVersionId(UUID assetVersionId) { this.assetVersionId = assetVersionId; }

    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String relativePath) { this.relativePath = relativePath; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }

    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    public String getTextContent() { return textContent; }
    public void setTextContent(String textContent) { this.textContent = textContent; }

    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }

    public Instant getCreatedAt() { return createdAt; }
}
