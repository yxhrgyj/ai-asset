<template>
  <div class="portal-layout">
    <header class="portal-header">
      <div class="portal-header-inner">
        <RouterLink to="/" class="portal-brand" aria-label="数科院 AI 资产管理平台首页">
          <img class="system-brand-mark" src="/brand/logo-mark.png" alt="" width="56" height="36" />
          <strong>数科院 AI 资产管理平台</strong>
        </RouterLink>

        <button ref="menuToggle" class="portal-menu-toggle" type="button" aria-label="打开导航" aria-controls="portal-navigation" :aria-expanded="menuOpen" @click="menuOpen = !menuOpen">
          <X v-if="menuOpen" :size="20" aria-hidden="true" /><Menu v-else :size="20" aria-hidden="true" />
        </button>

        <PortalNav :open="menuOpen" @close="menuOpen = false" />

        <div class="portal-header-actions">
          <RouterLink v-if="auth.user" to="/admin" class="portal-admin-link">管理中心</RouterLink>
          <RouterLink v-else to="/login" class="portal-admin-link">管理中心</RouterLink>
          <button v-if="auth.user" type="button" class="portal-user-chip" :aria-label="`${auth.user.displayName || auth.user.username}，退出登录`" title="退出登录" @click="logout">
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
        <span>数科院 AI 资产管理平台</span>
        <span>公开浏览，登录后下载</span>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { Menu, X } from '@lucide/vue'
import PortalNav from './PortalNav.vue'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const menuOpen = ref(false)
const menuToggle = ref<HTMLButtonElement | null>(null)
const userInitial = computed(() => (auth.user?.displayName || auth.user?.username || 'U').slice(0, 1).toUpperCase())

onMounted(() => {
  if (!auth.user) void auth.fetchMe()
  window.addEventListener('keydown', closeWithEscape)
})
onBeforeUnmount(() => window.removeEventListener('keydown', closeWithEscape))
watch(() => route.fullPath, () => { menuOpen.value = false })

function closeWithEscape(event: KeyboardEvent) {
  if (event.key === 'Escape' && menuOpen.value && menuToggle.value?.getClientRects().length) {
    menuOpen.value = false
    menuToggle.value.focus()
  }
}

async function logout() {
  await auth.logout()
  menuOpen.value = false
  await router.push('/')
}
</script>
