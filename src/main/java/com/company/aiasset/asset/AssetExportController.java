package com.company.aiasset.asset;

import com.company.aiasset.storage.FileStorage;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.http.ResponseEntity;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 把一个版本打成 zip 下载：正文 + 附件。
 *
 * 入口文件名按资产类型走：SKILL 出 SKILL.md（带 frontmatter，附件与它同级，
 * 可直接解到 .claude/skills/ 下用）；RULE 与 DOCUMENT 出 README.md，附件在 files/。
 * 别的工具（Cursor 的 .mdc、Copilot 的 .instructions.md）frontmatter 语义不同，
 * 需要各自的导出配置，不能靠改文件名糊过去。
 *
 * 这是"资产能落到 IDE 里"的最后一环——方案文档 4.2 的取用路径。
 * 用 StreamingResponseBody 边打边传，不在内存里攒整个 zip：
 * 附件上限 10MB 但数量不限，攒内存在并发下会顶爆堆。
 */
@RestController
@RequestMapping("/api/assets/{assetId}")
public class AssetExportController {

    private final AssetService service;
    private final AssetVersionRepository versions;
    private final AssetFileRepository files;
    private final FileStorage storage;

    public AssetExportController(AssetService service,
                                AssetVersionRepository versions,
                                AssetFileRepository files,
                                FileStorage storage) {
        this.service = service;
        this.versions = versions;
        this.files = files;
        this.storage = storage;
    }

    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(@PathVariable UUID assetId,
                                                        @RequestParam(required = false) Integer versionNo) {
        Asset a = service.mustFind(assetId);
        List<AssetVersion> all = service.versionsOf(assetId);

        AssetVersion v = versionNo != null
                ? all.stream().filter(x -> x.getVersionNo() == versionNo).findFirst()
                      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "版本不存在"))
                : all.stream().filter(x -> x.getStatus() == AssetVersion.Status.PUBLISHED).findFirst()
                      .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                              "尚无已发布版本可导出"));

        List<AssetFile> attachments = files.findByAssetVersionIdOrderByRelativePath(v.getId());
        String zipName = a.getSlug() + "-v" + v.getVersionNo() + ".zip";

        boolean skill = a.getType() == Asset.Type.SKILL;
        // Claude Code 约定：入口必须叫 SKILL.md，且附件与它同级——
        // 正文里引用脚本写的是相对路径（scripts/check.py），加 files/ 前缀会让引用全部失效。
        String entryName = skill ? "SKILL.md" : "README.md";
        String filePrefix = skill ? "" : "files/";

        StreamingResponseBody body = out -> {
            try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
                zip.putNextEntry(new ZipEntry(entryName));
                if (skill) {
                    zip.write(frontmatter(a).getBytes(StandardCharsets.UTF_8));
                }
                zip.write(header(a, v).getBytes(StandardCharsets.UTF_8));
                if (v.getBody() != null) {
                    zip.write(v.getBody().getBytes(StandardCharsets.UTF_8));
                }
                zip.closeEntry();

                for (AssetFile f : attachments) {
                    if (f.getStorageKey() == null || !storage.exists(f.getStorageKey())) {
                        continue;
                    }
                    zip.putNextEntry(new ZipEntry(filePrefix + f.getRelativePath()));
                    try (InputStream in = storage.open(f.getStorageKey())) {
                        in.transferTo(zip);
                    }
                    zip.closeEntry();
                }
            }
        };

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, AssetFileController.attachment(zipName));
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set("X-Content-Type-Options", "nosniff");
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    /**
     * 导出物带出处信息：否则文件散落到各人机器上之后，
     * 没人知道它来自哪个资产的哪一版，也就无法回溯更新。
     */
    private String header(Asset a, AssetVersion v) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!-- 来自团队 AI 资产库 -->\n")
          .append("<!-- 资产：").append(a.getName()).append("（").append(a.getSlug()).append("） -->\n")
          .append("<!-- 版本：v").append(v.getVersionNo())
          .append("　状态：").append(v.getStatus()).append(" -->\n");
        if (v.getContentHash() != null) {
            sb.append("<!-- 内容哈希：").append(v.getContentHash(), 0, 16).append(" -->\n");
        }
        sb.append("<!-- 修改请回到平台发新版本，本地改动不会同步 -->\n\n");
        return sb.toString();
    }

    /**
     * Claude Code Skill 的 YAML frontmatter，必须位于文件最开头。
     *
     * name 只允许小写字母、数字与连字符；slug 已是 URL 友好的，
     * 但仍逐字符过一遍，避免历史数据里的意外字符让 Skill 整体加载失败。
     *
     * description 决定这个 Skill 会不会被触发：Claude 平时只把 description
     * 读进上下文，命中了才加载正文。summary 为空时退回资产名，
     * 总比没有 description 导致永远不被选中要好。
     */
    private String frontmatter(Asset a) {
        StringBuilder name = new StringBuilder();
        for (char c : a.getSlug().toLowerCase().toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-') {
                name.append(c);
            } else if (name.length() > 0 && name.charAt(name.length() - 1) != '-') {
                name.append('-');
            }
        }
        String safeName = name.length() > 0 ? name.toString() : "skill";

        String desc = a.getSummary() != null && !a.getSummary().isBlank()
                ? a.getSummary() : a.getName();

        return "---\nname: " + safeName + "\ndescription: " + yaml(desc) + "\n---\n\n";
    }

    /**
     * 折成 YAML 双引号标量。中文摘要里常见的冒号、井号、引号都会破坏裸标量，
     * 换行更会直接截断 frontmatter，因此统一压成一行再转义。
     */
    private String yaml(String s) {
        String flat = s.replace("\r", " ").replace("\n", " ").trim();
        StringBuilder sb = new StringBuilder("\"");
        for (char c : flat.toCharArray()) {
            if (c == '"' || c == '\\') {
                sb.append('\\').append(c);
            } else if (c == '\t') {
                sb.append("\\t");
            } else {
                sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
