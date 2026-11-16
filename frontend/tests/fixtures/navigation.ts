import type { CurrentUser } from '../../src/modules/auth/types'
import { student } from './auth'

export const roleCodes = ['USER', 'SYSTEM_ADMIN', 'MAINTAINER'] as const

export const roleProfiles: Record<typeof roleCodes[number], CurrentUser> = {
  USER: student,
  SYSTEM_ADMIN: {
    ...student, id: '00000000-0000-0000-0000-000000002002',
    loginName: 'test-admin', displayName: 'Test Admin', role: 'SYSTEM_ADMIN',
    permissions: [
      'identity:read', 'identity:users:read', 'identity:users:update',
      'identity:roles:read', 'identity:roles:write', 'identity:settings:read', 'identity:settings:write',
      'resource:rooms:read', 'resource:rooms:write', 'resource:checkin-codes:read',
      'reservation:read', 'reservation:create', 'reservation:cancel', 'reservation:checkin',
      'reservation:admin-cancel', 'reservation:admin-read',
      'approval:read', 'approval:apply', 'approval:decide', 'approval:flow:read',
      'approval:flow:write', 'approval:admin-cancel', 'repair:report', 'repair:history:read',
      'repair:maintain', 'repair:assign', 'repair:accept', 'repair:evaluate', 'repair:admin-cancel',
      'notification:read', 'audit:read', 'statistics:read',
    ],
  },
  MAINTAINER: {
    ...student, id: '00000000-0000-0000-0000-000000002003',
    loginName: 'test-maintainer', displayName: 'Test Maintainer', role: 'MAINTAINER',
    permissions: [...student.permissions, 'repair:maintain'],
  },
}

// Fresh backend defaults; role labels do not grant access in production.
export const rolePaths = {
  USER: ['/workspace', '/rooms', '/reservations', '/room-requests', '/repairs', '/maintenance/history', '/notifications'],
  SYSTEM_ADMIN: ['/workspace', '/rooms', '/manage/rooms', '/reservations', '/manage/reservations',
    '/room-requests', '/manage/approvals', '/repairs', '/manage/repairs', '/maintenance/tasks',
    '/maintenance/history', '/system/users', '/system/settings', '/statistics', '/audit', '/notifications'],
  MAINTAINER: ['/workspace', '/rooms', '/reservations', '/room-requests', '/repairs',
    '/maintenance/tasks', '/maintenance/history', '/notifications'],
}
