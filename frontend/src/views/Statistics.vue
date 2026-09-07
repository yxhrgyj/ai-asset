<template>
  <MainLayout>
    <div class="workbench">
      <header class="workbench-header">
        <h1>工作台</h1>
        <div class="workbench-refresh">
          <span v-if="updatedAt" class="updated-time">更新于 {{ updatedAt }}</span>
          <button type="button" class="refresh-button" aria-label="刷新统计" title="刷新统计" :disabled="loading" @click="loadData">
            <RefreshCw :size="16" :class="{ spinning: loading }" aria-hidden="true" />
          </button>
        </div>
      </header>

      <nav class="workbench-actions" aria-label="快捷入口">
        <h2 class="actions-label">快捷入口</h2>
        <div class="workbench-action-grid">
          <RouterLink v-for="action in actions" :key="action.to" :to="action.to" :class="`action-${action.tone}`">
            <span class="action-icon"><component :is="action.icon" :size="18" aria-hidden="true" /></span>
            <span class="action-name">{{ action.label }}</span>
            <ChevronRight :size="16" class="action-arrow" aria-hidden="true" />
          </RouterLink>
        </div>
      </nav>

      <div v-if="error" class="workbench-error" role="alert">
        <CircleAlert :size="20" aria-hidden="true" />
        <span>{{ error }}</span>
        <button type="button" :disabled="loading" @click="loadData"><RefreshCw :size="16" aria-hidden="true" />重试</button>
      </div>
      <div v-if="loading" class="workbench-loading" role="status">{{ overview ? '正在更新统计...' : '正在加载工作台...' }}</div>

      <div v-if="overview" :aria-busy="loading">
        <section aria-label="资产概览" class="overview-grid">
          <div v-for="metric in metrics" :key="metric.label" class="metric-card">
            <div class="metric-heading">
              <span>{{ metric.label }}</span>
              <component :is="metric.icon" :size="20" :class="metric.tone" aria-hidden="true" />
            </div>
            <div class="metric-value">{{ formatNumber(metric.value) }}</div>
          </div>
        </section>
        <section aria-label="协作概览" class="collaboration-metrics">
          <div><Users :size="16" aria-hidden="true" /><span>用户数</span><strong class="metric-value">{{ formatNumber(overview.totalUsers) }}</strong></div>
          <div><UsersRound :size="16" aria-hidden="true" /><span>团队数量</span><strong class="metric-value">{{ formatNumber(teamCount) }}</strong></div>
          <div><ClipboardCheck :size="16" aria-hidden="true" /><span>待审批</span><strong class="metric-value pending-count">{{ formatNumber(overview.pendingApprovals) }}</strong></div>
        </section>

        <section class="workbench-section" aria-labelledby="popular-title">
          <div class="section-heading"><h2 id="popular-title">热门资产</h2><span>近 30 天下载排行</span></div>
          <div v-if="!popularAssets.length" class="workbench-empty">近 30 天暂无下载记录</div>
          <table v-else class="workbench-table popular-table" aria-labelledby="popular-title">
            <thead><tr><th scope="col">资产名称</th><th scope="col">类型</th><th scope="col">下载次数</th><th scope="col">最后下载</th></tr></thead>
            <tbody>
              <tr v-for="asset in popularAssets" :key="asset.id">
                <td data-label="资产名称"><RouterLink :to="`/admin/assets/${asset.id}`">{{ asset.name }}</RouterLink></td>
                <td data-label="类型"><span class="type-badge">{{ formatType(asset.type) }}</span></td>
                <td data-label="下载次数" class="number-cell">{{ formatNumber(asset.downloadCount) }}</td>
                <td data-label="最后下载">{{ formatDate(asset.lastDownloadedAt) }}</td>
              </tr>
            </tbody>
          </table>
        </section>

        <section class="workbench-section" aria-labelledby="active-title">
          <div class="section-heading"><h2 id="active-title">活跃用户</h2><span>近 30 天维护资产排行</span></div>
          <div v-if="!activeUsers.length" class="workbench-empty">近 30 天暂无资产维护记录</div>
          <table v-else class="workbench-table" aria-labelledby="active-title">
            <thead><tr><th scope="col">用户</th><th scope="col">维护资产数</th><th scope="col">相关资产累计下载</th></tr></thead>
            <tbody>
              <tr v-for="user in activeUsers" :key="user.userId">
                <td data-label="用户"><span>{{ user.displayName || user.username }}</span><span class="user-handle">@{{ user.username }}</span></td>
                <td data-label="维护资产数" class="number-cell">{{ formatNumber(user.assetCount) }}</td>
                <td data-label="相关资产累计下载" class="number-cell">{{ formatNumber(user.downloadCount) }}</td>
              </tr>
            </tbody>
          </table>
        </section>

        <section id="activity" class="workbench-section" aria-labelledby="downloads-title">
          <div class="section-heading"><h2 id="downloads-title">最近下载</h2><span>最新 20 条记录</span></div>
          <div v-if="!recentDownloads.length" class="workbench-empty">暂无下载记录</div>
          <table v-else class="workbench-table" aria-labelledby="downloads-title">
            <thead><tr><th scope="col">资产名称</th><th scope="col">下载用户</th><th scope="col">下载时间</th></tr></thead>
            <tbody>
              <tr v-for="(record, index) in recentDownloads" :key="`${record.assetId}-${record.downloadedAt}-${index}`">
                <td data-label="资产名称"><RouterLink :to="`/admin/assets/${record.assetId}`">{{ record.assetName }}</RouterLink></td>
                <td data-label="下载用户">{{ record.username }}</td>
                <td data-label="下载时间">{{ formatDate(record.downloadedAt) }}</td>
              </tr>
            </tbody>
          </table>
        </section>
      </div>
    </div>
  </MainLayout>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import { Library, CircleCheck, FilePenLine, Download, Users, UsersRound, ClipboardCheck, FolderKanban, UserRoundCog, RefreshCw, ChevronRight, CircleAlert } from '@lucide/vue'
