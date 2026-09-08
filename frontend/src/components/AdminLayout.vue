<template>
  <div class="admin-layout" :class="{ 'is-collapsed': collapsed }" @keydown.esc="handleEscape">
    <header class="admin-topbar">
      <div class="admin-topbar-accent"></div>
      <div class="admin-topbar-main">
        <button ref="menuToggle" class="admin-menu-toggle" type="button" :aria-label="collapsed ? '展开管理导航' : '收起管理导航'" :aria-expanded="!collapsed" aria-controls="admin-navigation" :title="collapsed ? '展开管理导航' : '收起管理导航'" @click="collapsed = !collapsed"><PanelLeftOpen v-if="collapsed" :size="20" aria-hidden="true" /><PanelLeftClose v-else :size="20" aria-hidden="true" /></button>
        <RouterLink to="/admin" class="admin-logo">
          <img class="system-brand-mark" src="/brand/logo-mark.png" alt="" width="56" height="36" />
          <span>数科院AI资产管理后台</span>
        </RouterLink>
        <div class="admin-user-info">
          <span class="admin-avatar">{{ userInitial }}</span>
          <span class="admin-user-name">{{ auth.user?.displayName }}</span>
          <button class="admin-logout" type="button" @click="handleLogout">退出</button>
        </div>
      </div>
    </header>

    <div class="admin-main-container">
      <button v-if="isSmallScreen && !collapsed" class="admin-sidebar-backdrop" type="button" tabindex="-1" aria-label="关闭导航" @click="closeSidebar"></button>
      <aside id="admin-navigation" class="admin-sidebar" @scroll="tooltip = null">
        <div class="admin-sidebar-head">
          <RouterLink to="/" class="admin-portal-link" aria-label="返回门户" @click="closeOnSmallScreen" @pointerenter="showTooltip($event, '返回门户')" @pointerleave="tooltip = null" @focus="showTooltip($event, '返回门户')" @blur="tooltip = null"><ExternalLink :size="16" aria-hidden="true" /><span class="nav-label">返回门户</span></RouterLink>
        </div>
        <nav class="admin-sidebar-nav" aria-label="管理导航">
          <RouterLink v-for="item in navItems" :key="item.to" :to="item.to" :aria-label="item.label" class="admin-nav-item" :class="{ 'is-active': isActive(item.to) }" :aria-current="isActive(item.to) ? 'page' : undefined" active-class="" exact-active-class="" @click="closeOnSmallScreen" @pointerenter="showTooltip($event, item.label)" @pointerleave="tooltip = null" @focus="showTooltip($event, item.label)" @blur="tooltip = null">
            <component :is="item.icon" class="admin-nav-icon" :size="16" aria-hidden="true" />
            <span class="nav-label">{{ item.label }}</span>
          </RouterLink>
        </nav>
      </aside>
      <main class="admin-content" :inert="isSmallScreen && !collapsed">
        <RouterView />
      </main>
    </div>
    <Teleport to="body">
      <span v-if="tooltip && collapsed" class="nav-tooltip" role="tooltip" :style="{ top: `${tooltip.top}px`, left: `${tooltip.left}px` }">{{ tooltip.label }}</span>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onBeforeUnmount } from 'vue'
