const BASE = '/api'

function apiUrl(path: string): string {
  const normalized = path.startsWith('/') ? path : `/${path}`
  if (normalized === BASE || normalized.startsWith(`${BASE}/`)) {
    return normalized
  }
  return `${BASE}${normalized}`
}

async function errorMessage(response: Response): Promise<string> {
  const text = await response.text()
  if (!text) return `HTTP ${response.status}`

  try {
    const payload = JSON.parse(text) as { message?: unknown }
    return typeof payload.message === 'string' ? payload.message : text
  } catch {
    return text
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(apiUrl(path), {
    ...options,
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...options.headers
    }
  })

  if (!response.ok) {
    throw new Error(await errorMessage(response))
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json() as Promise<T>
}

export const apiClient = {
  get: <T>(path: string) => request<T>(path, { method: 'GET' }),

  post: <T>(path: string, body?: unknown) => request<T>(path, {
    method: 'POST',
    body: body === undefined ? undefined : JSON.stringify(body)
  }),

  put: <T>(path: string, body?: unknown) => request<T>(path, {
    method: 'PUT',
    body: body === undefined ? undefined : JSON.stringify(body)
  }),

  delete: <T = void>(path: string) => request<T>(path, { method: 'DELETE' })
}
