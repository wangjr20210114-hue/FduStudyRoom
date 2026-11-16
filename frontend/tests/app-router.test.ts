import { describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import { createAppRouter } from '../src/app/router'
import { canAccessPage, safeDestination, visibleNavigation } from '../src/app/router/navigation'
import { createLoginFlow } from '../src/modules/auth/model/login-flow'
import type { AuthApi } from '../src/modules/auth/api/auth'
import { ApiError } from '../src/shared/api/errors'
import { passwordConfig, student } from './fixtures/auth'
import { roleCodes, rolePaths, roleProfiles } from './fixtures/navigation'
import { featurePage } from '../src/shared/navigation/feature-page'

// Keep route behavior separate from the actual component rendering tests.
vi.mock('../src/modules/auth/pages/LoginPage.vue', () => ({ default: {} }))
vi.mock('../src/modules/auth/pages/AccountPage.vue', () => ({ default: {} }))
vi.mock('../src/app/layouts/AppShell.vue', () => ({ default: {} }))
vi.mock('../src/app/pages/StatusPage.vue', () => ({ default: {} }))
vi.mock('../src/app/pages/WorkspacePage.vue', () => ({ default: {} }))
vi.mock('../src/shared/pages/FeaturePage.vue', () => ({ default: {} }))

function setup(identity: typeof student | null = student) {
  const api = {
    configuration: vi.fn<AuthApi['configuration']>().mockResolvedValue(passwordConfig),
    currentUser: vi.fn<AuthApi['currentUser']>().mockResolvedValue(identity),
    login: vi.fn<AuthApi['login']>().mockResolvedValue(student),
    register: vi.fn<AuthApi['register']>().mockResolvedValue(student),
    logout: vi.fn<AuthApi['logout']>().mockResolvedValue(undefined),
  }
  const session = createLoginFlow(api)
  return { api, session, router: createAppRouter(session, createMemoryHistory()) }
}

describe('protected navigation', () => {
  it('sends an anonymous deep link to login and keeps an allowed destination', async () => {
    const { router, session } = setup(null)
    await router.push('/system/users')
    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/system/users')
    expect(session.user.value).toBeNull()
  })
  it('restores a server session before entering the workspace', async () => {
    const { router, api, session } = setup()
    await router.push('/')
    expect(router.currentRoute.value.name).toBe('workspace')
    expect(session.user.value).toEqual(student)
    expect(api.currentUser).toHaveBeenCalledTimes(1)
  })
  it('returns a signed-in user to an allowed destination from login', async () => {
    const { router } = setup()
    await router.push('/login?redirect=/account')
    expect(router.currentRoute.value.name).toBe('account')
  })
  it('ignores an external redirect provided in the URL', async () => {
    const { router } = setup()
    await router.push('/login?redirect=https://example.com')
    expect(router.currentRoute.value.name).toBe('workspace')
  })
  it('checks permissions even when the requested destination came from login', async () => {
    const { router } = setup()
    await router.push('/login?redirect=/system/users')
    expect(router.currentRoute.value.name).toBe('forbidden')
  })
  it('does not grant user management from the role name alone', async () => {
    const { router } = setup({ ...student, role: 'SYSTEM_ADMIN' })
    await router.push('/system/users')
    expect(router.currentRoute.value.name).toBe('forbidden')
  })
  it('allows a backend-granted permission without depending on a role name', async () => {
    const { router } = setup({ ...student, role: 'CUSTOM', permissions: ['identity:read', 'identity:users:read'] })
    await router.push('/system/users')
    expect(router.currentRoute.value.name).toBe('users')
  })
  it('checks the session again on the next navigation and rejects an expired session', async () => {
    const { router, api, session } = setup()
    await router.push('/workspace')
    api.currentUser.mockResolvedValueOnce(null)
    await router.push('/account')
    expect(router.currentRoute.value.name).toBe('login')
    expect(session.user.value).toBeNull()
  })
  it('removes revoked permissions before entering the next protected page', async () => {
    const { router, api, session } = setup({ ...student, permissions: ['identity:read', 'identity:users:read'] })
    await router.push('/workspace')
    api.currentUser.mockResolvedValue(student)
    await router.push('/system/users')
    expect(router.currentRoute.value.name).toBe('forbidden')
    expect(visibleNavigation(session.user.value).map(item => item.path)).toEqual(rolePaths.USER)
  })
  it('hides previously confirmed identity when a later service check fails', async () => {
    const { router, api, session } = setup()
    await router.push('/workspace')
    api.currentUser.mockRejectedValueOnce(new ApiError('network'))
    await router.push('/account')
    expect(router.currentRoute.value.name).toBe('login')
    expect(session.user.value).toBeNull()
    expect(session.error.value).toMatchObject({ kind: 'network' })
  })
  it('renders unknown locations through the protected not-found route', async () => {
    const { router } = setup()
    await router.push('/does-not-exist')
    expect(router.currentRoute.value.name).toBe('not-found')
    expect(router.currentRoute.value.meta.requiresAuth).toBe(true)
  })
  it('does not reuse a previous identity after logout and back navigation', async () => {
    const { router, api, session } = setup()
    await router.push('/workspace')
    await session.signOut()
    api.currentUser.mockResolvedValue(null)
    await router.push('/account')
    expect(router.currentRoute.value.name).toBe('login')
    expect(session.user.value).toBeNull()
  })
})

describe('menu and return path', () => {
  it('only exposes entries backed by the current permissions', () => {
    expect(visibleNavigation(null)).toEqual([])
    expect(visibleNavigation(student).map(item => item.path)).toEqual(rolePaths.USER)
    expect(visibleNavigation(roleProfiles.MAINTAINER).map(item => item.path)).toEqual(rolePaths.MAINTAINER)
    expect(visibleNavigation({ ...student, permissions: ['identity:users:read'] }).map(item => item.path)).toEqual(['/system/users'])
  })
  it.each(['https://example.com', '//example.com', '/login', '/dev/components', '/workspace?next=https://example.com', ['/account'], null])('rejects unsupported return paths: %s', value => {
    expect(safeDestination(value)).toBe('/workspace')
  })
})

describe('role module navigation', () => {
  it.each(roleCodes)('allows every configured entry for %s', async role => {
    const { router }=setup(roleProfiles[role])
    for (const path of rolePaths[role]) {
      await router.push(path)
      expect(router.currentRoute.value.path).toBe(path)
      expect(router.currentRoute.value.name).not.toBe('not-found')
    }
  })

  it.each([
    ['USER','/manage/rooms'], ['USER','/manage/reservations'],
    ['USER','/manage/approvals'], ['USER','/manage/repairs'],
    ['USER','/system/settings'], ['USER','/statistics'], ['USER','/audit'],
    ['USER','/maintenance/tasks'], ['MAINTAINER','/manage/rooms'],
    ['MAINTAINER','/system/users'], ['MAINTAINER','/manage/approvals'],
  ] as const)('rejects a direct %s visit to %s', async (role,path) => {
    const { router }=setup(roleProfiles[role])
    await router.push(path)
    expect(router.currentRoute.value.name).toBe('forbidden')
  })

  it.each(roleCodes)('builds the expected %s menu', role => {
    expect(visibleNavigation(roleProfiles[role]).map(item=>item.path)).toEqual(rolePaths[role])
  })

  it('removes previous role entries when the server reports a different role', async () => {
    const { router,api,session }=setup(roleProfiles.SYSTEM_ADMIN)
    await router.push('/manage/rooms')
    api.currentUser.mockResolvedValue(roleProfiles.USER)
    await router.push('/manage/reservations')
    expect(router.currentRoute.value.name).toBe('forbidden')
    expect(visibleNavigation(session.user.value).map(item=>item.path)).toEqual(rolePaths.USER)
  })

  it('preserves a module deep link for an anonymous visitor', async () => {
    const { router }=setup(null)
    await router.push('/maintenance/tasks')
    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/maintenance/tasks')
  })

  it('checks the destination role after login redirects', async () => {
    const { router }=setup(roleProfiles.USER)
    await router.push('/login?redirect=/manage/approvals')
    expect(router.currentRoute.value.name).toBe('forbidden')
  })

  it('uses the same granted permissions regardless of a custom role label', () => {
    expect(visibleNavigation({...student,role:'CUSTOM'}).map(item=>item.path)).toEqual(rolePaths.USER)
  })

  it.each(['/rooms','/manage/approvals','/maintenance/tasks'])('accepts the known return path %s', path => {
    expect(safeDestination(path)).toBe(path)
  })
})


describe('rapid navigation', () => {
  it('opens the latest requested page while sharing an in-flight session check', async () => {
    const { router, api, session } = setup()
    await router.push('/workspace')
    let complete!: (identity: typeof student) => void
    let started!: () => void
    const checking = new Promise<void>(resolve => { started = resolve })
    api.currentUser.mockImplementationOnce(() => new Promise(resolve => { complete = resolve; started() }))
    const checkSession = vi.spyOn(session, 'initialize')
    const first = router.push('/rooms')
    await checking
    const latest = router.push('/reservations')
    await vi.waitFor(() => { expect(checkSession).toHaveBeenCalledTimes(2) })
    complete(student)
    await Promise.all([first, latest])
    expect(router.currentRoute.value.path).toBe('/reservations')
    expect(session.user.value).toEqual(student)
    expect(api.currentUser).toHaveBeenCalledTimes(2)
  })
})

describe('backend permission navigation contract', () => {
  const singlePermissionRoutes = [
    ['resource:rooms:read', '/rooms', 'rooms'],
    ['resource:rooms:write', '/manage/rooms', 'manage-rooms'],
    ['reservation:read', '/reservations', 'reservations'],
    ['reservation:admin-read', '/manage/reservations', 'manage-reservations'],
    ['approval:read', '/room-requests', 'room-requests'],
    ['approval:decide', '/manage/approvals', 'approvals'],
    ['approval:flow:read', '/manage/approvals', 'approvals'],
    ['repair:report', '/repairs', 'repairs'],
    ['repair:history:read', '/repairs', 'repairs'],
    ['repair:history:read', '/maintenance/history', 'maintenance-history'],
    ['identity:users:read', '/system/users', 'users'],
    ['identity:settings:read', '/system/settings', 'settings'],
    ['statistics:read', '/statistics', 'statistics'],
    ['audit:read', '/audit', 'audit'],
    ['notification:read', '/notifications', 'notifications'],
  ] as const

  it.each(singlePermissionRoutes)('lets CUSTOM with only %s reach %s', async (permission, path, name) => {
    const actor = { ...student, role: 'CUSTOM', permissions: [permission] }
    const { router } = setup(actor)
    await router.push(path)
    expect(router.currentRoute.value.name).toBe(name)
    expect(visibleNavigation(actor).map(item => item.path)).toContain(path)
  })

  it.each(roleCodes)('does not infer business permissions from fixed role %s', async role => {
    const actor = { ...roleProfiles[role], permissions: ['identity:read'] }
    const { router } = setup(actor)
    const businessPaths = [...new Set(Object.values(rolePaths).flat())].filter(path => path !== '/workspace')
    for (const path of businessPaths) {
      await router.push(path)
      expect(router.currentRoute.value.name, role + ': ' + path).toBe('forbidden')
    }
    expect(visibleNavigation(actor).map(item => item.path)).toEqual(['/workspace'])
  })

  it.each([
    [['repair:history:read'], '/manage/repairs', false],
    [['repair:assign'], '/manage/repairs', false],
    [['repair:accept'], '/manage/repairs', false],
    [['repair:history:read', 'repair:evaluate'], '/manage/repairs', false],
    [['repair:history:read', 'repair:assign'], '/manage/repairs', true],
    [['repair:history:read', 'repair:accept'], '/manage/repairs', true],
    [['repair:history:read', 'repair:admin-cancel'], '/manage/repairs', true],
    [['repair:history:read'], '/maintenance/tasks', false],
    [['repair:maintain'], '/maintenance/tasks', false],
    [['repair:history:read', 'repair:maintain'], '/maintenance/tasks', true],
  ] as const)('requires actual read and action grants %s for %s', async (permissions, path, allowed) => {
    const actor = { ...student, role: 'CUSTOM', permissions: [...permissions] }
    const { router } = setup(actor)
    await router.push(path)
    expect(router.currentRoute.value.name === 'forbidden').toBe(!allowed)
    expect(visibleNavigation(actor).some(item => item.path === path)).toBe(allowed)
  })

  it('combines the required grant with anyPermissions and rejects an empty alternative set', () => {
    const actor = { ...student, permissions: ['repair:accept'] }
    expect(canAccessPage(actor, { anyPermissions: ['repair:assign', 'repair:accept'] })).toBe(true)
    expect(canAccessPage(actor, { permission: 'repair:history:read', anyPermissions: ['repair:accept'] })).toBe(false)
    expect(canAccessPage(actor, { anyPermissions: [] })).toBe(false)
    expect(canAccessPage(null, { anyPermissions: ['repair:accept'] })).toBe(false)
  })

  it('preserves anyPermissions in feature metadata without silently adding identity:read', () => {
    const alternatives = ['approval:decide', 'approval:flow:read']
    const route = featurePage({ path: 'fixture', name: 'fixture', titleKey: 'shell.navigation.approvals', anyPermissions: alternatives })
    expect(route.meta.anyPermissions).toEqual(alternatives)
    expect(route.meta.permission).toBeUndefined()
  })

  it('keeps granted pages when the server changes only the role label', async () => {
    const { router, api, session } = setup(roleProfiles.SYSTEM_ADMIN)
    await router.push('/manage/rooms')
    api.currentUser.mockResolvedValue({ ...roleProfiles.SYSTEM_ADMIN, role: 'CUSTOM' })
    await router.push('/manage/reservations')
    expect(router.currentRoute.value.name).toBe('manage-reservations')
    expect(session.user.value?.role).toBe('CUSTOM')
    expect(visibleNavigation(session.user.value).map(item => item.path)).toEqual(rolePaths.SYSTEM_ADMIN)
  })

  it('revokes granted pages when permissions change even if the role label does not', async () => {
    const { router, api, session } = setup(roleProfiles.SYSTEM_ADMIN)
    await router.push('/manage/rooms')
    api.currentUser.mockResolvedValue({ ...roleProfiles.SYSTEM_ADMIN, permissions: ['identity:read'] })
    await router.push('/manage/reservations')
    expect(router.currentRoute.value.name).toBe('forbidden')
    expect(visibleNavigation(session.user.value).map(item => item.path)).toEqual(['/workspace'])
  })
})
