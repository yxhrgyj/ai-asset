export interface Team {
  id: string
  name: string
  createdAt: string
}

export interface CreateTeamRequest {
  name: string
}

export interface UpdateTeamRequest {
  name: string
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

export const teamApi = {
  list: () => request<Team[]>('/teams'),
  create: (req: CreateTeamRequest) => request<Team>('/teams', {
    method: 'POST',
    body: JSON.stringify(req)
  }),
  update: (id: string, req: UpdateTeamRequest) => request<Team>(`/teams/${id}`, {
    method: 'PUT',
    body: JSON.stringify(req)
  }),
  delete: (id: string) => request<void>(`/teams/${id}`, {
    method: 'DELETE'
  })
}
