import { afterEach, describe, expect, it, vi } from 'vitest'
import { createApiClient } from '../src/shared/api/client'
import { ApiError } from '../src/shared/api/errors'

afterEach(() => { vi.useRealTimers() })

const jsonResponse = (value: unknown, status = 200) => new Response(JSON.stringify(value), {
  status, headers: { 'Content-Type': 'application/json; charset=utf-8' },
})

describe('API request behavior', () => {
  it('encodes filters, sends the current language and returns JSON', async () => {
    const fetcher = vi.fn<typeof fetch>().mockImplementation(async () => jsonResponse({ rooms: [] }))
    let locale = 'zh-CN'
    const client = createApiClient({ baseUrl: '/api', fetch: fetcher, getLocale: () => locale })
    await expect(client.request('/rooms', { query: { name: '一教 & 二教', available: true, page: 0, omitted: undefined } })).resolves.toEqual({ rooms: [] })
    const [url, init] = fetcher.mock.calls[0]!
    expect(url).toBe('/api/rooms?name=%E4%B8%80%E6%95%99+%26+%E4%BA%8C%E6%95%99&available=true&page=0')
    expect(new Headers(init?.headers).get('Accept-Language')).toBe('zh-CN')
    expect(init).toMatchObject({ credentials: 'same-origin', redirect: 'error', method: 'GET' })
    expect(new Headers(init?.headers).has('Content-Type')).toBe(false)
    locale = 'en'
    await client.request('/rooms')
    expect(new Headers(fetcher.mock.calls[1]![1]?.headers).get('Accept-Language')).toBe('en')
  })

  it('serializes a JSON mutation and preserves a caller CSRF header', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 204 }))
    const client = createApiClient({ baseUrl: '/api/', fetch: fetcher })
    await expect(client.request('/reservations', { method: 'POST', body: { seatId: 'seat-1' }, headers: { 'X-CSRF-Token': 'test-csrf' } })).resolves.toBeUndefined()
    const [url, init] = fetcher.mock.calls[0]!
    expect(url).toBe('/api/reservations')
    expect(init?.body).toBe('{"seatId":"seat-1"}')
    expect(new Headers(init?.headers).get('Content-Type')).toBe('application/json')
    expect(new Headers(init?.headers).get('X-CSRF-Token')).toBe('test-csrf')
  })

  it.each([401, 403, 404, 409, 422, 429, 500])('keeps HTTP %s, business code and trace ID without exposing the server message', async status => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(jsonResponse({ code: 'ROOM_UNAVAILABLE', traceId: 'trace-1', message: 'internal database detail' }, status))
    const client = createApiClient({ baseUrl: '/api', fetch: fetcher })
    const error = await client.request('/rooms').catch(error => error)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ kind: 'http', status, code: 'ROOM_UNAVAILABLE', traceId: 'trace-1' })
    expect(String(error)).not.toContain('internal database detail')
    expect(fetcher).toHaveBeenCalledTimes(1)
  })

  it('retains HTTP status when the error page is HTML or malformed JSON', async () => {
    for (const response of [
      new Response('<h1>Unavailable</h1>', { status: 503, headers: { 'Content-Type': 'text/html' } }),
      new Response('{broken', { status: 502, headers: { 'Content-Type': 'application/json' } }),
    ]) {
      const client = createApiClient({ baseUrl: '/api', fetch: vi.fn<typeof fetch>().mockResolvedValue(response) })
      await expect(client.request('/rooms')).rejects.toMatchObject({ kind: 'http', status: response.status })
    }
  })

  it.each([
    ['text/html', '<html>Sign in</html>'],
    ['application/json', '{broken'],
    ['application/json', ''],
  ])('rejects an invalid successful response (%s)', async (contentType, body) => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(body, { headers: { 'Content-Type': contentType } }))
    await expect(createApiClient({ baseUrl: '/api', fetch: fetcher }).request('/rooms')).rejects.toMatchObject({ kind: 'invalid_response', status: 200 })
  })

  it('maps a connection failure to a network error without retrying a mutation', async () => {
    const fetcher = vi.fn<typeof fetch>().mockRejectedValue(new TypeError('Connection failed'))
    await expect(createApiClient({ baseUrl: '/api', fetch: fetcher }).request('/reservations', { method: 'POST', body: {} })).rejects.toMatchObject({ kind: 'network' })
    expect(fetcher).toHaveBeenCalledTimes(1)
  })

  it('cancels before sending when the caller signal is already aborted', async () => {
    const fetcher = vi.fn<typeof fetch>()
    const controller = new AbortController()
    controller.abort()
    await expect(createApiClient({ baseUrl: '/api', fetch: fetcher }).request('/rooms', { signal: controller.signal })).rejects.toMatchObject({ kind: 'cancelled' })
    expect(fetcher).not.toHaveBeenCalled()
  })

  function pendingFetch() {
    return vi.fn<typeof fetch>((_url, init) => new Promise((_resolve, reject) => {
      init?.signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')), { once: true })
    }))
  }

  it('distinguishes timeout from cancellation and clears the timer', async () => {
    vi.useFakeTimers()
    const client = createApiClient({ baseUrl: '/api', fetch: pendingFetch(), timeoutMs: 50 })
    const assertion = expect(client.request('/rooms')).rejects.toMatchObject({ kind: 'timeout' })
    await vi.advanceTimersByTimeAsync(50)
    await assertion
    expect(vi.getTimerCount()).toBe(0)
  })

  it('honors cancellation while a request is pending', async () => {
    vi.useFakeTimers()
    const controller = new AbortController()
    const request = createApiClient({ baseUrl: '/api', fetch: pendingFetch() }).request('/rooms', { signal: controller.signal })
    const assertion = expect(request).rejects.toMatchObject({ kind: 'cancelled' })
    controller.abort()
    await assertion
    expect(vi.getTimerCount()).toBe(0)
  })

  it('rejects invalid paths, GET bodies and invalid timeouts before sending', async () => {
    const fetcher = vi.fn<typeof fetch>()
    const client = createApiClient({ baseUrl: '/api', fetch: fetcher })
    for (const path of ['https://example.com', '//example.com', '/../rooms', '/rooms?secret=x', '/rooms#x', '/rooms\\x']) {
      await expect(client.request(path)).rejects.toMatchObject({ kind: 'invalid_request' })
    }
    await expect(client.request('/rooms', { body: {} })).rejects.toMatchObject({ kind: 'invalid_request' })
    await expect(client.request('/rooms', { timeoutMs: 0 })).rejects.toMatchObject({ kind: 'invalid_request' })
    expect(fetcher).not.toHaveBeenCalled()
  })
})
