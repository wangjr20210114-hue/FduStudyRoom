import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    permission?: string
    anyPermissions?: readonly string[]
    navigation?: boolean
    titleKey?: string
    messageKey?: string
  }
}
