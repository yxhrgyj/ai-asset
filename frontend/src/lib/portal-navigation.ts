import type { PortalAssetScope, PortalAssetType } from '../types/portal'

export function normalizePortalFilters(query: Record<string, unknown>): {
  q: string
  type: PortalAssetType | ''
  scope: PortalAssetScope | ''
  page: number
} {
  const type = typeof query.type === 'string' && ['RULE', 'SKILL', 'DOCUMENT'].includes(query.type)
    ? query.type as PortalAssetType : ''
  const scope = typeof query.scope === 'string' && ['ORGANIZATION', 'TECH_STACK', 'PROJECT'].includes(query.scope)
    ? query.scope as PortalAssetScope : ''
  const page = typeof query.page === 'string' && /^\d+$/.test(query.page) ? Number(query.page) : 0
  return {
    q: typeof query.q === 'string' ? query.q.trim() : '',
    type,
    scope,
    page: Number.isInteger(page) && page <= 2147483647 ? page : 0
  }
}

export function safeReturnUrl(value: unknown): string {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || /[\\\u0000-\u001f\u007f]/.test(value)) {
    return '/admin'
  }
  try {
    const url = new URL(value, 'https://portal.invalid')
    const pathname = decodeURIComponent(url.pathname).replace(/\/+$/, '')
    if (url.origin !== 'https://portal.invalid' || ['/login', '/change-password'].includes(pathname)) return '/admin'
    return url.pathname + url.search + url.hash
  } catch {
    return '/admin'
  }
}
