import { getAppLocale } from '../i18n'
import { createApiClient } from './client'

export const api = createApiClient({
  baseUrl: '/api',
  getLocale: getAppLocale,
})

export { createApiClient } from './client'
export type { RequestOptions } from './client'
export { ApiError } from './errors'
export type { ApiErrorKind } from './errors'
export { getApiErrorMessage } from './message'
