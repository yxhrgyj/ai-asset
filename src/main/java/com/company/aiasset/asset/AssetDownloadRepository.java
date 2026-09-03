package com.company.aiasset.asset;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AssetDownloadRepository extends JpaRepository<AssetDownload, UUID> {
}
