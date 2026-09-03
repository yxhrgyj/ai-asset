export interface Approval {
  id: string
  assetVersionId: string
  assetId: string
  assetName: string
  versionNo: number
  submittedBy: string
  submittedByName: string
  submittedAt: string
  decidedBy?: string
  decidedByName?: string
  decidedAt?: string
  decision?: 'APPROVED' | 'REJECTED'
  comment?: string
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

export const approvalApi = {
  /** 获取待审批列表 */
  getPending: async (): Promise<Approval[]> => {
    return request<Approval[]>('/approvals/pending')
  },

  /** 获取某个版本的审批历史 */
  getVersionApprovals: async (versionId: string): Promise<Approval[]> => {
    return request<Approval[]>(`/approvals/version/${versionId}`)
  },

  /** 提交审批 */
  submit: async (assetId: string): Promise<Approval> => {
    return request<Approval>(`/approvals/submit/${assetId}`, {
      method: 'POST'
    })
  },

  /** 审批决定 */
  decide: async (approvalId: string, decision: 'APPROVED' | 'REJECTED', comment?: string): Promise<Approval> => {
    return request<Approval>(`/approvals/${approvalId}/decide`, {
      method: 'POST',
      body: JSON.stringify({ decision, comment })
    })
  },

  /** 撤回审批 */
  withdraw: async (approvalId: string): Promise<void> => {
    return request<void>(`/approvals/${approvalId}/withdraw`, {
      method: 'POST'
    })
  }
}
