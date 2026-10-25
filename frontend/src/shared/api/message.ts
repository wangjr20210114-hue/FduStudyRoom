import { i18n } from '../i18n'
import { ApiError } from './errors'

export function getApiErrorMessage(error: unknown): string {
  const { t } = i18n.global
  if (!(error instanceof ApiError)) return t('errors.unknown')
  switch (error.kind) {
    case 'network': return t('errors.network')
    case 'timeout': return t('errors.timeout')
    case 'cancelled': return t('errors.cancelled')
    case 'invalid_response': return t('errors.invalidResponse')
    case 'invalid_request': return t('errors.invalidRequest')
    case 'http':
      switch (error.status) {
        case 401: return t('errors.unauthenticated')
        case 403: return t('errors.forbidden')
        case 404: return t('errors.notFound')
        case 409: return t('errors.conflict')
        case 422: return t('errors.validation')
        case 429: return t('errors.rateLimited')
        default: return error.status && error.status >= 500 ? t('errors.server') : t('errors.http')
      }
  }
}
