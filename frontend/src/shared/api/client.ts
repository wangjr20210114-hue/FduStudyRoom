import { ApiError } from './errors'

type QueryValue = string | number | boolean | null | undefined
type JsonValue = string | number | boolean | null | JsonValue[] | { [key: string]: JsonValue }

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  query?: Record<string, QueryValue>
  body?: JsonValue
  headers?: HeadersInit
  signal?: AbortSignal
  timeoutMs?: number
}

interface ClientOptions {
  baseUrl: string
  getLocale?: () => string
  timeoutMs?: number
  fetch?: typeof globalThis.fetch
}

function getErrorDetails(value: unknown): { code?: string; traceId?: string } {
  if (!value || typeof value !== 'object') return {}
  const details = value as Record<string, unknown>
  return {
    code: typeof details.code === 'string' ? details.code : undefined,
    traceId: typeof details.traceId === 'string' ? details.traceId : undefined,
  }
}

export function createApiClient(config: ClientOptions) {
  const fetcher = config.fetch ?? globalThis.fetch.bind(globalThis)

  async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
    if (!path.startsWith('/') || path.startsWith('//') || /[?#\\]/.test(path) || path.split('/').some(part => part === '.' || part === '..')) {
      throw new ApiError('invalid_request')
    }
    const method = options.method ?? 'GET'
    const timeoutMs = options.timeoutMs ?? config.timeoutMs ?? 15_000
    if (!Number.isFinite(timeoutMs) || timeoutMs <= 0 || (method === 'GET' && options.body !== undefined)) {
      throw new ApiError('invalid_request')
    }

    const query = new URLSearchParams()
    for (const [key, value] of Object.entries(options.query ?? {})) {
      if (value !== undefined && value !== null) query.set(key, String(value))
    }
    const search = query.size ? `?${query}` : ''
    const url = `${config.baseUrl.replace(/\/$/, '')}${path}${search}`
    const headers = new Headers(options.headers)
    headers.set('Accept', 'application/json')
    if (config.getLocale) headers.set('Accept-Language', config.getLocale())

    let body: string | undefined
    try {
      body = options.body === undefined ? undefined : JSON.stringify(options.body)
    } catch {
      throw new ApiError('invalid_request')
    }
    if (body !== undefined) headers.set('Content-Type', 'application/json')

    const controller = new AbortController()
    let abortKind: 'timeout' | 'cancelled' | undefined
    const cancel = () => {
      if (!controller.signal.aborted) {
        abortKind = 'cancelled'
        controller.abort()
      }
    }
    if (options.signal?.aborted) throw new ApiError('cancelled')
    options.signal?.addEventListener('abort', cancel, { once: true })
    const timer = setTimeout(() => {
      if (!controller.signal.aborted) {
        abortKind = 'timeout'
        controller.abort()
      }
    }, timeoutMs)

    try {
      const response = await fetcher(url, {
        method, headers, body, signal: controller.signal,
        credentials: 'same-origin',
        redirect: 'error',
      })
      const contentType = response.headers.get('content-type')?.split(';')[0]?.trim().toLowerCase() ?? ''
      const isJson = contentType === 'application/json' || (contentType.startsWith('application/') && contentType.endsWith('+json'))
      const text = await response.text()
      if (abortKind) throw new ApiError(abortKind)

      let payload: unknown
      if (text && isJson) {
        try {
          payload = JSON.parse(text)
        } catch {
          if (response.ok) throw new ApiError('invalid_response', { status: response.status })
        }
      }
      if (!response.ok) {
        throw new ApiError('http', { status: response.status, ...getErrorDetails(payload) })
      }
      if (response.status === 204 || response.status === 205) return undefined as T
      if (!isJson || !text) throw new ApiError('invalid_response', { status: response.status })
      return payload as T
    } catch (error) {
      if (error instanceof ApiError) throw error
      if (abortKind) throw new ApiError(abortKind)
      throw new ApiError('network')
    } finally {
      clearTimeout(timer)
      options.signal?.removeEventListener('abort', cancel)
    }
  }

  return { request }
}
