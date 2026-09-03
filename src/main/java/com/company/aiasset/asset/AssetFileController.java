package com.company.aiasset.asset;

import com.company.aiasset.security.CurrentUser;
import com.company.aiasset.storage.FileStorage;
import com.company.aiasset.storage.UploadPolicy;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 附件的上传、下载、删除。附件属于版本，因此只能操作开放版本——
 * 已发布版本的附件跟着正文一起冻结，否则"版本不可变"就名不副实。
 */
@RestController
@RequestMapping("/api/assets/{assetId}/files")
public class AssetFileController {

    private final AssetService service;
    private final AssetVersionRepository versions;
    private final AssetFileRepository files;
    private final FileStorage storage;
    private final UploadPolicy policy;

    public AssetFileController(AssetService service,
                              AssetVersionRepository versions,
                              AssetFileRepository files,
                              FileStorage storage,
                              UploadPolicy policy) {
        this.service = service;
        this.versions = versions;
        this.files = files;
        this.storage = storage;
        this.policy = policy;
    }

    /**
     * 上传附件。
     *
     * path 可选，用于保留目录结构：Skill 常常是 SKILL.md + scripts/check.py +
     * templates/report.md 这样一组文件，前端用 webkitRelativePath 或用户手填的
     * 相对路径传过来，这里原样保留（校验后）。不传则退回用文件名本身。
     */
    @PostMapping
    @Transactional
    public ResponseEntity<AssetDto.FileInfo> upload(@PathVariable UUID assetId,
                                                    @RequestPart("file") MultipartFile file,
                                                    @RequestParam(value = "path", required = false) String path,
                                                    CurrentUser current) throws IOException {
        Asset a = service.mustFind(assetId);
        service.requireOwnerOrAdmin(a, current);

        String name = policy.validate(file, path);
        AssetVersion v = openVersion(assetId);

        if (files.existsByAssetVersionIdAndRelativePath(v.getId(), name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "同名附件已存在：" + name);
        }

        FileStorage.Stored stored;
        try (InputStream in = file.getInputStream()) {
            stored = storage.store(in);
        }

        AssetFile f = new AssetFile();
        f.setAssetVersionId(v.getId());
        f.setRelativePath(name);
        f.setMimeType(file.getContentType());
        f.setSizeBytes(stored.sizeBytes());
        f.setContentHash(stored.sha256());
        f.setStorageKey(stored.storageKey());
        files.save(f);

        return ResponseEntity.status(201).body(AssetDto.of(f));
    }

    /**
     * 下载。一律用 Content-Disposition: attachment 并显式关掉嗅探——
     * 即使白名单挡过了类型，也不让浏览器在本站源下直接渲染上传内容。
     */
    @GetMapping("/{fileId}")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID assetId,
                                                        @PathVariable UUID fileId) throws IOException {
        service.mustFind(assetId);
        AssetFile f = files.findById(fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "附件不存在"));

        // 校验附件确实属于该资产，防止用别的资产 id 拼出可访问的链接。
        AssetVersion v = versions.findById(f.getAssetVersionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "附件不存在"));
        if (!v.getAssetId().equals(assetId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "附件不存在");
        }
        if (f.getStorageKey() == null || !storage.exists(f.getStorageKey())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "附件内容缺失");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, attachment(f.getRelativePath()));
        headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setContentLength(f.getSizeBytes());

        return new ResponseEntity<>(
                new InputStreamResource(storage.open(f.getStorageKey())), headers, HttpStatus.OK);
    }

    /**
     * 打包下载指定版本的所有文件为 ZIP。
     */
    @GetMapping("/download-all")
    public ResponseEntity<InputStreamResource> downloadAll(@PathVariable UUID assetId,
                                                           @RequestParam(required = false) UUID versionId) throws IOException {
        Asset asset = service.mustFind(assetId);

        // 如果没指定版本，使用最新已发布版本或草稿
        AssetVersion version;
        if (versionId != null) {
            version = versions.findById(versionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "版本不存在"));
            if (!version.getAssetId().equals(assetId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "版本不存在");
            }
        } else {
            List<AssetVersion> all = service.versionsOf(assetId);
            version = all.stream()
                    .filter(v -> v.getStatus() == AssetVersion.Status.PUBLISHED)
                    .findFirst()
                    .orElse(all.isEmpty() ? null : all.get(0));
            if (version == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "没有可下载的版本");
            }
        }

        List<AssetFile> fileList = files.findByAssetVersionIdOrderByRelativePath(version.getId());
        if (fileList.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "该版本没有附件");
        }

        // 创建 ZIP
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (AssetFile file : fileList) {
                if (file.getStorageKey() == null || !storage.exists(file.getStorageKey())) {
                    continue; // 跳过缺失文件
                }

                ZipEntry entry = new ZipEntry(file.getRelativePath());
                entry.setSize(file.getSizeBytes());
                zos.putNextEntry(entry);

                try (InputStream in = storage.open(file.getStorageKey())) {
                    in.transferTo(zos);
                }
                zos.closeEntry();
            }
        }

        byte[] zipBytes = baos.toByteArray();
        String zipFilename = sanitizeFilename(asset.getName()) + "-v" + version.getVersionNo() + ".zip";

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, attachment(zipFilename));
        headers.set(HttpHeaders.CONTENT_TYPE, "application/zip");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setContentLength(zipBytes.length);

        return new ResponseEntity<>(
                new InputStreamResource(new ByteArrayInputStream(zipBytes)), headers, HttpStatus.OK);
    }

    @DeleteMapping("/{fileId}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable UUID assetId,
                                       @PathVariable UUID fileId,
                                       CurrentUser current) {
        Asset a = service.mustFind(assetId);
        service.requireOwnerOrAdmin(a, current);

        AssetFile f = files.findById(fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "附件不存在"));
        AssetVersion open = openVersion(assetId);
        if (!f.getAssetVersionId().equals(open.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只能删除当前草稿的附件");
        }

        files.delete(f);
        // 物理文件留着：事务可能回滚，且同一哈希未来可能被复用。
        // 清理交给后续的孤儿文件巡检任务，v1 不做。
        return ResponseEntity.noContent().build();
    }

    private AssetVersion openVersion(UUID assetId) {
        AssetVersion v = versions.findOpenVersion(assetId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.CONFLICT,
                        "当前没有草稿版本，附件只能加在草稿上"));
        if (v.isFrozen()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已发布版本不能改附件");
        }
        return v;
    }

    /** RFC 5987：中文文件名需要 filename* 才能在各浏览器正确落地。 */
    static String attachment(String filename) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + filename.replaceAll("[\"\\\\]", "_")
                + "\"; filename*=UTF-8''" + encoded;
    }

    /** 清理文件名，移除不安全字符 */
    private static String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5_\\-]", "_");
    }
}
