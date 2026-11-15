import { featurePage } from '@/shared/navigation/feature-page'

const component = () => import('./pages/ApprovalPage.vue')
export const approvalRoutes = [
  { ...featurePage({ path: 'room-requests', name: 'room-requests', titleKey: 'shell.navigation.roomRequests', permission: 'approval:read' }), component },
  { ...featurePage({ path: 'manage/approvals', name: 'approvals', titleKey: 'shell.navigation.approvals', anyPermissions: ['approval:decide', 'approval:flow:read'] }), component },
]
