<template>
  <MainLayout>
    <div v-if="loading" class="loading">加载中...</div>

    <div v-else-if="asset" class="asset-detail">
      <!-- 顶部操作栏 -->
      <div class="page-header">
        <button @click="goBack" class="btn-back">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="19" y1="12" x2="5" y2="12"/>
            <polyline points="12 19 5 12 12 5"/>
          </svg>
          返回
        </button>
        <div class="header-actions" v-if="detail?.canEdit">
          <button v-if="!isDraft" @click="createNewDraft" class="btn-secondary">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19"/>
              <line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
            创建新版本
          </button>
          <button v-if="isDraft" @click="showEditor = true" class="btn-primary">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
              <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
            </svg>
            编辑内容
          </button>
          <button v-if="isDraft" @click="submitForApproval" class="btn-primary">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"/>
            </svg>
            提交审批
          </button>
          <button v-if="isPending" @click="withdrawApproval" class="btn-secondary">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M3 12h18M12 5l7 7-7 7"/>
            </svg>
            撤回审批
          </button>
        </div>
      </div>

      <!-- 资产信息 -->
      <div class="asset-info">
        <div class="asset-header">
          <div class="asset-type-icon" :class="getTypeClass(asset.type)">
            {{ getTypeIcon(asset.type) }}
          </div>
          <div>
            <h1 class="asset-title">{{ asset.name }}</h1>
            <p v-if="asset.summary" class="asset-summary">{{ asset.summary }}</p>
          </div>
        </div>

        <div class="asset-meta-grid">
          <div class="meta-item">
            <span class="meta-label">类型</span>
            <span class="tag" :class="`tag-${asset.type.toLowerCase()}`">
              {{ getTypeLabel(asset.type) }}
            </span>
          </div>
          <div class="meta-item">
            <span class="meta-label">范围</span>
            <span class="tag tag-scope">{{ getScopeLabel(asset.scope) }}</span>
          </div>
          <div v-if="asset.category" class="meta-item">
            <span class="meta-label">分类</span>
            <span class="meta-value">{{ asset.category }}</span>
          </div>
          <div v-if="asset.techStack" class="meta-item">
            <span class="meta-label">技术栈</span>
            <span class="meta-value">{{ asset.techStack }}</span>
          </div>
          <div v-if="currentVersion" class="meta-item">
            <span class="meta-label">当前版本</span>
            <span class="meta-value">v{{ currentVersion.versionNo }}</span>
          </div>
          <div v-if="currentVersion" class="meta-item">
            <span class="meta-label">版本状态</span>
            <span class="tag" :class="getStatusClass(currentVersion.status)">
              {{ getStatusLabel(currentVersion.status) }}
            </span>
          </div>
        </div>

        <div v-if="asset.tags && asset.tags.length > 0" class="asset-tags">
          <span v-for="tag in asset.tags" :key="tag" class="tag-small">{{ tag }}</span>
        </div>
      </div>

      <!-- 版本切换 -->
      <div v-if="detail?.versions && detail.versions.length > 0" class="version-selector">
        <label class="selector-label">查看版本：</label>
        <select v-model="selectedVersionNo" @change="loadVersion" class="version-select">
          <option :value="null">最新已发布版本</option>
          <option v-for="v in detail.versions" :key="v.id" :value="v.versionNo">
            v{{ v.versionNo }} - {{ getStatusLabel(v.status) }} - {{ formatDate(v.createdAt) }}
          </option>
        </select>
      </div>

      <!-- 内容预览 -->
      <div v-if="detail?.body" class="content-preview">
        <div class="content-header">
          <h2 class="content-title">内容</h2>
        </div>
        <div class="markdown-body" v-html="renderedBody"></div>
      </div>

      <div v-else class="empty-content">
        <p>暂无内容</p>
        <button v-if="detail?.canEdit && isDraft" @click="showEditor = true" class="btn-primary">
          开始编辑
        </button>
      </div>

      <!-- 附件列表 -->
      <div v-if="detail?.files && detail.files.length > 0 || (detail?.canEdit && isDraft)" class="files-section">
        <div class="section-header">
          <h2 class="section-title">附件</h2>
          <div class="section-actions">
            <button v-if="detail?.files && detail.files.length > 0" @click="downloadAllFiles" class="btn-secondary btn-sm">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                <polyline points="7 10 12 15 17 10"/>
                <line x1="12" y1="15" x2="12" y2="3"/>
              </svg>
              下载全部
            </button>
            <button v-if="detail?.canEdit && isDraft" @click="showUploadDialog = true" class="btn-secondary btn-sm">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                <polyline points="17 8 12 3 7 8"/>
                <line x1="12" y1="3" x2="12" y2="15"/>
              </svg>
              上传附件
            </button>
          </div>
        </div>

        <div v-if="detail?.files && detail.files.length > 0" class="files-list">
          <div v-for="file in detail.files" :key="file.id" class="file-item">
            <div class="file-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"/>
                <polyline points="13 2 13 9 20 9"/>
              </svg>
            </div>
            <div class="file-info">
              <div class="file-name">{{ file.relativePath }}</div>
              <div class="file-meta">{{ formatFileSize(file.sizeBytes) }}</div>
            </div>
            <div class="file-actions">
              <button @click="downloadSingleFile(file)" class="btn-icon" title="下载">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                  <polyline points="7 10 12 15 17 10"/>
                  <line x1="12" y1="15" x2="12" y2="3"/>
                </svg>
              </button>
              <button v-if="detail?.canEdit && isDraft" @click="deleteFile(file.id)" class="btn-icon btn-danger" title="删除">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="3 6 5 6 21 6"/>
                  <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                </svg>
              </button>
            </div>
          </div>
        </div>

        <div v-else class="empty-files">
          <p>暂无附件</p>
        </div>
      </div>

      <!-- 版本历史 -->
      <div v-if="detail?.versions && detail.versions.length > 0" class="versions-section">
        <h2 class="section-title">版本历史</h2>
        <div class="versions-timeline">
          <div v-for="version in detail.versions" :key="version.id" class="timeline-item">
            <div class="timeline-marker"></div>
            <div class="timeline-content">
              <div class="timeline-header">
                <span class="version-number">v{{ version.versionNo }}</span>
                <span class="tag" :class="getStatusClass(version.status)">
                  {{ getStatusLabel(version.status) }}
                </span>
                <span class="timeline-date">{{ formatDate(version.createdAt) }}</span>
              </div>
              <div v-if="version.changelog" class="timeline-body">
                {{ version.changelog }}
              </div>
              <div class="timeline-meta">
                <span v-if="version.createdByName">作者：{{ version.createdByName }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 编辑器对话框 -->
      <div v-if="showEditor" class="dialog-overlay" @click="closeEditor">
        <div class="dialog dialog-large" @click.stop>
          <div class="dialog-header">
            <h2>编辑内容</h2>
            <button @click="closeEditor" class="btn-close">×</button>
          </div>
          <div class="dialog-body">
            <div class="form-group">
              <label class="form-label">Markdown 内容</label>
              <textarea
                v-model="editorContent"
                class="editor-textarea"
                placeholder="使用 Markdown 格式编写内容..."
                rows="20"
              />
            </div>
            <div class="form-group">
              <label class="form-label">变更说明（可选）</label>
              <input
                v-model="editorChangelog"
                type="text"
                class="form-input"
                placeholder="简要说明本次修改的内容"
              />
            </div>
            <div v-if="editorError" class="error-message">{{ editorError }}</div>
            <div class="dialog-footer">
              <button @click="closeEditor" class="btn-secondary">取消</button>
              <button @click="saveDraft" class="btn-primary">保存草稿</button>
            </div>
          </div>
        </div>
      </div>

      <!-- 上传附件对话框 -->
      <div v-if="showUploadDialog" class="dialog-overlay" @click="closeUploadDialog">
        <div class="dialog" @click.stop>
          <div class="dialog-header">
            <h2>上传附件</h2>
            <button @click="closeUploadDialog" class="btn-close">×</button>
          </div>
          <div class="dialog-body">
            <div class="form-group">
              <label class="form-label">选择文件</label>
              <input
                type="file"
                ref="fileInput"
                @change="handleFileSelect"
                class="file-input"
                multiple
              />
              <div class="file-input-hint">
                支持的文件类型：.md, .py, .js, .sh, .json, .yaml, .txt 等文本文件及脚本
              </div>
            </div>

            <div v-if="selectedFiles.length > 0" class="selected-files">
              <div v-for="(item, index) in selectedFiles" :key="index" class="selected-file-item">
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
                <button @click="removeSelectedFile(index)" class="btn-icon-sm">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <line x1="18" y1="6" x2="6" y2="18"/>
                    <line x1="6" y1="6" x2="18" y2="18"/>
                  </svg>
                </button>
              </div>
            </div>

            <div v-if="uploadError" class="error-message">{{ uploadError }}</div>
            <div v-if="uploading" class="upload-progress">
              <div class="progress-bar">
                <div class="progress-fill" :style="{ width: uploadProgress + '%' }"></div>
              </div>
              <div class="progress-text">上传中... {{ uploadProgress }}%</div>
            </div>

            <div class="dialog-footer">
              <button @click="closeUploadDialog" class="btn-secondary" :disabled="uploading">取消</button>
              <button @click="uploadFiles" class="btn-primary" :disabled="selectedFiles.length === 0 || uploading">
                上传 {{ selectedFiles.length }} 个文件
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="error-state">
      <p>资产不存在</p>
      <button @click="goBack" class="btn-primary">返回列表</button>
    </div>
  </MainLayout>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { assetApi, type AssetDetail, type AssetType, type AssetScope, type AssetFile } from '../api/asset'
import { approvalApi } from '../api/approval'
import MainLayout from '../components/MainLayout.vue'
import { marked } from 'marked'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const detail = ref<AssetDetail | null>(null)
const selectedVersionNo = ref<number | null>(null)
const showEditor = ref(false)
const editorContent = ref('')
const editorChangelog = ref('')
const editorError = ref('')

const showUploadDialog = ref(false)
const selectedFiles = ref<Array<{ file: File, relativePath: string }>>([])
const fileInput = ref<HTMLInputElement | null>(null)
const uploadError = ref('')
const uploading = ref(false)
const uploadProgress = ref(0)

const asset = computed(() => detail.value?.asset)
const currentVersion = computed(() => detail.value?.currentVersion)

const isDraft = computed(() =>
  currentVersion.value?.status === 'DRAFT'
)

const isPending = computed(() =>
  currentVersion.value?.status === 'PENDING'
)

const hasContent = computed(() => {
  const hasBody = detail.value?.body && detail.value.body.trim().length > 0
  const hasFiles = detail.value?.files && detail.value.files.length > 0
  return hasBody || hasFiles
})

const renderedBody = computed(() => {
  if (!detail.value?.body) return ''
  return marked(detail.value.body)
})

watch(() => route.params.id, () => {
  if (route.params.id) {
    loadAsset()
  }
})

onMounted(async () => {
  await loadAsset()
})

const loadAsset = async () => {
  loading.value = true
  try {
    detail.value = await assetApi.get(route.params.id as string)
    if (detail.value?.body) {
      editorContent.value = detail.value.body
    }
  } catch (err: any) {
    console.error('Failed to load asset:', err)
  } finally {
    loading.value = false
  }
}

const loadVersion = async () => {
  if (!route.params.id) return
  loading.value = true
  try {
    detail.value = await assetApi.get(
      route.params.id as string,
      selectedVersionNo.value ?? undefined
    )
  } catch (err: any) {
    console.error('Failed to load version:', err)
  } finally {
    loading.value = false
  }
}

const createNewDraft = async () => {
  try {
    await assetApi.newDraft(route.params.id as string)
    await loadAsset()
    showEditor.value = true
  } catch (err: any) {
    alert(err.message || '创建草稿失败')
  }
}

const saveDraft = async () => {
  editorError.value = ''
  try {
    await assetApi.saveDraft(route.params.id as string, {
      body: editorContent.value,
      changelog: editorChangelog.value || undefined
    })
    await loadAsset()
    closeEditor()
  } catch (err: any) {
    editorError.value = err.message || '保存失败'
  }
}

const submitForApproval = async () => {
  if (!hasContent.value) {
    alert('请先编辑并保存内容后再提交审批')
    return
  }
  if (!confirm('确认提交审批？提交后将无法编辑，直到审批完成。')) return
  try {
    await approvalApi.submit(route.params.id as string)
    await loadAsset()
    alert('已提交审批，请等待审批人处理')
  } catch (err: any) {
    alert(err.message || '提交失败')
  }
}

const withdrawApproval = async () => {
  if (!confirm('确认撤回审批？撤回后版本将回到草稿状态。')) return
  try {
    // 需要先获取当前版本的审批记录
    const approvals = await approvalApi.getVersionApprovals(currentVersion.value!.id)
    const pendingApproval = approvals.find(a => !a.decidedAt)

    if (!pendingApproval) {
      alert('未找到待审批记录')
      return
    }

    await approvalApi.withdraw(pendingApproval.id)
    await loadAsset()
    alert('已撤回审批')
  } catch (err: any) {
    alert(err.message || '撤回失败')
  }
}

const publishDraft = async () => {
  if (!confirm('确认发布当前版本？发布后内容不可修改。')) return
  try {
    await assetApi.publish(route.params.id as string)
    await loadAsset()
  } catch (err: any) {
    alert(err.message || '发布失败')
  }
}

const closeEditor = () => {
  showEditor.value = false
  editorChangelog.value = ''
  editorError.value = ''
}

const handleFileSelect = (event: Event) => {
  const input = event.target as HTMLInputElement
  if (!input.files) return

  const files = Array.from(input.files)
  selectedFiles.value = files.map(file => ({
    file,
    relativePath: file.name
  }))
}

const removeSelectedFile = (index: number) => {
  selectedFiles.value.splice(index, 1)
}

const uploadFiles = async () => {
  if (selectedFiles.value.length === 0) return

  uploading.value = true
  uploadError.value = ''
  uploadProgress.value = 0

  try {
    const total = selectedFiles.value.length
    for (let i = 0; i < total; i++) {
      const item = selectedFiles.value[i]
      await assetApi.uploadFile(
        route.params.id as string,
        item.file,
        item.relativePath !== item.file.name ? item.relativePath : undefined
      )
      uploadProgress.value = Math.round(((i + 1) / total) * 100)
    }

    await loadAsset()
    closeUploadDialog()
  } catch (err: any) {
    uploadError.value = err.message || '上传失败'
  } finally {
    uploading.value = false
  }
}

const deleteFile = async (fileId: string) => {
  if (!confirm('确认删除此附件？')) return

  try {
    await assetApi.deleteFile(route.params.id as string, fileId)
    await loadAsset()
  } catch (err: any) {
    alert(err.message || '删除失败')
  }
}

const getDownloadUrl = (file: AssetFile) => {
  return assetApi.downloadFile(route.params.id as string, file.id)
}

const downloadSingleFile = async (file: AssetFile) => {
  // 记录下载次数
  try {
    await assetApi.recordDownload(route.params.id as string, detail.value!.currentVersion?.id)
    // 更新本地显示的下载次数
    if (asset.value) {
      asset.value.downloadCount = (asset.value.downloadCount || 0) + 1
    }
  } catch (err) {
    console.error('Failed to record download:', err)
  }

  // 触发下载
  const url = getDownloadUrl(file)
  const link = document.createElement('a')
  link.href = url
  link.download = file.relativePath
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

const downloadAllFiles = async () => {
  if (!detail.value?.files || detail.value.files.length === 0) return

  // 记录下载次数
  try {
    await assetApi.recordDownload(route.params.id as string, detail.value.currentVersion?.id)
    // 更新本地显示的下载次数
    if (asset.value) {
      asset.value.downloadCount = (asset.value.downloadCount || 0) + 1
    }
  } catch (err) {
    console.error('Failed to record download:', err)
  }

  // 触发 ZIP 打包下载
  const url = assetApi.downloadAllFiles(route.params.id as string, detail.value.currentVersion?.id)
  const link = document.createElement('a')
  link.href = url
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

const closeUploadDialog = () => {
  showUploadDialog.value = false
  selectedFiles.value = []
  uploadError.value = ''
  uploadProgress.value = 0
  if (fileInput.value) {
    fileInput.value.value = ''
  }
}

const formatFileSize = (bytes: number) => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

const goBack = () => {
  router.push('/assets')
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

const getStatusLabel = (status: string) => {
  const labels: Record<string, string> = {
    DRAFT: '草稿',
    PENDING: '待审批',
    REJECTED: '已拒绝',
    PUBLISHED: '已发布',
    DEPRECATED: '已弃用',
    WITHDRAWN: '已撤回'
  }
  return labels[status] || status
}

const getStatusClass = (status: string) => {
  const classes: Record<string, string> = {
    DRAFT: 'tag-draft',
    PENDING: 'tag-pending',
    REJECTED: 'tag-rejected',
    PUBLISHED: 'tag-published',
    DEPRECATED: 'tag-deprecated',
    WITHDRAWN: 'tag-withdrawn'
  }
  return classes[status] || ''
}

const formatDate = (dateStr: string) => {
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN')
}
</script>

<style scoped>
.loading, .error-state {
  text-align: center;
  padding: var(--sp-24);
  color: var(--color-text-secondary);
}

.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-16);
}

.asset-detail {
  max-width: 1200px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--sp-24);
}

.btn-back {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-8);
  height: 40px;
  padding: 0 var(--sp-20);
  background: var(--color-bg-1);
  color: var(--color-text-primary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-8);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-back svg {
  width: 16px;
  height: 16px;
}

.btn-back:hover {
  background: var(--color-bg-2);
  border-color: var(--color-primary);
}

.header-actions {
  display: flex;
  gap: var(--sp-12);
}

.btn-primary, .btn-secondary {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-8);
  height: 40px;
  padding: 0 var(--sp-20);
  font-size: 14px;
  font-weight: 500;
  border-radius: var(--radius-8);
  cursor: pointer;
  transition: all 0.2s;
}