import { statisticsApi, type OverviewStats, type PopularAsset, type ActiveUser, type DownloadRecord } from '../api/statistics'
import { teamApi } from '../api/team'
import { useAuthStore } from '../stores/auth'
import MainLayout from '../components/MainLayout.vue'

const auth = useAuthStore()
const loading = ref(false)
const error = ref('')
const updatedAt = ref('')
const overview = ref<OverviewStats | null>(null)
const teamCount = ref(0)
const popularAssets = ref<PopularAsset[]>([])
const activeUsers = ref<ActiveUser[]>([])
const recentDownloads = ref<DownloadRecord[]>([])
const actions = computed(() => [
  { to: '/admin/assets', label: '资产库', icon: Library, tone: 'blue' },
  { to: '/admin/projects', label: '项目管理', icon: FolderKanban, tone: 'cyan' },
  { to: '/admin/teams', label: '团队管理', icon: UsersRound, tone: 'green' },
  ...(auth.canApprove() ? [{ to: '/admin/approvals', label: '审批管理', icon: ClipboardCheck, tone: 'amber' }] : []),
  ...(auth.isAdmin() ? [{ to: '/admin/users', label: '用户管理', icon: UserRoundCog, tone: 'neutral' }] : [])
])
const metrics = computed(() => overview.value ? [
  { label: '总资产数（含归档）', value: overview.value.totalAssets, icon: Library, tone: 'tone-blue' },
  { label: '已发布资产', value: overview.value.publishedAssets, icon: CircleCheck, tone: 'tone-green' },
  { label: '草稿版本', value: overview.value.draftAssets, icon: FilePenLine, tone: 'tone-amber' },
  { label: '累计下载', value: overview.value.totalDownloads, icon: Download, tone: 'tone-cyan' }
] : [])

