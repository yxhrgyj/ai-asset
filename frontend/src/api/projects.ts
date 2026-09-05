import { apiClient } from './client'

export interface Project {
  id: string
  name: string
  slug: string
  description: string | null
  ownerUserId: string
  teamId: string | null
  techStacks: string[]
  archived: boolean
  createdAt: string
  updatedAt: string
}

export interface CreateProjectRequest {
  name: string
  slug: string
  description?: string
  teamId?: string | null
  techStacks: string[]
}

export interface UpdateProjectRequest {
  name?: string
  description?: string
  techStacks?: string[]
}

export interface ProjectRule {
  id: string
  projectId: string
  assetId: string
  assetVersionId: string
  enabled: boolean
  addedBy: string
  createdAt: string
  updatedAt: string
}

export interface MergedRulesResult {
  orgCount: number
  techStackCount: number
  projectCount: number
  mergedCount: number
  rules: MergedRuleItem[]
}

export interface MergedRuleItem {
  ruleKey: string
  title: string
  body: string
  level: 'REQUIRED' | 'RECOMMENDED'
  pathGlobs: string[]
  sortOrder: number
}

export const projectsApi = {
  async list(): Promise<Project[]> {
    return apiClient.get<Project[]>('/projects')
  },

  async create(data: CreateProjectRequest): Promise<Project> {
    return apiClient.post<Project>('/projects', data)
  },

  async get(id: string): Promise<Project> {
    return apiClient.get<Project>(`/projects/${id}`)
  },

  async update(id: string, data: UpdateProjectRequest): Promise<Project> {
    return apiClient.put<Project>(`/projects/${id}`, data)
  },

  async archive(id: string): Promise<void> {
    await apiClient.delete(`/projects/${id}`)
  },

  async listRules(id: string): Promise<ProjectRule[]> {
    return apiClient.get<ProjectRule[]>(`/projects/${id}/rules`)
  },

  async addRule(id: string, assetVersionId: string): Promise<ProjectRule> {
    return apiClient.post<ProjectRule>(`/projects/${id}/rules`, { assetVersionId })
  },

  async removeRule(projectId: string, assetVersionId: string): Promise<void> {
    await apiClient.delete(`/projects/${projectId}/rules/${assetVersionId}`)
  },

  async getMergedRules(id: string): Promise<MergedRulesResult> {
    return apiClient.get<MergedRulesResult>(`/projects/${id}/merged-rules`)
  }
}
