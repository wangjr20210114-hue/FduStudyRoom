import { featurePage } from '@/shared/navigation/feature-page'

export const approvalRoutes = [
  featurePage({ path: 'room-requests', name: 'room-requests', titleKey: 'shell.navigation.roomRequests', permission: 'approval:read' }),
  featurePage({ path: 'manage/approvals', name: 'approvals', titleKey: 'shell.navigation.approvals', anyPermissions: ['approval:decide', 'approval:flow:read'] }),
]
