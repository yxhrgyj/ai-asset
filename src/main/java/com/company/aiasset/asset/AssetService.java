package com.company.aiasset.asset;

import com.company.aiasset.security.CurrentUser;
import com.company.aiasset.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 资产的写操作。读操作简单，直接在控制器里用仓库。
 *
 * 权限模型（v1 从简）：作者及以上可建资产；改与发布限本人或管理员。
 * 第 3 步接入审批流后，publish 会改为提交审批而非直接发布。
 */
@Service
public class AssetService {

    private final AssetRepository assets;
    private final AssetVersionRepository versions;
    private final AssetDownloadRepository downloads;

    public AssetService(AssetRepository assets, AssetVersionRepository versions, AssetDownloadRepository downloads) {
        this.assets = assets;
        this.versions = versions;
        this.downloads = downloads;
    }

    public record CreateRequest(Asset.Type type,
                                String name,
                                String slug,
                                String summary,
                                String category,
                                String[] tags,
                                Asset.Scope scope,
                                String techStack) {}

    @Transactional
    public Asset create(CreateRequest req, CurrentUser current) {
        requireAuthor(current);

        Asset a = new Asset();
        a.setType(require(req.type(), "type"));
        a.setName(requireText(req.name(), "name"));
        a.setSlug(normalizeSlug(req.slug(), req.name()));
        a.setSummary(req.summary());
        a.setCategory(req.category());
        a.setTags(req.tags());
        a.setScope(req.scope() == null ? Asset.Scope.ORGANIZATION : req.scope());
        a.setTechStack(req.techStack());
        a.setOwnerUserId(current.user().getId());

        // assets_tech_stack_pairing 在数据库层也会拦，但报错信息不友好。
        if ((a.getScope() == Asset.Scope.TECH_STACK) != (a.getTechStack() != null && !a.getTechStack().isBlank())) {
            throw bad("scope=TECH_STACK 必须填 techStack，其他范围不能填");
        }
        if (assets.existsBySlug(a.getSlug())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "标识已被占用：" + a.getSlug());
        }

        assets.save(a);

        // 建资产即建第 1 版草稿，避免出现没有任何版本的空资产。
        AssetVersion v = new AssetVersion();
        v.setAssetId(a.getId());
        v.setVersionNo(1);
        v.setStatus(AssetVersion.Status.DRAFT);
        v.setCreatedBy(current.user().getId());
        versions.save(v);

