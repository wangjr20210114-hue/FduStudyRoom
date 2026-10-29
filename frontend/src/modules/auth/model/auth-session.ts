import { inject, type InjectionKey } from 'vue'
import type { createLoginFlow } from './login-flow'

export type AuthSession = ReturnType<typeof createLoginFlow>
export const authSessionKey: InjectionKey<AuthSession> = Symbol('auth-session')

export function useAuthSession(): AuthSession {
  const session = inject(authSessionKey)
  if (!session) throw new Error('Auth session is not provided.')
  return session
}
