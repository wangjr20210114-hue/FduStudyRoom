import type { RouteMeta } from 'vue-router'
import type { CurrentUser } from '@/modules/auth/types'
import { workspaceRoutes } from './pages'

export const navigationItems = workspaceRoutes.filter(route => route.meta.navigation).map(route => ({
  path: `/${route.path}`,
  label: route.meta.titleKey,
  permission: route.meta.permission,
  roles: route.meta.roles,
}))

export function hasPermission(user: CurrentUser | null, permission: string): boolean {
  return user?.permissions.includes(permission) ?? false
}

export function canAccessPage(user: CurrentUser | null, page: Pick<RouteMeta, 'permission' | 'roles'>): boolean {
  if (!user || (page.roles && !page.roles.includes(user.role))) return false
  return !page.permission || hasPermission(user, page.permission)
}

export function visibleNavigation(user: CurrentUser | null) {
  return navigationItems.filter(item => canAccessPage(user, item))
}

export function safeDestination(value: unknown): string {
  const paths = [...workspaceRoutes.map(route => `/${route.path}`), '/account']
  return typeof value === 'string' && paths.includes(value) ? value : '/workspace'
}
