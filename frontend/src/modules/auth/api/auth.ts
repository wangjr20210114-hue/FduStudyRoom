import { api, ApiError } from '@/shared/api'
import { isDevelopmentAccountKey, type AuthConfig, type CurrentUser, type DevelopmentAccountKey } from '../types'

type Transport = Pick<typeof api, 'request'>
const record = (value: unknown): value is Record<string, unknown> => !!value && typeof value === 'object' && !Array.isArray(value)
const text = (value: unknown): value is string => typeof value === 'string' && value.trim().length > 0
const invalidResponse = () => new ApiError('invalid_response')

export function parseAuthConfig(value: unknown): AuthConfig {
  if (!record(value) || !['development', 'fudan'].includes(String(value.mode)) || typeof value.available !== 'boolean'
      || !Array.isArray(value.developmentAccounts) || typeof value.unavailableCode !== 'string') throw invalidResponse()
  const accounts = value.developmentAccounts.map((account: unknown) => {
    if (!record(account) || !isDevelopmentAccountKey(account.key) || !text(account.label)) throw invalidResponse()
    return { key: account.key, label: account.label }
  })
  if (new Set(accounts.map(account => account.key)).size !== accounts.length
      || (value.mode === 'fudan' && accounts.length > 0)
      || (value.mode === 'development' && value.available && accounts.length === 0)) throw invalidResponse()
  return { mode: value.mode as AuthConfig['mode'], available: value.available,
    developmentAccounts: accounts, unavailableCode: value.unavailableCode }
}

export function parseCurrentUser(value: unknown): CurrentUser {
  if (!record(value) || !text(value.id) || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value.id)
      || !text(value.loginName) || !text(value.displayName) || !text(value.role)
      || !['STUDENT', 'STAFF'].includes(String(value.identityType))
      || !['DEVELOPMENT', 'FUDAN_UIS'].includes(String(value.identitySource))
      || !Array.isArray(value.permissions) || !value.permissions.every(text)
      || !Number.isInteger(value.authVersion) || Number(value.authVersion) < 1
      || typeof value.profileIncomplete !== 'boolean') throw invalidResponse()
  return value as unknown as CurrentUser
}

export function createAuthApi(transport: Transport = api) {
  async function csrfHeaders(signal?: AbortSignal): Promise<Record<string, string>> {
    const value = await transport.request<unknown>('/auth/csrf', { signal })
    if (!record(value) || value.headerName !== 'X-XSRF-TOKEN' || !text(value.token) || value.token.length > 2048) throw invalidResponse()
    return { [value.headerName]: value.token }
  }
  return {
    async configuration(signal?: AbortSignal) {
      return parseAuthConfig(await transport.request<unknown>('/auth/config', { signal }))
    },
    async currentUser(signal?: AbortSignal): Promise<CurrentUser | null> {
      try {
        return parseCurrentUser(await transport.request<unknown>('/auth/me', { signal }))
      } catch (error) {
        if (error instanceof ApiError && error.kind === 'http' && [401, 403].includes(error.status ?? 0)) return null
        throw error
      }
    },
    async loginDevelopment(account: DevelopmentAccountKey, signal?: AbortSignal) {
      if (!import.meta.env.DEV) throw new ApiError('http', { status: 403, code: 'DEVELOPMENT_LOGIN_DISABLED' })
      const headers = await csrfHeaders(signal)
      return parseCurrentUser(await transport.request<unknown>('/auth/dev/login', {
        method: 'POST', body: { account }, headers, signal,
      }))
    },
    async logout(signal?: AbortSignal) {
      const headers = await csrfHeaders(signal)
      await transport.request<void>('/auth/logout', { method: 'POST', headers, signal })
    },
  }
}

export type AuthApi = ReturnType<typeof createAuthApi>
export const authApi = createAuthApi()
