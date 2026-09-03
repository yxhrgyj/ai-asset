import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import Login from '../views/Login.vue'
import ChangePassword from '../views/ChangePassword.vue'
import Home from '../views/Home.vue'
import Teams from '../views/Teams.vue'
import Users from '../views/Users.vue'
import Assets from '../views/Assets.vue'

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
      name: 'home',
      component: Home
    },
    {
      path: '/teams',
      name: 'teams',
      component: Teams
    },
    {
      path: '/users',
      name: 'users',
      component: Users,
      meta: { adminOnly: true }
    },
    {
      path: '/assets',
      name: 'assets',
      component: Assets
    },
    {
      path: '/assets/:id',
      name: 'asset-detail',
      component: () => import('../views/AssetDetail.vue')
    }
  ]
})

router.beforeEach(async (to, from, next) => {
  const auth = useAuthStore()

  if (!auth.user && !to.meta.public) {
    await auth.fetchMe()
  }

  if (!to.meta.public && !auth.user) {
    next({ name: 'login' })
  } else if (to.name === 'login' && auth.user) {
    next({ name: 'home' })
  } else if (auth.user?.mustChangePassword && to.name !== 'change-password') {
    next({ name: 'change-password' })
  } else if (to.meta.adminOnly && auth.user?.role !== 'ADMIN') {
    next({ name: 'home' })
  } else {
    next()
  }
})

export default router
