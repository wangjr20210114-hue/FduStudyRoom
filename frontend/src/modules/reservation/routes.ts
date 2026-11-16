import type { WorkspaceRoute } from '@/shared/navigation/types'
export const reservationRoutes:WorkspaceRoute[]=[
 {path:'reservations',name:'reservations',component:()=>import('./pages/ReservationPage.vue'),meta:{titleKey:'shell.navigation.reservations',permission:'reservation:read',navigation:true}},
 {path:'manage/reservations',name:'manage-reservations',component:()=>import('./pages/ReservationPage.vue'),props:{manage:true},meta:{titleKey:'shell.navigation.manageReservations',permission:'reservation:admin-read',navigation:true}},
]
