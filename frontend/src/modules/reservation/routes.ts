import { featurePage } from '@/shared/navigation/feature-page'

export const reservationRoutes = [
  featurePage({ path: 'reservations', name: 'reservations', titleKey: 'shell.navigation.reservations', permission: 'reservation:read' }),
  featurePage({ path: 'manage/reservations', name: 'manage-reservations', titleKey: 'shell.navigation.manageReservations', permission: 'reservation:admin-read' }),
]
