export function dateText(value: string | null | undefined, locale: string): string {
  if (!value || !Number.isFinite(Date.parse(value))) return '—'
  return new Intl.DateTimeFormat(locale, { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))
}
export function numberText(value: unknown, locale: string): string {
  return typeof value === 'number' && Number.isFinite(value) ? new Intl.NumberFormat(locale, { maximumFractionDigits: 2 }).format(value) : '—'
}
export function rateText(value: unknown, locale: string): string {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0 && value <= 1 ? new Intl.NumberFormat(locale, { style: 'percent', maximumFractionDigits: 2 }).format(value) : '—'
}
