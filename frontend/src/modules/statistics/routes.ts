import { featurePage } from '@/shared/navigation/feature-page'
export const statisticsRoutes = [
  featurePage({ path: 'statistics', name: 'statistics', titleKey: 'shell.navigation.statistics', roles: ['SYSTEM_ADMIN'] }),
]
