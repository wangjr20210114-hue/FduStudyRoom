import type { WorkspaceRoute } from '@/shared/navigation/types'
export const systemRoutes:WorkspaceRoute[]=[
 {path:'system/users',name:'users',component:()=>import('./pages/UsersPage.vue'),meta:{titleKey:'shell.navigation.users',navigation:true,anyPermissions:['identity:users:read','identity:users:update','identity:roles:read','identity:roles:write']}},
 {path:'system/settings',name:'settings',component:()=>import('./pages/SettingsPage.vue'),meta:{titleKey:'shell.navigation.settings',navigation:true,anyPermissions:['identity:settings:read','identity:settings:write']}},
]
