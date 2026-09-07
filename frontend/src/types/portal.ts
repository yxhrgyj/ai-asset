export type PortalAssetType = 'RULE' | 'SKILL' | 'DOCUMENT'
export type PortalAssetScope = 'ORGANIZATION' | 'TECH_STACK' | 'PROJECT'

export interface PortalFile {
  id: string
  relativePath: string
  mimeType?: string
  sizeBytes: number
  contentHash: string
  createdAt?: string
}

export interface AssetSummary {
  id: string
  name: string
  slug: string
  type: PortalAssetType
  scope: PortalAssetScope
  summary: string | null
  category: string | null
  tags: string[]
  techStack: string | null
  downloadCount: number
  publishedAt: string | null
  updatedAt: string | null
}

export interface PublishedVersion {
  id: string
  versionNo: number
  changelog: string | null
  publishedAt: string | null
}

export interface AssetDetail {
  summary: AssetSummary
  body: string | null
  versionNo: number
  changelog: string | null
  files: PortalFile[]
  versions: PublishedVersion[]
  canEdit: boolean
}

export interface Statistics {
  totalAssets: number
  totalDownloads: number
  typeBreakdown: {
    rule: number
    skill: number
    document: number
  }
  scopeBreakdown: {
    organization: number
    techStack: number
    project: number
  }
}

export interface AssetListParams {
  q?: string
  type?: PortalAssetType
  scope?: PortalAssetScope
  tag?: string
  page?: number
  size?: number
}

export interface AssetListResponse {
  items: AssetSummary[]
  total: number
  page: number
  size: number
}
