import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import test from 'node:test'

const source = name => fs.readFile(new URL(`../src/${name}`, import.meta.url), 'utf8')

test('workbench merges real statistics, team count and role-aware shortcuts', async () => {
  const view = await source('views/Statistics.vue')
  assert.match(view, />工作台<\/h1>/)
  for (const api of ['getOverview', 'getPopularAssets', 'getActiveUsers', 'getRecentDownloads']) {
    assert.match(view, new RegExp(`statisticsApi\\.${api}\\(`))
  }
  assert.match(view, /teamApi\.list\(/)
  assert.doesNotMatch(view, /canApprove|pendingApprovals|审批管理|待审批/)
  assert.match(view, /auth\.isAdmin\(/)
  assert.match(view, /loadData/)
  assert.match(view, /重试/)
  assert.match(view, /刷新统计/)
  assert.doesNotMatch(view, /项目绑定|PROMPT|WORKFLOW/)
  assert.doesNotMatch(view, /`\/assets\//)
})

test('old Home entry reuses workbench instead of keeping duplicate metrics', async () => {
  const home = await source('views/Home.vue')
  assert.match(home, /import Statistics from '.\/Statistics.vue'/)
  assert.match(home, /<Statistics\s*\/>/)
  assert.doesNotMatch(home, /项目绑定|assetApi|teamApi/)
})

test('statistics URLs redirect to the single workbench', async () => {
  const router = await source('router/index.ts')
  assert.match(router, /name: 'admin-home', component: Statistics/)
  assert.match(router, /name: 'admin-statistics', redirect: to => \(\{ path: '\/admin', query: to.query, hash: to.hash \}\)/)
  const layout = await source('components/AdminLayout.vue')
  assert.doesNotMatch(layout, /label: '平台统计'/)
  assert.match(layout, /from '@lucide\/vue'/)
})

test('team identity uses the shared icon library and labelled controls', async () => {
  const teams = await source('views/Teams.vue')
  assert.match(teams, /from '@lucide\/vue'/)
  assert.match(teams, /class="team-icon"[^>]*><UsersRound/)
  assert.match(teams, /aria-label="编辑团队"/)
  assert.match(teams, /aria-label="删除团队"/)
  assert.doesNotMatch(teams, /\p{Extended_Pictographic}/u)
})

test('workbench shortcuts use aligned controls with in-app navigation indicators', async () => {
  const view = await source('views/Statistics.vue')
  assert.match(view, /class="workbench-action-grid"/)
  assert.match(view, /class="action-icon"/)
  assert.match(view, /<ChevronRight/)
  assert.doesNotMatch(view, /ArrowUpRight/)
})

test('Escape only closes an open small-screen navigation drawer', async () => {
  const layout = await source('components/AdminLayout.vue')
  assert.match(layout, /@keydown\.esc="handleEscape"/)
  assert.match(layout, /if \(!isSmallScreen\.value \|\| collapsed\.value\) return/)
})
