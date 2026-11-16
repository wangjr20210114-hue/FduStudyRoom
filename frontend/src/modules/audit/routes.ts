import type { WorkspaceRoute } from '@/shared/navigation/types'

export const auditRoutes: WorkspaceRoute[] = [{ path: 'audit', name: 'audit', component: () => import('./pages/AuditPage.vue'), meta: { titleKey: 'shell.navigation.audit', permission: 'audit:read', navigation: true } }]
