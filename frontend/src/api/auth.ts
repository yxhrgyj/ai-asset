export interface User {
  id: string
  username: string
  displayName: string
  role: 'USER' | 'AUTHOR' | 'APPROVER' | 'ADMIN'
  mustChangePassword: boolean
}

export interface LoginRequest {
  username: string
  password: string
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

const BASE = '/api'

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    ...options,
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers
    }
  })

  if (!res.ok) {
    const text = await res.text()
    throw new Error(text || `HTTP ${res.status}`)
  }

  // 204 No Content 不需要解析 JSON
  if (res.status === 204) {
    return undefined as T
  }

  return res.json()
}

export const authApi = {
  login: (req: LoginRequest) => request<User>('/auth/login', {
    method: 'POST',
    body: JSON.stringify(req)
  }),

  logout: () => request<void>('/auth/logout', { method: 'POST' }),

  me: () => request<User>('/auth/me'),

  changePassword: (req: ChangePasswordRequest) => request<void>('/auth/change-password', {
    method: 'POST',
    body: JSON.stringify(req)
  })
}
