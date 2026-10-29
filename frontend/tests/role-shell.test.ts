import { afterEach, describe, expect, it, vi } from 'vitest'
import { createSSRApp, h } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { createMemoryHistory, RouterView } from 'vue-router'
import { createAppRouter } from '../src/app/router'
import { authSessionKey } from '../src/modules/auth/model/auth-session'
import { createLoginFlow } from '../src/modules/auth/model/login-flow'
import type { AuthApi } from '../src/modules/auth/api/auth'
import type { CurrentUser } from '../src/modules/auth/types'
import { i18n, setAppLocale } from '../src/shared/i18n'
import { passwordConfig } from './fixtures/auth'
import { roleCodes, rolePaths, roleProfiles } from './fixtures/navigation'
import App from '../src/app/App.vue'
import { ApiError } from '../src/shared/api/errors'
// Load UI modules before test timers start on the Windows/WSL checkout.
import '../src/app/layouts/AppShell.vue'
import '../src/app/pages/WorkspacePage.vue'
import '../src/shared/pages/FeaturePage.vue'
import '../src/modules/auth/pages/LoginPage.vue'

// Node SSR has no computed CSS; keep the real app and shell, using default theme tokens.
vi.mock('../src/shared/styles/theme', () => ({ createThemeOverrides: () => ({}) }))

afterEach(() => { setAppLocale('zh-CN') })

async function setupPage(identity: CurrentUser, path: string, fullApp = false) {
  const api = {
    configuration: async () => passwordConfig,
    currentUser: vi.fn<AuthApi['currentUser']>().mockResolvedValue(identity),
    login: async () => identity, register: async () => identity, logout: async () => undefined,
  } satisfies AuthApi
  const session = createLoginFlow(api)
  const router = createAppRouter(session, createMemoryHistory())
  const app = createSSRApp(fullApp ? App : { render: () => h(RouterView) })
  app.provide(authSessionKey, session)
  app.use(i18n)
  app.use(router)
  await router.push(path)
  await router.isReady()
  return { app, api, session, router }
}

async function renderPage(identity: CurrentUser, path: string) {
  const { app } = await setupPage(identity, path)
  return renderToString(app)
}

function navFrom(html: string) {
  const navigation = html.match(/<nav\b[^>]*id="main-navigation"[^>]*>([\s\S]*?)<\/nav>/)?.[1]
  expect(navigation).toBeTruthy()
  return navigation!
}

describe('role shell rendering', () => {
  it.each(roleCodes)('renders only the %s navigation links', async role => {
    const html = await renderPage(roleProfiles[role], '/workspace')
    const navigation = navFrom(html)
    const paths = [...navigation.matchAll(/<a\b[^>]*href="([^"]+)"/g)].map(match => match[1])
    expect(paths).toEqual(rolePaths[role])
    expect(html).toContain('aria-controls="main-navigation"')
    expect(html).toContain('aria-expanded="false"')
    const workspace = html.match(/<div\b[^>]*class="workspace-entries"[^>]*>([\s\S]*?)<\/div>/)?.[1]
    expect(workspace).toBeTruthy()
    const entries = [...workspace!.matchAll(/<a\b[^>]*href="([^"]+)"/g)].map(match => match[1])
    expect(entries).toEqual(rolePaths[role].filter(path => path !== '/workspace'))
  })

  it.each([
    ['USER', '/reservations', '我的预约'],
    ['SYSTEM_ADMIN', '/manage/approvals', '教室审批'],
    ['MAINTAINER', '/maintenance/tasks', '维修任务'],
  ] as const)('renders %s page content and highlights its selected entry', async (role, path, title) => {
    const html = await renderPage(roleProfiles[role], path)
    expect(html).toMatch(new RegExp('<h1[^>]*>' + title + '</h1>'))
    expect(html).toContain('暂未开放')
    const selected = [...navFrom(html).matchAll(/<a\b[^>]*>/g)].map(match => match[0]).find(tag => tag.includes(`href="${path}"`))
    expect(selected).toContain('aria-current="page"')
    expect(selected).toContain('router-link-exact-active')
  })

  it('translates navigation, role and page title into English', async () => {
    setAppLocale('en')
    const html = await renderPage(roleProfiles.USER, '/rooms')
    expect(navFrom(html)).toContain('Book a seat')
    expect(navFrom(html)).toContain('My bookings')
    expect(html).toContain('Not available yet')
    expect(html).toMatch(/<h1[^>]*>Book a seat<\/h1>/)
    expect(html).toContain('User</span>')
  })
})


describe('navigation without a full-page loading flash', () => {
  it.each([
    ['USER', '/reservations', '我的预约'],
    ['SYSTEM_ADMIN', '/manage/approvals', '教室审批'],
    ['MAINTAINER', '/maintenance/tasks', '维修任务'],
  ] as const)('keeps the %s shell visible while checking the next page', async (role, path, title) => {
    const { app, api, session, router } = await setupPage(roleProfiles[role], '/workspace', true)
    let complete!: (identity: CurrentUser) => void
    let started!: () => void
    const checking = new Promise<void>(resolve => { started = resolve })
    api.currentUser.mockImplementationOnce(() => new Promise(resolve => { complete = resolve; started() }))
    const navigation = router.push(path)
    await checking

    expect(session.phase.value).toBe('loading')
    expect(session.user.value).toEqual(roleProfiles[role])
    expect(router.currentRoute.value.path).toBe('/workspace')
    const pendingHtml = await renderToString(app)
    expect(navFrom(pendingHtml)).toContain('href="/workspace"')
    expect(pendingHtml).toContain('class="workspace-entries"')
    expect(pendingHtml).not.toContain('class="session-loading"')
    expect(pendingHtml).toContain('aria-busy="true"')

    complete(roleProfiles[role])
    await navigation
    const html = await renderToString(app)
    expect(router.currentRoute.value.path).toBe(path)
    expect(html).toMatch(new RegExp('<h1[^>]*>' + title + '</h1>'))
    expect(html).not.toContain('class="session-loading"')
  })

  it('clears protected content if the navigation session check fails', async () => {
    const { app, api, session, router } = await setupPage(roleProfiles.USER, '/workspace', true)
    api.currentUser.mockRejectedValueOnce(new ApiError('network'))
    await router.push('/reservations')
    expect(session.user.value).toBeNull()
    expect(router.currentRoute.value.name).toBe('login')
    const html = await renderToString(app)
    expect(html).not.toContain('id="main-navigation"')
    expect(html).not.toContain('class="workspace-entries"')
    expect(html).not.toContain('class="shell-page"')
  })
})
