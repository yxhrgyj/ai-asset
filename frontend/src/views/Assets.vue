<template>
  <MainLayout>
    <div class="assets-page">
      <div class="page-header">
        <div>
          <h1 class="page-title">资产库</h1>
          <p class="page-subtitle">管理 AI 编码规则、技能包和规范文档</p>
        </div>
        <button v-if="auth.canAuthor()" @click="showCreateDialog = true" class="btn-primary">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"/>
            <line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
          创建资产
        </button>
      </div>

      <!-- 搜索与筛选 -->
      <div class="search-bar">
        <div class="search-input-wrapper">
          <svg class="search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"/>
            <path d="m21 21-4.35-4.35"/>
          </svg>
          <input
            v-model="searchQuery"
            type="text"
            class="search-input"
            placeholder="搜索资产名称、内容、标签..."
            @keyup.enter="loadAssets"
          />
        </div>
        <div class="filters">
          <select v-model="filterType" @change="loadAssets" class="filter-select">
            <option value="">全部类型</option>
            <option value="RULE">编码规则</option>
            <option value="SKILL">技能包</option>
            <option value="DOCUMENT">规范文档</option>
          </select>
          <select v-model="filterScope" @change="loadAssets" class="filter-select">
            <option value="">全部范围</option>
            <option value="ORGANIZATION">组织级</option>
            <option value="TECH_STACK">技术栈</option>
            <option value="PROJECT">项目级</option>
          </select>
          <button @click="loadAssets" class="btn-search">搜索</button>
        </div>
      </div>

      <div v-if="loading" class="loading">加载中...</div>

      <div v-else-if="assets.length === 0" class="empty-state">
        <div class="empty-icon">📦</div>
        <p class="empty-text">{{ searchQuery ? '未找到匹配的资产' : '暂无资产' }}</p>
        <button v-if="auth.canAuthor() && !searchQuery" @click="showCreateDialog = true" class="btn-primary">
          创建第一个资产
        </button>
      </div>

      <div v-else class="assets-list">
        <div v-for="asset in assets" :key="asset.id" class="asset-card" @click="goToDetail(asset.id)">
          <div class="asset-card-header">
            <div class="asset-type-icon" :class="getTypeClass(asset.type)">
              {{ getTypeIcon(asset.type) }}
            </div>
            <div class="asset-card-main">
              <h3 class="asset-name">{{ asset.name }}</h3>
              <p v-if="asset.summary" class="asset-summary">{{ asset.summary }}</p>
              <div class="asset-meta">
                <span class="tag" :class="`tag-${asset.type.toLowerCase()}`">
                  {{ getTypeLabel(asset.type) }}
                </span>
                <span class="tag tag-scope">
                  {{ getScopeLabel(asset.scope) }}
                </span>
                <span v-if="asset.category" class="meta-text">{{ asset.category }}</span>
                <span v-if="asset.publishedVersion" class="meta-text">v{{ asset.publishedVersion }}</span>
                <span class="meta-text download-count">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                    <polyline points="7 10 12 15 17 10"/>
                    <line x1="12" y1="15" x2="12" y2="3"/>
                  </svg>
                  {{ asset.downloadCount }}
                </span>
              </div>
            </div>
          </div>
          <div v-if="asset.tags && asset.tags.length > 0" class="asset-tags">
            <span v-for="tag in asset.tags.slice(0, 5)" :key="tag" class="tag-small">
              {{ tag }}
            </span>
            <span v-if="asset.tags.length > 5" class="tag-small">+{{ asset.tags.length - 5 }}</span>
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <div v-if="total > pageSize" class="pagination">
        <button
          @click="changePage(currentPage - 1)"
          :disabled="currentPage === 0"
          class="btn-page"
        >
          上一页
        </button>
        <span class="page-info">第 {{ currentPage + 1 }} 页，共 {{ totalPages }} 页</span>
        <button
          @click="changePage(currentPage + 1)"
          :disabled="currentPage >= totalPages - 1"
          class="btn-page"
        >
          下一页
        </button>
      </div>

      <!-- 创建对话框 -->
      <div v-if="showCreateDialog" class="dialog-overlay" @click="closeDialog">
        <div class="dialog dialog-large" @click.stop>
          <div class="dialog-header">
            <h2>创建资产</h2>
            <button @click="closeDialog" class="btn-close">×</button>
          </div>
          <form @submit.prevent="handleCreate" class="dialog-body">
            <!-- 第一步：基本信息 -->
            <div v-if="createStep === 1">
              <h3 class="step-title">第一步：基本信息</h3>

              <div class="form-group">
                <label class="form-label">资产类型</label>
                <CustomSelect
                  v-model="formData.type"
                  :options="typeOptions"
                />
              </div>

              <div class="form-group">
                <label class="form-label">资产名称</label>
                <input
                  v-model="formData.name"
                  type="text"
                  class="form-input"
                  placeholder="请输入资产名称"
                  required
                />
              </div>

              <div class="form-group">
                <label class="form-label">唯一标识（slug）</label>
                <input
                  v-model="formData.slug"
                  type="text"
                  class="form-input"
                  placeholder="例如：vue3-component-naming"
                  pattern="[a-z0-9-]+"
                  required
                />
                <p class="form-hint">只能包含小写字母、数字和连字符</p>
              </div>

              <div class="form-group">
                <label class="form-label">简介</label>
                <textarea
                  v-model="formData.summary"
                  class="form-textarea"
                  placeholder="请输入资产简介"
                  rows="3"
                />
              </div>

              <div class="form-group">
                <label class="form-label">分类</label>
                <input
                  v-model="formData.category"
                  type="text"
                  class="form-input"
                  placeholder="例如：前端规范、后端规范"
                />
              </div>

              <div class="form-group">
                <label class="form-label">标签</label>
                <input
                  v-model="tagsInput"
                  type="text"
                  class="form-input"
                  placeholder="用逗号分隔，例如：Vue3, TypeScript, 组件"
                />
              </div>

              <div class="form-group">
                <label class="form-label">适用范围</label>
                <CustomSelect
                  v-model="formData.scope"
                  :options="scopeOptions"
                />
              </div>

              <div v-if="formData.scope === 'TECH_STACK'" class="form-group">
                <label class="form-label">技术栈标识</label>
                <input
                  v-model="formData.techStack"
                  type="text"
                  class="form-input"
                  placeholder="例如：vue3, spring-boot"
                  :required="formData.scope === 'TECH_STACK'"
                />
              </div>

              <div class="form-group">
                <label class="form-label">所属团队</label>
                <CustomSelect
                  v-model="formData.teamId"
                  :options="teamOptions"
                />
              </div>
            </div>

            <!-- 第二步：内容编辑 -->
            <div v-if="createStep === 2" class="content-editor-container">
              <h3 class="step-title">第二步：编写内容（可选）</h3>

              <div class="editor-layout">
                <div class="editor-panel">
                  <label class="form-label">Markdown 内容</label>
                  <textarea
                    v-model="formData.body"
                    class="editor-textarea"
                    placeholder="使用 Markdown 格式编写内容...&#10;&#10;您也可以跳过此步骤，创建后再编辑"
                  />
                </div>

                <div class="preview-panel">
                  <label class="form-label">预览</label>
                  <div class="markdown-preview" v-html="renderMarkdown(formData.body)"></div>
                </div>
              </div>

              <div class="form-group">
                <label class="form-label">变更说明</label>
                <input
                  v-model="formData.changelog"
                  type="text"
                  class="form-input"
                  placeholder="简要说明（可选）"
                />
              </div>
            </div>

            <!-- 第三步：上传附件 -->
            <div v-if="createStep === 3">
              <h3 class="step-title">第三步：上传附件（可选）</h3>

              <div class="form-group">
                <label class="form-label">选择文件</label>
                <input
                  type="file"
                  ref="createFileInput"
                  @change="handleCreateFileSelect"
                  class="file-input"
                  multiple
                />
                <div class="file-input-hint">
                  支持的文件类型：.md, .py, .js, .sh, .json, .yaml, .txt 等文本文件及脚本
                </div>
              </div>

              <div v-if="createFiles.length > 0" class="selected-files">
                <div v-for="(item, index) in createFiles" :key="index" class="selected-file-item">
                  <div class="selected-file-info">
                    <div class="selected-file-name">{{ item.file.name }}</div>
                    <div class="selected-file-size">{{ formatFileSize(item.file.size) }}</div>
                  </div>
                  <div class="selected-file-path">
                    <label class="form-label-sm">相对路径（可选）</label>
                    <input
                      v-model="item.relativePath"
                      type="text"
                      class="form-input form-input-sm"
                      placeholder="例如: scripts/check.py"
                    />
                  </div>
                  <button type="button" @click="removeCreateFile(index)" class="btn-icon-sm">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <line x1="18" y1="6" x2="6" y2="18"/>
                      <line x1="6" y1="6" x2="18" y2="18"/>
                    </svg>
                  </button>
                </div>
              </div>
            </div>

            <div v-if="error" class="error-message">{{ error }}</div>
            <div v-if="creatingProgress" class="progress-message">{{ creatingProgress }}</div>

            <div class="dialog-footer">
              <button v-if="createStep > 1" type="button" @click="createStep--" class="btn-secondary" :disabled="creating">上一步</button>
              <button type="button" @click="closeDialog" class="btn-secondary" :disabled="creating">取消</button>
              <button v-if="createStep < 3" type="button" @click="createStep++" class="btn-primary" :disabled="creating">下一步</button>
              <button v-if="createStep === 3" type="submit" class="btn-primary" :disabled="creating">
                {{ creating ? (creatingProgress || '创建中...') : '完成创建' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  </MainLayout>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { assetApi, type Asset, type AssetType, type AssetScope } from '../api/asset'
import { teamApi, type Team } from '../api/team'
import MainLayout from '../components/MainLayout.vue'
import CustomSelect from '../components/CustomSelect.vue'
import { marked } from 'marked'

const router = useRouter()
const auth = useAuthStore()

const assets = ref<Asset[]>([])
const teams = ref<Team[]>([])
const loading = ref(true)
const error = ref('')

const searchQuery = ref('')
const filterType = ref<AssetType | ''>('')
const filterScope = ref<AssetScope | ''>('')
const currentPage = ref(0)
const pageSize = ref(20)
const total = ref(0)

const showCreateDialog = ref(false)
const createStep = ref(1)
const creating = ref(false)
const creatingProgress = ref('')
const formData = ref({
  type: 'RULE' as AssetType,
  name: '',
  slug: '',
  summary: '',
  category: '',
  scope: 'ORGANIZATION' as AssetScope,
  techStack: '',
  teamId: '',
  body: '',
  changelog: ''
})
const tagsInput = ref('')
const createFiles = ref<Array<{ file: File, relativePath: string }>>([])
const createFileInput = ref<HTMLInputElement | null>(null)

// 下拉框选项
const typeOptions = [
  { label: '编码规则', value: 'RULE' },
  { label: '技能包', value: 'SKILL' },
  { label: '规范文档', value: 'DOCUMENT' }
]

const scopeOptions = [
  { label: '组织级', value: 'ORGANIZATION' },
  { label: '技术栈', value: 'TECH_STACK' },
  { label: '项目级', value: 'PROJECT' }
]

const teamOptions = computed(() => [
  { label: '无团队', value: '' },
  ...teams.value.map(team => ({ label: team.name, value: team.id }))
])

const totalPages = computed(() => Math.ceil(total.value / pageSize.value))

onMounted(async () => {
  await Promise.all([loadAssets(), loadTeams()])
})

const loadAssets = async () => {
  loading.value = true
  try {
    const response = await assetApi.list({
      q: searchQuery.value || undefined,
      type: filterType.value || undefined,
      scope: filterScope.value || undefined,
      page: currentPage.value,
      size: pageSize.value
    })
    assets.value = response.items
    total.value = response.total
  } catch (err: any) {
    error.value = err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

const loadTeams = async () => {
  try {
    teams.value = await teamApi.list()
  } catch (err) {
    console.error('Failed to load teams:', err)
  }
}

const changePage = (page: number) => {
  currentPage.value = page
  loadAssets()
}

const goToDetail = (id: string) => {
  router.push(`/assets/${id}`)
}

const handleCreate = async () => {
  error.value = ''
  creating.value = true

  try {
    const tags = tagsInput.value
      .split(',')
      .map(t => t.trim())
      .filter(t => t.length > 0)

    const data = {
      type: formData.value.type,
      name: formData.value.name,
      slug: formData.value.slug,
      summary: formData.value.summary || undefined,
      category: formData.value.category || undefined,
      tags: tags.length > 0 ? tags : undefined,
      scope: formData.value.scope,
      techStack: formData.value.scope === 'TECH_STACK' ? formData.value.techStack : undefined,
      teamId: formData.value.teamId || undefined
    }

    const created = await assetApi.create(data)

    // 如果有内容，保存草稿
    if (formData.value.body && formData.value.body.trim()) {
      creatingProgress.value = '正在保存内容...'
      await assetApi.saveDraft(created.id, {
        body: formData.value.body,
        changelog: formData.value.changelog || undefined
      })
    }

    // 如果有附件，上传附件
    if (createFiles.value.length > 0) {
      for (let i = 0; i < createFiles.value.length; i++) {
        const item = createFiles.value[i]
        creatingProgress.value = `正在上传附件 ${i + 1}/${createFiles.value.length}...`
        await assetApi.uploadFile(
          created.id,
          item.file,
          item.relativePath !== item.file.name ? item.relativePath : undefined
        )
      }
    }

    router.push(`/assets/${created.id}`)
  } catch (err: any) {
    error.value = err.message || '创建失败'
  } finally {
    creating.value = false
    creatingProgress.value = ''
  }
}

const handleCreateFileSelect = (event: Event) => {
  const input = event.target as HTMLInputElement
  if (!input.files) return

  const files = Array.from(input.files)
  createFiles.value = files.map(file => ({
    file,
    relativePath: file.name
  }))
}

const removeCreateFile = (index: number) => {
  createFiles.value.splice(index, 1)
}

const formatFileSize = (bytes: number) => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

const closeDialog = () => {
  showCreateDialog.value = false
  createStep.value = 1
  formData.value = {
    type: 'RULE',
    name: '',
    slug: '',
    summary: '',
    category: '',
    scope: 'ORGANIZATION',
    techStack: '',
    teamId: '',
    body: '',
    changelog: ''
  }
  tagsInput.value = ''
  createFiles.value = []
  error.value = ''
  if (createFileInput.value) {
    createFileInput.value.value = ''
  }
}

const getTypeIcon = (type: AssetType) => {
  const icons: Record<AssetType, string> = {
    RULE: '📋',
    SKILL: '🎯',
    DOCUMENT: '📄'
  }
  return icons[type]
}

const getTypeLabel = (type: AssetType) => {
  const labels: Record<AssetType, string> = {
    RULE: '编码规则',
    SKILL: '技能包',
    DOCUMENT: '规范文档'
  }
  return labels[type]
}

const getTypeClass = (type: AssetType) => {
  return `type-${type.toLowerCase()}`
}

const getScopeLabel = (scope: AssetScope) => {
  const labels: Record<AssetScope, string> = {
    ORGANIZATION: '组织级',
    TECH_STACK: '技术栈',
    PROJECT: '项目级'
  }
  return labels[scope]
}

const renderMarkdown = (text: string) => {
  if (!text) return '<p class="empty-preview">暂无内容</p>'
  return marked(text)
}
</script>

<style scoped>
.assets-page {
  max-width: 1400px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: var(--sp-24);
}

.page-title {
  font-size: 32px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: var(--sp-4);
}

.page-subtitle {
  font-size: 14px;
  color: var(--color-text-secondary);
}

.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-8);
  height: 40px;
  padding: 0 var(--sp-24);
  background: var(--color-primary);
  color: white;
  border: none;
  border-radius: var(--radius-8);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-primary svg {
  width: 16px;
  height: 16px;
}

.btn-primary:hover {
  background: var(--color-primary-dark);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(27, 170, 127, 0.3);
}

/* 搜索栏 */
.search-bar {
  background: var(--color-bg-1);
  border-radius: var(--radius-12);
  padding: var(--sp-20);
  margin-bottom: var(--sp-20);
  box-shadow: var(--shadow-card);
}

.search-input-wrapper {
  position: relative;
  margin-bottom: var(--sp-16);
}

.search-icon {
  position: absolute;
  left: var(--sp-16);
  top: 50%;
  transform: translateY(-50%);
  width: 20px;
  height: 20px;
  color: var(--color-text-tertiary);
  pointer-events: none;
}

.search-input {
  width: 100%;
  height: 48px;
  padding: 0 var(--sp-16) 0 48px;
  font-size: 14px;
  color: var(--color-text-primary);
  background: var(--color-bg-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-8);
  outline: none;
  transition: all 0.2s;
}

.search-input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(27, 170, 127, 0.1);
}

.filters {
  display: flex;
  gap: var(--sp-12);
  align-items: center;
}

.filter-select {
  height: 40px;
  min-width: 140px;
  padding: 0 var(--sp-16);
  padding-right: var(--sp-32);
  font-size: 14px;
  color: var(--color-text-primary);
  background: var(--color-bg-1);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-8);
  outline: none;
  cursor: pointer;
  transition: all 0.2s;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%23666' d='M6 9L1 4h10z'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 12px center;
}

.filter-select:hover {
  border-color: var(--color-primary);
}

.filter-select:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(27, 170, 127, 0.1);
}

