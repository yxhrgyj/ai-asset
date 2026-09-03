package com.company.aiasset.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 本地文件系统存储。
 *
 * 存放键由服务端生成（uuid 两级分桶），不含任何用户输入的路径成分——
 * 用户提供的文件名只作为展示值存在 asset_files.relative_path，永不参与路径拼接。
 * 这样即使前端传来 `../../etc/passwd` 也影响不到落盘位置。
 *
 * 之所以抽成一个类而不是散在控制器里：方案文档 5.3 提到后续可能换对象存储，
 * 届时只需另写一个实现。v1 不引入接口，等真有第二种实现时再抽。
 */
@Component
public class FileStorage {

    private final Path root;

    public FileStorage(@Value("${app.storage.root}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    public record Stored(String storageKey, long sizeBytes, String sha256) {}

    /**
     * 落盘并同时算出 sha256，避免为了取哈希把文件读两遍。
     * 先写临时文件再原子移动，防止中途失败留下半个文件被当成完整附件。
     */
    public Stored store(InputStream in) throws IOException {
        String key = newKey();
        Path target = resolve(key);
        Files.createDirectories(target.getParent());

        Path tmp = Files.createTempFile(target.getParent(), "upload-", ".part");
        long size;
        String hash;
        try {
            MessageDigest digest = sha256();
            try (OutputStream fileOut = Files.newOutputStream(tmp);
                 DigestOutputStream out = new DigestOutputStream(fileOut, digest)) {
                size = in.transferTo(out);
            }
            hash = HexFormat.of().formatHex(digest.digest());
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
        return new Stored(key, size, hash);
    }

    public InputStream open(String storageKey) throws IOException {
        return Files.newInputStream(resolve(storageKey));
    }

    public boolean exists(String storageKey) {
        return Files.isRegularFile(resolve(storageKey));
    }

    public void delete(String storageKey) throws IOException {
        Files.deleteIfExists(resolve(storageKey));
    }

    private String newKey() {
        String id = UUID.randomUUID().toString();
        // 两级分桶：单目录文件数过万后某些文件系统的目录遍历会变慢。
        return id.substring(0, 2) + "/" + id.substring(2, 4) + "/" + id;
    }

    /**
     * 即便 key 由服务端生成，仍然校验结果落在 root 之内——
     * 万一将来 key 的来源变了，这里能挡住越界读写。
     */
    private Path resolve(String storageKey) {
        Path p = root.resolve(storageKey).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("非法存储键：" + storageKey);
        }
        return p;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
