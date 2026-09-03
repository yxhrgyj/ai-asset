package com.company.aiasset.asset;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetFileRepository extends JpaRepository<AssetFile, UUID> {

    List<AssetFile> findByAssetVersionIdOrderByRelativePath(UUID assetVersionId);

    Optional<AssetFile> findByAssetVersionIdAndRelativePath(UUID assetVersionId, String relativePath);

    boolean existsByAssetVersionIdAndRelativePath(UUID assetVersionId, String relativePath);
}