.btn-primary {
  background: var(--color-primary);
  color: white;
  border: none;
}

.btn-primary:hover:not(:disabled) {
  background: var(--color-primary-dark);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(27, 170, 127, 0.3);
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-primary svg, .btn-secondary svg {
  width: 16px;
  height: 16px;
}

.btn-secondary {
  background: var(--color-bg-1);
  color: var(--color-text-primary);
  border: 1px solid var(--color-border);
}

.btn-secondary:hover:not(:disabled) {
  background: var(--color-bg-2);
  border-color: var(--color-primary);
}

.btn-secondary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-sm {
  height: 32px;
  padding: 0 var(--sp-16);
  font-size: 13px;
}

.btn-sm svg {
  width: 14px;
  height: 14px;
}

/* 资产信息卡片 */
.asset-info {
  background: var(--color-bg-1);
  border-radius: var(--radius-12);
  padding: var(--sp-24);
  margin-bottom: var(--sp-20);
  box-shadow: var(--shadow-card);
}

.asset-header {
  display: flex;
  gap: var(--sp-20);
  margin-bottom: var(--sp-24);
  padding-bottom: var(--sp-24);
  border-bottom: 1px solid var(--color-border);
}

.asset-type-icon {
  width: 64px;
  height: 64px;
  border-radius: var(--radius-12);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
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

.asset-title {
  font-size: 28px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: var(--sp-8);
}

.asset-summary {
  font-size: 16px;
  color: var(--color-text-secondary);
  line-height: 1.6;
}

.asset-meta-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: var(--sp-20);
  margin-bottom: var(--sp-20);
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: var(--sp-8);
}

.meta-label {
  font-size: 12px;
  color: var(--color-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.meta-value {
  font-size: 14px;
  color: var(--color-text-primary);
  font-weight: 500;
}

.tag {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: var(--sp-4) var(--sp-12);
  border-radius: var(--radius-4);
  font-size: 12px;
  font-weight: 500;
  width: fit-content;
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

.tag-draft {
  background: var(--color-bg-3);
  color: var(--color-text-secondary);
}

.tag-pending {
  background: var(--color-warning-bg);
  color: var(--color-warning);
}

.tag-rejected {
  background: var(--color-error-bg);
  color: var(--color-error);
}

.tag-published {
  background: var(--color-success-bg);
  color: var(--color-success);
}

.tag-deprecated {
  background: rgba(134, 144, 156, 0.1);
  color: var(--color-text-tertiary);
}

.tag-withdrawn {
  background: var(--color-error-bg);
  color: var(--color-error);
}

.asset-tags {
  display: flex;
  gap: var(--sp-8);
  flex-wrap: wrap;
}

.tag-small {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 var(--sp-12);
  background: var(--color-bg-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-4);
  font-size: 12px;
  color: var(--color-text-secondary);
}

/* 版本选择器 */
.version-selector {
  background: var(--color-bg-1);
  border-radius: var(--radius-12);
  padding: var(--sp-16);
  margin-bottom: var(--sp-20);
  display: flex;
  align-items: center;
  gap: var(--sp-12);
}

.selector-label {
  font-size: 14px;
  color: var(--color-text-primary);
  font-weight: 500;
}

.version-select {
  flex: 1;
  max-width: 400px;
  height: 36px;
  padding: 0 var(--sp-12);
  padding-right: var(--sp-32);
  font-size: 14px;
  color: var(--color-text-primary);
  background: var(--color-bg-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-8);
  cursor: pointer;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%23666' d='M6 9L1 4h10z'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 12px center;
}

.version-select:hover {
  border-color: var(--color-primary);
}

.version-select:focus {
  border-color: var(--color-primary);
  outline: none;
  box-shadow: 0 0 0 3px rgba(27, 170, 127, 0.1);
}

/* 内容预览 */
.content-preview {
  background: var(--color-bg-1);
  border-radius: var(--radius-12);
  padding: var(--sp-24);
  margin-bottom: var(--sp-20);
  box-shadow: var(--shadow-card);
}

.content-header {
  margin-bottom: var(--sp-20);
  padding-bottom: var(--sp-16);
  border-bottom: 1px solid var(--color-border);
}

.content-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.markdown-body {
  font-size: 14px;
  line-height: 1.8;
  color: var(--color-text-primary);
}

.markdown-body :deep(h1) {
  font-size: 28px;
  margin: var(--sp-24) 0 var(--sp-16);
}

.markdown-body :deep(h2) {
  font-size: 24px;
  margin: var(--sp-20) 0 var(--sp-12);
}

.markdown-body :deep(h3) {
  font-size: 20px;
  margin: var(--sp-16) 0 var(--sp-12);
}

.markdown-body :deep(p) {
  margin: var(--sp-12) 0;
}

.markdown-body :deep(code) {
  background: var(--color-bg-2);
  padding: 2px 6px;
  border-radius: var(--radius-4);
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
}

.markdown-body :deep(pre) {
  background: var(--color-bg-2);
  padding: var(--sp-16);
  border-radius: var(--radius-8);
  overflow-x: auto;
  margin: var(--sp-16) 0;
}

.empty-content {
  background: var(--color-bg-1);
  border-radius: var(--radius-12);
  padding: var(--sp-24);
  text-align: center;
  margin-bottom: var(--sp-20);
}

.empty-content p {
  color: var(--color-text-secondary);
  margin-bottom: var(--sp-16);
}

/* 版本历史 */
.versions-section {
  background: var(--color-bg-1);
  border-radius: var(--radius-12);
  padding: var(--sp-24);
  margin-bottom: var(--sp-20);
  box-shadow: var(--shadow-card);
}

.section-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: var(--sp-20);
  padding-bottom: var(--sp-16);
  border-bottom: 1px solid var(--color-border);
}

.versions-timeline {
  position: relative;
}

.timeline-item {
  position: relative;
  padding-left: 32px;
  padding-bottom: var(--sp-24);
}

.timeline-item:last-child {
  padding-bottom: 0;
}

.timeline-marker {
  position: absolute;
  left: 0;
  top: 4px;
  width: 12px;
  height: 12px;
  background: var(--color-primary);
  border: 3px solid var(--color-bg-1);
  border-radius: 50%;
  box-shadow: 0 0 0 2px var(--color-border);
}

.timeline-item:not(:last-child)::before {
  content: '';
  position: absolute;
  left: 5px;
  top: 16px;
  width: 2px;
  height: calc(100% - 16px);
  background: var(--color-border);
}

.timeline-content {
  background: var(--color-bg-2);
  border-radius: var(--radius-8);
  padding: var(--sp-16);
}

.timeline-header {
  display: flex;
  align-items: center;
  gap: var(--sp-12);
  margin-bottom: var(--sp-12);
}

.version-number {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.timeline-date {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-left: auto;
}

.timeline-body {
  font-size: 14px;
  color: var(--color-text-primary);
  margin-bottom: var(--sp-12);
  line-height: 1.6;
}

.timeline-meta {
  font-size: 12px;
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
  align-items: center;
  justify-content: center;
  z-index: 2000;
  padding: var(--sp-24);
}

.dialog {
  background: var(--color-bg-1);
  border-radius: var(--radius-16);
  width: 100%;
  max-width: 600px;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.2);
  max-height: 90vh;
  display: flex;
  flex-direction: column;
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
  flex-shrink: 0;
}

.btn-close:hover {
  background: var(--color-bg-2);
  color: var(--color-text-primary);
}

.dialog-body {
  padding: var(--sp-24);
  overflow-y: auto;
  flex: 1;
}

.form-group {
  margin-bottom: var(--sp-20);
}

.form-label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-primary);
  margin-bottom: var(--sp-8);
}

.form-input {
  width: 100%;
  height: 40px;
  padding: var(--sp-12) var(--sp-16);
  font-size: 14px;
  color: var(--color-text-primary);
  background: var(--color-bg-1);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-8);
  outline: none;
  transition: all 0.2s;
}

