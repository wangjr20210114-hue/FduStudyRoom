import './email-intent'
import { computed, ref, shallowRef } from 'vue'
import { ApiError } from '@/shared/api'
import { authApi, type AuthApi } from '../api/auth'
import type { AuthConfig, CurrentUser, LoginCredentials } from '../types'

export function createLoginFlow(service: AuthApi = authApi) {
  const config = shallowRef<AuthConfig | null>(null)
  const user = shallowRef<CurrentUser | null>(null)
  const error = shallowRef<unknown>(null)
  const phase = ref<'idle' | 'loading' | 'signing-in' | 'registering' | 'signing-out'>('idle')
  const busy = computed(() => phase.value !== 'idle')
  let controller: AbortController | undefined
  let disposed = false
  let initialization: Promise<void> | undefined

  async function execute(nextPhase: typeof phase.value, operation: (signal: AbortSignal) => Promise<() => void>) {
    if (disposed || busy.value) return
    phase.value = nextPhase
    error.value = null
    const active = new AbortController()
    controller = active
    try {
      const apply = await operation(active.signal)
      if (!disposed && !active.signal.aborted) apply()
    } catch (failure) {
      if (!disposed && !active.signal.aborted && !(failure instanceof ApiError && failure.kind === 'cancelled')) error.value = failure
    } finally {
      active.abort()
      if (!disposed && controller === active) { phase.value = 'idle'; controller = undefined }
    }
  }

  function initialize({ preserveIdentity = false }: { preserveIdentity?: boolean } = {}): Promise<void> {
    if (initialization) return initialization
    initialization = execute('loading', async signal => {
      if (!preserveIdentity) { config.value = null; user.value = null }
      try {
        const [configuration, identity] = await Promise.all([service.configuration(signal), service.currentUser(signal)])
        return () => { config.value = configuration; user.value = identity }
      } catch (failure) {
        if (!disposed && !signal.aborted) { config.value = null; user.value = null }
        throw failure
      }
    }).finally(() => { initialization = undefined })
    return initialization
  }

  async function authenticate(credentials: LoginCredentials, registration: boolean) {
    if (!config.value?.available || (registration && !config.value.registrationOpen)) return
    await execute(registration ? 'registering' : 'signing-in', async signal => {
      if (!/^[a-z0-9][a-z0-9_.-]{2,31}$/i.test(credentials.loginName.trim())) {
        throw new ApiError('invalid_request', { code: 'INVALID_LOGIN_NAME' })
      }
      if (credentials.password.length < 8 || credentials.password.length > 64 || !credentials.password.trim()) {
        throw new ApiError('invalid_request', { code: 'INVALID_PASSWORD' })
      }
      await (registration ? service.register(credentials, signal) : service.login(credentials, signal))
      const identity = await service.currentUser(signal)
      if (!identity) throw new ApiError('http', { status: 401, code: 'LOGIN_NOT_CONFIRMED' })
      return () => { user.value = identity }
    })
  }

  async function signOut() {
    if (!user.value) return
    await execute('signing-out', async signal => {
      try { await service.logout(signal) }
      catch (failure) {
        if (!(failure instanceof ApiError && failure.kind === 'http' && failure.status === 401)) throw failure
      }
      return () => { user.value = null }
    })
  }

  function clearError() { if (!busy.value) error.value = null }
  function dispose() { disposed = true; controller?.abort() }
  return { config, user, error, phase, busy, initialize,
    signIn: (credentials: LoginCredentials) => authenticate(credentials, false),
    register: (credentials: LoginCredentials) => authenticate(credentials, true),
    signOut, clearError, dispose }
}
