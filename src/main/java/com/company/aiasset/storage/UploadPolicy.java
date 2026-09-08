package com.company.aiasset.storage;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/** Validate actual content before storage. Attachments are always downloaded, never rendered inline. */
@Component
public class UploadPolicy {
    public static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 20_000_000;
    private static final Set<String> TEXTUAL = Set.of(
            "md", "txt", "csv", "json", "yaml", "yml", "toml", "ini", "conf", "env.example",
            "xml", "properties", "editorconfig", "sh", "bash", "zsh", "ps1", "bat", "cmd", "py", "rb", "pl", "lua",
            "js", "mjs", "cjs", "ts", "tsx", "jsx", "vue", "java", "kt", "go", "rs", "c", "h", "cpp", "hpp",
            "cs", "php", "scala", "swift", "sql", "r", "tpl", "tmpl", "j2", "mustache", "hbs", "patch", "diff");
    private static final Set<String> IMAGES = Set.of("png", "jpg", "jpeg", "gif", "webp");
    private static final Set<String> BROWSER_MIME_ALIASES = Set.of("", "application/octet-stream", "application/x-zip-compressed");

    public String validate(MultipartFile file, String declaredPath) {
        if (file.isEmpty()) throw bad("文件为空");
        if (file.getSize() > MAX_BYTES) throw tooLarge("单个文件不能超过 10 MB");
        String path = safePath(declaredPath == null || declaredPath.isBlank() ? file.getOriginalFilename() : declaredPath, 4);
        String extension = extensionOf(path);
        String declared = file.getContentType() == null ? "" : file.getContentType().split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!TEXTUAL.contains(extension) && !BROWSER_MIME_ALIASES.contains(declared) && !declared.equals(mediaType(path))) {
            throw bad("文件类型声明与扩展名不一致：" + path);
        }
        try (InputStream input = file.getInputStream()) {
            byte[] bytes = input.readNBytes((int) MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw tooLarge("单个文件不能超过 10 MB");
            if (bytes.length == 0) throw bad("文件为空");
            validateContent(path, bytes, false);
        } catch (IOException e) {
            throw bad("无法读取上传文件，请重新选择文件");
        }
        return path;
    }

    /** Use a server-selected MIME in metadata, even when the browser sends a generic type. */
    public String mediaType(String path) {
        return switch (extensionOf(path)) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "pdf" -> "application/pdf";
            case "zip" -> "application/zip";
            default -> "text/plain";
        };
    }

    static void validateContent(String path, byte[] bytes, boolean insideZip) {
        String extension = extensionOf(path);
        if (extension.equals("zip")) {
            if (insideZip) throw bad("ZIP 内不支持嵌套压缩包：" + path);
            ZipUploadValidator.validate(bytes);
        } else if (IMAGES.contains(extension)) {
            validateImage(bytes, extension, path);
        } else if (extension.equals("pdf")) {
            String end = new String(bytes, Math.max(0, bytes.length - 1024), Math.min(bytes.length, 1024), StandardCharsets.ISO_8859_1);
            if (!starts(bytes, 0x25, 0x50, 0x44, 0x46, 0x2d) || !end.contains("%%EOF")) throw bad("PDF 内容无效或与扩展名不符：" + path);
        } else if (TEXTUAL.contains(extension)) {
            validateText(bytes, path);
        } else {
            throw bad("不支持的文件类型：" + path + "。支持文本、源码、配置、PNG/JPG/GIF/WebP、PDF 和 ZIP；不接受安装包、编译产物、HTML 或 SVG");
        }
    }

    private static void validateImage(byte[] bytes, String extension, String path) {
        boolean signature = switch (extension) {
            case "png" -> starts(bytes, 137, 80, 78, 71, 13, 10, 26, 10);
            case "jpg", "jpeg" -> starts(bytes, 255, 216, 255);
            case "gif" -> starts(bytes, 71, 73, 70, 56, 55, 97) || starts(bytes, 71, 73, 70, 56, 57, 97);
            case "webp" -> starts(bytes, 82, 73, 70, 70) && bytes.length >= 12 && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
            default -> false;
        };
        if (!signature) throw bad("图片实际内容与扩展名不符：" + path);
        try (MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw bad("无法识别图片内容：" + path);
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String expected = extension.equals("jpg") ? "jpeg" : extension;
                if (!reader.getFormatName().equalsIgnoreCase(expected)) throw bad("图片格式与扩展名不符：" + path);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > 12_000 || height > 12_000 || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw bad("图片尺寸过大，最多 2000 万像素且单边不超过 12000 像素：" + path);
                }
                // Decode a bounded sample without allocating a full-size raster.
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(Math.max(1, (width + 511) / 512), Math.max(1, (height + 511) / 512), 0, 0);
                if (reader.read(0, param) == null) throw bad("图片内容不完整：" + path);
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException e) {
            throw bad("图片损坏或内容不完整：" + path);
        }
    }

    private static void validateText(byte[] bytes, String path) {
        if (starts(bytes, 77, 90) || starts(bytes, 127, 69, 76, 70) || starts(bytes, 80, 75, 3, 4)
                || starts(bytes, 202, 254, 186, 190) || starts(bytes, 37, 80, 68, 70) || starts(bytes, 137, 80, 78, 71)) {
            throw bad("二进制文件不能伪装成文本或脚本：" + path);
        }
        Charset charset = starts(bytes, 255, 254) ? StandardCharsets.UTF_16LE
                : starts(bytes, 254, 255) ? StandardCharsets.UTF_16BE : StandardCharsets.UTF_8;
        try {
            String decoded = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            if (decoded.indexOf('\0') >= 0) throw bad("文本文件包含二进制内容：" + path);
        } catch (CharacterCodingException e) {
            throw bad("文本文件须使用 UTF-8 或带 BOM 的 UTF-16 编码：" + path);
        }
    }

    static String safePath(String original, int maxDepth) {
        if (original == null || original.isBlank()) throw bad("文件名缺失");
        String path = original.replace('\\', '/');
        if (path.startsWith("/") || path.matches("^[A-Za-z]:.*")) throw bad("路径必须是相对路径：" + original);
        if (path.length() > 400) throw bad("路径过长");
        String[] parts = path.split("/", -1);
        if (parts.length > maxDepth) throw bad("目录层级不能超过 " + maxDepth + " 层：" + original);
        for (String part : parts) {
            if (part.isBlank() || part.startsWith(".") || part.endsWith(".") || part.endsWith(" ")) throw bad("路径不能包含空片段、点目录、隐藏文件或结尾空格：" + original);
            if (part.length() > 200 || part.matches("(?s).*[\\x00-\\x1f\\x7f<>:\"|?*\\u202a-\\u202e\\u2066-\\u2069].*")) throw bad("路径含非法字符或片段过长：" + original);
            if (part.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?")) throw bad("路径不能使用系统保留名称：" + original);
        }
        return String.join("/", parts);
    }

    static String extensionOf(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        if (name.equals("env.example")) return name;
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) throw bad("文件名缺少扩展名：" + name);
        return name.substring(dot + 1);
    }

    static boolean starts(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if ((bytes[i] & 255) != prefix[i]) return false;
        return true;
    }
    static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    static ResponseStatusException tooLarge(String message) { return new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, message); }
}
