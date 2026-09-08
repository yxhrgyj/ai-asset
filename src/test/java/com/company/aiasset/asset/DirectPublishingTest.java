package com.company.aiasset.asset;

import com.company.aiasset.security.CurrentUser;
import com.company.aiasset.user.User;
import com.company.aiasset.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DirectPublishingTest {
    private static final Path STORAGE = Path.of(System.getProperty("java.io.tmpdir"), "asset-upload-test-" + UUID.randomUUID());
    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) { registry.add("app.storage.root", STORAGE::toString); }

    @Autowired MockMvc mvc;
    @Autowired AssetService service;
    @Autowired AssetRepository assets;
    @Autowired AssetVersionRepository versions;
    @Autowired AssetFileRepository files;
    @Autowired UserRepository users;
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @Test
    void ownerPublishesAttachmentsWithoutBodyAndNewDraftKeepsThem() throws Exception {
        User owner = user(User.Role.AUTHOR);
        Asset asset = create(owner);
        mvc.perform(multipart("/api/assets/" + asset.getId() + "/files")
                        .file(textFile("guide.txt")).sessionAttr("USER_ID", owner.getId()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.mimeType").value("text/plain"));
        AssetVersion draft = versions.findOpenVersion(asset.getId()).orElseThrow();
        AssetFile file = files.findByAssetVersionIdOrderByRelativePath(draft.getId()).get(0);
        mvc.perform(post("/api/assets/" + asset.getId() + "/publish").sessionAttr("USER_ID", owner.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.contentHash").isNotEmpty());
        versions.flush();
        mvc.perform(get("/api/public/assets/" + asset.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.files.length()").value(1));
        mvc.perform(multipart("/api/assets/" + asset.getId() + "/files")
                        .file(textFile("frozen.txt")).sessionAttr("USER_ID", owner.getId()))
                .andExpect(status().isConflict());
        AssetVersion next = service.newDraft(asset.getId(), new CurrentUser(owner));
        files.flush();
        AssetFile copy = files.findByAssetVersionIdOrderByRelativePath(next.getId()).get(0);
        assertNotEquals(file.getId(), copy.getId());
        assertEquals(file.getStorageKey(), copy.getStorageKey());
        assertEquals(file.getContentHash(), copy.getContentHash());
        mvc.perform(get("/api/assets/" + asset.getId()).sessionAttr("USER_ID", owner.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentVersion.versionNo").value(2))
                .andExpect(jsonPath("$.files.length()").value(1));
    }

    @Test
    void rejectsEmptyPublishAndRestrictsPublishingToOwnerOrAdmin() throws Exception {
        User owner = user(User.Role.AUTHOR), other = user(User.Role.AUTHOR), reader = user(User.Role.USER), admin = user(User.Role.ADMIN);
        Asset asset = create(owner);
        mvc.perform(post("/api/assets/" + asset.getId() + "/publish").sessionAttr("USER_ID", owner.getId()))
                .andExpect(status().isBadRequest());
        service.saveDraft(asset.getId(), "# Content", null, new CurrentUser(owner));
        for (User denied : new User[]{other, reader}) {
            mvc.perform(post("/api/assets/" + asset.getId() + "/publish").sessionAttr("USER_ID", denied.getId()))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/assets/" + asset.getId() + "/publish").sessionAttr("USER_ID", admin.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void uploadRejectsUnsafeContentAndUnauthorizedWritersBeforePersisting() throws Exception {
        User owner = user(User.Role.AUTHOR), other = user(User.Role.AUTHOR);
        Asset asset = create(owner);
        mvc.perform(multipart("/api/assets/" + asset.getId() + "/files")
                        .file(textFile("guide.txt")).sessionAttr("USER_ID", other.getId()))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/assets/" + asset.getId() + "/files")
                        .file(textFile("fake.png")).sessionAttr("USER_ID", owner.getId()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(multipart("/api/assets/" + asset.getId() + "/files")
                        .file(textFile("guide.txt")).param("path", "../guide.txt").sessionAttr("USER_ID", owner.getId()))
                .andExpect(status().isBadRequest());
        assertTrue(files.findByAssetVersionIdOrderByRelativePath(versions.findOpenVersion(asset.getId()).orElseThrow().getId()).isEmpty());
    }

    @Test
    void draftDownloadsAndExportsRequireOwnershipAndPublishedDownloadsAreAttachments() throws Exception {
        User owner = user(User.Role.AUTHOR), other = user(User.Role.AUTHOR);
        Asset asset = create(owner);
        mvc.perform(multipart("/api/assets/" + asset.getId() + "/files")
                        .file(textFile("guide.txt")).sessionAttr("USER_ID", owner.getId())).andExpect(status().isCreated());
        AssetVersion draft = versions.findOpenVersion(asset.getId()).orElseThrow();
        AssetFile file = files.findByAssetVersionIdOrderByRelativePath(draft.getId()).get(0);
        String base = "/api/assets/" + asset.getId();
        for (String path : new String[]{"/files/" + file.getId(), "/files/download-all", "/export?versionNo=1"}) {
            mvc.perform(get(base + path).sessionAttr("USER_ID", other.getId())).andExpect(status().isForbidden());
        }
        mvc.perform(get(base + "?versionNo=1").sessionAttr("USER_ID", other.getId())).andExpect(status().isNotFound());
        service.publish(asset.getId(), new CurrentUser(owner));
        versions.flush();
        mvc.perform(get(base + "/files/" + file.getId()).sessionAttr("USER_ID", other.getId()))
                .andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Type", "application/octet-stream"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")))
                .andExpect(content().bytes("Guide content".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void downloadRankingFiltersHiddenAssetsBeforeLimitAndSupportsType() throws Exception {
        User owner = user(User.Role.AUTHOR);
        Asset first = create(owner), second = create(owner), hidden = create(owner), archived = create(owner), skill = create(owner);
        for (Asset asset : new Asset[]{first, second, archived, skill}) {
            service.saveDraft(asset.getId(), "Body", null, new CurrentUser(owner));
            service.publish(asset.getId(), new CurrentUser(owner));
        }
        first.setDownloadCount(2_000_000); second.setDownloadCount(1_000_000);
        hidden.setDownloadCount(9_000_000); archived.setDownloadCount(8_000_000); archived.setArchived(true);
        skill.setDownloadCount(3_000_000); skill.setType(Asset.Type.SKILL);
        assets.flush(); versions.flush();
        mvc.perform(get("/api/public/download-ranking").param("limit", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(skill.getId().toString()))
                .andExpect(jsonPath("$[1].id").value(first.getId().toString()));
        mvc.perform(get("/api/public/download-ranking").param("type", "RULE").param("limit", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(first.getId().toString()));
    }

    @Test
    void downloadCountersIgnoreDraftsAndRejectUnrelatedVersionIds() {
        User owner = user(User.Role.AUTHOR);
        Asset first = create(owner), second = create(owner);
        AssetVersion draft = versions.findOpenVersion(first.getId()).orElseThrow();
        service.recordDownload(first.getId(), draft.getId(), new CurrentUser(owner));
        assertEquals(0, first.getDownloadCount());
        assertThrows(ResponseStatusException.class, () -> service.recordDownload(second.getId(), draft.getId(), new CurrentUser(owner)));
    }

    @Test
    void retiredApprovalRoutesAreNotRegistered() throws Exception {
        User admin = user(User.Role.ADMIN);
        mvc.perform(get("/api/approvals/pending").sessionAttr("USER_ID", admin.getId())).andExpect(status().isNotFound());
        mvc.perform(post("/api/assets/" + UUID.randomUUID() + "/submit").sessionAttr("USER_ID", admin.getId())).andExpect(status().isNotFound());
    }

    @Test
    void migrationRecoversPendingAndLatestRejectedVersionsWithoutPublishingThem() {
        User owner = user(User.Role.APPROVER);
        Asset pending = create(owner), rejected = create(owner);
        AssetVersion p = versions.findOpenVersion(pending.getId()).orElseThrow(), r = versions.findOpenVersion(rejected.getId()).orElseThrow();
        p.setStatus(AssetVersion.Status.PENDING); r.setStatus(AssetVersion.Status.REJECTED); versions.flush();
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V8__direct_publishing.sql")).execute(dataSource);
        assertEquals("DRAFT", jdbc.queryForObject("select status from asset_versions where id = ?", String.class, p.getId()));
        assertEquals("DRAFT", jdbc.queryForObject("select status from asset_versions where id = ?", String.class, r.getId()));
        assertEquals("AUTHOR", jdbc.queryForObject("select role from users where id = ?", String.class, owner.getId()));
    }

    private User user(User.Role role) {
        return users.saveAndFlush(new User("upload-test-" + UUID.randomUUID(), "Upload Test", "$2a$10$9d4.jHhtRtqaGWqUaN/hjO4w8qDcyY0jtjLh.sVJuuzkZ0hrogVvm", role));
    }

    private Asset create(User owner) {
        Asset asset = service.create(new AssetService.CreateRequest(Asset.Type.RULE, "Test asset", "upload-test-" + UUID.randomUUID(), null, null, null, Asset.Scope.ORGANIZATION, null), new CurrentUser(owner));
        versions.flush();
        return asset;
    }

    private MockMultipartFile textFile(String name) {
        return new MockMultipartFile("file", name, "application/octet-stream", "Guide content".getBytes(StandardCharsets.UTF_8));
    }
}
