import type { WorkspaceRoute } from '@/shared/navigation/types'
const files = import.meta.glob<Record<string, WorkspaceRoute[]>>('../../modules/*/routes.ts', { eager: true })
const order = ['resource', 'reservation', 'approval', 'repair', 'system', 'statistics', 'audit', 'notification', 'agent']
const moduleName = (path: string) => path.split('/')[3] ?? ''
const index = (name: string) => order.includes(name) ? order.indexOf(name) : order.length
const routes = Object.entries(files).sort(([a], [b]) => index(moduleName(a)) - index(moduleName(b))).flatMap(([, exports]) => Object.values(exports).flat())
const names = new Set<string>(), paths = new Set<string>()
for (const route of routes) {
  if (!route.name || !route.path || names.has(route.name) || paths.has(route.path)) throw new Error('Duplicate or invalid module route')
  names.add(route.name); paths.add(route.path)
}
export const workspaceRoutes: WorkspaceRoute[] = [
  { path: 'workspace', name: 'workspace', component: () => import('../pages/WorkspacePage.vue'),
    meta: { permission: 'identity:read', titleKey: 'shell.navigation.workspace', navigation: true } },
  ...routes,
]
