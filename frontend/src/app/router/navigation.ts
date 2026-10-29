import type { CurrentUser } from '@/modules/auth/types'

export const navigationItems = [
  { path: '/workspace', label: 'shell.navigation.workspace', permission: 'identity:read' },
  { path: '/system/users', label: 'shell.navigation.users', permission: 'identity:users:read' },
] as const

export function hasPermission(user: CurrentUser | null, permission: string): boolean {
  return user?.permissions.includes(permission) ?? false
}

export function visibleNavigation(user: CurrentUser | null) {
  return navigationItems.filter(item => hasPermission(user, item.permission))
}

export function safeDestination(value: unknown): string {
  const paths: readonly string[] = [...navigationItems.map(item => item.path), '/account']
  return typeof value === 'string' && paths.includes(value) ? value : '/workspace'
}
