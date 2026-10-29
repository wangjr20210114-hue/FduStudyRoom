export interface AuthConfig {
  mode: 'password'
  available: boolean
  registrationOpen: boolean
}
export interface LoginCredentials {
  loginName: string
  password: string
}
export interface CurrentUser {
  id: string
  loginName: string
  displayName: string
  role: string
  permissions: string[]
  authVersion: number
  profileIncomplete: boolean
}
