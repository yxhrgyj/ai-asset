<template>
  <div class="admin-layout">
    <header class="admin-topbar">
      <div class="admin-topbar-accent"></div>
      <div class="admin-topbar-main">
        <button class="admin-menu-toggle" type="button" aria-label="打开管理导航" @click="sidebarOpen = !sidebarOpen">☰</button>
        <RouterLink to="/admin" class="admin-logo">
          <span class="admin-logo-mark">AI</span>
          <span>资产管理后台</span>
        </RouterLink>
        <div class="admin-user-info">
          <span class="admin-avatar">{{ userInitial }}</span>
          <span class="admin-user-name">{{ auth.user?.displayName }}</span>
          <button class="admin-logout" type="button" @click="handleLogout">退出</button>
        </div>
      </div>
    </header>

    <div class="admin-main-container">
      <button v-if="sidebarOpen" class="admin-sidebar-backdrop" type="button" aria-label="关闭导航" @click="sidebarOpen = false"></button>
      <aside class="admin-sidebar" :class="{ 'is-open': sidebarOpen }">
        <div class="admin-sidebar-head">
          <span class="admin-sidebar-label">工作台</span>
          <RouterLink to="/" class="admin-portal-link" @click="sidebarOpen = false">返回门户 ↗</RouterLink>
        </div>
        <nav class="admin-sidebar-nav">
          <RouterLink v-for="item in navItems" :key="item.to" :to="item.to" class="admin-nav-item" :active-class="item.to === '/admin' ? '' : 'is-active'" exact-active-class="is-active" @click="sidebarOpen = false">
            <span class="admin-nav-icon" aria-hidden="true">{{ item.icon }}</span>
            <span>{{ item.label }}</span>
          </RouterLink>
        </nav>
      </aside>
      <main class="admin-content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const sidebarOpen = ref(false)
const userInitial = computed(() => (auth.user?.displayName || auth.user?.username || 'U').slice(0, 1).toUpperCase())
const navItems = computed(() => [
  { to: '/admin', label: '首页', icon: '⌂' },
  { to: '/admin/assets', label: '资产库', icon: '▤' },
  { to: '/admin/projects', label: '项目管理', icon: '□' },
  { to: '/admin/teams', label: '团队管理', icon: '◎' },
  { to: '/admin/statistics', label: '平台统计', icon: '◒' },
  ...(auth.canApprove() ? [{ to: '/admin/approvals', label: '审批管理', icon: '✓' }] : []),
  ...(auth.isAdmin() ? [{ to: '/admin/users', label: '用户管理', icon: '◌' }] : [])
])

async function handleLogout() {
  await auth.logout()
  await router.push('/login')
}
</script>
