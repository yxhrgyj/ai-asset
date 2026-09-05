import { apiClient } from './client'

export interface Team {
  id: string
  name: string
  createdAt: string
}

export const teamsApi = {
  async list(): Promise<Team[]> {
    return apiClient.get<Team[]>('/teams')
  },

  async get(id: string): Promise<Team> {
    return apiClient.get<Team>(`/teams/${id}`)
  },

  async create(data: { name: string }): Promise<Team> {
    return apiClient.post<Team>('/teams', data)
  },

  async update(id: string, data: { name: string }): Promise<Team> {
    return apiClient.put<Team>(`/teams/${id}`, data)
  },

  async delete(id: string): Promise<void> {
    await apiClient.delete(`/teams/${id}`)
  }
}
