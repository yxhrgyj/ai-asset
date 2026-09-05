package com.company.aiasset.statistics;

import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {

    private final StatisticsService service;

    public StatisticsController(StatisticsService service) {
        this.service = service;
    }

    /** 获取平台总体统计 */
    @GetMapping("/overview")
    public OverviewStats getOverview() {
        return service.getOverview();
    }

    /** 获取热门资产（按下载次数排序） */
    @GetMapping("/popular-assets")
    public List<PopularAssetStats> getPopularAssets(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "30") int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        return service.getPopularAssets(limit, since);
    }

    /** 获取活跃用户（按创建/编辑资产数量排序） */
    @GetMapping("/active-users")
    public List<ActiveUserStats> getActiveUsers(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "30") int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        return service.getActiveUsers(limit, since);
    }

    /** 获取最近下载记录 */
    @GetMapping("/recent-downloads")
    public List<DownloadRecord> getRecentDownloads(
            @RequestParam(defaultValue = "20") int limit) {
        return service.getRecentDownloads(limit);
    }

    public record OverviewStats(
            long totalAssets,
            long publishedAssets,
            long draftAssets,
            long totalDownloads,
            long totalUsers,
            long pendingApprovals
    ) {}

    public record PopularAssetStats(
            String id,
            String name,
            String type,
            String slug,
            long downloadCount,
            Instant lastDownloadedAt
    ) {}

    public record ActiveUserStats(
            String userId,
            String username,
            String displayName,
            long assetCount,
            long downloadCount
    ) {}

    public record DownloadRecord(
            String assetId,
            String assetName,
            String userId,
            String username,
            Instant downloadedAt
    ) {}
}
