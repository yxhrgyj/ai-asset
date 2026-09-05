<template>
  <MainLayout>
    <div v-if="loading" class="loading" role="status">正在加载项目详情...</div>

    <div v-else-if="error" class="error-state" role="alert">
      <span>{{ error }}</span>
      <button class="btn-text" @click="loadPage">重试</button>
    </div>

    <div v-else-if="project" class="project-detail-page">
    <div class="page-header">
      <div>
        <div class="breadcrumb">
          <router-link to="/projects" class="breadcrumb-link">项目管理</router-link>
          <span class="breadcrumb-separator">/</span>
          <span>{{ project.name }}</span>
        </div>
        <h1 class="page-title">{{ project.name }}</h1>
        <p v-if="project.description" class="project-description">{{ project.description }}</p>
      </div>
    </div>

    <div class="tabs">
      <button
        :class="['tab', { active: activeTab === 'rules' }]"
        @click="activeTab = 'rules'"
      >
        合并后的规则
      </button>
      <button
        :class="['tab', { active: activeTab === 'manage' }]"
        @click="activeTab = 'manage'"
      >
        项目级规则管理
      </button>
    </div>

    <!-- 合并规则视图 -->
    <div v-if="activeTab === 'rules'" class="tab-content">
      <div class="section-header">
        <div>
          <p class="section-description">
            规则按优先级合并：组织级 → 技术栈级 → 项目级。同一 rule_key 由最高优先级覆盖。
          </p>
          <div v-if="mergedRules" class="merge-stats">
            <span class="stat-item">组织级: {{ mergedRules.orgCount }}</span>
            <span class="stat-item">技术栈级: {{ mergedRules.techStackCount }}</span>
            <span class="stat-item">项目级: {{ mergedRules.projectCount }}</span>
            <span class="stat-item total">合并后: {{ mergedRules.mergedCount }}</span>
          </div>
        </div>
        <button class="btn-primary" @click="downloadRules" :disabled="!mergedRules || mergedRules.mergedCount === 0">
          下载为 Markdown
        </button>
      </div>

      <div v-if="!mergedRules" class="loading">加载中...</div>
      <div v-else-if="mergedRules.mergedCount === 0" class="empty-state">
        暂无规则
      </div>
      <div v-else class="rules-list">
        <div v-for="rule in mergedRules.rules" :key="rule.ruleKey" class="rule-card">
          <div class="rule-header">
            <span :class="['rule-level', rule.level.toLowerCase()]">
              {{ rule.level === 'REQUIRED' ? '必须' : '建议' }}
            </span>
          </div>
          <h3 class="rule-title">{{ rule.title }}</h3>
          <div class="rule-body markdown-content">{{ rule.body }}</div>
          <div v-if="rule.pathGlobs.length > 0" class="rule-globs">
            <span class="globs-label">适用路径:</span>
            <code v-for="glob in rule.pathGlobs" :key="glob" class="glob-item">{{ glob }}</code>
          </div>
        </div>
      </div>
    </div>

    <!-- 项目级规则管理视图 -->
    <div v-if="activeTab === 'manage'" class="tab-content">
      <div class="section-header">
        <p class="section-description">
          为项目添加规则资产。这些规则优先级最高，会覆盖组织级与技术栈级的同名规则。
        </p>
        <button class="btn-primary" @click="showAddRuleDialog = true">
          添加规则
        </button>
      </div>

      <div v-if="projectRules.length === 0" class="empty-state">
        暂无项目级规则
      </div>
      <div v-else class="project-rules-list">
        <div v-for="rule in projectRules" :key="rule.id" class="project-rule-item">
          <div class="rule-info">
            <span class="rule-version-id">{{ rule.assetVersionId }}</span>
            <span class="rule-added-at">添加于 {{ formatDate(rule.createdAt) }}</span>
          </div>
          <button class="btn-text btn-sm" @click="removeRule(rule.assetVersionId)">移除</button>
        </div>
      </div>
    </div>

    <!-- 添加规则对话框 -->
    <div v-if="showAddRuleDialog" class="dialog-overlay" @click.self="showAddRuleDialog = false">
      <div class="dialog">
        <div class="dialog-header">
          <h2>添加规则资产</h2>
          <button class="btn-close" @click="showAddRuleDialog = false">×</button>
        </div>

        <div class="dialog-body">
          <div class="form-group">
            <label>选择规则资产版本</label>
            <select v-model="selectedVersionId" class="form-control">
              <option value="">请选择</option>
              <option v-for="option in ruleOptions" :key="option.versionId" :value="option.versionId">
                {{ option.assetName }} · v{{ option.versionNo }} · {{ formatScope(option.scope) }}
              </option>
            </select>
            <p v-if="ruleOptions.length === 0" class="form-hint">暂无可用的已发布规则版本</p>
          </div>
        </div>

        <div class="dialog-footer">
          <button class="btn-secondary" @click="showAddRuleDialog = false">取消</button>
          <button class="btn-primary" @click="handleAddRule" :disabled="!selectedVersionId || saving">
            {{ saving ? '添加中...' : '添加' }}
          </button>
        </div>
      </div>
    </div>
    </div>
  </MainLayout>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { projectsApi, type Project, type ProjectRule, type MergedRulesResult } from '../api/projects'
