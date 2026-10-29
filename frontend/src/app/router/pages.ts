import type { WorkspaceRoute } from '@/shared/navigation/types'
import { resourceRoutes } from '@/modules/resource/routes'
import { reservationRoutes } from '@/modules/reservation/routes'
import { approvalRoutes } from '@/modules/approval/routes'
import { repairRoutes } from '@/modules/repair/routes'
import { systemRoutes } from '@/modules/system/routes'
import { auditRoutes } from '@/modules/audit/routes'
import { notificationRoutes } from '@/modules/notification/routes'

export const workspaceRoutes: WorkspaceRoute[] = [
  {
    path: 'workspace',
    name: 'workspace',
    component: () => import('../pages/WorkspacePage.vue'),
    meta: { permission: 'identity:read', titleKey: 'shell.navigation.workspace', navigation: true },
  },
  ...resourceRoutes,
  ...reservationRoutes,
  ...approvalRoutes,
  ...repairRoutes,
  ...systemRoutes,
  ...auditRoutes,
  ...notificationRoutes,
]
