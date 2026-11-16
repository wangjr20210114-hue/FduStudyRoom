import type { WorkspaceRoute } from '@/shared/navigation/types'

export const notificationRoutes: WorkspaceRoute[] = [{ path: 'notifications', name: 'notifications', component: () => import('./pages/NotificationsPage.vue'), meta: { titleKey: 'shell.navigation.notifications', permission: 'notification:read', navigation: true } }]
