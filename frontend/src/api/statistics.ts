export interface OverviewStats {
  totalAssets: number
  publishedAssets: number
  draftAssets: number
  totalDownloads: number
  totalUsers: number
  pendingApprovals: number
}

export interface PopularAsset {
  id: string
  name: string
  type: string
  slug: string
  downloadCount: number
  lastDownloadedAt: string
}

export interface ActiveUser {
  userId: string
  username: string
  displayName: string
  assetCount: number
  downloadCount: number
}

export interface DownloadRecord {
  assetId: string
  assetName: string
  userId: string
  username: string
  downloadedAt: string
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

  return res.json()
}

export const statisticsApi = {
  getOverview: () => request<OverviewStats>('/statistics/overview'),

  getPopularAssets: (limit = 10, days = 30) =>
    request<PopularAsset[]>(`/statistics/popular-assets?limit=${limit}&days=${days}`),

  getActiveUsers: (limit = 10, days = 30) =>
    request<ActiveUser[]>(`/statistics/active-users?limit=${limit}&days=${days}`),

  getRecentDownloads: (limit = 20) =>
    request<DownloadRecord[]>(`/statistics/recent-downloads?limit=${limit}`)
}
