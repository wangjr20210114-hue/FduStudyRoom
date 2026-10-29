import { featurePage } from '@/shared/navigation/feature-page'

export const resourceRoutes = [
  featurePage({ path: 'rooms', name: 'rooms', titleKey: 'shell.navigation.rooms', roles: ['USER'] }),
  featurePage({ path: 'manage/rooms', name: 'manage-rooms', titleKey: 'shell.navigation.manageRooms', roles: ['SYSTEM_ADMIN'] }),
]
