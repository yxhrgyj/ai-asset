<template>
  <div class="change-password-page">
    <div class="change-password-container">
      <div class="header">
        <div class="logo">🔐</div>
        <h1 class="title">修改密码</h1>
        <p class="subtitle">首次登录需要修改初始密码</p>
      </div>

      <form @submit.prevent="handleSubmit" class="form">
        <div class="form-group">
          <label class="form-label">当前密码</label>
          <input
            v-model="currentPassword"
            type="password"
            class="form-input"
            placeholder="请输入当前密码"
            required
            autocomplete="current-password"
          />
        </div>

        <div class="form-group">
          <label class="form-label">新密码</label>
          <input
            v-model="newPassword"
            type="password"
            class="form-input"
            placeholder="请输入新密码（至少 8 位）"
            required
            autocomplete="new-password"
          />
        </div>

        <div class="form-group">
          <label class="form-label">确认新密码</label>
          <input
            v-model="confirmPassword"
            type="password"
            class="form-input"
            placeholder="请再次输入新密码"
            required
            autocomplete="new-password"
          />
        </div>

        <div v-if="error" class="error-message">
          {{ error }}
        </div>

        <button type="submit" class="btn-submit">
          确认修改
        </button>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const error = ref('')

const handleSubmit = async () => {
  error.value = ''

  if (newPassword.value.length < 8) {
    error.value = '新密码至少 8 位'
    return
  }

  if (newPassword.value !== confirmPassword.value) {
    error.value = '两次输入的密码不一致'
    return
  }

  try {
    await auth.changePassword(currentPassword.value, newPassword.value)
    router.push('/')
  } catch (err: any) {
    error.value = err.message || '修改失败'
  }
}
</script>

<style scoped>
.change-password-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--color-primary-lighter) 0%, var(--color-primary) 100%);
  padding: var(--sp-24);
}

.change-password-container {
  width: 100%;
  max-width: 420px;
  background: var(--color-bg-1);
  border-radius: var(--radius-16);
  padding: var(--sp-24);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
}

.header {
  text-align: center;
  margin-bottom: var(--sp-24);
}

.logo {
  font-size: 56px;
  margin-bottom: var(--sp-16);
}

.title {
  font-size: 32px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: var(--sp-8);
}

.subtitle {
  font-size: 14px;
  color: var(--color-text-secondary);
}

.form {
  display: flex;
  flex-direction: column;
  gap: var(--sp-20);
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: var(--sp-8);
}

.form-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-primary);
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

.form-input::placeholder {
  color: var(--color-text-tertiary);
}

.error-message {
  padding: var(--sp-12) var(--sp-16);
  background: var(--color-error-bg);
  color: var(--color-error);
  border-radius: var(--radius-8);
  font-size: 14px;
  border-left: 3px solid var(--color-error);
}

.btn-submit {
  width: 100%;
  height: 40px;
  background: var(--color-primary);
  color: white;
  font-size: 14px;
  font-weight: 500;
  border: none;
  border-radius: var(--radius-8);
  cursor: pointer;
  transition: all 0.2s;
  margin-top: var(--sp-8);
}

.btn-submit:hover {
  background: var(--color-primary-dark);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(27, 170, 127, 0.3);
}

.btn-submit:active {
  transform: translateY(0);
}
</style>
