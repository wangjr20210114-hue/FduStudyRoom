import type { AuthConfig, CurrentUser } from '../../src/modules/auth/types'

export const developmentConfig: AuthConfig = {
  mode: 'development', available: true, unavailableCode: '',
  developmentAccounts: [
    { key: 'student', label: '开发学生' },
    { key: 'admin', label: '开发管理员' },
    { key: 'maintainer', label: '开发维护人员' },
  ],
}
export const student: CurrentUser = {
  id: '00000000-0000-0000-0000-000000002001', loginName: 'DEV-STUDENT', displayName: '开发学生',
  identityType: 'STUDENT', identitySource: 'DEVELOPMENT', role: 'USER', permissions: ['identity:read'],
  authVersion: 1, profileIncomplete: true,
}
