package com.company.aiasset.project;

import java.util.UUID;

public class AddProjectRuleRequest {
    private UUID assetVersionId;

    public UUID getAssetVersionId() {
        return assetVersionId;
    }

    public void setAssetVersionId(UUID assetVersionId) {
        this.assetVersionId = assetVersionId;
    }
}
