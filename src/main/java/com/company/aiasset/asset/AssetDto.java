package com.company.aiasset.asset;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 对外响应形状。实体不直接序列化：assets 表以后会加内部字段，
 * 直接返回实体会让接口随表结构漂移。
 */
public final class AssetDto {

    private AssetDto() {}

    public record Summary(UUID id,
                          String type,
                          String name,
                          String slug,
                          String summary,
                          String category,
                          List<String> tags,
                          String scope,
                          String techStack,
                          boolean archived,
                          int downloadCount,
                          Instant updatedAt) {}

    public record VersionInfo(UUID id,
                              int versionNo,
                              String status,
                              String changelog,
                              String contentHash,
                              Instant publishedAt,
                              Instant updatedAt) {}

    public record FileInfo(UUID id,
                           String relativePath,
                           String mimeType,
                           long sizeBytes,
                           String contentHash,
                           Instant createdAt) {}

    /** 详情页一次拿全：本体 + 当前展示版本 + 版本列表 + 附件。 */
    public record Detail(Summary asset,
                         VersionInfo currentVersion,
                         String body,
                         List<VersionInfo> versions,
                         List<FileInfo> files,
                         boolean canEdit) {}

    public static Summary of(Asset a) {
        return new Summary(a.getId(), a.getType().name(), a.getName(), a.getSlug(),
                a.getSummary(), a.getCategory(), List.of(a.getTags()),
                a.getScope().name(), a.getTechStack(), a.isArchived(),
                a.getDownloadCount(), a.getUpdatedAt());
    }

    public static VersionInfo of(AssetVersion v) {
        return new VersionInfo(v.getId(), v.getVersionNo(), v.getStatus().name(),
                v.getChangelog(), v.getContentHash(), v.getPublishedAt(), v.getUpdatedAt());
    }

    public static FileInfo of(AssetFile f) {
        return new FileInfo(f.getId(), f.getRelativePath(), f.getMimeType(),
                f.getSizeBytes(), f.getContentHash(), f.getCreatedAt());
    }
}
