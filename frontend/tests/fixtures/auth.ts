import type { AuthConfig, CurrentUser } from '../../src/modules/auth/types'
export const passwordConfig: AuthConfig = { mode: 'password', available: true, registrationOpen: true }
export const student: CurrentUser = {
  id: '00000000-0000-0000-0000-000000002001', loginName: 'test-user', displayName: 'Test User',
  role: 'USER', permissions: ['identity:read'], authVersion: 1, profileIncomplete: true,
}
export const credentials = { loginName: 'test-user', password: 'test-password-123' }
