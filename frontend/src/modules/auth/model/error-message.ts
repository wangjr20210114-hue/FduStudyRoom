import { ApiError, getApiErrorMessage } from '@/shared/api'
import { i18n } from '@/shared/i18n'

export function getLoginErrorMessage(error: unknown): string {
  const { t } = i18n.global
  if (error instanceof ApiError) {
    switch (error.code) {
      case 'CSRF_INVALID': return t('auth.errors.csrf')
      case 'INVALID_PASSWORD': return t('auth.errors.password')
      case 'INVALID_CREDENTIALS': return t('auth.errors.credentials')
      case 'LOGIN_NAME_TAKEN': return t('auth.errors.taken')
      case 'INVALID_LOGIN_NAME': return t('auth.fields.accountHint')
      case 'ACCOUNT_DISABLED': return t('auth.errors.disabled')
      case 'LOGIN_NOT_CONFIRMED': return t('auth.errors.unconfirmed')
    }
  }
  return getApiErrorMessage(error)
}
