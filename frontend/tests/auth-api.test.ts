import { describe, expect, it, vi } from 'vitest'
import { createAuthApi, parseAuthConfig, parseCurrentUser } from '../src/modules/auth/api/auth'
import { ApiError } from '../src/shared/api/errors'
import { credentials, passwordConfig, student } from './fixtures/auth'

function transport() { return { request: vi.fn() } }
describe('password auth API', () => {
  it.each(['login', 'register'] as const)('fetches fresh CSRF before %s and sends no role or identity', async action => {
    const http = transport()
    http.request.mockResolvedValueOnce({ headerName: 'X-XSRF-TOKEN', token: 'fresh-token' }).mockResolvedValueOnce(student)
    const api = createAuthApi(http)
    expect(await api[action](credentials)).toEqual(student)
    expect(http.request.mock.calls[0]![0]).toBe('/auth/csrf')
    expect(http.request.mock.calls[1]).toEqual(['/auth/'+action, { method: 'POST', body: credentials, headers: { 'X-XSRF-TOKEN': 'fresh-token' }, signal: undefined }])
  })
  it('refreshes CSRF before logout', async () => {
    const http = transport()
    http.request.mockResolvedValueOnce({ headerName: 'X-XSRF-TOKEN', token: 'next-token' }).mockResolvedValueOnce(undefined)
    await createAuthApi(http).logout()
    expect(http.request.mock.calls[1]).toEqual(['/auth/logout', { method: 'POST', headers: { 'X-XSRF-TOKEN': 'next-token' }, signal: undefined }])
  })
  it.each([401,403])('treats current-user HTTP %s as signed out', async status => {
    const http = transport(); http.request.mockRejectedValue(new ApiError('http',{status}))
    expect(await createAuthApi(http).currentUser()).toBeNull()
  })
  it('does not treat an unavailable backend as a signed-out success', async () => {
    const http = transport(); http.request.mockRejectedValue(new ApiError('http',{status:503}))
    await expect(createAuthApi(http).currentUser()).rejects.toMatchObject({status:503})
  })
  it('rejects an unexpected CSRF header before submitting a password', async () => {
    const http = transport();http.request.mockResolvedValue({headerName:'Authorization',token:'bad'})
    await expect(createAuthApi(http).login(credentials)).rejects.toMatchObject({kind:'invalid_response'})
    expect(http.request).toHaveBeenCalledTimes(1)
  })
  it('accepts only password configuration', () => {
    expect(parseAuthConfig(passwordConfig)).toEqual(passwordConfig)
    for (const mode of ['development','fudan']) expect(() => parseAuthConfig({...passwordConfig,mode})).toThrow(ApiError)
  })
  it('validates identity fields returned by the backend', () => {
    expect(parseCurrentUser(student)).toEqual(student)
    for (const user of [{...student,id:'invalid'},{...student,permissions:[3]},{...student,authVersion:0}]) expect(() => parseCurrentUser(user)).toThrow(ApiError)
  })
})
