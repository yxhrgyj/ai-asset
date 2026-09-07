package com.company.aiasset.portal;

import com.company.aiasset.asset.Asset;
import com.company.aiasset.asset.AssetRepository;
import com.company.aiasset.asset.AssetVersion;
import com.company.aiasset.asset.AssetVersionRepository;
import com.company.aiasset.user.User;
import com.company.aiasset.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PortalControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AssetRepository assets;

    @Autowired
    private AssetVersionRepository versions;

    @Autowired
    private UserRepository users;

    @Test
    void listPublicAssetsFiltersUnpublishedAndArchivedBeforePaging() throws Exception {
        String token = "portal-test-" + UUID.randomUUID();
        User owner = saveUser(token);

        saveAsset(token + "-published-a", "Published A", owner, false, AssetVersion.Status.PUBLISHED);
        saveAsset(token + "-published-b", "Published B", owner, false, AssetVersion.Status.PUBLISHED);
        saveAsset(token + "-draft", "Draft", owner, false, AssetVersion.Status.DRAFT);
        saveAsset(token + "-archived", "Archived", owner, true, AssetVersion.Status.PUBLISHED);

        mvc.perform(get("/api/public/assets")
                        .param("q", token)
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void publicAssetListingAllowsAnonymousRequests() throws Exception {
        mvc.perform(get("/api/public/assets"))
                .andExpect(status().isOk());
    }

    @Test
    void publicAssetDetailReturnsLatestPublishedVersionAndNeverDraftContent() throws Exception {
        String token = "portal-detail-" + UUID.randomUUID();
        User owner = saveUser(token);
        Asset asset = saveAsset(token + "-asset", "Detail Asset", owner, false, AssetVersion.Status.PUBLISHED);

        AssetVersion v2 = new AssetVersion();
        v2.setAssetId(asset.getId());
        v2.setVersionNo(2);
        v2.setStatus(AssetVersion.Status.PUBLISHED);
        v2.setBody("published v2");
        v2.setChangelog("second release");
        v2.setCreatedBy(owner.getId());
        v2.setPublishedAt(Instant.parse("2026-09-05T00:00:00Z"));
        v2.setContentHash("portal-detail-hash");
        versions.saveAndFlush(v2);

        mvc.perform(get("/api/public/assets/" + asset.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.name").value("Detail Asset " + token + "-asset"))
                .andExpect(jsonPath("$.body").value("published v2"))
                .andExpect(jsonPath("$.versionNo").value(2))
                .andExpect(jsonPath("$.changelog").value("second release"))
                .andExpect(jsonPath("$.canEdit").value(false))
                .andExpect(jsonPath("$.versions.length()").value(2));
    }

    @Test
    void publicAssetDetailHidesDraftOnlyAndArchivedAssets() throws Exception {
        String token = "portal-hidden-" + UUID.randomUUID();
        User owner = saveUser(token);
        Asset draft = saveAsset(token + "-draft", "Draft Only", owner, false, AssetVersion.Status.DRAFT);
        Asset archived = saveAsset(token + "-archived", "Archived", owner, true, AssetVersion.Status.PUBLISHED);

        mvc.perform(get("/api/public/assets/" + draft.getId()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/public/assets/" + archived.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicDetailEditFlagRequiresAuthorPermissionEvenForOwner() throws Exception {
        String token = "portal-permission-" + UUID.randomUUID();
        User owner = saveUser(token, User.Role.USER);
        Asset asset = saveAsset(token, "Owned Asset", owner, false, AssetVersion.Status.PUBLISHED);

        mvc.perform(get("/api/public/assets/" + asset.getId())
                        .sessionAttr("USER_ID", owner.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canEdit").value(false));
    }

    @Test
    void publicDetailRejectsRequestedDraftVersion() throws Exception {
        String token = "portal-version-" + UUID.randomUUID();
        User owner = saveUser(token);
        Asset asset = saveAsset(token, "Versioned Asset", owner, false, AssetVersion.Status.PUBLISHED);
        AssetVersion draft = new AssetVersion();
        draft.setAssetId(asset.getId());
        draft.setVersionNo(2);
        draft.setBody("private draft");
        draft.setCreatedBy(owner.getId());
        versions.saveAndFlush(draft);

        mvc.perform(get("/api/public/assets/" + asset.getId()).param("versionNo", "2"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/public/assets/" + asset.getId()).param("versionNo", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value(1))
                .andExpect(jsonPath("$.versions.length()").value(1));
    }

    private User saveUser(String token) {
        return saveUser(token, User.Role.AUTHOR);
    }

    private User saveUser(String token, User.Role role) {
        return users.saveAndFlush(new User(
                "portal-user-" + token,
                "Portal Test User",
                "$2a$10$9d4.jHhtRtqaGWqUaN/hjO4w8qDcyY0jtjLh.sVJuuzkZ0hrogVvm",
                role));
    }

    private Asset saveAsset(String slug, String name, User owner, boolean archived,
                            AssetVersion.Status status) {
        Asset asset = new Asset();
        asset.setName(name + " " + slug);
        asset.setSlug(slug);
        asset.setType(Asset.Type.RULE);
        asset.setScope(Asset.Scope.ORGANIZATION);
        asset.setOwnerUserId(owner.getId());
        asset.setArchived(archived);
        Asset saved = assets.saveAndFlush(asset);

        AssetVersion version = new AssetVersion();
        version.setAssetId(saved.getId());
        version.setVersionNo(1);
        version.setStatus(status);
        version.setBody("# " + name);
        version.setCreatedBy(owner.getId());
        if (status == AssetVersion.Status.PUBLISHED) {
            version.setContentHash("portal-test-hash");
        }
        versions.saveAndFlush(version);
        return saved;
    }
}
