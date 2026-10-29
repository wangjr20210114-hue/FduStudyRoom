import type { WorkspaceRoute } from './types'

interface FeaturePageOptions {
  path: string
  name: string
  titleKey: string
  roles?: readonly string[]
  permission?: string
}

export function featurePage(options: FeaturePageOptions): WorkspaceRoute {
  return {
    path: options.path,
    name: options.name,
    component: () => import('../pages/FeaturePage.vue'),
    meta: {
      titleKey: options.titleKey,
      roles: options.roles,
      permission: options.permission ?? 'identity:read',
      navigation: true,
    },
  }
}
