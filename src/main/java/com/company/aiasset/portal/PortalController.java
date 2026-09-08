package com.company.aiasset.portal;

import com.company.aiasset.asset.Asset;
import com.company.aiasset.asset.AssetDto;
import com.company.aiasset.asset.AssetFileRepository;
import com.company.aiasset.asset.AssetRepository;
import com.company.aiasset.asset.AssetVersion;
import com.company.aiasset.asset.AssetVersionRepository;
import com.company.aiasset.security.CurrentUser;
import com.company.aiasset.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/public")
public class PortalController {

    private final AssetRepository assets;
    private final AssetVersionRepository versions;
    private final AssetFileRepository files;

    public PortalController(AssetRepository assets,
                             AssetVersionRepository versions,
                             AssetFileRepository files) {
        this.assets = assets;
        this.versions = versions;
        this.files = files;
    }

    @GetMapping("/assets")
    public Map<String, Object> list(@RequestParam(required = false) String q,
                                    @RequestParam(required = false) Asset.Type type,
                                    @RequestParam(required = false) Asset.Scope scope,
                                    @RequestParam(required = false) String tag,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        Page<Asset> found = assets.searchPublished(
                blankToNull(q),
                type == null ? null : type.name(),
                scope == null ? null : scope.name(),
                blankToNull(tag),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));

        List<PortalDto.AssetSummary> items = found.getContent().stream()
                .map(asset -> versions.findLatestPublished(asset.getId())
                        .map(version -> PortalDto.AssetSummary.of(asset, version))
                        .orElse(null))
                .filter(summary -> summary != null)
                .toList();

        return Map.of(
                "items", items,
                "total", found.getTotalElements(),
                "page", found.getNumber(),
                "size", found.getSize());
    }

    @GetMapping("/download-ranking")
    public List<PortalDto.AssetSummary> downloadRanking(
            @RequestParam(required = false) Asset.Type type,
            @RequestParam(defaultValue = "5") int limit) {
        return assets.findDownloadRanking(type == null ? null : type.name(), Math.max(1, Math.min(limit, 20)))
                .stream()
                .map(asset -> versions.findLatestPublished(asset.getId())
                        .map(version -> PortalDto.AssetSummary.of(asset, version))
                        .orElse(null))
                .filter(summary -> summary != null)
                .toList();
    }

    @GetMapping("/assets/{id}")
    public PortalDto.AssetDetail detail(@PathVariable UUID id,
                                        @RequestParam(required = false) Integer versionNo) {
        Asset asset = assets.findById(id)
                .filter(found -> !found.isArchived())
                .orElseThrow(this::notFound);

        AssetVersion latest = versionNo == null
                ? versions.findLatestPublished(id).orElseThrow(this::notFound)
                : versions.findByAssetIdOrderByVersionNoDesc(id).stream()
                        .filter(version -> version.getVersionNo() == versionNo
                                && version.getStatus() == AssetVersion.Status.PUBLISHED)
                        .findFirst()
                        .orElseThrow(this::notFound);

        List<AssetVersion> publishedVersions = versions.findByAssetIdOrderByVersionNoDesc(id).stream()
                .filter(version -> version.getStatus() == AssetVersion.Status.PUBLISHED)
                .toList();

        return new PortalDto.AssetDetail(
                PortalDto.AssetSummary.of(asset, latest),
                latest.getBody(),
                latest.getVersionNo(),
                latest.getChangelog(),
                files.findByAssetVersionIdOrderByRelativePath(latest.getId()).stream()
                        .map(AssetDto::of)
                        .toList(),
                publishedVersions.stream().map(PortalDto.PublishedVersion::of).toList(),
                canEdit(asset));
    }

    @GetMapping("/statistics")
    public PortalDto.Statistics statistics() {
        List<Asset> publishedAssets = assets.findByArchivedFalse().stream()
                .filter(asset -> versions.findLatestPublished(asset.getId()).isPresent())
                .toList();

        long totalDownloads = publishedAssets.stream()
                .mapToLong(Asset::getDownloadCount)
                .sum();

        return new PortalDto.Statistics(
                publishedAssets.size(),
                totalDownloads,
                new PortalDto.TypeBreakdown(
                        countType(publishedAssets, Asset.Type.RULE),
                        countType(publishedAssets, Asset.Type.SKILL),
                        countType(publishedAssets, Asset.Type.DOCUMENT)),
                new PortalDto.ScopeBreakdown(
                        countScope(publishedAssets, Asset.Scope.ORGANIZATION),
                        countScope(publishedAssets, Asset.Scope.TECH_STACK),
                        countScope(publishedAssets, Asset.Scope.PROJECT)));
    }

    private boolean canEdit(Asset asset) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            return false;
        }
        return new CurrentUser(user).canAuthor()
                && (user.getRole() == User.Role.ADMIN || user.getId().equals(asset.getOwnerUserId()));
    }

    private long countType(List<Asset> assets, Asset.Type type) {
        return assets.stream().filter(asset -> asset.getType() == type).count();
    }

    private long countScope(List<Asset> assets, Asset.Scope scope) {
        return assets.stream().filter(asset -> asset.getScope() == scope).count();
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "资产不存在或未发布");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
