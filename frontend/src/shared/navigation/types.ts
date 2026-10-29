import type { RouteRecordSingleView } from 'vue-router'

export type WorkspaceRoute = RouteRecordSingleView & {
  name: string
  meta: {
    titleKey: string
    permission: string
    navigation?: boolean
    roles?: readonly string[]
  }
}
