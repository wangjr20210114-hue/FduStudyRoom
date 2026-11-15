<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthSession } from '@/modules/auth/model/auth-session'
import { AppPanel, AppButton, AppAlert, AppLoading } from '@/shared/components'
import { ApiError, getApiErrorMessage } from '@/shared/api'
import { auditEntries, safeSummary, type AuditEntry, type AuditFilter } from '../api/audit'
import { dateText } from '@/modules/notification/model/format'
const { t, locale } = useI18n({ useScope: 'global' }); const session = useAuthSession(); const message = (key: string) => t('modules.audit.' + key)
const allowed = computed(() => session.user.value?.permissions.includes('audit:read') ?? false)
const draft = reactive<AuditFilter>({ traceId: '', sourceModule: '', sourceId: '' }); let applied: AuditFilter = { ...draft }
const items = ref<AuditEntry[]>([]); const page = ref(0); const total = ref(0); const loading = ref(false); const error = ref(''); let sequence = 0; let controller: AbortController | undefined
async function load() {
  const current = ++sequence; controller?.abort(); controller = new AbortController(); items.value = []; total.value = 0; error.value = ''
  if (!allowed.value) { loading.value = false; return }
  loading.value = true
  try { const value = await auditEntries(page.value, applied, controller.signal); if (current !== sequence) return
    if (!value.items.length && page.value > 0 && page.value * 20 >= value.total) { page.value = Math.max(0, Math.ceil(value.total / 20) - 1); await load(); return }
    items.value = value.items; total.value = value.total
  } catch (cause) { if (current === sequence && !(cause instanceof ApiError && cause.kind === 'cancelled')) error.value = getApiErrorMessage(cause) }
  finally { if (current === sequence) loading.value = false }
}
function search() { page.value = 0; applied = { traceId: draft.traceId.trim(), sourceModule: draft.sourceModule, sourceId: draft.sourceId.trim() }; void load() }
function move(amount: number) { page.value += amount; void load() }
watch([allowed, () => session.user.value?.id], search, { immediate: true }); onBeforeUnmount(() => { sequence++; controller?.abort() })
</script>
<template>
  <AppPanel data-audit-page><h1 tabindex="-1">{{ message('title') }}</h1>
    <form class="filters" @submit.prevent="search"><label>{{ message('trace') }}<input v-model="draft.traceId" data-audit-trace maxlength="64"></label><label>{{ message('source') }}<select v-model="draft.sourceModule" data-audit-source><option value="">{{ message('all') }}</option><option v-for="source in ['M1','M2','M3','D','EXTERNAL']" :key="source" :value="source">{{ message('sources.' + source) }}</option></select></label><label>{{ message('object') }}<input v-model="draft.sourceId" data-audit-source-id></label><AppButton data-audit-search html-type="submit" :loading="loading" :disabled="!allowed">{{ message('search') }}</AppButton></form>
    <AppAlert v-if="!allowed" :title="t('errors.forbidden')" type="error" /><AppAlert v-if="error" data-audit-error :title="error" type="error" /><AppLoading v-if="loading" :label="message('loading')" /><p v-else-if="!error && allowed && !items.length">{{ message('empty') }}</p>
    <article v-for="entry in items" :key="entry.id" data-audit-event :data-id="entry.id" :data-audit-success="entry.success" class="entry"><h2>{{ entry.action }} · {{ message(entry.success ? 'success' : 'failure') }}</h2>
      <dl><dt>{{ message('actor') }}</dt><dd>{{ entry.actorId ?? message('anonymous') }}</dd><dt>{{ message('object') }}</dt><dd>{{ entry.sourceId ?? '—' }}</dd><dt>{{ message('source') }}</dt><dd>{{ entry.sourceModule }} · {{ entry.sourceService }}</dd><dt>{{ message('time') }}</dt><dd><time :datetime="entry.occurredAt">{{ dateText(entry.occurredAt, locale) }}</time></dd><dt>{{ message('trace') }}</dt><dd>{{ entry.traceId }}</dd></dl>
      <details data-audit-summary><summary>{{ message('summary') }}</summary><h3>{{ message('before') }}</h3><pre>{{ JSON.stringify(safeSummary(entry.beforeData), null, 2) }}</pre><h3>{{ message('after') }}</h3><pre>{{ JSON.stringify(safeSummary(entry.afterData), null, 2) }}</pre></details>
    </article>
    <footer v-if="allowed" class="paging"><AppButton data-audit-prev :disabled="loading || page === 0" @click="move(-1)">{{ message('previous') }}</AppButton><span>{{ message('page') }} {{ page + 1 }} · {{ message('total') }} {{ total }}</span><AppButton data-audit-next :disabled="loading || (page + 1) * 20 >= total" @click="move(1)">{{ message('next') }}</AppButton></footer>
  </AppPanel>
</template>
<style scoped>
h1 { margin:0 0 var(--space-4);font-size:24px } h2 { font-size:18px } .filters,.paging { display:flex;gap:var(--space-3);flex-wrap:wrap;align-items:end } label { display:grid;gap:var(--space-2) } input,select { min-height:36px;padding:var(--space-2);color:var(--color-text);background:var(--color-surface);border:1px solid var(--color-border);border-radius:var(--radius-control) } .entry { padding:var(--space-3) 0;border-bottom:1px solid var(--color-border) } dl { display:grid;grid-template-columns:max-content minmax(0,1fr);gap:var(--space-2) } dd { margin:0;overflow-wrap:anywhere } pre { white-space:pre-wrap;overflow-wrap:anywhere } .paging { margin-top:var(--space-4);align-items:center }
</style>
