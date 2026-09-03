package com.company.aiasset.asset;

import com.company.aiasset.security.CurrentUser;
import com.company.aiasset.user.User;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final AssetService service;
    private final AssetRepository assets;
    private final AssetVersionRepository versions;
    private final AssetFileRepository files;

    public AssetController(AssetService service,
                          AssetRepository assets,
                          AssetVersionRepository versions,
                          AssetFileRepository files) {
        this.service = service;
        this.assets = assets;
        this.versions = versions;
        this.files = files;
    }

    /**
     * 列表与搜索同一端点：前端只有一个列表页，带不带 q 都走这里。
     * 空字符串按"不搜索"处理，否则 trigram 相似度对空串无意义。
     */
    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) @Size(max = 100) String q,
                                    @RequestParam(required = false) Asset.Type type,
                                    @RequestParam(required = false) Asset.Scope scope,
                                    @RequestParam(required = false) String tag,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        Page<Asset> found = assets.search(
                blankToNull(q),
                type == null ? null : type.name(),
                scope == null ? null : scope.name(),
                blankToNull(tag),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));

        return Map.of(
                "items", found.getContent().stream().map(AssetDto::of).toList(),
                "total", found.getTotalElements(),
                "page", found.getNumber(),
                "size", found.getSize());
    }

    /**
     * 详情。默认展示最新已发布版本；没有已发布版本时（新建后还没发过）
     * 退回展示草稿，否则作者看不到自己刚写的东西。
     */
    @GetMapping("/{id}")
    public AssetDto.Detail detail(@PathVariable UUID id,
                                  @RequestParam(required = false) Integer versionNo,
                                  CurrentUser current) {
        Asset a = service.mustFind(id);
        List<AssetVersion> all = service.versionsOf(id);

        AssetVersion shown = versionNo != null
                ? all.stream().filter(v -> v.getVersionNo() == versionNo).findFirst()
                      .orElseThrow(() -> new IllegalArgumentException("版本不存在：" + versionNo))
                : all.stream().filter(v -> v.getStatus() == AssetVersion.Status.PUBLISHED).findFirst()
                      .orElse(all.isEmpty() ? null : all.get(0));

        boolean canEdit = current.canAuthor()
                && (a.getOwnerUserId().equals(current.user().getId())
                    || current.user().getRole() == User.Role.ADMIN);

        return new AssetDto.Detail(
                AssetDto.of(a),
                shown == null ? null : AssetDto.of(shown),
                shown == null ? null : shown.getBody(),
                all.stream().map(AssetDto::of).toList(),
                shown == null ? List.of()
                        : files.findByAssetVersionIdOrderByRelativePath(shown.getId())
                               .stream().map(AssetDto::of).toList(),
                canEdit);
    }

    @PostMapping
    public ResponseEntity<AssetDto.Summary> create(@RequestBody AssetService.CreateRequest req,
                                                   CurrentUser current) {
        Asset a = service.create(req, current);
        return ResponseEntity.status(201).body(AssetDto.of(a));
    }

    @PatchMapping("/{id}")
    public AssetDto.Summary updateMeta(@PathVariable UUID id,
                                       @RequestBody AssetService.CreateRequest req,
                                       CurrentUser current) {
        return AssetDto.of(service.updateMeta(id, req, current));
    }

    public record DraftRequest(String body, String changelog) {}

    @PutMapping("/{id}/draft")
    public AssetDto.VersionInfo saveDraft(@PathVariable UUID id,
                                          @RequestBody DraftRequest req,
                                          CurrentUser current) {
        return AssetDto.of(service.saveDraft(id, req.body(), req.changelog(), current));
    }

    @PostMapping("/{id}/versions")
    public ResponseEntity<AssetDto.VersionInfo> newDraft(@PathVariable UUID id, CurrentUser current) {
        return ResponseEntity.status(201).body(AssetDto.of(service.newDraft(id, current)));
    }

    @PostMapping("/{id}/publish")
    public AssetDto.VersionInfo publish(@PathVariable UUID id, CurrentUser current) {
        return AssetDto.of(service.publish(id, current));
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<Void> archive(@PathVariable UUID id,
                                        @RequestParam(defaultValue = "true") boolean archived,
                                        CurrentUser current) {
        service.archive(id, archived, current);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/download")
    public ResponseEntity<Void> recordDownload(@PathVariable UUID id,
                                               @RequestParam(required = false) UUID versionId,
                                               CurrentUser current) {
        service.recordDownload(id, versionId, current);
        return ResponseEntity.noContent().build();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
