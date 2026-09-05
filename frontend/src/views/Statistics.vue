<template>
  <MainLayout>
    <div class="statistics-page">
      <div class="page-header">
        <h1 class="page-title">平台统计</h1>
      </div>

      <div v-if="loading" class="loading">加载中...</div>

      <div v-else-if="error" class="error-message">{{ error }}</div>

      <div v-else class="statistics-content">
        <!-- 总体统计卡片 -->
        <div class="overview-grid">
          <div class="stat-card">
            <div class="stat-icon assets">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
              </svg>
            </div>
            <div class="stat-content">
              <div class="stat-label">总资产数</div>
              <div class="stat-value">{{ overview.totalAssets }}</div>
            </div>
          </div>

          <div class="stat-card">
            <div class="stat-icon published">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"/>
              </svg>
            </div>
            <div class="stat-content">
              <div class="stat-label">已发布</div>
              <div class="stat-value">{{ overview.publishedAssets }}</div>
            </div>
          </div>

          <div class="stat-card">
            <div class="stat-icon drafts">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"/>
              </svg>
            </div>
            <div class="stat-content">
              <div class="stat-label">草稿版本</div>
              <div class="stat-value">{{ overview.draftAssets }}</div>
            </div>
          </div>

          <div class="stat-card">
            <div class="stat-icon downloads">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"/>
              </svg>
            </div>
            <div class="stat-content">
              <div class="stat-label">总下载</div>
              <div class="stat-value">{{ overview.totalDownloads }}</div>
            </div>
          </div>

          <div class="stat-card">
            <div class="stat-icon users">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z"/>
              </svg>
            </div>
            <div class="stat-content">
              <div class="stat-label">用户数</div>
              <div class="stat-value">{{ overview.totalUsers }}</div>
            </div>
          </div>

          <div class="stat-card">
            <div class="stat-icon approvals">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"/>
              </svg>
            </div>
            <div class="stat-content">
              <div class="stat-label">待审批</div>
              <div class="stat-value">{{ overview.pendingApprovals }}</div>
            </div>
          </div>
        </div>

        <!-- 热门资产 -->
        <div class="section">
          <div class="section-header">
            <h2>热门资产</h2>
            <span class="section-subtitle">近 30 天下载排行</span>
          </div>
          <div v-if="popularAssets.length === 0" class="empty-state">
            暂无数据
          </div>
          <div v-else class="list-table">
            <div class="table-row table-header">
              <div class="table-cell">资产名称</div>
              <div class="table-cell">类型</div>
              <div class="table-cell">下载次数</div>
              <div class="table-cell">最后下载时间</div>
            </div>
            <router-link
              v-for="asset in popularAssets"
              :key="asset.id"
              :to="`/assets/${asset.id}`"
              class="table-row table-link"
            >
              <div class="table-cell">{{ asset.name }}</div>
              <div class="table-cell">
                <span class="type-badge">{{ formatType(asset.type) }}</span>
              </div>
              <div class="table-cell">{{ asset.downloadCount }}</div>
              <div class="table-cell">{{ formatDate(asset.lastDownloadedAt) }}</div>
            </router-link>
          </div>
        </div>

        <!-- 活跃用户 -->
        <div class="section">
          <div class="section-header">
            <h2>活跃用户</h2>
            <span class="section-subtitle">近 30 天创建资产排行</span>
          </div>
          <div v-if="activeUsers.length === 0" class="empty-state">
            暂无数据
          </div>
          <div v-else class="list-table">
            <div class="table-row table-header">
              <div class="table-cell">用户</div>
              <div class="table-cell">创建资产数</div>
              <div class="table-cell">资产总下载</div>
            </div>
            <div
              v-for="user in activeUsers"
              :key="user.userId"
              class="table-row"
            >
              <div class="table-cell">
                <div class="user-info">
                  <span class="username">{{ user.displayName }}</span>
                  <span class="user-handle">@{{ user.username }}</span>
                </div>
              </div>
              <div class="table-cell">{{ user.assetCount }}</div>
              <div class="table-cell">{{ user.downloadCount }}</div>
            </div>
          </div>
        </div>

        <!-- 最近下载 -->
        <div class="section">
          <div class="section-header">
            <h2>最近下载</h2>
          </div>
          <div v-if="recentDownloads.length === 0" class="empty-state">
            暂无记录
          </div>
          <div v-else class="list-table">
            <div class="table-row table-header">
              <div class="table-cell">资产名称</div>
              <div class="table-cell">下载用户</div>
              <div class="table-cell">下载时间</div>
            </div>
            <div
              v-for="(record, index) in recentDownloads"
              :key="index"
              class="table-row"
            >
              <div class="table-cell">
                <router-link :to="`/assets/${record.assetId}`" class="asset-link">
                  {{ record.assetName }}
                </router-link>
              </div>
              <div class="table-cell">{{ record.username }}</div>
              <div class="table-cell">{{ formatDate(record.downloadedAt) }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </MainLayout>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { statisticsApi, type OverviewStats, type PopularAsset, type ActiveUser, type DownloadRecord } from '../api/statistics'
import MainLayout from '../components/MainLayout.vue'

const loading = ref(true)
const error = ref('')

const overview = ref<OverviewStats>({
  totalAssets: 0,
  publishedAssets: 0,
  draftAssets: 0,
  totalDownloads: 0,
  totalUsers: 0,
  pendingApprovals: 0
})
const popularAssets = ref<PopularAsset[]>([])
const activeUsers = ref<ActiveUser[]>([])
const recentDownloads = ref<DownloadRecord[]>([])

const loadData = async () => {
  try {
    loading.value = true
    error.value = ''
    const [overviewData, popularData, activeData, downloadsData] = await Promise.all([
      statisticsApi.getOverview(),
      statisticsApi.getPopularAssets(),
      statisticsApi.getActiveUsers(),
      statisticsApi.getRecentDownloads()
    ])
    overview.value = overviewData
    popularAssets.value = popularData
    activeUsers.value = activeData
    recentDownloads.value = downloadsData
  } catch (err: any) {
    error.value = err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

const formatDate = (dateStr: string) => {
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const seconds = Math.floor(diff / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)

  if (days > 7) {
    return date.toLocaleDateString('zh-CN')
  } else if (days > 0) {
    return `${days} 天前`
  } else if (hours > 0) {
    return `${hours} 小时前`
  } else if (minutes > 0) {
    return `${minutes} 分钟前`
  } else {
    return '刚刚'
  }
}

const formatType = (type: string) => {
  const typeMap: Record<string, string> = {
    'PROMPT': 'Prompt',
    'SKILL': 'Skill',
    'WORKFLOW': '工作流',
    'AGENT': 'Agent',
    'TOOL': '工具',
    'DATASET': '数据集',
    'OTHER': '其他'
  }
  return typeMap[type] || type
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.statistics-page {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: var(--sp-32);
}

.page-title {
  font-size: 28px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0;
}

.loading {
  text-align: center;
  padding: var(--sp-48);
  color: var(--color-text-secondary);
}

.error-message {
  padding: var(--sp-16);
  background: var(--color-error-bg);
  color: var(--color-error);
  border-radius: var(--radius-8);
  text-align: center;
  border-left: 3px solid var(--color-error);
}

/* 总体统计卡片 */
.overview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--sp-16);
  margin-bottom: var(--sp-32);
}

.stat-card {
  background: var(--color-bg-1);
  border-radius: var(--radius-12);
  padding: var(--sp-20);
  display: flex;
  align-items: center;
  gap: var(--sp-16);
  border: 1px solid var(--color-border);
  transition: all 0.2s;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-card);
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-8);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-icon svg {
  width: 24px;
  height: 24px;
}

.stat-icon.assets {
  background: rgba(59, 130, 246, 0.1);
  color: #3b82f6;
}

.stat-icon.published {
  background: rgba(16, 185, 129, 0.1);
  color: #10b981;
}

.stat-icon.drafts {
  background: rgba(251, 191, 36, 0.1);
  color: #fbbf24;
}

.stat-icon.downloads {
  background: rgba(139, 92, 246, 0.1);
  color: #8b5cf6;
}

.stat-icon.users {
  background: rgba(236, 72, 153, 0.1);
  color: #ec4899;
}

.stat-icon.approvals {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}

.stat-content {
  flex: 1;
}

.stat-label {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: var(--sp-4);
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: var(--color-text-primary);
}

/* 列表区域 */
.section {
  background: var(--color-bg-1);
  border-radius: var(--radius-12);
  padding: var(--sp-24);
  margin-bottom: var(--sp-24);
  border: 1px solid var(--color-border);
}

.section-header {
  display: flex;
  align-items: baseline;
  gap: var(--sp-12);
  margin-bottom: var(--sp-20);
  padding-bottom: var(--sp-16);
  border-bottom: 1px solid var(--color-border);
}

.section-header h2 {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0;
}

.section-subtitle {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.empty-state {
  text-align: center;
  padding: var(--sp-32);
  color: var(--color-text-tertiary);
}

/* 表格样式 */
.list-table {
  display: flex;
  flex-direction: column;
}

.table-row {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr 1.5fr;
  gap: var(--sp-16);
  padding: var(--sp-12) var(--sp-16);
  border-bottom: 1px solid var(--color-border);
  align-items: center;
}

.table-row:last-child {
  border-bottom: none;
}

.table-header {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  background: var(--color-bg-2);
  border-radius: var(--radius-8);
  margin-bottom: var(--sp-8);
}

.table-link {
  text-decoration: none;
  color: inherit;
  transition: background 0.2s;
}

.table-link:hover {
  background: var(--color-bg-2);
}

.table-cell {
  font-size: 14px;
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.type-badge {
  display: inline-block;
  padding: 4px 12px;
  background: var(--color-bg-2);
  color: var(--color-text-primary);
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
}

.user-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.username {
  font-weight: 500;
  color: var(--color-text-primary);
}

.user-handle {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.asset-link {
  color: var(--color-primary);
  text-decoration: none;
  transition: opacity 0.2s;
}

.asset-link:hover {
  opacity: 0.8;
}

@media (max-width: 768px) {
  .overview-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .table-row {
    grid-template-columns: 1fr;
    gap: var(--sp-8);
  }

  .table-header {
    display: none;
  }

  .table-cell::before {
    content: attr(data-label);
    font-weight: 600;
    margin-right: var(--sp-8);
    color: var(--color-text-secondary);
  }
}
</style>
