/**
 * 资产接口封装。
 *
 * 约定与 auth.js 一致：出错不抛异常，返回 { data, error } —— 视图层拿到
 * error 就显示提示，不需要在每个 await 外面套 try/catch。
 *
 * 所有请求都带 credentials: 'include'，否则会话 Cookie 不会被发出，
 * 后端一律返回 401。
 */

async function request(url, options = {}) {
  let res
  try {
    res = await fetch(url, { credentials: 'include', ...options })
  } catch {
    return { data: null, error: '无法连接后端，确认 8080 端口的服务已启动' }
  }

  if (res.status === 401) {
    // 会话过期。让调用方决定是否跳登录页，这里不直接操作路由。
    return { data: null, error: '会话已过期，请重新登录', unauthorized: true }
  }

  if (!res.ok) {
    const body = await res.json().catch(() => ({}))
    return { data: null, error: body.message || `请求失败（${res.status}）` }
  }

  if (res.status === 204) {
    return { data: null, error: null }
  }

  return { data: await res.json(), error: null }
}

function json(method, body) {
  return {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  }
}

export const ASSET_TYPES = [
  { value: 'PROMPT', label: '提示词' },
  { value: 'SKILL', label: 'Skill' },
  { value: 'RULE', label: '规则' },
  { value: 'SUBAGENT', label: '子代理' },
  { value: 'MCP', label: 'MCP 配置' },
  { value: 'DOC', label: '文档' }
]

export const SCOPES = [
  { value: 'ORGANIZATION', label: '全公司' },
  { value: 'TEAM', label: '团队' },
  { value: 'TECH_STACK', label: '技术栈' },
  { value: 'PERSONAL', label: '个人' }
]

export function typeLabel(value) {
  return ASSET_TYPES.find((t) => t.value === value)?.label ?? value
}

export function scopeLabel(value) {
  return SCOPES.find((s) => s.value === value)?.label ?? value
}

/** 列表与搜索。空值参数不拼进 query，避免后端把空串当搜索词。 */
export function listAssets({ q, type, scope, tag, page = 0, size = 20 } = {}) {
  const params = new URLSearchParams()
  if (q) params.set('q', q)
  if (type) params.set('type', type)
  if (scope) params.set('scope', scope)
  if (tag) params.set('tag', tag)
  params.set('page', page)
  params.set('size', size)
  return request(`/api/assets?${params}`)
}

export function getAsset(id, versionNo) {
  const suffix = versionNo == null ? '' : `?versionNo=${versionNo}`
  return request(`/api/assets/${id}${suffix}`)
}

export function createAsset(payload) {
  return request('/api/assets', json('POST', payload))
}

export function updateAssetMeta(id, payload) {
  return request(`/api/assets/${id}`, json('PATCH', payload))
}

export function saveDraft(id, body, changelog) {
  return request(`/api/assets/${id}/draft`, json('PUT', { body, changelog }))
}

export function newDraft(id) {
  return request(`/api/assets/${id}/versions`, { method: 'POST' })
}

export function publishAsset(id) {
  return request(`/api/assets/${id}/publish`, { method: 'POST' })
}

export function archiveAsset(id, archived) {
  return request(`/api/assets/${id}/archive?archived=${archived}`, { method: 'POST' })
}

/**
 * 附件上传走 multipart，不能设 Content-Type——边界串由浏览器生成。
 *
 * 选目录上传时浏览器会在 File 上带 webkitRelativePath（形如
 * my-skill/scripts/check.py），把它作为 path 传给后端以保留目录结构。
 * 第一段是用户选的那个目录名，对资产内部路径没有意义，去掉。
 */
export function uploadFile(assetId, file, path) {
  const form = new FormData()
  form.append('file', file)

  const rel = path ?? file.webkitRelativePath ?? ''
  const stripped = rel.includes('/') ? rel.slice(rel.indexOf('/') + 1) : ''
  const query = stripped ? `?path=${encodeURIComponent(stripped)}` : ''

  return request(`/api/assets/${assetId}/files${query}`, { method: 'POST', body: form })
}

export function deleteFile(assetId, fileId) {
  return request(`/api/assets/${assetId}/files/${fileId}`, { method: 'DELETE' })
}

export function fileUrl(assetId, fileId) {
  return `/api/assets/${assetId}/files/${fileId}`
}

export function exportUrl(assetId, versionNo) {
  const suffix = versionNo == null ? '' : `?versionNo=${versionNo}`
  return `/api/assets/${assetId}/export${suffix}`
}
