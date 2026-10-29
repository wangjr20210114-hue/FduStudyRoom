import { i18n } from '@/shared/i18n'

export function getRoleLabel(role: string): string {
  const { t } = i18n.global
  if (role === 'USER') return t('auth.roles.student')
  if (role === 'SYSTEM_ADMIN') return t('auth.roles.admin')
  if (role === 'MAINTAINER') return t('auth.roles.maintainer')
  return role
}
