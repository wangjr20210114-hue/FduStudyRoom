<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthSession } from '@/modules/auth/model/auth-session'
import { AppPanel, AppButton, AppAlert, AppLoading } from '@/shared/components'
import { ApiError, getApiErrorMessage } from '@/shared/api'
import { notifications, markRead, type Notice } from '../api/notifications'
import { dateText } from '../model/format'
const { t, locale } = useI18n({ useScope: 'global' }); const session = useAuthSession()
const owner = computed(() => session.user.value?.id ?? '')
const allowed = computed(() => session.user.value?.permissions.includes('notification:read') ?? false)
const items = ref<Notice[]>([]); const page = ref(0); const total = ref(0); const loading = ref(false); const error = ref(''); const writing = ref('')
let sequence = 0; let controller: AbortController | undefined; let writeController: AbortController | undefined
const message = (key: string) => t('modules.notification.' + key)
const statusKeys = ['NOT_CONFIGURED', 'PENDING', 'SENT', 'FAILED', 'UNKNOWN', 'CANCELLED']
function emailLabel(status: string) { return message(statusKeys.includes(status) ? 'email.' + status : 'unavailable') }
function failureLabel(code: string | null) { return message(['SMTP_REJECTED', 'SMTP_CONNECT_FAILED', 'SMTP_DELIVERY_UNCERTAIN', 'SMTP_NOT_CONFIGURED', 'EMAIL_ADDRESS_MISSING'].includes(code ?? '') ? 'failure.' + code : 'failure.other') }
async function load() {
  const current = ++sequence; controller?.abort(); controller = new AbortController(); error.value = ''; items.value = []; total.value = 0
  if (!allowed.value || !owner.value) { loading.value = false; return }
  loading.value = true
  try {
    const value = await notifications(page.value, owner.value, controller.signal)
    if (current !== sequence) return
    if (!value.items.length && page.value > 0 && page.value * 20 >= value.total) { page.value = Math.max(0, Math.ceil(value.total / 20) - 1); await load(); return }
    items.value = value.items; total.value = value.total
  } catch (cause) { if (current === sequence && !(cause instanceof ApiError && cause.kind === 'cancelled')) error.value = getApiErrorMessage(cause) }
  finally { if (current === sequence) loading.value = false }
}
async function read(item: Notice) {
  if (writing.value || item.readAt || !allowed.value) return
  const originalOwner = owner.value; writing.value = item.id; writeController?.abort(); writeController = new AbortController(); error.value = ''
  try { await markRead(item.id, writeController.signal); if (owner.value === originalOwner) await load() }
  catch (cause) { if (owner.value === originalOwner && !(cause instanceof ApiError && cause.kind === 'cancelled')) error.value = getApiErrorMessage(cause) }
  finally { writing.value = '' }
}
function move(amount: number) { page.value += amount; void load() }
watch([owner, allowed, locale], () => { page.value = 0; void load() }, { immediate: true })
onBeforeUnmount(() => { sequence++; controller?.abort(); writeController?.abort() })
</script>
<template>
  <AppPanel data-notifications-page>
    <header class="heading"><h1 tabindex="-1">{{ message('title') }}</h1><AppButton data-notification-refresh :loading="loading" :disabled="!allowed" @click="load">{{ message('refresh') }}</AppButton></header>
    <AppAlert v-if="!allowed" :title="t('errors.forbidden')" type="error" />
    <AppAlert v-if="error" data-notification-error :title="error" type="error" />
    <AppLoading v-if="loading" :label="message('loading')" />
    <p v-else-if="!error && allowed && !items.length">{{ message('empty') }}</p>
    <article v-for="item in items" :key="item.id" data-notification :data-id="item.id" class="notice">
      <h2>{{ item.title }}</h2><p class="body">{{ item.body }}</p>
      <p><time :datetime="item.scheduledAt">{{ dateText(item.scheduledAt, locale) }}</time> · {{ message('inApp.' + item.status) }}</p>
      <p>{{ message('emailTitle') }}：<span :data-email-status="item.emailStatus">{{ emailLabel(item.emailStatus) }}</span></p>
      <p v-if="item.lastErrorCode">{{ failureLabel(item.lastErrorCode) }}<template v-if="item.nextRetryAt"> · {{ message('retryAt') }} <time data-retry-at :datetime="item.nextRetryAt">{{ dateText(item.nextRetryAt, locale) }}</time></template></p>
      <p v-if="item.readAt" data-read-at>{{ message('read') }} · <time :datetime="item.readAt">{{ dateText(item.readAt, locale) }}</time></p>
      <AppButton v-else data-mark-read :loading="writing === item.id" :disabled="!!writing" @click="read(item)">{{ message('markRead') }}</AppButton>
    </article>
    <footer v-if="allowed" class="paging"><AppButton data-notification-prev :disabled="loading || page === 0" @click="move(-1)">{{ message('previous') }}</AppButton><span>{{ message('page') }} {{ page + 1 }} · {{ message('total') }} {{ total }}</span><AppButton data-notification-next :disabled="loading || (page + 1) * 20 >= total" @click="move(1)">{{ message('next') }}</AppButton></footer>
  </AppPanel>
</template>
<style scoped>
.heading,.paging { display:flex;align-items:center;gap:var(--space-3);flex-wrap:wrap;justify-content:space-between } h1 { margin:0 0 var(--space-4);font-size:24px } h2 { margin:0;font-size:18px } .notice { padding:var(--space-4) 0;border-top:1px solid var(--color-border) } .body { white-space:pre-wrap } p { overflow-wrap:anywhere } .paging { margin-top:var(--space-4) }
</style>
