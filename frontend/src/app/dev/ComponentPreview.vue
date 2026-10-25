<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type zhCN from '@/shared/i18n/locales/zh-CN'
import {
  AppAlert, AppButton, AppLoading, AppPanel, AppStatusTag, AppTextField, LanguageSwitcher,
} from '@/shared/components'

const { t } = useI18n({ useScope: 'global' })
const displayName = ref('')
const submitted = ref(false)
const fieldError = computed(() => submitted.value && !displayName.value.trim() ? t('preview.form.required') : '')
const formChecked = computed(() => submitted.value && !!displayName.value.trim())
const buttonFeedback = ref<keyof typeof zhCN.preview.feedback>('initial')
const loading = ref(false)
const retrying = ref(false)
const recovered = ref(false)
let actionTimer: ReturnType<typeof setTimeout> | undefined
let retryTimer: ReturnType<typeof setTimeout> | undefined

watch(displayName, () => { submitted.value = false })

function simulateLoading() {
  loading.value = true
  buttonFeedback.value = 'loading'
  actionTimer = setTimeout(() => {
    loading.value = false
    buttonFeedback.value = 'done'
  }, 1000)
}

function simulateRetry() {
  retrying.value = true
  retryTimer = setTimeout(() => {
    retrying.value = false
    recovered.value = true
  }, 800)
}

const rootStyles = getComputedStyle(document.documentElement)
const swatches = [
  { key: 'primary', token: '--color-primary' },
  { key: 'soft', token: '--color-primary-soft' },
  { key: 'success', token: '--color-success' },
  { key: 'warning', token: '--color-warning' },
  { key: 'error', token: '--color-error' },
] as const

onBeforeUnmount(() => {
  clearTimeout(actionTimer)
  clearTimeout(retryTimer)
})
</script>

<template>
  <div class="component-preview">
    <a class="skip-link" href="#preview-main">{{ t('preview.skip') }}</a>
    <header class="preview-header">
      <a class="preview-brand" href="/" :aria-label="t('common.homeLabel')">
        <img src="/favicon.svg" alt="" width="34" height="34" />
        <span>FduStudyRoom</span>
      </a>
      <div class="preview-header-actions">
        <LanguageSwitcher />
        <a class="preview-back" href="/">{{ t('common.home') }} <span aria-hidden="true">↗</span></a>
      </div>
    </header>

    <main id="preview-main" class="preview-main">
      <section class="preview-intro" aria-labelledby="preview-title">
        <p class="preview-eyebrow">{{ t('preview.eyebrow') }}</p>
        <h1 id="preview-title">{{ t('preview.title') }}</h1>
        <p class="preview-description">{{ t('preview.description') }}</p>
        <nav class="preview-links" :aria-label="t('preview.categories')">
          <a href="#preview-actions">{{ t('preview.actions.title') }}</a>
          <a href="#preview-form">{{ t('preview.form.title') }}</a>
          <a href="#preview-feedback">{{ t('preview.messages.title') }}</a>
        </nav>
      </section>

      <div class="preview-grid">
        <AppPanel id="preview-actions" :title="t('preview.actions.title')" :description="t('preview.actions.description')">
          <template #actions><AppStatusTag tone="success">{{ t('preview.actions.interactive') }}</AppStatusTag></template>
          <div class="preview-stack">
            <div class="preview-button-row">
              <AppButton variant="primary" @click="buttonFeedback = 'primary'">{{ t('preview.actions.primary') }}</AppButton>
              <AppButton @click="buttonFeedback = 'secondary'">{{ t('preview.actions.secondary') }}</AppButton>
              <AppButton variant="ghost" @click="buttonFeedback = 'ghost'">{{ t('preview.actions.ghost') }}</AppButton>
              <AppButton variant="danger" @click="buttonFeedback = 'danger'">{{ t('preview.actions.danger') }}</AppButton>
            </div>
            <div class="preview-button-row">
              <AppButton size="small" @click="buttonFeedback = 'small'">{{ t('preview.actions.small') }}</AppButton>
              <AppButton @click="buttonFeedback = 'medium'">{{ t('preview.actions.medium') }}</AppButton>
              <AppButton size="large" @click="buttonFeedback = 'large'">{{ t('preview.actions.large') }}</AppButton>
            </div>
            <div class="preview-button-row">
              <AppButton variant="primary" :loading="loading" @click="simulateLoading">{{ t('preview.actions.loading') }}</AppButton>
              <AppButton disabled>{{ t('preview.actions.disabled') }}</AppButton>
            </div>
            <p class="preview-caption preview-event" role="status" aria-live="polite">{{ t(`preview.feedback.${buttonFeedback}`) }}</p>
            <div class="preview-divider" />
            <div class="preview-tag-row" :aria-label="t('preview.actions.statusLabel')">
              <AppStatusTag>{{ t('preview.status.neutral') }}</AppStatusTag>
              <AppStatusTag tone="info">{{ t('preview.status.info') }}</AppStatusTag>
              <AppStatusTag tone="success">{{ t('preview.status.success') }}</AppStatusTag>
              <AppStatusTag tone="warning">{{ t('preview.status.warning') }}</AppStatusTag>
              <AppStatusTag tone="error">{{ t('preview.status.error') }}</AppStatusTag>
            </div>
          </div>
        </AppPanel>

        <AppPanel id="preview-form" :title="t('preview.form.title')" :description="t('preview.form.description')">
          <form class="preview-stack" novalidate @submit.prevent="submitted = true">
            <AppTextField
              v-model="displayName"
              :label="t('preview.form.label')"
              name="display-name"
              autocomplete="nickname"
              :placeholder="t('preview.form.placeholder')"
              :hint="t('preview.form.hint')"
              :error="fieldError"
              required
              clearable
            />
            <AppTextField
              :model-value="t('preview.form.disabledValue')"
              :label="t('preview.form.disabledLabel')"
              :hint="t('preview.form.disabledHint')"
              disabled
            />
            <AppButton variant="primary" html-type="submit" block>{{ t('common.checkInput') }}</AppButton>
            <AppAlert v-if="formChecked" type="success" :title="t('preview.form.success')" :description="t('preview.form.successDescription')" />
          </form>
        </AppPanel>

        <AppPanel id="preview-feedback" :title="t('preview.messages.title')" :description="t('preview.messages.description')">
          <div class="preview-stack">
            <AppLoading :label="t('preview.messages.loading')" />
            <AppAlert type="info" :title="t('preview.messages.infoTitle')" :description="t('preview.messages.infoDescription')" />
            <AppAlert type="warning" :title="t('preview.messages.warningTitle')" :description="t('preview.messages.warningDescription')" />
            <AppAlert v-if="!recovered" type="error" :title="t('preview.messages.errorTitle')" :description="t('preview.messages.errorDescription')">
              <template #actions>
                <AppButton size="small" :loading="retrying" @click="simulateRetry">{{ t('common.retry') }}</AppButton>
              </template>
            </AppAlert>
            <AppAlert v-else type="success" :title="t('preview.messages.successTitle')" :description="t('preview.messages.successDescription')">
              <template #actions>
                <AppButton size="small" variant="ghost" @click="recovered = false">{{ t('preview.messages.restore') }}</AppButton>
              </template>
            </AppAlert>
          </div>
        </AppPanel>

        <AppPanel :title="t('preview.visual.title')" :description="t('preview.visual.description')">
          <div class="preview-swatches">
            <div v-for="swatch in swatches" :key="swatch.token" class="preview-swatch">
              <span class="preview-swatch__color" :style="{ background: `var(${swatch.token})` }" aria-hidden="true" />
              <span>{{ t(`preview.visual.${swatch.key}`) }}</span>
              <code>{{ rootStyles.getPropertyValue(swatch.token).trim().toUpperCase() }}</code>
            </div>
          </div>
          <template #footer><p class="preview-caption">{{ t('preview.visual.footer') }}</p></template>
        </AppPanel>
      </div>
    </main>

    <footer class="preview-footer">{{ t('preview.footer') }}</footer>
  </div>
