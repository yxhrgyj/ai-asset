import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import Login from '../views/Login.vue'
import ChangePassword from '../views/ChangePassword.vue'
import Teams from '../views/Teams.vue'
import Users from '../views/Users.vue'
import Assets from '../views/Assets.vue'
import Approvals from '../views/Approvals.vue'
import Statistics from '../views/Statistics.vue'
import Projects from '../views/Projects.vue'
import PortalLayout from '../components/PortalLayout.vue'
import AdminLayout from '../components/AdminLayout.vue'
import { safeReturnUrl } from '../lib/portal-navigation'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: Login,
      meta: { public: true }
    },
    {
      path: '/change-password',
      name: 'change-password',
      component: ChangePassword
    },
    {
      path: '/',
      component: PortalLayout,
      meta: { public: true },
      children: [
        { path: '', name: 'portal-home', component: () => import('../views/PortalHome.vue') },
        { path: 'assets-public', name: 'assets-public', component: () => import('../views/AssetsPublic.vue') },
        { path: 'assets-public/:id', name: 'asset-public-detail', component: () => import('../views/AssetPublicDetail.vue') },
        { path: 'statistics-public', name: 'statistics-public', component: () => import('../views/StatisticsPublic.vue') },
        { path: 'about', name: 'about', component: () => import('../views/About.vue') },
        { path: 'help', name: 'help', component: () => import('../views/Help.vue') }
      ]
    },
    {
      path: '/admin',
      component: AdminLayout,
      meta: { layout: 'admin' },
      children: [
        { path: '', name: 'admin-home', component: Statistics },
        { path: 'teams', name: 'admin-teams', component: Teams },
        { path: 'users', name: 'admin-users', component: Users, meta: { adminOnly: true } },
        { path: 'assets', name: 'admin-assets', component: Assets },
        { path: 'assets/:id', name: 'admin-asset-detail', component: () => import('../views/AssetDetail.vue') },
        { path: 'approvals', name: 'admin-approvals', component: Approvals, meta: { approverOnly: true } },
        { path: 'statistics', name: 'admin-statistics', redirect: to => ({ path: '/admin', query: to.query, hash: to.hash }) },
        { path: 'projects', name: 'admin-projects', component: Projects },
        { path: 'projects/:id', name: 'admin-project-detail', component: () => import('../views/ProjectDetail.vue') }
      ]
    },
    { path: '/teams', redirect: '/admin/teams' },
    { path: '/users', redirect: '/admin/users' },
    { path: '/assets', redirect: '/admin/assets' },
    { path: '/assets/:id', redirect: to => ({ path: `/admin/assets/${to.params.id}`, query: to.query, hash: to.hash }) },
    { path: '/approvals', redirect: '/admin/approvals' },
    { path: '/statistics', redirect: '/admin/statistics' },
    { path: '/projects', redirect: '/admin/projects' },
    { path: '/projects/:id', redirect: to => ({ path: `/admin/projects/${to.params.id}`, query: to.query, hash: to.hash }) }
  ]
})

router.beforeEach(async (to, _from, next) => {
  const auth = useAuthStore()

  if (!auth.user && !to.meta.public) {
    await auth.fetchMe()
  }

  if (!to.meta.public && !auth.user) {
    next({ name: 'login', query: { returnUrl: to.fullPath } })
  } else if (auth.user?.mustChangePassword && to.name !== 'change-password') {
    next({ name: 'change-password', query: { returnUrl: safeReturnUrl(to.name === 'login' ? to.query.returnUrl : to.fullPath) } })
  } else if (to.name === 'login' && auth.user) {
    next(safeReturnUrl(to.query.returnUrl))
  } else if (to.meta.adminOnly && auth.user?.role !== 'ADMIN') {
    next({ name: 'admin-home' })
  } else if (to.meta.approverOnly && auth.user?.role !== 'APPROVER' && auth.user?.role !== 'ADMIN') {
    next({ name: 'admin-home' })
  } else {
    next()
  }
})

export default router
