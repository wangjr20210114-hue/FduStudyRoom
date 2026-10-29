import { describe, expect, it, vi } from 'vitest'
import { createApiClient } from '../src/shared/api/client'
import { createAuthApi, parseAuthConfig, parseCurrentUser } from '../src/modules/auth/api/auth'
import { developmentConfig, student } from './fixtures/auth'

const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), {
  status, headers: { 'Content-Type': 'application/json' },
})

describe('authentication API boundary', () => {
  it('gets a CSRF token before development login and sends only the account key', async () => {
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(json({ headerName: 'X-XSRF-TOKEN', token: 'masked-csrf' }))
      .mockResolvedValueOnce(json(student))
    const service = createAuthApi(createApiClient({ baseUrl: '/api', fetch: fetcher }))
    await expect(service.loginDevelopment('student')).resolves.toEqual(student)
    expect(fetcher.mock.calls.map(call => call[0])).toEqual(['/api/auth/csrf', '/api/auth/dev/login'])
    const init = fetcher.mock.calls[1]![1]!
    expect(init.body).toBe('{"account":"student"}')
    expect(new Headers(init.headers).get('X-XSRF-TOKEN')).toBe('masked-csrf')
    expect(init.credentials).toBe('same-origin')
  })
  it('obtains a fresh CSRF token for logout rather than reusing one from login', async () => {
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(json({ headerName: 'X-XSRF-TOKEN', token: 'before-login' }))
      .mockResolvedValueOnce(json(student))
      .mockResolvedValueOnce(json({ headerName: 'X-XSRF-TOKEN', token: 'after-login' }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    const service = createAuthApi(createApiClient({ baseUrl: '/api', fetch: fetcher }))
    await service.loginDevelopment('student')
    await service.logout()
    expect(fetcher.mock.calls.map(call => call[0])).toEqual(['/api/auth/csrf', '/api/auth/dev/login', '/api/auth/csrf', '/api/auth/logout'])
    expect(new Headers(fetcher.mock.calls[3]![1]?.headers).get('X-XSRF-TOKEN')).toBe('after-login')
  })
  it.each([401, 403])('treats a denied current session (%s) as signed out', async status => {
    const service = createAuthApi(createApiClient({ baseUrl: '/api', fetch: vi.fn<typeof fetch>().mockResolvedValue(json({ code: 'INVALID_SESSION' }, status)) }))
    await expect(service.currentUser()).resolves.toBeNull()
  })
  it('preserves service failures rather than pretending the user is signed out', async () => {
    const service = createAuthApi(createApiClient({ baseUrl: '/api', fetch: vi.fn<typeof fetch>().mockResolvedValue(json({ code: 'IDENTITY_UNAVAILABLE' }, 503)) }))
    await expect(service.currentUser()).rejects.toMatchObject({ status: 503, code: 'IDENTITY_UNAVAILABLE' })
  })
  it('rejects invalid CSRF metadata before sending credentials', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(json({ headerName: 'Authorization', token: 'unsafe' }))
    const service = createAuthApi(createApiClient({ baseUrl: '/api', fetch: fetcher }))
    await expect(service.loginDevelopment('student')).rejects.toMatchObject({ kind: 'invalid_response' })
    expect(fetcher).toHaveBeenCalledTimes(1)
  })
  it('validates configuration and refuses unknown or duplicate development choices', () => {
    expect(parseAuthConfig(developmentConfig)).toEqual(developmentConfig)
    for (const config of [
      { ...developmentConfig, mode: 'unknown' },
      { ...developmentConfig, developmentAccounts: [{ key: 'arbitrary-user', label: 'Fake' }] },
      { ...developmentConfig, developmentAccounts: [developmentConfig.developmentAccounts[0], developmentConfig.developmentAccounts[0]] },
      { ...developmentConfig, developmentAccounts: [] },
      { ...developmentConfig, mode: 'fudan' },
    ]) expect(() => parseAuthConfig(config)).toThrow()
  })
  it('rejects incomplete or wrongly typed identities before they reach the UI', () => {
    expect(parseCurrentUser(student)).toEqual(student)
    for (const user of [{ ...student, id: 'not-a-uuid' }, { ...student, permissions: 'admin' }, { ...student, authVersion: 0 }, { ...student, identitySource: 'OTHER' }]) {
      expect(() => parseCurrentUser(user)).toThrow()
    }
  })
})
