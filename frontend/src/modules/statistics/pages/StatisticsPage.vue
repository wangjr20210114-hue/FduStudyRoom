<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthSession } from '@/modules/auth/model/auth-session'
import { AppPanel, AppButton, AppAlert, AppLoading } from '@/shared/components'
import { ApiError, getApiErrorMessage } from '@/shared/api'
import { statistics, rooms, type StatisticsResult } from '../api/statistics'
import { dateText, numberText, rateText } from '@/modules/notification/model/format'
const { t, locale } = useI18n({ useScope: 'global' }); const session = useAuthSession(); const message = (key: string) => t('modules.statistics.' + key)
const allowed = computed(() => session.user.value?.permissions.includes('statistics:read') ?? false)
const mayReadRooms = computed(() => session.user.value?.permissions.includes('resource:rooms:read') ?? false)
const localValue = (date: Date) => new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 19)
const now = new Date(Date.now() - 1000); const from = ref(localValue(new Date(now.getTime() - 86400000))); const to = ref(localValue(now)); const asOf = ref(localValue(now)); const roomId = ref('')
const roomOptions = ref<{ id: string; name: string; code: string }[]>([]); const result = ref<StatisticsResult | null>(null); const loading = ref(false); const error = ref(''); const roomError = ref(''); let sequence = 0; let controller: AbortController | undefined; let roomController: AbortController | undefined
const minuteValue = (value: number | null) => numberText(value === null ? null : value / 60, locale.value)
async function load() {
  const current = ++sequence; controller?.abort(); controller = new AbortController(); result.value = null; error.value = ''
  if (!allowed.value) { loading.value = false; return }
  const start = new Date(from.value), end = new Date(to.value), cutoff = new Date(asOf.value)
  if (![start, end, cutoff].every(d => Number.isFinite(d.getTime())) || start >= end) { loading.value = false; error.value = message('invalidRange'); return }
  loading.value = true
  try { const value = await statistics({ from: start.toISOString(), to: end.toISOString(), asOf: cutoff.toISOString(), roomId: roomId.value || undefined }, controller.signal); if (current === sequence) result.value = value }
  catch (cause) { if (current === sequence && !(cause instanceof ApiError && cause.kind === 'cancelled')) error.value = cause instanceof ApiError && cause.status === 503 ? message('sourceUnavailable') : getApiErrorMessage(cause) }
  finally { if (current === sequence) loading.value = false }
}
watch([mayReadRooms, () => session.user.value?.id], async () => {
  roomController?.abort(); roomController = new AbortController(); const current = roomController; roomOptions.value = []; roomError.value = ''; roomId.value = ''
  if (!mayReadRooms.value || !allowed.value) return
  try { const value = await rooms(current.signal); if (!current.signal.aborted) roomOptions.value = value }
  catch (cause) { if (!current.signal.aborted) roomError.value = getApiErrorMessage(cause) }
}, { immediate: true })
watch([allowed, () => session.user.value?.id], () => { void load() }, { immediate: true })
onBeforeUnmount(() => { sequence++; controller?.abort(); roomController?.abort() })
</script>
<template>
  <AppPanel data-statistics-page><h1 tabindex="-1">{{ message('title') }}</h1>
    <form class="filters" @submit.prevent="load"><label>{{ message('from') }}<input v-model="from" data-statistics-from type="datetime-local" step="1" required></label><label>{{ message('to') }}<input v-model="to" data-statistics-to type="datetime-local" step="1" required></label><label>{{ message('asOf') }}<input v-model="asOf" data-statistics-asof type="datetime-local" step="1" required></label><label v-if="mayReadRooms">{{ message('room') }}<select v-model="roomId" data-statistics-room><option value="">{{ message('allRooms') }}</option><option v-for="room in roomOptions" :key="room.id" :value="room.id">{{ room.name }} ({{ room.code }})</option></select></label><AppButton data-statistics-search html-type="submit" :loading="loading" :disabled="!allowed">{{ message('search') }}</AppButton></form>
    <AppAlert v-if="!allowed" :title="t('errors.forbidden')" type="error" /><AppAlert v-if="roomError" :title="roomError" type="error" /><AppAlert v-if="error" data-statistics-error :title="error" type="error" /><AppLoading v-if="loading" :label="message('loading')" />
    <template v-if="result"><p v-if="result.status === 'NO_CAPACITY'">{{ message('noCapacity') }}</p><p v-if="result.status === 'PENDING_EVALUATION'">{{ message('pending') }}</p><p v-if="!result.historyComplete" data-statistics-history>{{ message('historyUnavailable') }}<time v-if="result.historyAvailableFrom" :datetime="result.historyAvailableFrom"> {{ dateText(result.historyAvailableFrom, locale) }}</time></p>
      <dl class="metrics"><div><dt>{{ message('occupancyRate') }}</dt><dd data-stat="occupancyRate">{{ rateText(result.occupancyRate, locale) }}</dd></div><div><dt>{{ message('checkInRate') }}</dt><dd data-stat="checkInRate">{{ rateText(result.checkInRate, locale) }}</dd></div><div><dt>{{ message('noShowRate') }}</dt><dd data-stat="noShowRate">{{ rateText(result.noShowRate, locale) }}</dd></div><div><dt>{{ message('capacityMinutes') }}</dt><dd data-stat="capacityMinutes">{{ minuteValue(result.capacitySeatSeconds) }}</dd></div><div><dt>{{ message('occupiedMinutes') }}</dt><dd data-stat="occupiedMinutes">{{ minuteValue(result.occupiedSeatSeconds) }}</dd></div><div v-for="key in ['confirmedBookingCount', 'checkedInCount', 'noShowCount', 'cohortCount', 'pendingEvaluationCount', 'excludedUnconfirmedCount', 'excludedBeforeStartCancelledCount', 'excludedSystemTerminatedCount', 'notDueCount'] as const" :key="key"><dt>{{ message(key) }}</dt><dd :data-stat="key">{{ numberText(result[key], locale) }}</dd></div></dl>
      <details><summary>{{ message('basis') }}</summary><p>{{ message('calculation') }}</p><p>{{ message('sources') }}：{{ result.sources.map(source => message('source.' + source.service)).join('、') }}</p></details>
    </template>
  </AppPanel>
</template>
<style scoped>
h1 { margin:0 0 var(--space-4);font-size:24px } .filters { display:flex;gap:var(--space-3);flex-wrap:wrap;align-items:end } label { display:grid;gap:var(--space-2) } input,select { min-height:36px;padding:var(--space-2);max-width:100%;color:var(--color-text);background:var(--color-surface);border:1px solid var(--color-border);border-radius:var(--radius-control) } .metrics { display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:var(--space-4) } dd { margin:var(--space-2) 0;font-size:24px } p { overflow-wrap:anywhere }
</style>
