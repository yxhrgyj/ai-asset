package com.company.aiasset.portal;

import com.company.aiasset.asset.Asset;
import com.company.aiasset.asset.AssetVersion;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Stable, read-only response shapes for the unauthenticated portal. */
public final class PortalDto {

    private PortalDto() {}

    public record AssetSummary(
            UUID id,
            String name,
            String slug,
            String type,
            String scope,
            String summary,
            String category,
            List<String> tags,
            String techStack,
            int downloadCount,
            Instant publishedAt,
            Instant updatedAt
    ) {
        public static AssetSummary of(Asset asset, AssetVersion published) {
            Instant publishedAt = published.getPublishedAt() != null
                    ? published.getPublishedAt()
                    : published.getCreatedAt();
            return new AssetSummary(
                    asset.getId(),
                    asset.getName(),
                    asset.getSlug(),
                    asset.getType().name(),
                    asset.getScope().name(),
                    asset.getSummary(),
                    asset.getCategory(),
                    List.of(asset.getTags()),
                    asset.getTechStack(),
                    asset.getDownloadCount(),
                    publishedAt,
                    asset.getUpdatedAt());
        }
    }

    public record PublishedVersion(
            UUID id,
            int versionNo,
            String changelog,
            Instant publishedAt
    ) {
        public static PublishedVersion of(AssetVersion version) {
            return new PublishedVersion(
                    version.getId(),
                    version.getVersionNo(),
                    version.getChangelog(),
                    version.getPublishedAt());
        }
    }

    public record AssetDetail(
            AssetSummary summary,
            String body,
            int versionNo,
            String changelog,
            List<com.company.aiasset.asset.AssetDto.FileInfo> files,
            List<PublishedVersion> versions,
            boolean canEdit
    ) {}

    public record Statistics(
            long totalAssets,
            long totalDownloads,
            TypeBreakdown typeBreakdown,
            ScopeBreakdown scopeBreakdown
    ) {}

    public record TypeBreakdown(long rule, long skill, long document) {}

    public record ScopeBreakdown(long organization, long techStack, long project) {}
}