import { assetApi, type AssetScope } from '../api/asset'
import MainLayout from '../components/MainLayout.vue'
import { useDialog } from '../composables/useDialog'

const route = useRoute()
const projectId = route.params.id as string
const { alert, confirm } = useDialog()

interface RuleVersionOption {
  versionId: string
  assetName: string
  scope: AssetScope
  versionNo: number
}

const project = ref<Project | null>(null)
const mergedRules = ref<MergedRulesResult | null>(null)
const projectRules = ref<ProjectRule[]>([])
const ruleOptions = ref<RuleVersionOption[]>([])
const loading = ref(true)
const saving = ref(false)
const error = ref('')

const activeTab = ref<'rules' | 'manage'>('rules')
const showAddRuleDialog = ref(false)
const selectedVersionId = ref('')

onMounted(loadPage)

async function loadPage() {
  loading.value = true
  error.value = ''
  try {
    const [nextProject, nextMergedRules, nextProjectRules, nextRuleOptions] = await Promise.all([
      projectsApi.get(projectId),
      projectsApi.getMergedRules(projectId),
      projectsApi.listRules(projectId),
      loadRuleOptions()
    ])
    project.value = nextProject
    mergedRules.value = nextMergedRules
    projectRules.value = nextProjectRules
    ruleOptions.value = nextRuleOptions
  } catch (err) {
    error.value = getErrorMessage(err, '加载项目详情失败')
  } finally {
    loading.value = false
  }
}

async function loadRuleOptions(): Promise<RuleVersionOption[]> {
  const assets = await assetApi.list({ type: 'RULE', size: 100 })
  const optionGroups = await Promise.all(assets.items.map(async (asset) => {
    const detail = await assetApi.get(asset.id)
    return detail.versions
      .filter((version) => version.status === 'PUBLISHED')
      .map((version) => ({
        versionId: version.id,
        assetName: asset.name,
        scope: asset.scope,
        versionNo: version.versionNo
      }))
  }))
  return optionGroups.flat()
}

async function refreshRules() {
  const [nextProjectRules, nextMergedRules] = await Promise.all([
    projectsApi.listRules(projectId),
    projectsApi.getMergedRules(projectId)
  ])
  projectRules.value = nextProjectRules
  mergedRules.value = nextMergedRules
}

async function handleAddRule() {
  if (!selectedVersionId.value || saving.value) return

  saving.value = true
  try {
    await projectsApi.addRule(projectId, selectedVersionId.value)
    showAddRuleDialog.value = false
    selectedVersionId.value = ''
    await refreshRules()
    await alert({ message: '规则已添加', type: 'success' })
  } catch (err) {
    await alert({ message: getErrorMessage(err, '添加失败'), type: 'error' })
  } finally {
    saving.value = false
  }
}

async function removeRule(assetVersionId: string) {
  const confirmed = await confirm({
    title: '移除项目规则',
    message: '确定要移除该规则吗？',
    type: 'warning'
  })
  if (!confirmed) return

  try {
    await projectsApi.removeRule(projectId, assetVersionId)
    await refreshRules()
  } catch (err) {
    await alert({ message: getErrorMessage(err, '移除失败'), type: 'error' })
  }
}