.btn-search {
  height: 40px;
  padding: 0 var(--sp-24);
  background: var(--color-primary);
  color: white;
  border: none;
  border-radius: var(--radius-8);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  margin-left: auto;
}

.btn-search:hover {
  background: var(--color-primary-dark);
}

.loading {
  text-align: center;
  padding: var(--sp-24);
  color: var(--color-text-secondary);
}

.empty-state {
  text-align: center;
  padding: var(--sp-24) var(--sp-24) var(--sp-24);
}

.empty-icon {
  font-size: 64px;
  margin-bottom: var(--sp-16);
}

.empty-text {
  font-size: 16px;
  color: var(--color-text-secondary);
  margin-bottom: var(--sp-20);
}

/* 资产列表 */
.assets-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: var(--sp-16);
}

.asset-card {
  background: var(--color-bg-1);
  border-radius: var(--radius-12);
  padding: var(--sp-20);
  box-shadow: var(--shadow-card);
  transition: all 0.2s;
  cursor: pointer;
}

.asset-card:hover {
  box-shadow: var(--shadow-hover);
  transform: translateY(-2px);
}

.asset-card-header {
  display: flex;
  gap: var(--sp-16);
  margin-bottom: var(--sp-16);
}

.asset-type-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-12);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  flex-shrink: 0;
}

