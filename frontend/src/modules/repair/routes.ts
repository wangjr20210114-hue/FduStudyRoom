import { featurePage } from '@/shared/navigation/feature-page'
const component=()=>import('./pages/RepairPage.vue')
export const repairRoutes = [
  { ...featurePage({ path: 'repairs', name: 'repairs', titleKey: 'shell.navigation.repairs', anyPermissions: ['repair:report', 'repair:history:read'] }), component },
  { ...featurePage({ path: 'manage/repairs', name: 'manage-repairs', titleKey: 'shell.navigation.manageRepairs', permission: 'repair:history:read', anyPermissions: ['repair:assign', 'repair:accept', 'repair:admin-cancel'] }), component },
  { ...featurePage({ path: 'maintenance/tasks', name: 'maintenance-tasks', titleKey: 'shell.navigation.maintenanceTasks', permission: 'repair:history:read', anyPermissions: ['repair:maintain'] }), component },
  { ...featurePage({ path: 'maintenance/history', name: 'maintenance-history', titleKey: 'shell.navigation.maintenanceHistory', permission: 'repair:history:read' }), component },
]