import { LayoutDashboard, Library, FolderKanban, Users, UserRoundCog, PanelLeftOpen, PanelLeftClose, ExternalLink } from '@lucide/vue'
import { RouterLink, RouterView, useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const smallScreen = window.matchMedia('(max-width: 1023px)')
const isSmallScreen = ref(smallScreen.matches)
const collapsed = ref(smallScreen.matches)
const menuToggle = ref<HTMLButtonElement | null>(null)
const tooltip = ref<{ label: string; top: number; left: number } | null>(null)
const syncSidebar = () => {
  isSmallScreen.value = smallScreen.matches
  collapsed.value = smallScreen.matches
  tooltip.value = null
}
smallScreen.addEventListener('change', syncSidebar)
onBeforeUnmount(() => smallScreen.removeEventListener('change', syncSidebar))
function closeOnSmallScreen() {
  tooltip.value = null
  if (smallScreen.matches) collapsed.value = true
}
function closeSidebar() {
  collapsed.value = true
  tooltip.value = null
  menuToggle.value?.focus()
}
function handleEscape(event: KeyboardEvent) {
  tooltip.value = null
  if (!isSmallScreen.value || collapsed.value) return
  event.preventDefault()
  closeSidebar()
}
function showTooltip(event: Event, label: string) {
  if (!collapsed.value) return
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  tooltip.value = { label, top: Math.min(window.innerHeight - 20, Math.max(84, rect.top + rect.height / 2)), left: 72 }
}
function isActive(target: string) {
  const currentPath = route.matched[route.matched.length - 1]?.path || route.path
  return currentPath === target || (target !== '/admin' && currentPath.startsWith(`${target}/`))
}
const userInitial = computed(() => (auth.user?.displayName || auth.user?.username || 'U').slice(0, 1).toUpperCase())
const navItems = computed(() => [
  { to: '/admin', label: '工作台', icon: LayoutDashboard },
  { to: '/admin/assets', label: '资产库', icon: Library },
  { to: '/admin/projects', label: '项目管理', icon: FolderKanban },
  { to: '/admin/teams', label: '团队管理', icon: Users },
  ...(auth.isAdmin() ? [{ to: '/admin/users', label: '用户管理', icon: UserRoundCog }] : [])
])

async function handleLogout() {
  await auth.logout()
  await router.push('/login')
}
</script>

<style scoped>
.admin-layout { min-height: 100vh; background: var(--color-bg-2); --sidebar-width: 180px; --sidebar-bg: #086b50; --radius-12: 8px; --radius-16: 8px; --sp-32: 32px; --sp-48: 48px; }
.admin-layout.is-collapsed { --sidebar-width: 64px; }
.admin-topbar { position: fixed; top: 0; right: 0; left: 0; z-index: 30; background: var(--color-bg-1); }
.admin-topbar-accent { height: 12px; background: var(--color-primary); }
.admin-topbar-main { display: flex; align-items: center; height: 52px; padding: 0 24px; gap: 12px; border-bottom: 1px solid var(--color-border); }
.admin-logo { display: inline-flex; align-items: center; gap: 10px; color: var(--color-text-primary); font-weight: 600; font-size: 16px; min-width: 0; text-decoration: none; }
.admin-logo > span { line-height: 20px; overflow-wrap: anywhere; }
.admin-menu-toggle { display: grid; place-items: center; width: 32px; height: 32px; color: var(--color-text-primary); flex-shrink: 0; border: 0; border-radius: 4px; background: transparent; }
.admin-menu-toggle:hover { background: var(--color-bg-3); }
.admin-user-info { display: flex; align-items: center; gap: 10px; margin-left: auto; flex-shrink: 0; }
.admin-avatar { display: grid; place-items: center; width: 32px; height: 32px; border-radius: 50%; color: #fff; background: var(--sidebar-bg); font-size: 12px; font-weight: 600; }
.admin-user-name { color: var(--color-text-primary); font-size: 13px; max-width: 160px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.admin-logout { padding: 6px 12px; color: var(--color-text-secondary); background: transparent; border: 1px solid var(--color-border); border-radius: 4px; font-size: 12px; }
.admin-logout:hover { color: var(--color-text-primary); background: var(--color-bg-2); }
.admin-main-container { display: flex; min-height: 100vh; padding-top: 64px; }
.admin-sidebar { position: fixed; top: 64px; bottom: 0; left: 0; z-index: 25; width: var(--sidebar-width); padding: 12px 0; background: var(--sidebar-bg); overflow-y: auto; scrollbar-width: thin; scrollbar-color: #8ae1bc transparent; }
.admin-sidebar-head { margin: 0 8px; padding: 0 0 12px; border-bottom: 1px solid rgba(255,255,255,.15); }
.admin-portal-link { display: flex; align-items: center; gap: 12px; padding: 12px 8px; width: 100%; color: #fff; font-size: 12px; text-decoration: none; }
.admin-sidebar-nav { display: flex; flex-direction: column; gap: 0; padding-top: 12px; }
.admin-nav-item { display: flex; align-items: center; height: 48px; min-height: 48px; padding: 0 16px; gap: 12px; border: 0; color: #fff; font-size: 14px; text-decoration: none; }
.admin-nav-item:hover, .admin-portal-link:hover { background: rgba(255,255,255,.08); }
.admin-nav-item.is-active { background: rgba(255,255,255,.15); color: #fff; box-shadow: inset 3px 0 #8ae1bc; font-weight: 600; }
.admin-nav-icon { width: 16px; height: 16px; flex-shrink: 0; color: inherit; }
.admin-content { min-width: 0; width: calc(100% - var(--sidebar-width)); margin-left: var(--sidebar-width); padding: 20px 24px; }
.admin-sidebar-backdrop { display: none; }
.is-collapsed .nav-label { display: none; }
.is-collapsed .admin-nav-item, .is-collapsed .admin-portal-link { justify-content: center; }
.nav-tooltip { position: fixed; transform: translateY(-50%); padding: 6px 10px; background: #1d2129; color: white; border-radius: 4px; white-space: nowrap; pointer-events: none; font-size: 12px; z-index: 50; }
.admin-layout :deep(button:focus-visible), .admin-layout :deep(a:focus-visible) { outline: 2px solid #086b50; outline-offset: 3px; }
.admin-sidebar a:focus-visible { outline: 2px solid white; outline-offset: -3px; }
@media (max-width: 1023px) {
  .admin-content { width: calc(100% - 64px); margin-left: 64px; }
  .admin-sidebar-backdrop { display: block; position: fixed; inset: 64px 0 0; z-index: 24; background: rgba(0,0,0,.35); }
}
@media (max-width: 680px) {
  .admin-topbar-main { padding: 0 8px; gap: 6px; }
  .admin-logo { font-size: 13px; gap: 6px; }
  .admin-user-name, .admin-avatar { display: none; }
  .system-brand-mark { width: 40px; height: 28px; }
  .admin-content { padding: 20px 12px; }
  .admin-logout { white-space: nowrap; }
}
</style>
