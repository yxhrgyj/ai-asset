package com.company.aiasset.project;

import java.time.LocalDateTime;
import java.util.UUID;

public class ProjectRuleDto {
    private UUID id;
    private UUID projectId;
    private UUID assetId;
    private UUID assetVersionId;
    private Boolean enabled;
    private UUID addedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public void setAssetId(UUID assetId) {
        this.assetId = assetId;
    }

    public UUID getAssetVersionId() {
        return assetVersionId;
    }

    public void setAssetVersionId(UUID assetVersionId) {
        this.assetVersionId = assetVersionId;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public UUID getAddedBy() {
        return addedBy;
    }

    public void setAddedBy(UUID addedBy) {
        this.addedBy = addedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
