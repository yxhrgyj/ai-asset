package com.company.aiasset.storage;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;

/**
 * 上传白名单、路径校验与大小上限。
 *
 * 为什么允许脚本：Skill 在实际用法里是一个目录——SKILL.md 加上脚本、模板、
 * 参考文件。只放 md 和图片等于把 Skill 砍掉一半，取用方拿到的东西跑不起来。
 *
 * 允许脚本不等于放开任意文件。这里的判断依据是：附件永远以
 * Content-Disposition: attachment + X-Content-Type-Options: nosniff +
 * application/octet-stream 下发（见 AssetFileController.download），
 * 浏览器不会在本站源下渲染或执行它，所以「上传即存储型 XSS」的风险已经
 * 在下载环节堵住了，白名单不必再承担这一层。
 *
 * 仍然拒绝两类：
 * 1) 编译产物与安装包（exe/dll/msi/…）——资产库存的是可读的源文本，
 *    二进制既没法审阅也没法 diff，进来了只会变成没人敢删的黑盒。
 * 2) html/htm/svg/xhtml——万一将来有人给附件加了内联预览，这三类会立刻
 *    变成 XSS。留着它们的收益远小于这个隐患。
 */
@Component
public class UploadPolicy {

    /** 单文件 10MB。整体请求大小另由 spring.servlet.multipart 限制。 */
    public static final long MAX_BYTES = 10L * 1024 * 1024;

    /** 目录层级上限。Skill 目录不该深过这个数，深了通常是误传了整个仓库。 */
    private static final int MAX_DEPTH = 4;

    /**
     * 文本与源码类。这类文件的 Content-Type 各浏览器给得五花八门
     * （.py 可能是 text/x-python、application/octet-stream 或空），
     * 拿 MIME 严格配对只会误伤正常上传，所以只认扩展名。
     */
    private static final Set<String> TEXTUAL = Set.of(
            // 文档与配置
            "md", "txt", "csv", "json", "yaml", "yml", "toml", "ini", "conf", "env.example",
            "xml", "properties", "editorconfig",
            // 脚本
            "sh", "bash", "zsh", "ps1", "bat", "cmd", "py", "rb", "pl", "lua",
            // 源码
            "js", "mjs", "cjs", "ts", "tsx", "jsx", "vue", "java", "kt", "go", "rs",
            "c", "h", "cpp", "hpp", "cs", "php", "scala", "swift", "sql", "r",
            // 模板
            "tpl", "tmpl", "j2", "mustache", "hbs", "patch", "diff"
    );

    /** 二进制但可信的展示类。这几类要严格配对，因为浏览器对它们有嗅探行为。 */
    private static final Set<String> IMAGE_PDF = Set.of("png", "jpg", "jpeg", "gif", "webp", "pdf");

    private static final Set<String> DENIED = Set.of(
            "exe", "dll", "so", "dylib", "msi", "app", "deb", "rpm", "dmg", "pkg",
            "bin", "o", "obj", "class", "jar", "war", "pyc", "scr", "com", "cpl",
            "html", "htm", "xhtml", "svg", "swf"
    );

    /**
     * 校验并返回规范化后的相对路径，可直接作为 relative_path 入库。
     *
     * @param declaredPath 前端传来的相对路径（如 scripts/check.py），为空则退回用文件名
     */
    public String validate(MultipartFile file, String declaredPath) {
        if (file.isEmpty()) {
            throw bad("文件为空");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "单个文件不能超过 " + (MAX_BYTES / 1024 / 1024) + "MB");
        }

        String raw = (declaredPath == null || declaredPath.isBlank())
                ? file.getOriginalFilename()
                : declaredPath;
        String path = safePath(raw);
        String ext = extensionOf(path);

        if (DENIED.contains(ext)) {
            throw bad("不允许上传 ." + ext + " 文件：资产库只收可读的源文本，"
                    + "编译产物与可被浏览器渲染的 html/svg 除外");
        }

        if (IMAGE_PDF.contains(ext)) {
            requireMimeMatch(file, ext);
            return path;
        }
        if (TEXTUAL.contains(ext)) {
            return path;
        }
        throw bad("不支持的文件类型：." + ext
                + "。支持 Markdown、脚本（py/sh/ps1/js/…）、源码、配置与图片/PDF");
    }

    /** 只有图片和 PDF 需要扩展名与声明的 MIME 对得上。 */
    private void requireMimeMatch(MultipartFile file, String ext) {
        String declared = file.getContentType() == null
                ? "" : file.getContentType().toLowerCase(Locale.ROOT).split(";")[0].trim();
        String expected = switch (ext) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "application/pdf";
        };
        if (!declared.equals(expected)) {
            throw bad("文件类型与内容声明不一致：." + ext + " 对应 " + declared);
        }
    }

    /**
     * 规范化相对路径，保留目录结构。
     *
     * 与 asset_files_path_safe 的约束方向一致，但在这里挡掉能给出更友好的报错。
     * 保留目录是这次放宽的重点：以前把路径成分全剥掉，scripts/check.py 会变成
     * check.py，Skill 的目录结构在取用端就没了。
     */
    private String safePath(String original) {
        if (original == null || original.isBlank()) {
            throw bad("文件名缺失");
        }
        String path = original.replace('\\', '/').trim();
        if (path.startsWith("/") || path.matches("^[A-Za-z]:.*")) {
            throw bad("路径必须是相对路径：" + original);
        }
        if (path.length() > 400) {
            throw bad("路径过长");
        }

        String[] parts = path.split("/");
        StringBuilder out = new StringBuilder();
        int depth = 0;

        for (String part : parts) {
            String seg = part.trim();
            if (seg.isEmpty() || seg.equals(".")) {
                continue;   // 折叠 a//b 与 ./a
            }
            if (seg.equals("..")) {
                throw bad("路径不能包含 ..：" + original);
            }
            if (seg.startsWith(".")) {
                throw bad("不接受以点开头的文件或目录：" + seg);
            }
            if (seg.length() > 200) {
                throw bad("路径片段过长：" + seg);
            }
            // 控制字符与 Windows 保留字符。zip 解到 Windows 上会失败或被改名。
            if (seg.matches(".*[\\x00-\\x1f<>:\"|?*].*")) {
                throw bad("路径含非法字符：" + seg);
            }
            if (depth++ > 0) {
                out.append('/');
            }
            out.append(seg);
        }

        if (out.length() == 0) {
            throw bad("路径不合法：" + original);
        }
        if (depth > MAX_DEPTH) {
            throw bad("目录层级不能超过 " + MAX_DEPTH + " 层：" + original);
        }
        return out.toString();
    }

    private String extensionOf(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            throw bad("文件名缺少扩展名：" + name);
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
