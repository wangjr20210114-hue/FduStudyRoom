import { featurePage } from '@/shared/navigation/feature-page'

export const auditRoutes = [
  featurePage({ path: 'audit', name: 'audit', titleKey: 'shell.navigation.audit', roles: ['SYSTEM_ADMIN'] }),
]
