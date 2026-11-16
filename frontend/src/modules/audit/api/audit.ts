import { api } from '@/shared/api'
import { checkPage, type PageResult } from '@/modules/notification/api/notifications'
export interface AuditEntry { id: string; eventKey: string; sourceService: string; actorId: string | null; action: string; sourceModule: string; sourceId: string | null; success: boolean; occurredAt: string; traceId: string; beforeData: Record<string, unknown>; afterData: Record<string, unknown> }
export interface AuditFilter { traceId: string; sourceModule: string; sourceId: string }
export async function auditEntries(page: number, filter: AuditFilter, signal: AbortSignal) {
  return checkPage(await api.request<PageResult<AuditEntry>>('/audit', { signal, query: { page, size: 20, traceId: filter.traceId || undefined, sourceModule: filter.sourceModule || undefined, sourceId: filter.sourceId || undefined } }), page)
}
const safeKeys = new Set(['status', 'reasonCode', 'departmentCode', 'version', 'authVersion', 'score', 'level', 'enabled', 'canApplyRoom', 'hasEmail', 'verified', 'hasDepartment', 'roomId', 'seatId', 'roleId', 'startsAt', 'endsAt', 'roleCode', 'permissions', 'maxReservationHours', 'beforeStartReminderMinutes', 'afterStartReminderMinutes', 'checkinDeadlineMinutes', 'approvalTtlMinutes', 'repairSlaMinutes'])
export function safeSummary(value: Record<string, unknown> | null | undefined): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return {}
  return Object.fromEntries(Object.entries(value).filter(([key, item]) => safeKeys.has(key) && ((typeof item === 'string' && item.length <= 128) || typeof item === 'boolean' || (typeof item === 'number' && Number.isFinite(item)) || (key === 'permissions' && Array.isArray(item) && item.length <= 200 && item.every(v => typeof v === 'string' && /^[a-z][a-z0-9]*(?::[a-z][a-z0-9]*)+$/.test(v))))))
}
