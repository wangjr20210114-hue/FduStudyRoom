export type ApiErrorKind = 'http' | 'network' | 'timeout' | 'cancelled' | 'invalid_response' | 'invalid_request'

export class ApiError extends Error {
  readonly kind: ApiErrorKind
  readonly status?: number
  readonly code?: string
  readonly traceId?: string

  constructor(kind: ApiErrorKind, details: { status?: number; code?: string; traceId?: string } = {}) {
    super(kind)
    this.name = 'ApiError'
    this.kind = kind
    this.status = details.status
    this.code = details.code
    this.traceId = details.traceId
  }
}