.type-rule {
  background: rgba(22, 93, 255, 0.1);
}

.type-skill {
  background: rgba(27, 170, 127, 0.1);
}

.type-document {
  background: rgba(255, 179, 60, 0.1);
}

.asset-card-main {
  flex: 1;
  min-width: 0;
}

.asset-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: var(--sp-8);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.asset-summary {
  font-size: 14px;
  color: var(--color-text-secondary);
  margin-bottom: var(--sp-12);
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  line-height: 1.5;
}

.asset-meta {
  display: flex;
  gap: var(--sp-8);
  flex-wrap: wrap;
  align-items: center;
}

.tag {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: var(--sp-4) var(--sp-8);
  border-radius: var(--radius-4);
  font-size: 12px;
  font-weight: 500;
}

.tag-rule {
  background: rgba(22, 93, 255, 0.1);
  color: var(--color-line-3);
}

.tag-skill {
  background: var(--color-primary-lighter);
  color: var(--color-primary-darker);
}

.tag-document {
  background: var(--color-warning-bg);
  color: var(--color-warning);
}

.tag-scope {
  background: var(--color-bg-3);
  color: var(--color-text-secondary);
}

.meta-text {
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.download-count {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.download-count svg {
  width: 14px;
  height: 14px;
}

.asset-tags {
  display: flex;
  gap: var(--sp-8);
  flex-wrap: wrap;
}

.tag-small {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 var(--sp-8);
  background: var(--color-bg-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-4);
  font-size: 12px;
  color: var(--color-text-secondary);
}

/* 分页 */
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--sp-16);
  margin-top: var(--sp-24);
  padding: var(--sp-20);
}

