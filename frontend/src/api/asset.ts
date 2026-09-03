export type AssetType = 'RULE' | 'SKILL' | 'DOCUMENT'
export type AssetScope = 'ORGANIZATION' | 'TECH_STACK' | 'PROJECT'
export type VersionStatus = 'DRAFT' | 'PENDING' | 'REJECTED' | 'PUBLISHED' | 'DEPRECATED' | 'WITHDRAWN'

export interface Asset {
  id: string
  type: AssetType
  name: string
  slug: string
  summary?: string
  category?: string
  tags: string[]
  scope: AssetScope
  techStack?: string
  ownerUserId: string
  ownerName?: string
  teamId?: string
  teamName?: string
  archived: boolean
  downloadCount: number
  createdAt: string
  updatedAt: string
  latestVersion?: number
  publishedVersion?: number
}

export interface AssetVersion {
  id: string
  assetId: string
  versionNo: number
  status: VersionStatus
  contentHash?: string
  changelog?: string
  createdBy: string
  createdByName?: string
  publishedAt?: string
  createdAt: string
  updatedAt: string
}

export interface AssetFile {
  id: string
  assetVersionId: string
  relativePath: string
  sizeBytes: number
  contentHash: string
  mimeType?: string
}

export interface AssetDetail {
  asset: Asset
  currentVersion?: AssetVersion
  body?: string
  versions: AssetVersion[]
  files: AssetFile[]
  canEdit: boolean
}

export interface ListResponse {
  items: Asset[]
  total: number
  page: number
  size: number
}

export interface CreateAssetRequest {
  type: AssetType
  name: string
  slug: string
  summary?: string
  category?: string
  tags?: string[]
  scope?: AssetScope
  techStack?: string
  teamId?: string
}

export interface SaveDraftRequest {
  body: string
  changelog?: string
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

export const assetApi = {
  list: async (params?: {
    q?: string
    type?: AssetType
    scope?: AssetScope
    tag?: string
    page?: number
    size?: number
  }): Promise<ListResponse> => {
    const searchParams = new URLSearchParams()
    if (params?.q) searchParams.append('q', params.q)
    if (params?.type) searchParams.append('type', params.type)
    if (params?.scope) searchParams.append('scope', params.scope)
    if (params?.tag) searchParams.append('tag', params.tag)
    if (params?.page !== undefined) searchParams.append('page', params.page.toString())
    if (params?.size !== undefined) searchParams.append('size', params.size.toString())

    const query = searchParams.toString()
    return request<ListResponse>(`/assets${query ? '?' + query : ''}`)
  },

  get: async (id: string, versionNo?: number): Promise<AssetDetail> => {
    const query = versionNo ? `?versionNo=${versionNo}` : ''
    return request<AssetDetail>(`/assets/${id}${query}`)
  },

  create: async (data: CreateAssetRequest): Promise<Asset> => {
    return request<Asset>('/assets', {
      method: 'POST',
      body: JSON.stringify(data)
    })
  },

  updateMeta: async (id: string, data: Partial<CreateAssetRequest>): Promise<Asset> => {
    return request<Asset>(`/assets/${id}`, {
      method: 'PATCH',
      body: JSON.stringify(data)
    })
  },

  saveDraft: async (id: string, data: SaveDraftRequest): Promise<AssetVersion> => {
    return request<AssetVersion>(`/assets/${id}/draft`, {
      method: 'PUT',
      body: JSON.stringify(data)
    })
  },

  newDraft: async (id: string): Promise<AssetVersion> => {
    return request<AssetVersion>(`/assets/${id}/versions`, {
      method: 'POST'
    })
  },

  publish: async (id: string): Promise<AssetVersion> => {
    return request<AssetVersion>(`/assets/${id}/publish`, {
      method: 'POST'
    })
  },

  archive: async (id: string, archived: boolean = true): Promise<void> => {
    return request<void>(`/assets/${id}/archive?archived=${archived}`, {
      method: 'POST'
    })
  },

  recordDownload: async (id: string, versionId?: string): Promise<void> => {
    const query = versionId ? `?versionId=${versionId}` : ''
    return request<void>(`/assets/${id}/download${query}`, {
      method: 'POST'
    })
  },

  uploadFile: async (id: string, file: File, relativePath?: string): Promise<AssetFile> => {
    const formData = new FormData()
    formData.append('file', file)
    if (relativePath) {
      formData.append('path', relativePath)
    }

    const res = await fetch(`${BASE}/assets/${id}/files`, {
      method: 'POST',
      credentials: 'include',
      body: formData
    })

    if (!res.ok) {
      const text = await res.text()
      throw new Error(text || `HTTP ${res.status}`)
    }

    return res.json()
  },

  deleteFile: async (assetId: string, fileId: string): Promise<void> => {
    return request<void>(`/assets/${assetId}/files/${fileId}`, {
      method: 'DELETE'
    })
  },

  downloadFile: (assetId: string, fileId: string): string => {
    return `${BASE}/assets/${assetId}/files/${fileId}`
  },

  downloadAllFiles: (assetId: string, versionId?: string): string => {
    const query = versionId ? `?versionId=${versionId}` : ''
    return `${BASE}/assets/${assetId}/files/download-all${query}`
  }
}
