package com.company.aiasset.statistics;

import com.company.aiasset.asset.AssetDownloadRepository;
import com.company.aiasset.asset.AssetRepository;
import com.company.aiasset.asset.AssetVersionRepository;
import com.company.aiasset.user.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class StatisticsService {

    private final AssetRepository assets;
    private final AssetVersionRepository versions;
    private final AssetDownloadRepository downloads;
    private final UserRepository users;

    public StatisticsService(AssetRepository assets,
                            AssetVersionRepository versions,
                            AssetDownloadRepository downloads,
                            UserRepository users) {
        this.assets = assets;
        this.versions = versions;
        this.downloads = downloads;
        this.users = users;
    }

    public StatisticsController.OverviewStats getOverview() {
        long totalAssets = assets.count();
        long publishedAssets = assets.countPublished();
        long draftAssets = versions.countByStatus("DRAFT");
        long totalDownloads = downloads.count();
        long totalUsers = users.count();

        return new StatisticsController.OverviewStats(
                totalAssets,
                publishedAssets,
                draftAssets,
                totalDownloads,
                totalUsers
        );
    }

    public List<StatisticsController.PopularAssetStats> getPopularAssets(int limit, Instant since) {
        return downloads.findPopularAssets(since, limit);
    }

    public List<StatisticsController.ActiveUserStats> getActiveUsers(int limit, Instant since) {
        return assets.findActiveUsers(since, limit);
    }

    public List<StatisticsController.DownloadRecord> getRecentDownloads(int limit) {
        return downloads.findRecentDownloads(limit);
    }
}
