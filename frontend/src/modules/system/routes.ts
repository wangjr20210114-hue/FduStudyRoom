import { featurePage } from '@/shared/navigation/feature-page'

export const systemRoutes = [
  featurePage({ path: 'system/users', name: 'users', titleKey: 'shell.navigation.users', permission: 'identity:users:read' }),
  featurePage({ path: 'system/settings', name: 'settings', titleKey: 'shell.navigation.settings', roles: ['SYSTEM_ADMIN'] }),
  featurePage({ path: 'statistics', name: 'statistics', titleKey: 'shell.navigation.statistics', roles: ['SYSTEM_ADMIN'] }),
]
