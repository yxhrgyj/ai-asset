package com.company.aiasset.portal;

import com.company.aiasset.asset.Asset;
import com.company.aiasset.asset.AssetFileRepository;
import com.company.aiasset.asset.AssetRepository;
import com.company.aiasset.asset.AssetVersion;
import com.company.aiasset.asset.AssetVersionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortalStatisticsTest {

    @Mock
    private AssetRepository assets;

    @Mock
    private AssetVersionRepository versions;

    @Mock
    private AssetFileRepository files;

    @InjectMocks
    private PortalController controller;

    @Test
    void statisticsCountsOnlyPublishedNonArchivedAssetsByTypeAndScope() {
        Asset rule = asset(Asset.Type.RULE, Asset.Scope.ORGANIZATION, 10, false);
        Asset skill = asset(Asset.Type.SKILL, Asset.Scope.TECH_STACK, 5, false);
        Asset document = asset(Asset.Type.DOCUMENT, Asset.Scope.PROJECT, 3, false);
        Asset draft = asset(Asset.Type.RULE, Asset.Scope.ORGANIZATION, 100, false);
        when(assets.findByArchivedFalse()).thenReturn(List.of(rule, skill, document, draft));
        UUID ruleId = rule.getId();
        UUID skillId = skill.getId();
        UUID documentId = document.getId();
        UUID draftId = draft.getId();
        AssetVersion publishedRule = published(ruleId);
        AssetVersion publishedSkill = published(skillId);
        AssetVersion publishedDocument = published(documentId);
        when(versions.findLatestPublished(ruleId)).thenReturn(Optional.of(publishedRule));
        when(versions.findLatestPublished(skillId)).thenReturn(Optional.of(publishedSkill));
        when(versions.findLatestPublished(documentId)).thenReturn(Optional.of(publishedDocument));
        when(versions.findLatestPublished(draftId)).thenReturn(Optional.empty());

        PortalDto.Statistics result = controller.statistics();

        assertThat(result.totalAssets()).isEqualTo(3);
        assertThat(result.totalDownloads()).isEqualTo(18);
        assertThat(result.typeBreakdown()).isEqualTo(new PortalDto.TypeBreakdown(1, 1, 1));
        assertThat(result.scopeBreakdown()).isEqualTo(new PortalDto.ScopeBreakdown(1, 1, 1));
    }

    private static Asset asset(Asset.Type type, Asset.Scope scope, int downloads, boolean archived) {
        Asset asset = mock(Asset.class);
        lenient().when(asset.getId()).thenReturn(UUID.randomUUID());
        lenient().when(asset.getType()).thenReturn(type);
        lenient().when(asset.getScope()).thenReturn(scope);
        lenient().when(asset.getDownloadCount()).thenReturn(downloads);
        lenient().when(asset.isArchived()).thenReturn(archived);
        return asset;
    }

    private static AssetVersion published(UUID assetId) {
        AssetVersion version = new AssetVersion();
        version.setAssetId(assetId);
        version.setStatus(AssetVersion.Status.PUBLISHED);
        return version;
    }
}
