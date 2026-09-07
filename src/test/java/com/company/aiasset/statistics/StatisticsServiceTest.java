package com.company.aiasset.statistics;

import com.company.aiasset.asset.Asset;
import com.company.aiasset.asset.AssetRepository;
import com.company.aiasset.asset.AssetVersion;
import com.company.aiasset.asset.AssetVersionRepository;
import com.company.aiasset.user.User;
import com.company.aiasset.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
class StatisticsServiceTest {
    @Autowired private StatisticsService service;
    @Autowired private AssetRepository assets;
    @Autowired private AssetVersionRepository versions;
    @Autowired private UserRepository users;

    @Test
    void unpublishedAssetsDoNotIncreasePublishedCount() {
        long before = service.getOverview().publishedAssets();
        Asset asset = saveAsset(false);
        saveVersion(asset, 1, AssetVersion.Status.DRAFT);
        saveAsset(false);

        assertEquals(before, service.getOverview().publishedAssets());
    }

    @Test
    void publishedCountDeduplicatesVersionsAndExcludesArchivedAssets() {
        long before = service.getOverview().publishedAssets();
        Asset published = saveAsset(false);
        saveVersion(published, 1, AssetVersion.Status.PUBLISHED);
        saveVersion(published, 2, AssetVersion.Status.PUBLISHED);
        saveVersion(published, 3, AssetVersion.Status.DRAFT);
        saveVersion(saveAsset(true), 1, AssetVersion.Status.PUBLISHED);

        assertEquals(before + 1, service.getOverview().publishedAssets());
    }

    private Asset saveAsset(boolean archived) {
        String token = "statistics-test-" + UUID.randomUUID();
        User owner = users.saveAndFlush(new User(token, "Statistics Test",
                "$2a$10$9d4.jHhtRtqaGWqUaN/hjO4w8qDcyY0jtjLh.sVJuuzkZ0hrogVvm", User.Role.AUTHOR));
        Asset asset = new Asset();
        asset.setName(token);
        asset.setSlug(token);
        asset.setType(Asset.Type.RULE);
        asset.setScope(Asset.Scope.ORGANIZATION);
        asset.setOwnerUserId(owner.getId());
        asset.setArchived(archived);
        return assets.saveAndFlush(asset);
    }

    private void saveVersion(Asset asset, int number, AssetVersion.Status status) {
        AssetVersion version = new AssetVersion();
        version.setAssetId(asset.getId());
        version.setVersionNo(number);
        version.setStatus(status);
        version.setBody("Statistics regression fixture");
        version.setCreatedBy(asset.getOwnerUserId());
        if (status == AssetVersion.Status.PUBLISHED) version.setContentHash("statistics-test-hash-" + number);
        versions.saveAndFlush(version);
    }
}