.editor-textarea {
  width: 100%;
  padding: var(--sp-16);
  font-size: 14px;
  line-height: 1.6;
  color: var(--color-text-primary);
  background: var(--color-bg-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-8);
  outline: none;
  resize: vertical;
  font-family: 'Consolas', 'Monaco', monospace;
  transition: all 0.2s;
}

.form-input:focus, .editor-textarea:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(27, 170, 127, 0.1);
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

.dialog-footer {
  display: flex;
  gap: var(--sp-12);
  justify-content: flex-end;
  margin-top: var(--sp-24);
  padding-top: var(--sp-20);
  border-top: 1px solid var(--color-border);
  flex-shrink: 0;
}

/* 附件区域 */
.files-section {
  background: var(--color-bg-1);
  border-radius: var(--radius-12);
  padding: var(--sp-24);
  margin-bottom: var(--sp-20);
  box-shadow: var(--shadow-card);
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--sp-20);
  padding-bottom: var(--sp-16);
  border-bottom: 1px solid var(--color-border);
}

.section-actions {
  display: flex;
  gap: var(--sp-8);
}

.files-list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-12);
}

.file-item {
  display: flex;
  align-items: center;
  gap: var(--sp-16);
  padding: var(--sp-16);
  background: var(--color-bg-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-8);
  transition: all 0.2s;
}

