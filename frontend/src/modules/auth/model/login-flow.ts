import { computed, ref, shallowRef } from 'vue'
import { ApiError } from '@/shared/api'
import { authApi, type AuthApi } from '../api/auth'
import type { AuthConfig, CurrentUser, DevelopmentAccountKey } from '../types'

export function createLoginFlow(service: AuthApi = authApi, developmentBuild = import.meta.env.DEV) {
  const config = shallowRef<AuthConfig | null>(null)
  const user = shallowRef<CurrentUser | null>(null)
  const error = shallowRef<unknown>(null)
  const phase = ref<'idle' | 'loading' | 'signing-in' | 'signing-out'>('idle')
  const busy = computed(() => phase.value !== 'idle')
  let controller: AbortController | undefined
  let disposed = false
  let initialization: Promise<void> | undefined

  function validateIdentity(configuration: AuthConfig, identity: CurrentUser | null) {
    if (configuration.mode === 'development' && !developmentBuild) {
      throw new ApiError('http', { status: 503, code: 'DEVELOPMENT_LOGIN_DISABLED' })
    }
    const expectedSource = configuration.mode === 'development' ? 'DEVELOPMENT' : 'FUDAN_UIS'
    if (identity && identity.identitySource !== expectedSource) throw new ApiError('invalid_response')
  }

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

  function initialize(): Promise<void> {
    if (initialization) return initialization
    initialization = execute('loading', async signal => {
      config.value = null
      user.value = null
      const [configuration, identity] = await Promise.all([service.configuration(signal), service.currentUser(signal)])
      validateIdentity(configuration, identity)
      return () => { config.value = configuration; user.value = identity }
    }).finally(() => { initialization = undefined })
    return initialization
  }

  async function signInDevelopment(account: DevelopmentAccountKey) {
    if (!config.value || config.value.mode !== 'development' || !config.value.available || !developmentBuild
        || !config.value.developmentAccounts.some(option => option.key === account)) return
    await execute('signing-in', async signal => {
      await service.loginDevelopment(account, signal)
      const identity = await service.currentUser(signal)
      if (!identity) throw new ApiError('http', { status: 401, code: 'LOGIN_NOT_CONFIRMED' })
      validateIdentity(config.value!, identity)
      return () => { user.value = identity }
    })
  }

  async function signOut() {
    if (!user.value) return
    await execute('signing-out', async signal => {
      try {
        await service.logout(signal)
      } catch (failure) {
        if (!(failure instanceof ApiError && failure.kind === 'http' && failure.status === 401)) throw failure
      }
      return () => { user.value = null }
    })
  }

  function dispose() { disposed = true; controller?.abort() }
  return { config, user, error, phase, busy, initialize, signInDevelopment, signOut, dispose }
}