async function loadData() {
  if (loading.value) return
  loading.value = true
  error.value = ''
  try {
    const [overviewData, popularData, activeData, downloadsData, teams] = await Promise.all([
      statisticsApi.getOverview(),
      statisticsApi.getPopularAssets(),
      statisticsApi.getActiveUsers(),
      statisticsApi.getRecentDownloads(),
      teamApi.list()
    ])
    overview.value = overviewData
    popularAssets.value = popularData
    activeUsers.value = activeData
    recentDownloads.value = downloadsData
    teamCount.value = teams.length
    updatedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  } catch {
    error.value = overview.value ? '统计更新失败，当前显示上次成功加载的数据。' : '工作台数据加载失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

const formatNumber = (value: number) => value.toLocaleString('zh-CN')
const formatType = (type: string) => ({ RULE: '规则', SKILL: '技能', DOCUMENT: '文档' }[type] || type)
function formatDate(value: string | null | undefined) {
  const date = value ? new Date(value) : null
  if (!date || Number.isNaN(date.getTime())) return '暂无记录'
  return date.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false })
}

onMounted(loadData)
</script>

<style scoped>
.workbench { width: 100%; color: var(--color-text-primary); letter-spacing: 0; }
.workbench-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.workbench h1 { font-size: 20px; line-height: 28px; font-weight: 600; margin: 0; }
.workbench-refresh { display: flex; align-items: center; gap: 12px; }
.updated-time { color: var(--color-text-secondary); font-size: 12px; }
.refresh-button { display: grid; place-items: center; width: 32px; height: 32px; flex-shrink: 0; border: 1px solid var(--color-border); border-radius: 4px; background: white; color: var(--color-text-secondary); }
.refresh-button:hover { background: var(--color-bg-3); }
.refresh-button:disabled { cursor: wait; }
.workbench-actions { padding-bottom: 20px; border-bottom: 1px solid var(--color-border); margin-bottom: 20px; }
.actions-label { margin: 0 0 12px; font-size: 14px; font-weight: 600; line-height: 22px; color: var(--color-text-secondary); }
.workbench-action-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 12px; max-width: 1120px; }
.workbench-actions a { display: flex; align-items: center; gap: 8px; min-width: 0; height: 48px; padding: 0 12px; border: 1px solid var(--color-border); border-radius: 8px; color: var(--color-text-primary); background: white; text-decoration: none; font-size: 14px; transition: background 150ms ease, border-color 150ms ease; }
.workbench-actions a:hover { border-color: #97b8aa; background: #f0f8f4; }
.workbench-actions a:active { background: #e5f1eb; }
.action-icon { display: grid; place-items: center; flex-shrink: 0; width: 28px; height: 28px; border-radius: 4px; background: #edf3ff; color: #165dff; }
.action-cyan .action-icon { background: #e9f5f8; color: #087b92; }
.action-green .action-icon { background: #e7f5ef; color: #0a754a; }
.action-amber .action-icon { background: #fff4e0; color: #95600b; }
.action-neutral .action-icon { background: #f2f3f5; color: #4e5969; }
.action-name { flex: 1; white-space: nowrap; }
.action-arrow { flex-shrink: 0; color: var(--color-text-secondary); }
.overview-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; }
.metric-card { min-width: 0; padding: 16px 20px; background: white; border: 1px solid var(--color-border); border-radius: 8px; }
.metric-heading { display: flex; align-items: center; justify-content: space-between; gap: 8px; min-height: 24px; color: var(--color-text-secondary); font-size: 14px; }
.metric-heading svg { flex-shrink: 0; }
.metric-value { font-variant-numeric: tabular-nums; font-family: 'Barlow', -apple-system, BlinkMacSystemFont, 'Microsoft YaHei', sans-serif; font-size: 30px; line-height: 40px; font-weight: 700; overflow-wrap: anywhere; margin-top: 8px; }
.tone-blue { color: #165dff; }.tone-green { color: #0a754a; }.tone-amber { color: #95600b; }.tone-cyan { color: #087b92; }
.collaboration-metrics { display: flex; flex-wrap: wrap; gap: 12px 32px; padding: 16px 0 20px; border-bottom: 1px solid var(--color-border); color: var(--color-text-secondary); }
.collaboration-metrics > div { display: flex; align-items: center; gap: 8px; }
.collaboration-metrics .metric-value { margin: 0 0 0 4px; font-size: 20px; line-height: 28px; color: var(--color-text-primary); }
.collaboration-metrics .pending-count { color: #95600b; }
.workbench-section { margin-top: 24px; scroll-margin-top: 84px; }
.section-heading { display: flex; align-items: baseline; flex-wrap: wrap; gap: 8px 12px; margin-bottom: 12px; }
.section-heading h2 { font-size: 16px; line-height: 24px; font-weight: 600; margin: 0; }
.section-heading > span { font-size: 12px; color: var(--color-text-secondary); }
.workbench-table { table-layout: fixed; border-radius: 0; overflow: visible; }
.workbench-table th, .workbench-table td { padding: 12px 16px; overflow-wrap: anywhere; font-size: 14px; }
.workbench-table th:first-child { width: 50%; }
.popular-table th:first-child { width: 44%; }
.popular-table th:nth-child(2), .popular-table th:nth-child(3) { width: 16%; }
.workbench-table a { color: #086b50; text-decoration: none; }
.workbench-table a:hover { text-decoration: underline; text-underline-offset: 3px; }
.number-cell { font-variant-numeric: tabular-nums; }
.type-badge { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 12px; background: var(--color-bg-3); color: var(--color-text-secondary); }
.user-handle { display: block; font-size: 12px; color: var(--color-text-secondary); }
.workbench-empty { color: var(--color-text-secondary); text-align: center; padding: 24px 12px; border-block: 1px solid var(--color-border); }
.workbench-loading { padding: 12px 0; color: var(--color-text-secondary); font-size: 14px; }
.workbench-error { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; padding: 12px 16px; margin-bottom: 16px; background: var(--color-error-bg); color: #a32929; border-left: 3px solid var(--color-error); }
.workbench-error > span { flex: 1; min-width: 120px; }
.workbench-error button { display: inline-flex; align-items: center; justify-content: center; gap: 8px; min-height: 32px; padding: 4px 12px; border-radius: 4px; color: #a32929; background: white; border: 1px solid #d7a5a5; }
.spinning { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: 1100px) {
  .overview-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .workbench-action-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
@media (max-width: 680px) {
  .workbench-header { gap: 8px; }.workbench-refresh { gap: 6px; }
  .workbench-action-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
  .workbench-actions a { padding: 0 8px; gap: 6px; font-size: 13px; }
  .metric-card { padding: 12px; }.overview-grid { gap: 12px; }
  .metric-heading { align-items: flex-start; font-size: 12px; min-height: 40px; }.metric-heading svg { width: 16px; height: 16px; }
  .metric-value { font-size: 24px; line-height: 32px; }
  .collaboration-metrics { gap: 12px 20px; }
  .workbench-table, .workbench-table tbody, .workbench-table tr { display: block; width: 100%; }
  .workbench-table thead { display: none; }
  .workbench-table tr { padding: 12px 0; border-top: 1px solid var(--color-border); }
  .workbench-table td { display: grid; grid-template-columns: 92px minmax(0, 1fr); gap: 4px 8px; padding: 4px 12px; border: 0; height: auto; min-height: 28px; }
  .workbench-table td::before { content: attr(data-label); color: var(--color-text-secondary); font-size: 12px; }
  .workbench-table .user-handle { grid-column: 2; }
  .type-badge { justify-self: start; }
}
@media (max-width: 380px) { .workbench-action-grid { grid-template-columns: minmax(0, 1fr); } }
@media (prefers-reduced-motion: reduce) { .spinning { animation: none; }.workbench-actions a { transition: none; } }
</style>
