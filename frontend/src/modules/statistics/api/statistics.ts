import { api, ApiError } from '@/shared/api'
export interface StatisticsResult {
  status: string; from: string; to: string; asOf: string; roomId: string | null; algorithm: string
  capacitySeatSeconds: number | null; occupiedSeatSeconds: number | null; occupiedM1SeatSeconds: number | null; occupiedM2SeatSeconds: number | null
  occupancyRate: number | null; checkInRate: number | null; noShowRate: number | null
  confirmedBookingCount: number | null; confirmedM1BookingCount: number | null; confirmedM2BookingCount: number | null
  checkedInCount: number; noShowCount: number; cohortCount: number; excludedBeforeStartCancelledCount: number; excludedSystemTerminatedCount: number; notDueCount: number
  pendingEvaluationCount: number; excludedUnconfirmedCount: number; historyComplete: boolean; outcomesComplete: boolean; historyAvailableFrom: string | null; sources: { service: string; algorithm: string }[]
}
export async function statistics(query: { from: string; to: string; asOf: string; roomId?: string }, signal: AbortSignal) {
  const value = await api.request<StatisticsResult>('/statistics', { query, signal })
  if (!value || !['OK', 'NO_CAPACITY', 'HISTORY_UNAVAILABLE', 'PENDING_EVALUATION'].includes(value.status) || !Array.isArray(value.sources) || (['from', 'to', 'asOf'] as const).some(key => Date.parse(value[key]) !== Date.parse(query[key])) || (value.roomId ?? null) !== (query.roomId ?? null)) throw new ApiError('invalid_response')
  return value
}
export async function rooms(signal: AbortSignal) {
  return api.request<{ id: string; name: string; code: string }[]>('/resources/rooms', { signal })
}
