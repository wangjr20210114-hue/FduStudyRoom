import { createRouter, createWebHistory, type RouterHistory } from 'vue-router'
import type { AuthSession } from '@/modules/auth/model/auth-session'
import { canAccessPage, safeDestination } from './navigation'
import { workspaceRoutes } from './pages'

export function createAppRouter(session: AuthSession, history: RouterHistory = createWebHistory()) {
  const router = createRouter({
    history,
    routes: [
      { path: '/login', name: 'login', component: () => import('@/modules/auth/pages/LoginPage.vue') },
      { path: '/register', name: 'register', component: () => import('@/modules/auth/pages/LoginPage.vue') },
      {
        path: '/', component: () => import('../layouts/AppShell.vue'), meta: { requiresAuth: true },
        children: [
          { path: '', redirect: '/workspace' },
          ...workspaceRoutes,
          { path: 'account', name: 'account', component: () => import('@/modules/auth/pages/AccountPage.vue'),
            meta: { permission: 'identity:read', titleKey: 'shell.account' } },
          { path: 'forbidden', name: 'forbidden', component: () => import('../pages/StatusPage.vue'),
            meta: { titleKey: 'shell.forbidden', messageKey: 'errors.forbidden' } },
          { path: ':pathMatch(.*)*', name: 'not-found', component: () => import('../pages/StatusPage.vue'),
            meta: { titleKey: 'shell.notFound', messageKey: 'errors.notFound' } },
        ],
      },
      ...(import.meta.env.DEV ? [
        { path: '/dev/components', name: 'components', component: () => import('../dev/ComponentPreview.vue') },
      ] : []),
    ],
    scrollBehavior: () => ({ top: 0 }),
  })

  router.beforeEach(async to => {
    if (to.name === 'components') return true
    if (session.busy.value && session.phase.value !== 'loading') return false
    if (to.name === 'login' || to.name === 'register') {
      if (!session.config.value && !session.error.value) await session.initialize()
      return session.user.value ? safeDestination(to.query.redirect) : true
    }
    await session.initialize({ preserveIdentity: true })
    if (!session.user.value) {
      return { name: 'login', query: { redirect: safeDestination(to.fullPath) }, replace: true }
    }
    if (!canAccessPage(session.user.value, to.meta)) {
      return { name: 'forbidden', replace: true }
    }
    return true
  })
  return router
}
