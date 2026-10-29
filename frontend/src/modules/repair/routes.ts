import { featurePage } from '@/shared/navigation/feature-page'

export const repairRoutes = [
  featurePage({ path: 'repairs', name: 'repairs', titleKey: 'shell.navigation.repairs', roles: ['USER'] }),
  featurePage({ path: 'manage/repairs', name: 'manage-repairs', titleKey: 'shell.navigation.manageRepairs', roles: ['SYSTEM_ADMIN'] }),
  featurePage({ path: 'maintenance/tasks', name: 'maintenance-tasks', titleKey: 'shell.navigation.maintenanceTasks', roles: ['MAINTAINER'] }),
  featurePage({ path: 'maintenance/history', name: 'maintenance-history', titleKey: 'shell.navigation.maintenanceHistory', roles: ['MAINTAINER'] }),
]
