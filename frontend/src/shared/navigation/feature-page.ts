import type { WorkspaceRoute } from './types'

interface FeaturePageOptions {
  path: string
  name: string
  titleKey: string
  permission?: string
  anyPermissions?: readonly string[]
}

export function featurePage(options: FeaturePageOptions): WorkspaceRoute {
  return {
    path: options.path,
    name: options.name,
    component: () => import('../pages/FeaturePage.vue'),
    meta: {
      titleKey: options.titleKey,
      permission: options.permission ?? (options.anyPermissions === undefined ? 'identity:read' : undefined),
      anyPermissions: options.anyPermissions,
      navigation: true,
    },
  }
}