</template>

<style scoped>
.component-preview { min-height: 100vh; padding: 0 var(--space-8); }
.preview-header,
.preview-main,
.preview-footer { width: min(100%, 1120px); margin-inline: auto; }
.preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 88px;
  gap: var(--space-4);
  border-bottom: 1px solid var(--color-border);
}
.preview-brand {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  color: var(--color-text);
  font-size: 17px;
  font-weight: 600;
  text-decoration: none;
}
.preview-header-actions { display: flex; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: var(--space-4); }
.preview-back { font-size: var(--font-size-small); text-decoration: none; white-space: nowrap; }
.preview-intro { padding-block: var(--space-12) var(--space-8); }
.preview-eyebrow { margin: 0 0 var(--space-3); color: var(--color-primary); font-size: var(--font-size-small); font-weight: 600; }
h1 { margin: 0; font-size: clamp(27px, 3.5vw, 40px); font-weight: 600; letter-spacing: -0.03em; line-height: 1.35; }
.preview-description { max-width: 640px; margin: var(--space-4) 0 0; color: var(--color-text-secondary); }
.preview-links { display: flex; flex-wrap: wrap; gap: var(--space-5); margin-top: var(--space-6); font-size: var(--font-size-small); }
.preview-links a { text-decoration: none; }
.preview-links a:hover,
.preview-back:hover { text-decoration: underline; }
.preview-grid { display: grid; grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr); gap: var(--space-6); align-items: start; }
.preview-stack { display: grid; gap: var(--space-5); }
.preview-button-row,
.preview-tag-row { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-3); }
.preview-caption { margin: 0; color: var(--color-text-secondary); font-size: var(--font-size-caption); line-height: 1.7; }
.preview-event { min-height: 2.5em; }
.preview-divider { height: 1px; background: var(--color-border); }
.preview-swatches { display: grid; gap: var(--space-5); }
.preview-swatch { display: grid; grid-template-columns: 32px 1fr auto; align-items: center; gap: var(--space-3); font-size: var(--font-size-small); }
.preview-swatch__color { width: 32px; height: 32px; border: 1px solid rgb(24 50 53 / 8%); border-radius: var(--radius-control); }
.preview-swatch code { color: var(--color-text-secondary); font-size: var(--font-size-caption); }
.preview-footer { padding-block: var(--space-8); color: var(--color-text-muted); font-size: var(--font-size-caption); }
.skip-link { position: fixed; top: var(--space-3); left: var(--space-3); z-index: 10; padding: var(--space-3); background: var(--color-surface); transform: translateY(-200%); }
.skip-link:focus { transform: translateY(0); }
@media (max-width: 840px) {
  .preview-grid { grid-template-columns: minmax(0, 1fr); }
}
@media (max-width: 480px) {
  .component-preview { padding-inline: var(--space-4); }
  .preview-header { min-height: 76px; padding-block: var(--space-3); flex-wrap: wrap; gap: var(--space-3); }
  .preview-header-actions { gap: var(--space-3); }
  .preview-brand { font-size: var(--font-size-body); gap: var(--space-2); }
  .preview-brand img { width: 30px; height: 30px; }
  .preview-intro { padding-block: var(--space-8) var(--space-6); }
  .preview-description { font-size: var(--font-size-small); }
  .preview-grid { gap: var(--space-4); }
  .preview-links { gap: var(--space-4); }
  .preview-button-row { gap: var(--space-2); }
}
</style>
