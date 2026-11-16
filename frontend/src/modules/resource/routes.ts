import type { WorkspaceRoute } from '@/shared/navigation/types'
export const resourceRoutes:WorkspaceRoute[]=[
 {path:'rooms',name:'rooms',component:()=>import('./pages/ResourcePage.vue'),meta:{titleKey:'shell.navigation.rooms',permission:'resource:rooms:read',navigation:true}},
 {path:'manage/rooms',name:'manage-rooms',component:()=>import('./pages/ResourcePage.vue'),props:{manage:true},meta:{titleKey:'shell.navigation.manageRooms',permission:'resource:rooms:write',navigation:true}},
 {path:'rooms/:roomId/checkin-code',name:'room-code',component:()=>import('./pages/ResourceCodePage.vue'),meta:{titleKey:'modules.resource.code.title',permission:'resource:checkin-codes:read',navigation:false}},
]
