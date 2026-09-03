<template>
  <div class="login-page">
    <div class="login-container">
      <div class="login-header">
        <div class="logo">🤖</div>
        <h1 class="title">数科院AI资产管理平台</h1>
        <p class="subtitle">AI 资产管理系统</p>
      </div>

      <form @submit.prevent="handleLogin" class="login-form">
        <div class="form-group">
          <label class="form-label">用户名</label>
          <input
            v-model="username"
            type="text"
            class="form-input"
            placeholder="请输入用户名"
            required
            autofocus
            autocomplete="username"
          />
        </div>

        <div class="form-group">
          <label class="form-label">密码</label>
          <input
            v-model="password"
            type="password"
            class="form-input"
            placeholder="请输入密码"
            required
            autocomplete="current-password"
          />
        </div>

        <div v-if="error" class="error-message">
          {{ error }}
        </div>

        <button type="submit" class="btn-login" :disabled="auth.loading">
          {{ auth.loading ? '登录中...' : '登录' }}
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

const username = ref('')
const password = ref('')
const error = ref('')

const handleLogin = async () => {
  error.value = ''

  try {
    await auth.login(username.value, password.value)

    if (auth.user?.mustChangePassword) {
      router.push('/change-password')
    } else {
      router.push('/')
    }
  } catch (err: any) {
    error.value = err.message || '登录失败，请检查用户名和密码'
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--color-primary-lighter) 0%, var(--color-primary) 100%);
  padding: var(--sp-24);
}

.login-container {
  width: 100%;
  max-width: 420px;
  background: var(--color-bg-1);
  border-radius: var(--radius-16);
  padding: var(--sp-24) var(--sp-24) var(--sp-24);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
}

.login-header {
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

.login-form {
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

.btn-login {
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

.btn-login:hover:not(:disabled) {
  background: var(--color-primary-dark);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(27, 170, 127, 0.3);
}

.btn-login:active:not(:disabled) {
  transform: translateY(0);
}

.btn-login:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
