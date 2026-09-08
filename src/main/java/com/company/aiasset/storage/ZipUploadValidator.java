package com.company.aiasset.storage;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;

/** Inspect ZIPs in memory; never extract user paths onto the server filesystem. */
final class ZipUploadValidator {
    static final int MAX_ENTRIES = 200;
    static final long MAX_EXPANDED_BYTES = 50L * 1024 * 1024;
    private ZipUploadValidator() {}

    static void validate(byte[] bytes) {
        if (!UploadPolicy.starts(bytes, 80, 75, 3, 4)) throw UploadPolicy.bad("ZIP 文件为空、损坏或实际内容与扩展名不符");
        try (ZipFile zip = ZipFile.builder().setSeekableByteChannel(new SeekableInMemoryByteChannel(bytes)).get()) {
            List<ZipArchiveEntry> entries = new ArrayList<>();
            Set<String> paths = new HashSet<>(), filePaths = new HashSet<>();
            long declaredTotal = 0;
            var enumeration = zip.getEntriesInPhysicalOrder();
            while (enumeration.hasMoreElements()) {
                ZipArchiveEntry entry = enumeration.nextElement();
                if (entries.size() >= MAX_ENTRIES) throw UploadPolicy.bad("ZIP 内最多允许 200 个文件或目录");
                if (!zip.canReadEntryData(entry) || entry.getGeneralPurposeBit().usesEncryption()) throw UploadPolicy.bad("不支持加密或特殊压缩方式的 ZIP");
                if (entry.getMethod() != ZipEntry.STORED && entry.getMethod() != ZipEntry.DEFLATED) throw UploadPolicy.bad("ZIP 仅支持常规的存储或 Deflate 压缩方式");
                int fileType = entry.getUnixMode() & 0170000;
                if (entry.isUnixSymlink() || (fileType != 0 && fileType != 0100000 && fileType != 0040000)) throw UploadPolicy.bad("ZIP 内不能包含符号链接或特殊文件");
                String name = entry.getName();
                String path = UploadPolicy.safePath(entry.isDirectory() ? name.substring(0, name.length() - 1) : name, 8);
                String canonical = path.toLowerCase(Locale.ROOT);
                if (!paths.add(canonical)) throw UploadPolicy.bad("ZIP 内存在重复路径：" + path);
                if (!entry.isDirectory()) filePaths.add(canonical);
                long size = entry.getSize();
                if (size < 0 || entry.getCompressedSize() < 0 || size > UploadPolicy.MAX_BYTES) throw UploadPolicy.tooLarge("ZIP 内单个文件不能超过 10 MB");
                declaredTotal += size;
                if (declaredTotal > MAX_EXPANDED_BYTES) throw UploadPolicy.tooLarge("ZIP 解压后的总大小不能超过 50 MB");
                if (size > 1024 * 1024 && size > Math.max(1, entry.getCompressedSize()) * 100L) throw UploadPolicy.bad("ZIP 压缩比过高，请拆分文件或降低压缩率");
                entries.add(entry);
            }
            if (filePaths.isEmpty()) throw UploadPolicy.bad("ZIP 内没有可上传的文件");
            for (String path : paths) {
                for (int slash = path.indexOf('/'); slash >= 0; slash = path.indexOf('/', slash + 1)) {
                    if (filePaths.contains(path.substring(0, slash))) throw UploadPolicy.bad("ZIP 内文件与目录路径冲突：" + path);
                }
            }

            // Validate local names as well as the central directory to cover different extractors.
            try (ZipArchiveInputStream input = new ZipArchiveInputStream(new ByteArrayInputStream(bytes), "UTF-8", true)) {
                long expanded = 0;
                int index = 0;
                ZipArchiveEntry local;
                byte[] buffer = new byte[8192];
                while ((local = input.getNextEntry()) != null) {
                    if (index >= entries.size()) throw UploadPolicy.bad("ZIP 目录与实际文件不一致");
                    ZipArchiveEntry expected = entries.get(index++);
                    if (!local.getName().equals(expected.getName()) || local.getMethod() != expected.getMethod()
                            || local.getGeneralPurposeBit().usesEncryption() || !input.canReadEntryData(local)) throw UploadPolicy.bad("ZIP 文件头与目录不一致");
                    ByteArrayOutputStream content = new ByteArrayOutputStream();
                    CRC32 crc = new CRC32();
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        expanded += count;
                        if (content.size() + (long) count > UploadPolicy.MAX_BYTES || expanded > MAX_EXPANDED_BYTES) throw UploadPolicy.tooLarge("ZIP 实际解压大小超出安全限制");
                        if (expanded > 1024 * 1024 && expanded > Math.max(1, bytes.length) * 100L) throw UploadPolicy.bad("ZIP 实际压缩比过高");
                        content.write(buffer, 0, count);
                        crc.update(buffer, 0, count);
                    }
                    if (content.size() != expected.getSize() || crc.getValue() != expected.getCrc()) throw UploadPolicy.bad("ZIP 文件大小或校验值不正确");
                    if (expected.isDirectory()) {
                        if (content.size() != 0) throw UploadPolicy.bad("ZIP 目录不能携带文件内容");
                    } else {
                        UploadPolicy.validateContent(expected.getName(), content.toByteArray(), true);
                    }
                }
                if (index != entries.size()) throw UploadPolicy.bad("ZIP 文件不完整");
            }
        } catch (IOException | IllegalArgumentException e) {
            throw UploadPolicy.bad("ZIP 文件损坏或包含不支持的压缩内容");
        }
    }
}
