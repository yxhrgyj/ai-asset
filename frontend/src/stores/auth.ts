import { defineStore } from 'pinia'
import { ref } from 'vue'
import { authApi, type User } from '../api/auth'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const loading = ref(false)

  async function login(username: string, password: string) {
    loading.value = true
    try {
      user.value = await authApi.login({ username, password })
    } finally {
      loading.value = false
    }
  }

  async function logout() {
    await authApi.logout()
    user.value = null
  }

  async function fetchMe() {
    try {
      user.value = await authApi.me()
    } catch {
      user.value = null
    }
  }

  async function changePassword(currentPassword: string, newPassword: string) {
    await authApi.changePassword({ currentPassword, newPassword })
    if (user.value) {
      user.value.mustChangePassword = false
    }
  }

  function canAuthor(): boolean {
    if (!user.value) return false
    return ['AUTHOR', 'APPROVER', 'ADMIN'].includes(user.value.role)
  }

  function canApprove(): boolean {
    if (!user.value) return false
    return ['APPROVER', 'ADMIN'].includes(user.value.role)
  }

  function isAdmin(): boolean {
    return user.value?.role === 'ADMIN'
  }

  return { user, loading, login, logout, fetchMe, changePassword, canAuthor, canApprove, isAdmin }
})
