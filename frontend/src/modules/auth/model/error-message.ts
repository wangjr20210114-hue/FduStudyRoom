import { ApiError, getApiErrorMessage } from '@/shared/api'
import { i18n } from '@/shared/i18n'

export function getLoginErrorMessage(error: unknown): string {
  const { t } = i18n.global
  if (error instanceof ApiError) {
    switch (error.code) {
      case 'CSRF_INVALID': return t('auth.errors.csrf')
      case 'IDENTITY_INELIGIBLE': return t('auth.errors.ineligible')
      case 'ACCOUNT_DISABLED': return t('auth.errors.disabled')
      case 'UIS_NOT_CONFIGURED': return t('auth.official.unavailable')
      case 'DEVELOPMENT_LOGIN_DISABLED': return t('auth.errors.environment')
      case 'LOGIN_NOT_CONFIRMED': return t('auth.errors.unconfirmed')
    }
  }
  return getApiErrorMessage(error)
}
