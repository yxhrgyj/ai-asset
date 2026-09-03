export interface User {
  id: string
  username: string
  displayName: string
  email: string | null
  role: 'USER' | 'AUTHOR' | 'APPROVER' | 'ADMIN'
  status: 'ACTIVE' | 'DISABLED'
  teamId: string | null
  mustChangePassword: boolean
  lastLoginAt: string | null
  createdAt: string
}

export interface CreateUserRequest {
  username: string
  displayName: string
  email: string | null
  password: string
  role: 'USER' | 'AUTHOR' | 'APPROVER' | 'ADMIN'
  teamId: string | null
}

export interface UpdateUserRequest {
  displayName: string
  email: string | null
  role: 'USER' | 'AUTHOR' | 'APPROVER' | 'ADMIN'
  status: 'ACTIVE' | 'DISABLED'
  teamId: string | null
}

export interface ResetPasswordRequest {
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

  if (res.status === 204) {
    return undefined as T
  }

  return res.json()
}

export const userApi = {
  list: () => request<User[]>('/users'),
  get: (id: string) => request<User>(`/users/${id}`),
  create: (req: CreateUserRequest) => request<User>('/users', {
    method: 'POST',
    body: JSON.stringify(req)
  }),
  update: (id: string, req: UpdateUserRequest) => request<User>(`/users/${id}`, {
    method: 'PUT',
    body: JSON.stringify(req)
  }),
  resetPassword: (id: string, req: ResetPasswordRequest) => request<void>(`/users/${id}/reset-password`, {
    method: 'POST',
    body: JSON.stringify(req)
  })
}
