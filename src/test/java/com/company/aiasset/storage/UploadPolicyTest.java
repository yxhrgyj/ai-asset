package com.company.aiasset.storage;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class UploadPolicyTest {
    private final UploadPolicy policy = new UploadPolicy();
    private static final byte[] TEXT = "# Example\nprint('ok')\n".getBytes(StandardCharsets.UTF_8);

    @ParameterizedTest
    @ValueSource(strings = {"png", "jpg", "gif"})
    void acceptsDecodedImagesWithGenericBrowserMime(String extension) throws Exception {
        assertEquals("image." + extension, validate("image." + extension, image(extension)));
    }

    @Test
    void acceptsWebp() {
        byte[] bytes = Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");
        assertEquals("image.webp", validate("image.webp", bytes));
    }

    @Test
    void acceptsZipContainingSkillSourceAndImages() throws Exception {
        byte[] bytes = zip(new String[]{"SKILL.md", "scripts/check.py", "images/cover.png"}, new byte[][]{TEXT, TEXT, image("png")});
        assertEquals("bundle.zip", validate("bundle.zip", bytes));
        assertEquals("application/zip", policy.mediaType("bundle.zip"));
        assertEquals("image/png", policy.mediaType("cover.png"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"../x.txt", "/x.txt", "C:/x.txt", "a/../x.txt", "a//x.txt", "a/.env", "a/x.txt.", "a/x.txt ", "a/x:txt", "NUL.txt", "a/b/c/d/e.txt", "a\r\nx.txt", "a\u202etxt.png"})
    void rejectsUnsafeAttachmentPaths(String path) {
        assertThrows(ResponseStatusException.class, () -> policy.validate(new MockMultipartFile("file", "ok.txt", "text/plain", TEXT), path));
    }

    @ParameterizedTest
    @ValueSource(strings = {"png", "jpg", "gif", "webp", "zip", "pdf", "exe", "svg", "html"})
    void rejectsSpoofedAndUnsupportedFiles(String extension) {
        assertThrows(ResponseStatusException.class, () -> validate("fake." + extension, TEXT));
    }

    @Test
    void rejectsBinaryDisguisedAsSourceAndInvalidText() {
        assertThrows(ResponseStatusException.class, () -> validate("payload.py", new byte[]{77, 90, 0, 1}));
        assertThrows(ResponseStatusException.class, () -> validate("payload.txt", new byte[]{(byte) 0xff, 0}));
        assertThrows(ResponseStatusException.class, () -> validate("payload.txt", new byte[]{65, 0, 66}));
    }

    @Test
    void rejectsEmptyOversizeAndMimeMismatch() throws Exception {
        assertThrows(ResponseStatusException.class, () -> validate("empty.txt", new byte[0]));
        var sizeError = assertThrows(ResponseStatusException.class, () -> validate("big.txt", new byte[(int) UploadPolicy.MAX_BYTES + 1]));
        assertEquals(413, sizeError.getStatusCode().value());
        assertThrows(ResponseStatusException.class, () -> policy.validate(new MockMultipartFile("file", "x.png", "image/jpeg", image("png")), null));
    }

    @Test
    void rejectsOversizeImageDimensionsAndTruncatedImage() throws Exception {
        ByteArrayOutputStream huge = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(12001, 1, BufferedImage.TYPE_INT_RGB), "png", huge);
        assertThrows(ResponseStatusException.class, () -> validate("wide.png", huge.toByteArray()));
        assertThrows(ResponseStatusException.class, () -> validate("broken.png", Arrays.copyOf(image("png"), 12)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"../outside.txt", "/absolute.txt", "C:/outside.txt", "a/../../outside.txt", "a/.hidden.txt", "payload.exe", "payload.svg", "payload.html", "NUL.txt"})
    void rejectsUnsafeZipEntries(String path) throws Exception {
        byte[] bytes = zip(new String[]{path}, new byte[][]{TEXT});
        assertThrows(ResponseStatusException.class, () -> validate("bundle.zip", bytes));
    }

    @Test
    void rejectsNestedArchivesDuplicatePathsAndConflictingDirectories() throws Exception {
        byte[] nested = zip(new String[]{"nested.zip"}, new byte[][]{zip(new String[]{"ok.txt"}, new byte[][]{TEXT})});
        assertThrows(ResponseStatusException.class, () -> validate("bundle.zip", nested));
        byte[] duplicate = zip(new String[]{"A.txt", "a.txt"}, new byte[][]{TEXT, TEXT});
        assertThrows(ResponseStatusException.class, () -> validate("bundle.zip", duplicate));
        byte[] conflict = zip(new String[]{"a.txt", "a.txt/b.txt"}, new byte[][]{TEXT, TEXT});
        assertThrows(ResponseStatusException.class, () -> validate("bundle.zip", conflict));
    }

    @Test
    void rejectsZipSymlinksBombsAndTooManyEntries() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(out)) {
            ZipArchiveEntry entry = new ZipArchiveEntry("link.txt");
            entry.setUnixMode(0120777);
            zip.putArchiveEntry(entry);
            zip.write(TEXT);
            zip.closeArchiveEntry();
        }
        assertThrows(ResponseStatusException.class, () -> validate("links.zip", out.toByteArray()));
        byte[] repeated = new byte[2 * 1024 * 1024];
        Arrays.fill(repeated, (byte) 'A');
        byte[] bomb = zip(new String[]{"large.txt"}, new byte[][]{repeated});
        assertThrows(ResponseStatusException.class, () -> validate("bomb.zip", bomb));
        String[] names = new String[201];
        byte[][] contents = new byte[201][];
        for (int i = 0; i < names.length; i++) { names[i] = "file" + i + ".txt"; contents[i] = TEXT; }
        byte[] many = zip(names, contents);
        assertThrows(ResponseStatusException.class, () -> validate("many.zip", many));
    }

    @Test
    void rejectsTruncatedEncryptedAndMismatchedZipHeaders() throws Exception {
        byte[] original = zip(new String[]{"safe.txt"}, new byte[][]{TEXT});
        assertThrows(ResponseStatusException.class, () -> validate("broken.zip", Arrays.copyOf(original, original.length / 2)));
        byte[] mismatched = original.clone();
        mismatched[30] = 'x';
        assertThrows(ResponseStatusException.class, () -> validate("mismatch.zip", mismatched));
        byte[] encrypted = original.clone();
        encrypted[6] |= 1;
        assertThrows(ResponseStatusException.class, () -> validate("encrypted.zip", encrypted));
    }

    private String validate(String name, byte[] bytes) {
        return policy.validate(new MockMultipartFile("file", name, "application/octet-stream", bytes), null);
    }

    private static byte[] image(String format) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), format, out));
        return out.toByteArray();
    }

    static byte[] zip(String[] names, byte[][] contents) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(out)) {
            for (int i = 0; i < names.length; i++) {
                zip.putArchiveEntry(new ZipArchiveEntry(names[i]));
                zip.write(contents[i]);
                zip.closeArchiveEntry();
            }
        }
        return out.toByteArray();
    }
}
