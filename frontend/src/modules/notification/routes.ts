import { featurePage } from '@/shared/navigation/feature-page'

export const notificationRoutes = [
  featurePage({ path: 'notifications', name: 'notifications', titleKey: 'shell.navigation.notifications', permission: 'notification:read' }),
]
