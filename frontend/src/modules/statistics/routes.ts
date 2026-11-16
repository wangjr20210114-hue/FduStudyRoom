import type { WorkspaceRoute } from '@/shared/navigation/types'

export const statisticsRoutes: WorkspaceRoute[] = [{ path: 'statistics', name: 'statistics', component: () => import('./pages/StatisticsPage.vue'), meta: { titleKey: 'shell.navigation.statistics', permission: 'statistics:read', navigation: true } }]