.btn-page {
  height: 36px;
  padding: 0 var(--sp-20);
  background: var(--color-bg-1);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-8);
  font-size: 14px;
  color: var(--color-text-primary);
  cursor: pointer;
  transition: all 0.2s;
}

.btn-page:hover:not(:disabled) {
  background: var(--color-bg-2);
  border-color: var(--color-primary);
}

.btn-page:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-info {
  font-size: 14px;
  color: var(--color-text-secondary);
}

/* 对话框 */
.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  z-index: 2000;
  padding: var(--sp-24);
  padding-top: 5vh;
  overflow-y: auto;
}

.dialog {
  background: var(--color-bg-1);
  border-radius: var(--radius-16);
  width: 100%;
  max-width: 600px;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.2);
  max-height: 85vh;
  display: flex;
  flex-direction: column;
  overflow: visible;
}

.dialog-large {
  max-width: 900px;
}

.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--sp-20) var(--sp-24);
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}

.dialog-header h2 {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.btn-close {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  border-radius: var(--radius-8);
  font-size: 24px;
  color: var(--color-text-tertiary);
  cursor: pointer;
  transition: all 0.2s;
}

.btn-close:hover {
  background: var(--color-bg-2);
  color: var(--color-text-primary);
}

.dialog-body {
  padding: var(--sp-24);
  overflow-y: auto;
  flex: 1;
  min-height: 0;
}

.step-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: var(--sp-20);
  padding-bottom: var(--sp-12);
  border-bottom: 2px solid var(--color-primary);
}

