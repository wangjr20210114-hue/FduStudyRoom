import { api, ApiError } from '@/shared/api'
export interface PageResult<T> { items: T[]; page: number; size: number; total: number }
export interface Notice { id: string; eventId: string; recipientId: string; sourceModule: string; sourceId: string; title: string; body: string; locale: string; scheduledAt: string; status: string; emailStatus: string; lastErrorCode: string | null; nextRetryAt: string | null; readAt: string | null }
export function checkPage<T>(value: PageResult<T>, page: number): PageResult<T> {
  if (!value || !Array.isArray(value.items) || value.items.length > 20 || value.page !== page || value.size !== 20 || !Number.isInteger(value.total) || value.total < value.items.length) throw new ApiError('invalid_response')
  return value
}
export async function notifications(page: number, owner: string, signal: AbortSignal) {
  const result = checkPage(await api.request<PageResult<Notice>>('/notifications', { query: { page, size: 20 }, signal }), page)
  if (result.items.some(item => item.recipientId !== owner)) throw new ApiError('invalid_response')
  return result
}
export async function markRead(id: string, signal: AbortSignal) {
  const csrf = await api.request<{ headerName: string; token: string }>('/auth/csrf', { signal })
  if (csrf.headerName !== 'X-XSRF-TOKEN' || typeof csrf.token !== 'string' || !csrf.token || csrf.token.length > 2048) throw new ApiError('invalid_response')
  await api.request<void>('/notifications/' + encodeURIComponent(id) + '/read', { method: 'POST', headers: { [csrf.headerName]: csrf.token }, signal })
}
