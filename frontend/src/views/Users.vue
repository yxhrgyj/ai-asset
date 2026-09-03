<template>
  <MainLayout>
    <div class="users-page">
      <div class="page-header">
        <div>
          <h1 class="page-title">用户管理</h1>
          <p class="page-subtitle">管理系统用户和权限</p>
        </div>
        <button @click="showCreateDialog = true" class="btn-primary">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"/>
            <line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
          创建用户
        </button>
      </div>

      <div v-if="loading" class="loading">加载中...</div>

      <div v-else-if="users.length === 0" class="empty-state">
        <div class="empty-icon">👤</div>
        <p class="empty-text">暂无用户</p>
      </div>

      <div v-else class="table-container">
        <table>
          <thead>
            <tr>
              <th>用户名</th>
              <th>显示名称</th>
              <th>角色</th>
              <th>团队</th>
              <th>状态</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in users" :key="user.id">
              <td>{{ user.username }}</td>
              <td>{{ user.displayName }}</td>
              <td>
                <span class="tag" :class="getRoleClass(user.role)">
                  {{ getRoleLabel(user.role) }}
                </span>
              </td>
              <td>{{ user.teamName || '-' }}</td>
              <td>
                <span class="tag" :class="getStatusClass(user.status)">
                  {{ getStatusLabel(user.status) }}
                </span>
              </td>
              <td>{{ formatDate(user.createdAt) }}</td>
              <td>
                <div class="actions">
                  <button @click="editUser(user)" class="btn-icon" title="编辑">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                      <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                    </svg>
                  </button>
                  <button @click="resetPassword(user)" class="btn-icon" title="重置密码">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                      <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                    </svg>
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 创建/编辑对话框 -->
      <div v-if="showCreateDialog || showEditDialog" class="dialog-overlay" @click="closeDialogs">
        <div class="dialog" @click.stop>
          <div class="dialog-header">
            <h2>{{ showEditDialog ? '编辑用户' : '创建用户' }}</h2>
            <button @click="closeDialogs" class="btn-close">×</button>
          </div>
          <form @submit.prevent="handleSubmit" class="dialog-body">
            <div class="form-group">
              <label class="form-label">用户名</label>
              <input
                v-model="formData.username"
                type="text"
                class="form-input"
                placeholder="请输入用户名"
                :disabled="showEditDialog"
                required
              />
            </div>

            <div v-if="!showEditDialog" class="form-group">
              <label class="form-label">初始密码</label>
              <input
                v-model="formData.password"
                type="password"
                class="form-input"
                placeholder="请输入初始密码（至少 8 位）"
                required
              />
            </div>

            <div class="form-group">
              <label class="form-label">显示名称</label>
              <input
                v-model="formData.displayName"
                type="text"
                class="form-input"
                placeholder="请输入显示名称"
                required
              />
            </div>

            <div class="form-group">
              <label class="form-label">角色</label>
              <CustomSelect
                v-model="formData.role"
                :options="roleOptions"
              />
            </div>

            <div class="form-group">
              <label class="form-label">团队</label>
              <CustomSelect
                v-model="formData.teamId"
                :options="teamOptions"
              />
            </div>

            <div class="form-group">
              <label class="form-label">状态</label>
              <CustomSelect
                v-model="formData.status"
                :options="statusOptions"
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
import { ref, computed, onMounted } from 'vue'
import { userApi, type User } from '../api/user'
import { teamApi, type Team } from '../api/team'
import MainLayout from '../components/MainLayout.vue'
import CustomSelect from '../components/CustomSelect.vue'

const users = ref<User[]>([])
const teams = ref<Team[]>([])
const loading = ref(true)
const error = ref('')

const showCreateDialog = ref(false)
const showEditDialog = ref(false)
const formData = ref({
  id: '',
  username: '',
  password: '',
  displayName: '',
  role: 'MEMBER',
  teamId: '',
  status: 'ACTIVE'
})

const roleOptions = [
  { label: '普通成员', value: 'MEMBER' },
  { label: '作者', value: 'AUTHOR' },
  { label: '审批人', value: 'APPROVER' },
  { label: '管理员', value: 'ADMIN' }
]

const statusOptions = [
  { label: '启用', value: 'ACTIVE' },
  { label: '禁用', value: 'DISABLED' }
]

const teamOptions = computed(() => [
  { label: '无团队', value: '' },
  ...teams.value.map(team => ({ label: team.name, value: team.id }))
])

onMounted(async () => {
  await Promise.all([loadUsers(), loadTeams()])
})