.form-group {
  margin-bottom: var(--sp-24);
  position: relative;
  z-index: 1;
}

.form-label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-primary);
  margin-bottom: var(--sp-8);
}

.form-input, .form-textarea {
  width: 100%;
  padding: var(--sp-12) var(--sp-16);
  font-size: 14px;
  color: var(--color-text-primary);
  background: var(--color-bg-1);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-8);
  outline: none;
  transition: all 0.2s;
}

.form-input {
  height: 40px;
}

.form-textarea {
  resize: vertical;
  font-family: inherit;
}

.form-input:focus, .form-textarea:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(27, 170, 127, 0.1);
}

select.form-input {
  cursor: pointer;
  min-width: 140px;
  padding-right: var(--sp-32);
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%23666' d='M6 9L1 4h10z'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 12px center;
}

select.form-input:hover {
  border-color: var(--color-primary);
}

.form-hint {
  margin-top: var(--sp-4);
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.error-message {
  padding: var(--sp-12) var(--sp-16);
  background: var(--color-error-bg);
  color: var(--color-error);
  border-radius: var(--radius-8);
  font-size: 14px;
  border-left: 3px solid var(--color-error);
  margin-bottom: var(--sp-20);
}

.progress-message {
  padding: var(--sp-12) var(--sp-16);
  background: var(--color-primary-bg);
  color: var(--color-primary);
  border-radius: var(--radius-8);
  font-size: 14px;
  border-left: 3px solid var(--color-primary);
  margin-bottom: var(--sp-20);
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

.dialog-footer {
  display: flex;
  gap: var(--sp-12);
  justify-content: flex-end;
  padding-top: var(--sp-20);
  margin-top: var(--sp-24);
  border-top: 1px solid var(--color-border);
  flex-shrink: 0;
}

.btn-secondary {
  height: 40px;
  padding: 0 var(--sp-24);
  background: transparent;
  color: var(--color-text-primary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-8);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-secondary:hover {
  background: var(--color-bg-2);
  border-color: var(--color-primary);
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.editor-textarea {
  font-family: 'Consolas', 'Monaco', monospace;
  line-height: 1.6;
  resize: vertical;
}

/* 文件上传样式 */
.file-input {
  width: 100%;
  padding: var(--sp-12);
  font-size: 14px;
  color: var(--color-text-primary);
  background: var(--color-bg-2);
  border: 2px dashed var(--color-border);
  border-radius: var(--radius-8);
  cursor: pointer;
  transition: all 0.2s;
}

.file-input:hover {
  border-color: var(--color-primary);
}

.file-input-hint {
  margin-top: var(--sp-8);
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.selected-files {
  display: flex;
  flex-direction: column;
  gap: var(--sp-12);
  margin-top: var(--sp-16);
}

.selected-file-item {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-12);
  padding: var(--sp-12);
  background: var(--color-bg-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-8);
}

.selected-file-info {
  min-width: 180px;
}

.selected-file-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-primary);
  margin-bottom: var(--sp-4);
  word-break: break-all;
}

.selected-file-size {
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.selected-file-path {
  flex: 1;
  min-width: 0;
}

.form-label-sm {
  display: block;
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-secondary);
  margin-bottom: var(--sp-4);
}

.form-input-sm {
  width: 100%;
  height: 32px;
  padding: var(--sp-8) var(--sp-12);
  font-size: 13px;
  color: var(--color-text-primary);
  background: var(--color-bg-1);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-8);
  outline: none;
  transition: all 0.2s;
}

.form-input-sm:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(27, 170, 127, 0.1);
}

