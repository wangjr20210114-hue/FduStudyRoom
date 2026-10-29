import { afterEach, describe, expect, it, vi } from 'vitest'
import { createLoginFlow } from '../src/modules/auth/model/login-flow'
import type { AuthApi } from '../src/modules/auth/api/auth'
import { ApiError } from '../src/shared/api/errors'
import { getLoginErrorMessage } from '../src/modules/auth/model/error-message'
import { setAppLocale } from '../src/shared/i18n'
import { developmentConfig, student } from './fixtures/auth'

function service() {
  return {
    configuration: vi.fn<AuthApi['configuration']>().mockResolvedValue(developmentConfig),
    currentUser: vi.fn<AuthApi['currentUser']>().mockResolvedValue(null),
    loginDevelopment: vi.fn<AuthApi['loginDevelopment']>().mockResolvedValue(student),
    logout: vi.fn<AuthApi['logout']>().mockResolvedValue(undefined),
  }
}
afterEach(() => { setAppLocale('zh-CN') })

describe('login flow', () => {
  it('waits for the current-user check before confirming a new login', async () => {
    const api = service()
    const flow = createLoginFlow(api, true)
    await flow.initialize()
    api.currentUser.mockResolvedValueOnce(student)
    await flow.signInDevelopment('student')
    expect(api.loginDevelopment).toHaveBeenCalledWith('student', expect.any(AbortSignal))
    expect(flow.user.value).toEqual(student)
    expect(flow.busy.value).toBe(false)
  })
  it('does not accept a login POST alone as proof that the cookie is usable', async () => {
    const api = service()
    const flow = createLoginFlow(api, true)
    await flow.initialize()
    await flow.signInDevelopment('student')
    expect(flow.user.value).toBeNull()
    expect(flow.error.value).toMatchObject({ code: 'LOGIN_NOT_CONFIRMED' })
  })
  it('restores a valid server session during initialization', async () => {
    const api = service()
    api.currentUser.mockResolvedValue(student)
    const flow = createLoginFlow(api, true)
    await flow.initialize()
    expect(flow.user.value).toEqual(student)
    expect(api.loginDevelopment).not.toHaveBeenCalled()
  })
  it('blocks repeated login attempts while the first one is pending', async () => {
    const api = service()
    let complete!: (value: typeof student) => void
    api.loginDevelopment.mockImplementation(() => new Promise(resolve => { complete = resolve }))
    const flow = createLoginFlow(api, true)
    await flow.initialize()
    const pending = flow.signInDevelopment('student')
    await flow.signInDevelopment('admin')
    expect(api.loginDevelopment).toHaveBeenCalledTimes(1)
    api.currentUser.mockResolvedValue(student)
    complete(student)
    await pending
    expect(flow.user.value).toEqual(student)
  })
  it('cancels pending work and ignores late responses after disposal', async () => {
    const api = service()
    let complete!: (value: typeof developmentConfig) => void
    api.configuration.mockImplementation(() => new Promise(resolve => { complete = resolve }))
    const flow = createLoginFlow(api, true)
    const pending = flow.initialize()
    const signal = api.configuration.mock.calls[0]![0]!
    flow.dispose()
    complete(developmentConfig)
    await pending
    expect(signal.aborted).toBe(true)
    expect(flow.config.value).toBeNull()
    expect(flow.user.value).toBeNull()
    expect(flow.error.value).toBeNull()
  })
  it('does not invent development mode when the backend is unavailable', async () => {
    const api = service()
    api.configuration.mockRejectedValue(new ApiError('network'))
    const flow = createLoginFlow(api, true)
    await flow.initialize()
    expect(flow.config.value).toBeNull()
    expect(flow.error.value).toMatchObject({ kind: 'network' })
    await flow.signInDevelopment('student')
    expect(api.loginDevelopment).not.toHaveBeenCalled()
  })
  it('blocks development identities in a production frontend', async () => {
    const api = service()
    api.currentUser.mockResolvedValue(student)
    const flow = createLoginFlow(api, false)
    await flow.initialize()
    expect(flow.error.value).toMatchObject({ code: 'DEVELOPMENT_LOGIN_DISABLED' })
    expect(flow.user.value).toBeNull()
    await flow.signInDevelopment('student')
    expect(api.loginDevelopment).not.toHaveBeenCalled()
  })
  it('does not allow development sign-in in Fudan mode', async () => {
    const api = service()
    api.configuration.mockResolvedValue({ mode: 'fudan', available: false, developmentAccounts: [], unavailableCode: 'UIS_NOT_CONFIGURED' })
    const flow = createLoginFlow(api, true)
    await flow.initialize()
    await flow.signInDevelopment('student')
    expect(api.loginDevelopment).not.toHaveBeenCalled()
    expect(flow.error.value).toBeNull()
  })
  it('retains a known user when logout fails, and clears it after a successful retry', async () => {
    const api = service()
    api.currentUser.mockResolvedValue(student)
    const flow = createLoginFlow(api, true)
    await flow.initialize()
    api.logout.mockRejectedValueOnce(new ApiError('network'))
    await flow.signOut()
    expect(flow.user.value).toEqual(student)
    expect(flow.error.value).toMatchObject({ kind: 'network' })
    await flow.signOut()
    expect(flow.user.value).toBeNull()
    expect(flow.error.value).toBeNull()
  })
  it('returns to sign-in when logout discovers an already expired session', async () => {
    const api = service()
    api.currentUser.mockResolvedValue(student)
    api.logout.mockRejectedValue(new ApiError('http', { status: 401 }))
    const flow = createLoginFlow(api, true)
    await flow.initialize()
    await flow.signOut()
    expect(flow.user.value).toBeNull()
    expect(flow.error.value).toBeNull()
  })
  it('translates existing login errors with the current language', () => {
    const failure = new ApiError('http', { status: 403, code: 'ACCOUNT_DISABLED' })
    expect(getLoginErrorMessage(failure)).toBe('账号或角色已停用，请联系管理员。')
    setAppLocale('en')
    expect(getLoginErrorMessage(failure)).toBe('This account or role is disabled. Please contact an administrator.')
  })
})