function downloadRules() {
  if (!mergedRules.value || !project.value) return

  let markdown = `# ${project.value.name} - 合并规则\n\n`
  markdown += `> 合并统计：组织级 ${mergedRules.value.orgCount} 条，`
  markdown += `技术栈级 ${mergedRules.value.techStackCount} 条，`
  markdown += `项目级 ${mergedRules.value.projectCount} 条，`
  markdown += `合并后 ${mergedRules.value.mergedCount} 条\n\n`

  for (const rule of mergedRules.value.rules) {
    markdown += `## ${rule.title}\n\n`
    markdown += `- **级别**: ${rule.level === 'REQUIRED' ? '必须' : '建议'}\n`
    if (rule.pathGlobs.length > 0) {
      markdown += `- **适用路径**: ${rule.pathGlobs.map(g => `\`${g}\``).join(', ')}\n`
    }
    markdown += `\n${rule.body}\n\n`
  }

  const blob = new Blob([markdown], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${project.value.slug}-rules.md`
  a.click()
  URL.revokeObjectURL(url)
}

function formatScope(scope: AssetScope): string {
  const labels: Record<AssetScope, string> = {
    ORGANIZATION: '组织级',
    TECH_STACK: '技术栈级',
    PROJECT: '项目级'
  }
  return labels[scope]
}

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('zh-CN')
}

function getErrorMessage(err: unknown, fallback: string): string {
  return err instanceof Error && err.message ? err.message : fallback
}
</script>

<style scoped>
.project-detail-page {
  max-width: 1000px;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: var(--sp-8);
  font-size: 14px;
  color: var(--color-text-secondary);
  margin-bottom: var(--sp-8);
}

.breadcrumb-link {
  color: var(--color-primary);
  text-decoration: none;
}

.breadcrumb-link:hover {
  text-decoration: underline;
}

.breadcrumb-separator {
  color: var(--color-text-tertiary);
}

.page-title {
  font-size: 28px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0 0 var(--sp-8) 0;
}

.project-description {
  color: var(--color-text-secondary);
  line-height: 1.5;
  margin: 0;
}

.tabs {
  display: flex;
  gap: var(--sp-4);
  border-bottom: 1px solid var(--color-border);
  margin: var(--sp-24) 0;
}

.tab {
  padding: var(--sp-12) var(--sp-16);
  background: none;
  border: none;
  color: var(--color-text-secondary);
  font-size: 15px;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: all 0.2s;
}

.tab:hover {
  color: var(--color-text-primary);
}

.tab.active {
  color: var(--color-primary);
  border-bottom-color: var(--color-primary);
}

.tab-content {
  padding: var(--sp-20) 0;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: var(--sp-20);
}

.section-description {
  color: var(--color-text-secondary);
  font-size: 14px;
  line-height: 1.5;
  margin: 0 0 var(--sp-12) 0;
}

.merge-stats {
  display: flex;
  gap: var(--sp-16);
  font-size: 13px;
}

.stat-item {
  color: var(--color-text-secondary);
}

.stat-item.total {
  color: var(--color-primary);
  font-weight: 600;
}

.rules-list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-16);
}

.rule-card {
  background: var(--color-bg-1);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-12);
  padding: var(--sp-20);
}

.rule-header {
  display: flex;
  gap: var(--sp-12);
  margin-bottom: var(--sp-12);
}

.rule-level {
  display: inline-block;
  padding: var(--sp-4) var(--sp-12);
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.rule-level.required {
  background: #FEE2E2;
  color: #991B1B;
}

.rule-level.recommended {
  background: #DBEAFE;
  color: #1E40AF;
}

.rule-source {
  color: var(--color-text-tertiary);
  font-size: 12px;
}

.rule-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0 0 var(--sp-12) 0;
}

.markdown-content {
  font-size: 14px;
  line-height: 1.6;
  color: var(--color-text-secondary);
}

.markdown-content :deep(p) {
  margin: var(--sp-8) 0;
}

.markdown-content :deep(code) {
  background: var(--color-bg-2);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Monaco', 'Consolas', monospace;
  font-size: 13px;
}

.rule-globs {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-8);
  align-items: center;
  margin-top: var(--sp-12);
  padding-top: var(--sp-12);
  border-top: 1px solid var(--color-border);
}

.globs-label {
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.glob-item {
  font-size: 12px;
  font-family: 'Monaco', 'Consolas', monospace;
  background: var(--color-bg-2);
  padding: var(--sp-4) var(--sp-8);
  border-radius: var(--radius-8);
}

.project-rules-list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-12);
}

.project-rule-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--sp-16);
  background: var(--color-bg-1);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-8);
}

.rule-info {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
}

.rule-version-id {
  font-family: 'Monaco', 'Consolas', monospace;
  font-size: 13px;
  color: var(--color-text-primary);
}

.rule-added-at {
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.loading {
  text-align: center;
  padding: var(--sp-48);
  color: var(--color-text-secondary);
}
</style>
