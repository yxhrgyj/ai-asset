<template>
  <MainLayout>
    <div class="teams-page">
      <div class="page-header">
        <div>
          <h1 class="page-title">团队管理</h1>
          <p class="page-subtitle">管理组织内的团队和成员</p>
        </div>
        <button v-if="auth.user?.role === 'ADMIN'" @click="showCreateDialog = true" class="btn-primary">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"/>
            <line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
          创建团队
        </button>
      </div>

      <div v-if="loading" class="loading">加载中...</div>

      <div v-else-if="teams.length === 0" class="empty-state">
        <div class="empty-icon">📦</div>
        <p class="empty-text">暂无团队</p>
        <button v-if="auth.user?.role === 'ADMIN'" @click="showCreateDialog = true" class="btn-primary">
          创建第一个团队
        </button>
      </div>

      <div v-else class="teams-grid">
        <div v-for="team in teams" :key="team.id" class="team-card">
          <div class="team-header">
            <div class="team-icon">👥</div>
            <div class="team-info">
              <h3 class="team-name">{{ team.name }}</h3>
              <p class="team-meta">创建于 {{ formatDate(team.createdAt) }}</p>
            </div>
          </div>
          <div v-if="auth.user?.role === 'ADMIN'" class="team-actions">
            <button @click="editTeam(team)" class="btn-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
              </svg>
            </button>
            <button @click="deleteTeam(team)" class="btn-icon btn-danger-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="3 6 5 6 21 6"/>
                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
              </svg>
            </button>
          </div>
        </div>
      </div>

      <!-- 创建/编辑对话框 -->
      <div v-if="showCreateDialog || showEditDialog" class="dialog-overlay" @click="closeDialogs">
        <div class="dialog" @click.stop>
          <div class="dialog-header">
            <h2>{{ showEditDialog ? '编辑团队' : '创建团队' }}</h2>
            <button @click="closeDialogs" class="btn-close">×</button>
          </div>
          <form @submit.prevent="handleSubmit" class="dialog-body">
            <div class="form-group">
              <label class="form-label">团队名称</label>
              <input
                v-model="formData.name"
                type="text"
                class="form-input"
                placeholder="请输入团队名称"
                required
              />
            </div>
            <div v-if="error" class="error-message">{{ error }}</div>
            <div class="dialog-footer">
              <button type="button" @click="closeDialogs" class="btn-secondary">取消</button>
              <button type="submit" class="btn-primary">{{ showEditDialog ? '保存' : '创建' }}</button>
            </div>
          </form>
        </div>
      </div>
    </div>
  </MainLayout>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useAuthStore } from '../stores/auth'
import { teamApi, type Team } from '../api/team'
import MainLayout from '../components/MainLayout.vue'

const auth = useAuthStore()
const teams = ref<Team[]>([])
const loading = ref(true)
const error = ref('')

const showCreateDialog = ref(false)
const showEditDialog = ref(false)
const formData = ref({ id: '', name: '' })

onMounted(async () => {
  await loadTeams()
})

const loadTeams = async () => {
  loading.value = true
  try {
    teams.value = await teamApi.list()
  } catch (err: any) {
    error.value = err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

const editTeam = (team: Team) => {
  formData.value = { id: team.id, name: team.name }
  showEditDialog.value = true
}

const deleteTeam = async (team: Team) => {
  if (!confirm(`确认删除团队"${team.name}"？`)) return

  try {
    await teamApi.delete(team.id)
    await loadTeams()
  } catch (err: any) {
    alert(err.message || '删除失败')
  }
}

const handleSubmit = async () => {
  error.value = ''
  try {
    if (showEditDialog.value) {
      await teamApi.update(formData.value.id, { name: formData.value.name })
    } else {
      await teamApi.create({ name: formData.value.name })
    }
    await loadTeams()
    closeDialogs()
  } catch (err: any) {
    error.value = err.message || '操作失败'
  }
}

const closeDialogs = () => {
  showCreateDialog.value = false
  showEditDialog.value = false
  formData.value = { id: '', name: '' }
  error.value = ''
}

const formatDate = (dateStr: string) => {
  return new Date(dateStr).toLocaleDateString('zh-CN')
}
</script>

<style scoped>
.teams-page {
  max-width: 1200px;
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

.teams-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--sp-20);
}

.team-card {
  background: var(--color-bg-1);
  border-radius: var(--radius-12);
  padding: var(--sp-20);
  box-shadow: var(--shadow-card);
  transition: all 0.2s;
}

.team-card:hover {
  box-shadow: var(--shadow-hover);
  transform: translateY(-2px);
}

.team-header {
  display: flex;
  align-items: center;
  gap: var(--sp-16);
  margin-bottom: var(--sp-16);
}

.team-icon {
  width: 48px;
  height: 48px;
  background: var(--color-primary);
  border-radius: var(--radius-12);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  flex-shrink: 0;
}

.team-info {
  flex: 1;
  min-width: 0;
}

.team-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: var(--sp-4);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.team-meta {
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.team-actions {
  display: flex;
  gap: var(--sp-8);
  justify-content: flex-end;
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
}

.btn-icon svg {
  width: 16px;
  height: 16px;
}

.btn-icon:hover {
  background: var(--color-bg-2);
  color: var(--color-text-primary);
}

.btn-danger-icon:hover {
  background: var(--color-error-bg);
  color: var(--color-error);
  border-color: var(--color-error);
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
  max-width: 480px;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.2);
  max-height: 85vh;
  display: flex;
  flex-direction: column;
  overflow: visible;
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
  padding-bottom: 250px;
  overflow-y: auto;
  flex: 1;
}

.form-group {
  margin-bottom: var(--sp-24);
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

.form-input:focus {
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
}
</style>
