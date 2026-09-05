<template>
  <div class="layout">
    <div class="topbar">
      <div class="topbar-accent"></div>
      <div class="topbar-main">
        <div class="topbar-logo">
          <div class="logo-icon">🤖</div>
          <span class="logo-text">数科院AI资产管理平台</span>
        </div>
        <div class="topbar-right">
          <div class="user-info">
            <div class="user-avatar">{{ userInitial }}</div>
            <span class="user-name">{{ auth.user?.displayName }}</span>
            <button class="btn-logout" @click="handleLogout">登出</button>
          </div>
        </div>
      </div>
    </div>

    <div class="main-container">
      <aside class="sidebar">
        <nav class="sidebar-nav">
          <router-link to="/" class="nav-item" exact-active-class="active">
            <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
              <polyline points="9 22 9 12 15 12 15 22"/>
            </svg>
            <span>首页</span>
          </router-link>

          <router-link to="/assets" class="nav-item" active-class="active">
            <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
            </svg>
            <span>资产库</span>
          </router-link>

          <router-link to="/projects" class="nav-item" active-class="active">
            <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="3" width="7" height="7"/>
              <rect x="14" y="3" width="7" height="7"/>
              <rect x="14" y="14" width="7" height="7"/>
              <rect x="3" y="14" width="7" height="7"/>
            </svg>
            <span>项目管理</span>
          </router-link>

          <router-link to="/teams" class="nav-item" active-class="active">
            <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
              <circle cx="9" cy="7" r="4"/>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
            </svg>
            <span>团队管理</span>
          </router-link>

          <router-link to="/statistics" class="nav-item" active-class="active">
            <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"/>
            </svg>
            <span>平台统计</span>
          </router-link>

          <router-link v-if="auth.user?.role === 'APPROVER' || auth.user?.role === 'ADMIN'" to="/approvals" class="nav-item" active-class="active">
            <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"/>
            </svg>
            <span>审批管理</span>
          </router-link>

          <router-link v-if="auth.user?.role === 'ADMIN'" to="/users" class="nav-item" active-class="active">
            <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
              <circle cx="8.5" cy="7" r="4"/>
              <polyline points="17 11 19 13 23 9"/>
            </svg>
            <span>用户管理</span>
          </router-link>
        </nav>
      </aside>

      <main class="content">
        <slot></slot>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()

const userInitial = computed(() => {
  const name = auth.user?.displayName || auth.user?.username || 'U'
  return name.charAt(0).toUpperCase()
})

const handleLogout = async () => {
  await auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.topbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1000;
  background: var(--color-bg-1);
}

.topbar-accent {
  height: 12px;
  background: var(--color-primary);
}

.topbar-main {
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--sp-24);
  border-bottom: 1px solid var(--color-border);
}

.topbar-logo {
  display: flex;
  align-items: center;
  gap: var(--sp-12);
  width: var(--sidebar-width);
}

.logo-icon {
  font-size: 24px;
}

.logo-text {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
  white-space: nowrap;
}

.topbar-right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  gap: var(--sp-12);
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--color-primary);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
}

.user-name {
  font-size: 14px;
  color: var(--color-text-primary);
}

.btn-logout {
  padding: var(--sp-6) var(--sp-16);
  font-size: 14px;
  color: var(--color-text-secondary);
  background: transparent;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-8);
  cursor: pointer;
  transition: all 0.2s;
}

.btn-logout:hover {
  color: var(--color-text-primary);
  background: var(--color-bg-2);
}

.main-container {
  display: flex;
  margin-top: var(--topbar-height);
  min-height: calc(100vh - var(--topbar-height));
}

.sidebar {
  width: var(--sidebar-width);
  background: var(--color-primary);
  position: fixed;
  left: 0;
  top: var(--topbar-height);
  bottom: 0;
  overflow-y: auto;
}

.sidebar-nav {
  padding: var(--sp-16) 0;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: var(--sp-12);
  padding: var(--sp-12) var(--sp-16);
  color: rgba(255, 255, 255, 0.85);
  text-decoration: none;
  transition: all 0.2s;
  height: 48px;
  font-size: 14px;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #FFFFFF;
}

.nav-item.active {
  background: rgba(255, 255, 255, 0.15);
  color: #FFFFFF;
  font-weight: 500;
}

.nav-icon {
  width: 16px;
  height: 16px;
}

.content {
  flex: 1;
  margin-left: var(--sidebar-width);
  padding: var(--sp-24);
  background: var(--color-bg-2);
}
</style>
