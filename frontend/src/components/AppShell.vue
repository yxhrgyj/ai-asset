<script setup>
import { useRouter, RouterLink } from 'vue-router'
import { auth, logout } from '../stores/auth.js'

const router = useRouter()

// 角色显示名。后端返回枚举名，界面上给中文。
const ROLE_LABEL = {
  USER: '使用者',
  AUTHOR: '编写者',
  APPROVER: '审核者',
  ADMIN: '管理员'
}

async function handleLogout() {
  await logout()
  router.push('/login')
}
</script>

<template>
  <div class="shell">
    <header>
      <RouterLink to="/" class="brand">团队 AI 资产管理</RouterLink>
      <div class="who">
        <span class="name">{{ auth.user?.displayName }}</span>
        <span class="role">{{ ROLE_LABEL[auth.user?.role] ?? auth.user?.role }}</span>
        <button type="button" class="btn" @click="handleLogout">登出</button>
      </div>
    </header>

    <main>
      <slot />
    </main>
  </div>
</template>

<style scoped>
.shell {
  display: grid;
  grid-template-rows: auto 1fr;
  min-height: 100vh;
}

header {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-md);
  align-items: center;
  justify-content: space-between;
  padding: var(--spacing-md) var(--spacing-xl);
  background: var(--surface-1);
  border-bottom: 1px solid var(--surface-3);
}

.brand {
  font-size: clamp(1.05rem, 3vw, 1.35rem);
  font-weight: 600;
  color: var(--text);
  text-decoration: none;
  letter-spacing: -0.02em;
}

.who {
  display: flex;
  gap: var(--spacing-md);
  align-items: center;
  font-size: 0.875rem;
}

.role {
  padding: 0.15rem var(--spacing-sm);
  color: var(--accent);
  background: var(--surface-3);
  border-radius: var(--radius-full);
  font-size: 0.8125rem;
}

main {
  width: min(64rem, 100%);
  margin: 0 auto;
  padding: var(--spacing-xl);
}
</style>
