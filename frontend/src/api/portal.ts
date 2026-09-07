import { apiClient } from './client'
import type {
  AssetDetail,
  AssetListParams,
  AssetListResponse,
  Statistics
} from '../types/portal'

export const portalApi = {
  listAssets(params: AssetListParams = {}): Promise<AssetListResponse> {
    const query = new URLSearchParams()
    if (params.q) query.set('q', params.q)
    if (params.type) query.set('type', params.type)
    if (params.scope) query.set('scope', params.scope)
    if (params.tag) query.set('tag', params.tag)
    if (params.page !== undefined) query.set('page', String(params.page))
    if (params.size !== undefined) query.set('size', String(params.size))
    const suffix = query.toString()
    return apiClient.get<AssetListResponse>(`/public/assets${suffix ? `?${suffix}` : ''}`)
  },

  getAssetDetail(id: string, versionNo?: number): Promise<AssetDetail> {
    const query = versionNo === undefined ? '' : `?versionNo=${versionNo}`
    return apiClient.get<AssetDetail>(`/public/assets/${id}${query}`)
  },

  getStatistics(): Promise<Statistics> {
    return apiClient.get<Statistics>('/public/statistics')
  }
}
