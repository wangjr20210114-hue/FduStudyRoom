import { featurePage } from '@/shared/navigation/feature-page'

export const resourceRoutes = [
  featurePage({ path: 'rooms', name: 'rooms', titleKey: 'shell.navigation.rooms', permission: 'resource:rooms:read' }),
  featurePage({ path: 'manage/rooms', name: 'manage-rooms', titleKey: 'shell.navigation.manageRooms', permission: 'resource:rooms:write' }),
]