.file-item:hover {
  border-color: var(--color-primary);
  background: var(--color-bg-1);
}

.file-icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg-3);
  border-radius: var(--radius-8);
  flex-shrink: 0;
}

.file-icon svg {
  width: 20px;
  height: 20px;
  color: var(--color-text-secondary);
}

.file-info {
  flex: 1;
  min-width: 0;
}

.file-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-primary);
  margin-bottom: var(--sp-4);
  word-break: break-all;
}

.file-meta {
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.file-actions {
  display: flex;
  gap: var(--sp-8);
  flex-shrink: 0;
}

.btn-icon {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-8);
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: all 0.2s;
  text-decoration: none;
}

.btn-icon:hover {
  background: var(--color-bg-3);
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.btn-icon svg {
  width: 16px;
  height: 16px;
}

.btn-icon.btn-danger:hover {
  background: var(--color-error-bg);
  border-color: var(--color-error);
  color: var(--color-error);
}

.empty-files {
  text-align: center;
  padding: var(--sp-24);
  color: var(--color-text-tertiary);
  font-size: 14px;
}

/* 上传对话框 */
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

.upload-progress {
  margin-top: var(--sp-16);
}

.progress-bar {
  width: 100%;
  height: 8px;
  background: var(--color-bg-3);
  border-radius: 999px;
  overflow: hidden;
  margin-bottom: var(--sp-8);
}

.progress-fill {
  height: 100%;
  background: var(--color-primary);
  border-radius: 999px;
  transition: width 0.3s;
}

.progress-text {
  font-size: 13px;
  color: var(--color-text-secondary);
  text-align: center;
}
</style>