const loadUsers = async () => {
  loading.value = true
  try {
    users.value = await userApi.list()
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

const editUser = (user: User) => {
  formData.value = {
    id: user.id,
    username: user.username,
    password: '',
    displayName: user.displayName,
    role: user.role,
    teamId: user.teamId || '',
    status: user.status
  }
  showEditDialog.value = true
}

const resetPassword = async (user: User) => {
  const newPassword = prompt(`重置用户"${user.displayName}"的密码：\n\n请输入新密码（至少 8 位）`)
  if (!newPassword) return

  if (newPassword.length < 8) {
    alert('密码至少 8 位')
    return
  }

  try {
    await userApi.resetPassword(user.id)
    alert('密码已重置，用户下次登录需修改密码')
  } catch (err: any) {
    alert(err.message || '重置失败')
  }
}

const handleSubmit = async () => {
  error.value = ''

  if (!showEditDialog.value && formData.value.password.length < 8) {
    error.value = '密码至少 8 位'
    return
  }

  try {
    if (showEditDialog.value) {
      await userApi.update(formData.value.id, {
        displayName: formData.value.displayName,
        role: formData.value.role as any,
        teamId: formData.value.teamId || null,
        status: formData.value.status as any
      })
    } else {
      await userApi.create({
        username: formData.value.username,
        password: formData.value.password,
        displayName: formData.value.displayName,
        role: formData.value.role as any,
        teamId: formData.value.teamId || null
      })
    }
    await loadUsers()
    closeDialogs()
  } catch (err: any) {
    error.value = err.message || '操作失败'
  }
}

const closeDialogs = () => {
  showCreateDialog.value = false
  showEditDialog.value = false
  formData.value = {
    id: '',
    username: '',
    password: '',
    displayName: '',
    role: 'MEMBER',
    teamId: '',
    status: 'ACTIVE'
  }
  error.value = ''
}

const getRoleLabel = (role: string) => {
  const labels: Record<string, string> = {
    ADMIN: '管理员',
    APPROVER: '审批人',
    AUTHOR: '作者',
    MEMBER: '成员'
  }
  return labels[role] || role
}

const getRoleClass = (role: string) => {
  const classes: Record<string, string> = {
    ADMIN: 'tag-admin',
    APPROVER: 'tag-approver',
    AUTHOR: 'tag-author',
    MEMBER: 'tag-member'
  }
  return classes[role] || ''
}

const getStatusLabel = (status: string) => {
  return status === 'ACTIVE' ? '启用' : '禁用'
}

const getStatusClass = (status: string) => {
  return status === 'ACTIVE' ? 'tag-success' : 'tag-disabled'
}

const formatDate = (dateStr: string) => {
  return new Date(dateStr).toLocaleDateString('zh-CN')
}
</script>

<style scoped>
.users-page {
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
}

.table-container {
  background: var(--color-bg-1);
  border-radius: var(--radius-12);
  overflow: hidden;
  box-shadow: var(--shadow-card);
}

table {
  width: 100%;
  border-collapse: collapse;
}

thead {
  background: var(--color-bg-2);
}

th {
  padding: var(--sp-16);
  text-align: left;
  font-weight: 600;
  font-size: 14px;
  color: var(--color-text-secondary);
  height: 48px;
}

td {
  padding: var(--sp-16);
  border-top: 1px solid var(--color-border);
  font-size: 14px;
  color: var(--color-text-primary);
  height: 56px;
}

tbody tr:hover {
  background: var(--color-bg-2);
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

.tag-admin {
  background: var(--color-error-bg);
  color: var(--color-error);
}

.tag-approver {
  background: var(--color-warning-bg);
  color: var(--color-warning);
}

.tag-author {
  background: rgba(22, 93, 255, 0.1);
  color: var(--color-line-3);
}

.tag-member {
  background: var(--color-bg-3);
  color: var(--color-text-secondary);
}

.tag-success {
  background: var(--color-success-bg);
  color: var(--color-success);
}

.tag-disabled {
  background: var(--color-bg-3);
  color: var(--color-text-disabled);
}

.actions {
  display: flex;
  gap: var(--sp-8);
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

/* 对话框样式（与 Teams.vue 一致）*/
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
  max-width: 520px;
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

.form-input:disabled {
  background: var(--color-bg-3);
  color: var(--color-text-disabled);
  cursor: not-allowed;
}

select.form-input {
  cursor: pointer;
  padding-right: var(--sp-32);
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%23666' d='M6 9L1 4h10z'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 12px center;
}

select.form-input:hover {
  border-color: var(--color-primary);
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