.btn-icon-sm {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  border-radius: var(--radius-4);
  color: var(--color-text-tertiary);
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
  margin-top: 20px;
}

.btn-icon-sm:hover {
  background: var(--color-error-bg);
  color: var(--color-error);
}

.btn-icon-sm svg {
  width: 14px;
  height: 14px;
}

/* 内容编辑器布局 */
.content-editor-container {
  display: flex;
  flex-direction: column;
  gap: var(--sp-20);
}

.editor-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sp-16);
  margin-bottom: var(--sp-20);
}

.editor-panel,
.preview-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.editor-textarea {
  flex: 1;
  min-height: 400px;
  padding: var(--sp-16);
  font-size: 14px;
  line-height: 1.6;
  color: var(--color-text-primary);
  background: var(--color-bg-1);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-8);
  outline: none;
  transition: all 0.2s;
  resize: vertical;
  font-family: 'Consolas', 'Monaco', monospace;
}

.editor-textarea:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(27, 170, 127, 0.1);
}

.markdown-preview {
  flex: 1;
  min-height: 400px;
  padding: var(--sp-16);
  background: var(--color-bg-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-8);
  overflow-y: auto;
  font-size: 14px;
  line-height: 1.6;
  color: var(--color-text-primary);
}

.markdown-preview :deep(h1) {
  font-size: 24px;
  font-weight: 600;
  margin: var(--sp-20) 0 var(--sp-12);
  color: var(--color-text-primary);
  border-bottom: 2px solid var(--color-border);
  padding-bottom: var(--sp-8);
}

.markdown-preview :deep(h2) {
  font-size: 20px;
  font-weight: 600;
  margin: var(--sp-16) 0 var(--sp-10);
  color: var(--color-text-primary);
}

.markdown-preview :deep(h3) {
  font-size: 16px;
  font-weight: 600;
  margin: var(--sp-12) 0 var(--sp-8);
  color: var(--color-text-primary);
}

.markdown-preview :deep(p) {
  margin: var(--sp-12) 0;
}

.markdown-preview :deep(code) {
  padding: 2px 6px;
  background: var(--color-bg-3);
  border-radius: var(--radius-4);
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
}

.markdown-preview :deep(pre) {
  padding: var(--sp-12);
  background: var(--color-bg-3);
  border-radius: var(--radius-8);
  overflow-x: auto;
  margin: var(--sp-12) 0;
}

.markdown-preview :deep(pre code) {
  padding: 0;
  background: none;
}

.markdown-preview :deep(ul),
.markdown-preview :deep(ol) {
  margin: var(--sp-12) 0;
  padding-left: var(--sp-24);
}

.markdown-preview :deep(li) {
  margin: var(--sp-4) 0;
}

.markdown-preview .empty-preview {
  color: var(--color-text-tertiary);
  font-style: italic;
}
</style>
