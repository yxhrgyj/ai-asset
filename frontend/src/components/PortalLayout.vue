<template>
  <div class="portal-layout">
    <header class="portal-header">
      <div class="portal-header-inner">
        <RouterLink to="/" class="portal-brand" aria-label="数科院 AI 资产门户">
          <img class="system-brand-mark" src="/brand/logo-mark.png" alt="" width="56" height="36" />
          <span>
            <strong>数科院</strong>
            <small>AI 资产门户</small>
          </span>
        </RouterLink>

        <button class="portal-menu-toggle" type="button" aria-label="打开导航" :aria-expanded="menuOpen" @click="menuOpen = !menuOpen">
          <span></span><span></span><span></span>
        </button>

        <PortalNav :open="menuOpen" @close="menuOpen = false" />

        <div class="portal-header-actions">
          <RouterLink v-if="auth.user" to="/admin" class="portal-admin-link">管理中心</RouterLink>
          <RouterLink v-else to="/login" class="portal-admin-link">进入管理中心</RouterLink>
          <button v-if="auth.user" type="button" class="portal-user-chip" @click="logout">
            <span class="portal-user-dot">{{ userInitial }}</span>
            <span>{{ auth.user.displayName }}</span>
          </button>
          <RouterLink v-else to="/login" class="portal-login-link">登录</RouterLink>
        </div>
      </div>
    </header>

    <main class="portal-main">
      <RouterView />
    </main>

    <footer class="portal-footer">
      <div class="portal-footer-inner">
        <div>
          <span class="portal-footer-kicker">AI ASSET LIBRARY</span>
          <p>让可复用的知识，成为团队共同的工作基础。</p>
        </div>
        <div class="portal-footer-links">
          <RouterLink to="/assets-public">资产库</RouterLink>
          <RouterLink to="/statistics-public">数据台</RouterLink>
          <RouterLink to="/about">关于平台</RouterLink>
        </div>
        <span class="portal-footer-copy">© 2026 数科院</span>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import PortalNav from './PortalNav.vue'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const menuOpen = ref(false)
const userInitial = computed(() => (auth.user?.displayName || auth.user?.username || 'U').slice(0, 1).toUpperCase())

onMounted(() => {
  if (!auth.user) void auth.fetchMe()
})

async function logout() {
  await auth.logout()
  menuOpen.value = false
  await router.push('/')
}
</script>