        return a;
    }

    @Transactional
    public Asset updateMeta(UUID assetId, CreateRequest req, CurrentUser current) {
        Asset a = mustFind(assetId);
        requireOwnerOrAdmin(a, current);

        if (req.name() != null)     a.setName(requireText(req.name(), "name"));
        if (req.summary() != null)  a.setSummary(req.summary());
        if (req.category() != null) a.setCategory(req.category());
        if (req.tags() != null)     a.setTags(req.tags());
        if (req.type() != null)     a.setType(req.type());
        if (req.scope() != null) {
            a.setScope(req.scope());
            a.setTechStack(req.scope() == Asset.Scope.TECH_STACK ? req.techStack() : null);
        }
        // slug 不允许改：已经发出去的链接会失效。要换标识就新建资产。
        return a;
    }

    /** 编辑草稿正文。已发布版本改不动（数据库触发器会拒绝），所以先查状态给出可读的错误。 */
    @Transactional
    public AssetVersion saveDraft(UUID assetId, String body, String changelog, CurrentUser current) {
        Asset a = mustFind(assetId);
        requireOwnerOrAdmin(a, current);

        AssetVersion v = versions.findOpenVersion(assetId)
                .orElseThrow(() -> bad("当前没有可编辑的草稿，请先基于已发布版本创建新版本"));
        if (v.getStatus() != AssetVersion.Status.DRAFT) {
            throw bad("版本处于 " + v.getStatus() + " 状态，不能直接编辑");
        }
        v.setBody(body);
        if (changelog != null) {
            v.setChangelog(changelog);
        }
        return v;
    }

    /**
     * 发布当前草稿。content_hash 由发布时刻的正文算出，
     * asset_versions_published_needs_hash 要求它非空。
     */
    @Transactional
    public AssetVersion publish(UUID assetId, CurrentUser current) {
        Asset a = mustFind(assetId);
        requireOwnerOrAdmin(a, current);

        AssetVersion v = versions.findOpenVersion(assetId)
                .orElseThrow(() -> bad("没有待发布的草稿"));
        if (v.getBody() == null || v.getBody().isBlank()) {
            throw bad("正文为空，不能发布");
        }
        v.setStatus(AssetVersion.Status.PUBLISHED);
        v.setContentHash(sha256(v.getBody()));
        v.setPublishedAt(Instant.now());
        return v;
    }

    /**
     * 基于最新已发布版本开一个新草稿。
     * asset_versions_single_open_uk 保证同时只能有一个开放版本，先查再插避免撞唯一索引。
     */
    @Transactional
    public AssetVersion newDraft(UUID assetId, CurrentUser current) {
        Asset a = mustFind(assetId);
        requireOwnerOrAdmin(a, current);

        if (versions.findOpenVersion(assetId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已有一个未发布的草稿");
        }
        AssetVersion latest = versions.findLatestPublished(assetId)
                .orElseThrow(() -> bad("尚无已发布版本"));

        AssetVersion v = new AssetVersion();
        v.setAssetId(assetId);
        v.setVersionNo(versions.maxVersionNo(assetId) + 1);
        v.setStatus(AssetVersion.Status.DRAFT);
        v.setBody(latest.getBody());
        v.setCreatedBy(current.user().getId());
        return versions.save(v);
    }

    /** 归档而非删除：已被下载引用的资产物理删除会让追溯断链。 */
    @Transactional
    public void archive(UUID assetId, boolean archived, CurrentUser current) {
        Asset a = mustFind(assetId);
        requireOwnerOrAdmin(a, current);
        a.setArchived(archived);
    }

    /**
     * 记录资产下载。下载次数 +1，并记录审计日志。
     */
    @Transactional
    public void recordDownload(UUID assetId, UUID versionId, CurrentUser current) {
        Asset a = mustFind(assetId);
        a.setDownloadCount(a.getDownloadCount() + 1);

        AssetDownload record = new AssetDownload();
        record.setAssetId(assetId);
        record.setVersionId(versionId);
        record.setUserId(current.user().getId());
        downloads.save(record);
    }

    public Asset mustFind(UUID assetId) {
        return assets.findById(assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "资产不存在"));
    }

    public List<AssetVersion> versionsOf(UUID assetId) {
        return versions.findByAssetIdOrderByVersionNoDesc(assetId);
    }

    void requireOwnerOrAdmin(Asset a, CurrentUser current) {
        requireAuthor(current);
        boolean owner = a.getOwnerUserId().equals(current.user().getId());
        if (!owner && current.user().getRole() != User.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有资产负责人或管理员可以修改");
        }
    }

    private void requireAuthor(CurrentUser current) {
        if (!current.canAuthor()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色没有编辑权限");
        }
    }

    /**
     * slug 只保留小写字母数字与连字符。中文名转不出有意义的 slug，
     * 这种情况下要求显式提供；自动生成会得到一串空值。
     */
    private String normalizeSlug(String slug, String name) {
        String source = (slug == null || slug.isBlank()) ? name : slug;
        String s = source == null ? "" : source.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        if (s.isEmpty()) {
            throw bad("请提供 slug：名称不含可用于生成标识的字母数字");
        }
        return s.length() > 80 ? s.substring(0, 80) : s;
    }

    static String sha256(String text) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static <T> T require(T value, String field) {
        if (value == null) {
            throw bad("缺少必填字段：" + field);
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw bad("缺少必填字段：" + field);
        }
        return value.trim();
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
