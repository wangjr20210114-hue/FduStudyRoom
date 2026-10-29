export type DevelopmentAccountKey = 'student' | 'admin' | 'maintainer'
export interface AuthConfig {
  mode: 'development' | 'fudan'
  available: boolean
  developmentAccounts: { key: DevelopmentAccountKey; label: string }[]
  unavailableCode: string
}
export interface CurrentUser {
  id: string
  loginName: string
  displayName: string
  identityType: 'STUDENT' | 'STAFF'
  identitySource: 'DEVELOPMENT' | 'FUDAN_UIS'
  role: string
  permissions: string[]
  authVersion: number
  profileIncomplete: boolean
}
export function isDevelopmentAccountKey(value: unknown): value is DevelopmentAccountKey {
  return value === 'student' || value === 'admin' || value === 'maintainer'
}
