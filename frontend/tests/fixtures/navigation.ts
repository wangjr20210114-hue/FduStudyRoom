import type { CurrentUser } from '../../src/modules/auth/types'
import { student } from './auth'

export const roleCodes = ['USER', 'SYSTEM_ADMIN', 'MAINTAINER'] as const

export const roleProfiles: Record<typeof roleCodes[number], CurrentUser> = {
  USER: student,
  SYSTEM_ADMIN: {
    ...student, id: '00000000-0000-0000-0000-000000002002',
    loginName: 'test-admin', displayName: 'Test Admin', role: 'SYSTEM_ADMIN',
    permissions: ['identity:read', 'identity:users:read', 'identity:users:update'],
  },
  MAINTAINER: {
    ...student, id: '00000000-0000-0000-0000-000000002003',
    loginName: 'test-maintainer', displayName: 'Test Maintainer', role: 'MAINTAINER',
  },
}

export const rolePaths = {
  USER: ['/workspace', '/rooms', '/reservations', '/room-requests', '/repairs', '/notifications'],
  SYSTEM_ADMIN: ['/workspace', '/manage/rooms', '/manage/reservations', '/manage/approvals',
    '/manage/repairs', '/system/users', '/system/settings', '/statistics', '/audit', '/notifications'],
  MAINTAINER: ['/workspace', '/maintenance/tasks', '/maintenance/history', '/notifications'],
}
