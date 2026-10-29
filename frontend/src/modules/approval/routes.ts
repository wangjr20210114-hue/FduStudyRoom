import { featurePage } from '@/shared/navigation/feature-page'

export const approvalRoutes = [
  featurePage({ path: 'room-requests', name: 'room-requests', titleKey: 'shell.navigation.roomRequests', roles: ['USER'] }),
  featurePage({ path: 'manage/approvals', name: 'approvals', titleKey: 'shell.navigation.approvals', roles: ['SYSTEM_ADMIN'] }),
]
