import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import { auth, fetchMe } from './stores/auth.js'

const routes = [
  { path: '/login', component: () => import('./views/Login.vue') },
  { path: '/change-password', component: () => import('./views/ChangePassword.vue') },
  { path: '/', component: () => import('./views/Home.vue'), meta: { requiresAuth: true } },
  // /assets/new 必须排在 /assets/:id 之前，否则 new 会被当成 id 匹配掉。
  {
    path: '/assets/new',
    name: 'asset-new',
    component: () => import('./views/AssetEdit.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/assets/:id',
    name: 'asset-detail',
    component: () => import('./views/AssetDetail.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/assets/:id/edit',
    name: 'asset-edit',
    component: () => import('./views/AssetEdit.vue'),
    meta: { requiresAuth: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  // 首次进入或刷新后状态未知，先问后端。
  if (!auth.known) {
    await fetchMe()
  }

  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return { path: '/login' }
  }

  if (to.path === '/login' && auth.isLoggedIn) {
    return { path: '/' }
  }

  // 首登强制改口令：除改口令页自身外，一律拦到改口令页。
  if (auth.isLoggedIn && auth.mustChangePassword && to.path !== '/change-password') {
    return { path: '/change-password' }
  }

  // 已改过口令的人不需要看这一页。
  if (to.path === '/change-password' && auth.isLoggedIn && !auth.mustChangePassword) {
    return { path: '/' }
  }

  return true
})

createApp(App).use(router).mount('#app')
