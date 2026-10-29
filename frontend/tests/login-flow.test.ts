import { afterEach, describe, expect, it, vi } from 'vitest'
import { createLoginFlow } from '../src/modules/auth/model/login-flow'
import type { AuthApi } from '../src/modules/auth/api/auth'
import { ApiError } from '../src/shared/api/errors'
import { getLoginErrorMessage } from '../src/modules/auth/model/error-message'
import { setAppLocale } from '../src/shared/i18n'
import { credentials, passwordConfig, student } from './fixtures/auth'

function service() {
  return {
    configuration: vi.fn<AuthApi['configuration']>().mockResolvedValue(passwordConfig),
    currentUser: vi.fn<AuthApi['currentUser']>().mockResolvedValue(null),
    login: vi.fn<AuthApi['login']>().mockResolvedValue(student),
    register: vi.fn<AuthApi['register']>().mockResolvedValue(student),
    logout: vi.fn<AuthApi['logout']>().mockResolvedValue(undefined),
  }
}
afterEach(() => { setAppLocale('zh-CN') })
describe('password session flow', () => {
  it.each(['signIn','register'] as const)('confirms the cookie after %s', async action => {
    const api=service();const flow=createLoginFlow(api)
    await flow.initialize();api.currentUser.mockResolvedValueOnce(student)
    await flow[action](credentials)
    expect(flow.user.value).toEqual(student)
    expect(action==='signIn' ? api.login : api.register).toHaveBeenCalledWith(credentials,expect.any(AbortSignal))
  })
  it('does not accept a login response without a confirmed session', async () => {
    const flow=createLoginFlow(service());await flow.initialize();await flow.signIn(credentials)
    expect(flow.user.value).toBeNull();expect(flow.error.value).toMatchObject({code:'LOGIN_NOT_CONFIRMED'})
  })
  it('restores an existing session', async () => {
    const api=service();api.currentUser.mockResolvedValue(student)
    const flow=createLoginFlow(api);await flow.initialize()
    expect(flow.user.value).toEqual(student);expect(api.login).not.toHaveBeenCalled()
  })
  it('blocks duplicate submissions', async () => {
    const api=service();let complete!: (value:typeof student)=>void
    api.login.mockImplementation(()=>new Promise(resolve=>{complete=resolve}))
    const flow=createLoginFlow(api);await flow.initialize()
    const pending=flow.signIn(credentials);await flow.signIn(credentials)
    expect(api.login).toHaveBeenCalledTimes(1);api.currentUser.mockResolvedValue(student);complete(student);await pending
  })
  it('ignores late responses after disposal', async () => {
    const api=service();let complete!: (value:typeof passwordConfig)=>void
    api.configuration.mockImplementation(()=>new Promise(resolve=>{complete=resolve}))
    const flow=createLoginFlow(api);const pending=flow.initialize();flow.dispose();complete(passwordConfig);await pending
    expect(flow.user.value).toBeNull();expect(flow.config.value).toBeNull()
  })
  it('hides the previous identity when a new check fails', async () => {
    const api=service();api.currentUser.mockResolvedValue(student)
    const flow=createLoginFlow(api);await flow.initialize();api.currentUser.mockRejectedValue(new ApiError('network'));await flow.initialize()
    expect(flow.user.value).toBeNull();expect(flow.error.value).toMatchObject({kind:'network'})
  })
  it('does not register when registration is closed', async () => {
    const api=service();api.configuration.mockResolvedValue({...passwordConfig,registrationOpen:false})
    const flow=createLoginFlow(api);await flow.initialize();await flow.register(credentials)
    expect(api.register).not.toHaveBeenCalled()
  })
  it('keeps the session after a failed logout and clears it after retry', async () => {
    const api=service();api.currentUser.mockResolvedValue(student);api.logout.mockRejectedValueOnce(new ApiError('network'))
    const flow=createLoginFlow(api);await flow.initialize();await flow.signOut()
    expect(flow.user.value).toEqual(student);await flow.signOut();expect(flow.user.value).toBeNull()
  })
  it('clears an already expired session on logout', async () => {
    const api=service();api.currentUser.mockResolvedValue(student);api.logout.mockRejectedValue(new ApiError('http',{status:401}))
    const flow=createLoginFlow(api);await flow.initialize();await flow.signOut();expect(flow.user.value).toBeNull()
  })
  it.each(['signIn','register'] as const)('rejects invalid input before %s sends a request', async action => {
    const api=service();const flow=createLoginFlow(api);await flow.initialize()
    for (const password of ['short', ' '.repeat(8), 'x'.repeat(65)]) {
      await flow[action]({...credentials,password})
      expect(flow.error.value).toMatchObject({code:'INVALID_PASSWORD'})
    }
    await flow[action]({...credentials,loginName:'bad account'})
    expect(flow.error.value).toMatchObject({code:'INVALID_LOGIN_NAME'})
    expect(api.login).not.toHaveBeenCalled();expect(api.register).not.toHaveBeenCalled()
    expect(api.currentUser).toHaveBeenCalledTimes(1)
  })
  it('explains password validation failures in both languages', () => {
    const failure=new ApiError('http',{status:400,code:'INVALID_PASSWORD'})
    expect(getLoginErrorMessage(failure)).toBe('密码需为8–64位，且不能全部为空白。')
    setAppLocale('en')
    expect(getLoginErrorMessage(failure)).toBe('Use 8–64 characters; the password cannot be all whitespace.')
  })
  it('translates a login failure with the current language', () => {
    const failure=new ApiError('http',{status:401,code:'INVALID_CREDENTIALS'})
    expect(getLoginErrorMessage(failure)).toBe('账号或密码错误。');setAppLocale('en');expect(getLoginErrorMessage(failure)).toBe('Incorrect account or password.')
  })
})
