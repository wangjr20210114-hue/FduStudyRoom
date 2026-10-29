import { featurePage } from '@/shared/navigation/feature-page'

export const reservationRoutes = [
  featurePage({ path: 'reservations', name: 'reservations', titleKey: 'shell.navigation.reservations', roles: ['USER'] }),
  featurePage({ path: 'manage/reservations', name: 'manage-reservations', titleKey: 'shell.navigation.manageReservations', roles: ['SYSTEM_ADMIN'] }),
]
