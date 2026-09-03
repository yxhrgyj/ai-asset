<template>
  <MainLayout>
    <div class="approvals-page">
      <div class="page-header">
        <h1 class="page-title">审批管理</h1>
      </div>

      <div v-if="loading" class="loading">加载中...</div>

      <div v-else-if="error" class="error-message">{{ error }}</div>

      <div v-else-if="approvals.length === 0" class="empty-state">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"/>
        </svg>
        <p>暂无待审批的资产</p>
      </div>

      <div v-else class="approvals-list">
        <div v-for="approval in approvals" :key="approval.id" class="approval-card">
          <div class="approval-header">
            <router-link
              :to="`/assets/${approval.assetId}`"
              class="asset-link"
            >
              <h3>{{ approval.assetName }}</h3>
              <span class="version-badge">v{{ approval.versionNo }}</span>
            </router-link>
          </div>

          <div class="approval-meta">
            <div class="meta-item">
              <span class="label">提交人：</span>
              <span class="value">{{ approval.submittedByName }}</span>
            </div>
            <div class="meta-item">
              <span class="label">提交时间：</span>
              <span class="value">{{ formatDate(approval.submittedAt) }}</span>
            </div>
          </div>

          <div class="approval-actions">
            <button @click="handleApprove(approval)" class="btn-approve">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M5 13l4 4L19 7"/>
              </svg>
              批准
            </button>
            <button @click="handleReject(approval)" class="btn-reject">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M6 18L18 6M6 6l12 12"/>
              </svg>
              驳回
            </button>
          </div>
        </div>
      </div>

      <!-- 审批对话框 -->
      <div v-if="showDialog" class="modal-overlay" @click.self="closeDialog">
        <div class="modal-content">
          <div class="modal-header">
            <h2>{{ dialogType === 'approve' ? '批准资产' : '驳回资产' }}</h2>
            <button @click="closeDialog" class="btn-close">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M6 18L18 6M6 6l12 12"/>
              </svg>
            </button>
          </div>

          <div class="modal-body">
            <div class="asset-info">
              <p><strong>资产名称：</strong>{{ currentApproval?.assetName }}</p>
              <p><strong>版本号：</strong>v{{ currentApproval?.versionNo }}</p>
              <p><strong>提交人：</strong>{{ currentApproval?.submittedByName }}</p>
            </div>

            <div class="form-group">
              <label for="comment">{{ dialogType === 'approve' ? '批准意见' : '驳回理由' }}：</label>
              <textarea
                id="comment"
                v-model="comment"
                :placeholder="dialogType === 'approve' ? '可选填写批准意见...' : '请说明驳回理由...'"
                rows="4"
              ></textarea>
            </div>
          </div>

          <div class="modal-footer">
            <button @click="closeDialog" class="btn-secondary">取消</button>
            <button
              @click="confirmDecision"
              :class="dialogType === 'approve' ? 'btn-approve' : 'btn-reject'"
              :disabled="submitting"
            >
              {{ submitting ? '处理中...' : (dialogType === 'approve' ? '确认批准' : '确认驳回') }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </MainLayout>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { approvalApi, type Approval } from '../api/approval'
import MainLayout from '../components/MainLayout.vue'

const approvals = ref<Approval[]>([])
const loading = ref(true)
const error = ref('')

const showDialog = ref(false)
const dialogType = ref<'approve' | 'reject'>('approve')
const currentApproval = ref<Approval | null>(null)
const comment = ref('')
const submitting = ref(false)

const loadApprovals = async () => {
  try {
    loading.value = true
    error.value = ''
    approvals.value = await approvalApi.getPending()
  } catch (err: any) {
    error.value = err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

const handleApprove = (approval: Approval) => {
  currentApproval.value = approval
  dialogType.value = 'approve'
  comment.value = ''
  showDialog.value = true
}

const handleReject = (approval: Approval) => {
  currentApproval.value = approval
  dialogType.value = 'reject'
  comment.value = ''
  showDialog.value = true
}

const closeDialog = () => {
  showDialog.value = false
  currentApproval.value = null
  comment.value = ''
}

const confirmDecision = async () => {
  if (!currentApproval.value) return

  if (dialogType.value === 'reject' && !comment.value.trim()) {
    alert('请填写驳回理由')
    return
  }

  try {
    submitting.value = true
    await approvalApi.decide(
      currentApproval.value.id,
      dialogType.value === 'approve' ? 'APPROVED' : 'REJECTED',
      comment.value.trim() || undefined
    )
    closeDialog()
    await loadApprovals()
  } catch (err: any) {
    alert(err.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

const formatDate = (dateStr: string) => {
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN')
}

onMounted(() => {
  loadApprovals()
})
</script>

<style scoped>
.approvals-page {
  max-width: 900px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: var(--sp-24);
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

.empty-state {
  text-align: center;
  padding: var(--sp-48) var(--sp-24);
  color: var(--color-text-secondary);
}

.empty-state svg {
  width: 64px;
  height: 64px;
  margin-bottom: var(--sp-16);
  opacity: 0.3;
}

.empty-state p {
  font-size: 16px;
  margin: 0;
}

.approvals-list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-16);
}

.approval-card {
  background: var(--color-bg-1);
  border-radius: var(--radius-12);
  padding: var(--sp-20);
  border: 1px solid var(--color-border);
}

.approval-header {
  margin-bottom: var(--sp-16);
}

.asset-link {
  display: flex;
  align-items: center;
  gap: var(--sp-12);
  text-decoration: none;
  color: var(--color-text-primary);
  transition: opacity 0.2s;
}

.asset-link:hover {
  opacity: 0.8;
}

.asset-link h3 {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
}

.version-badge {
  display: inline-block;
  padding: 4px 12px;
  background: var(--color-primary);
  color: white;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
}

.approval-meta {
  display: flex;
  flex-direction: column;
  gap: var(--sp-8);
  margin-bottom: var(--sp-20);
  padding: var(--sp-12);
  background: var(--color-bg-2);
  border-radius: var(--radius-8);
}

.meta-item {
  display: flex;
  gap: var(--sp-8);
  font-size: 14px;
}

.meta-item .label {
  color: var(--color-text-secondary);
}

.meta-item .value {
  color: var(--color-text-primary);
  font-weight: 500;
}

.approval-actions {
  display: flex;
  gap: var(--sp-12);
}

.approval-actions button {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--sp-8);
  padding: var(--sp-10) var(--sp-20);
  border: none;
  border-radius: var(--radius-8);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.approval-actions svg {
  width: 18px;
  height: 18px;
}

.btn-approve {
  background: #10b981;
  color: white;
}

.btn-approve:hover {
  background: #059669;
}

.btn-reject {
  background: #ef4444;
  color: white;
}

.btn-reject:hover {
  background: #dc2626;
}

/* 模态框样式 */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: var(--color-bg-1);
  border-radius: var(--radius-16);
  width: 90%;
  max-width: 500px;
  max-height: 80vh;
  overflow: auto;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--sp-20) var(--sp-24);
  border-bottom: 1px solid var(--color-border);
}

.modal-header h2 {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
  color: var(--color-text-primary);
}

.btn-close {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: var(--color-text-secondary);
  border-radius: var(--radius-8);
  cursor: pointer;
  transition: all 0.2s;
}

.btn-close:hover {
  background: var(--color-bg-2);
}

.btn-close svg {
  width: 20px;
  height: 20px;
}

.modal-body {
  padding: var(--sp-24);
}

.asset-info {
  padding: var(--sp-16);
  background: var(--color-bg-2);
  border-radius: var(--radius-8);
  margin-bottom: var(--sp-20);
}

.asset-info p {
  margin: var(--sp-8) 0;
  font-size: 14px;
  color: var(--color-text-primary);
}

.form-group {
  margin-bottom: var(--sp-16);
}

.form-group label {
  display: block;
  margin-bottom: var(--sp-8);
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-primary);
}

.form-group textarea {
  width: 100%;
  padding: var(--sp-12);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-8);
  font-size: 14px;
  font-family: inherit;
  background: var(--color-bg-1);
  color: var(--color-text-primary);
  resize: vertical;
  min-height: 100px;
  outline: none;
  transition: all 0.2s;
}

.form-group textarea:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(27, 170, 127, 0.1);
}

.modal-footer {
  display: flex;
  gap: var(--sp-12);
  padding: var(--sp-20) var(--sp-24);
  border-top: 1px solid var(--color-border);
}

.modal-footer button {
  flex: 1;
  padding: var(--sp-10) var(--sp-20);
  border: none;
  border-radius: var(--radius-8);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-secondary {
  background: var(--color-bg-2);
  color: var(--color-text-primary);
  border: 1px solid var(--color-border);
}

.btn-secondary:hover {
  background: var(--color-border);
}

.modal-footer .btn-approve,
.modal-footer .btn-reject {
  color: white;
}

button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
