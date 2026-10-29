<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppAlert, AppButton, AppLoading, AppPanel, AppStatusTag, LanguageSwitcher } from '@/shared/components'
import { createLoginFlow } from '../model/login-flow'
import { getLoginErrorMessage } from '../model/error-message'
import { isDevelopmentAccountKey, type DevelopmentAccountKey } from '../types'

const { t } = useI18n({ useScope: 'global' })
const flow = createLoginFlow()
const { config, user, error, phase, busy } = flow
const selectedAccount = ref<DevelopmentAccountKey>('student')
const redirecting = ref(false)
const signedInHeading = ref<HTMLHeadingElement>()
const errorMessage = computed(() => error.value ? getLoginErrorMessage(error.value) : '')
const development = computed(() => import.meta.env.DEV && config.value?.mode === 'development')
const officialAvailable = computed(() => config.value?.mode === 'fudan' && config.value.available)
const roleLabel = computed(() => {
  const role = user.value?.role
  if (role === 'USER') return t('auth.roles.student')
  if (role === 'SYSTEM_ADMIN') return t('auth.roles.admin')
  if (role === 'MAINTAINER') return t('auth.roles.maintainer')
  return role ?? ''
})

watch(config, value => {
  if (value && !value.developmentAccounts.some(option => option.key === selectedAccount.value)) {
    selectedAccount.value = value.developmentAccounts[0]?.key ?? 'student'
  }
})
watch(user, async value => {
  if (value) { await nextTick(); signedInHeading.value?.focus({ preventScroll: true }) }
})

function chooseAccount(event: Event) {
  const value = (event.target as HTMLSelectElement).value
  if (isDevelopmentAccountKey(value)) selectedAccount.value = value
}
function startFudanLogin() {
  if (!officialAvailable.value || busy.value || redirecting.value) return
  redirecting.value = true
  window.location.assign('/api/auth/fudan/login')
}

onMounted(() => { void flow.initialize() })
onBeforeUnmount(flow.dispose)
</script>

<template>
  <div class="login-page">
    <header class="login-header">
      <a class="login-brand" href="/" aria-label="FduStudyRoom">
        <img src="/favicon.svg" alt="" width="38" height="38" />
        <span>FduStudyRoom<small>{{ t('app.title') }}</small></span>
      </a>
      <LanguageSwitcher />
    </header>

    <main class="login-main">
      <section class="login-intro" aria-labelledby="login-title">
        <p class="login-eyebrow"><span aria-hidden="true" />{{ t('auth.hero.eyebrow') }}</p>
        <h1 id="login-title">{{ t('auth.hero.titleLine1') }}<br /><span>{{ t('auth.hero.titleLine2') }}</span></h1>
        <p class="login-description">{{ t('auth.hero.description') }}</p>
        <ul class="login-benefits">
          <li v-for="feature in ['spaces', 'schedule', 'records'] as const" :key="feature">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="m5 12 4 4L19 6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" /></svg>
            {{ t(`auth.hero.${feature}`) }}
          </li>
        </ul>
        <div class="study-illustration" aria-hidden="true">
          <div class="study-window"><span /><span /><span /></div>
          <div class="study-desk"><i v-for="seat in 6" :key="seat" :class="{ 'study-seat-active': seat === 3 }" /></div>
          <span class="study-book" /><span class="study-book study-book-second" />
        </div>
      </section>

      <section class="login-card-area" :aria-label="t('auth.card.label')">
        <AppPanel class="login-card" :title="user ? t('auth.session.title') : t('auth.card.title')">
          <div class="login-stack">
            <AppAlert v-if="error" type="error" :title="t('auth.errors.title')" :description="errorMessage">
              <template #actions><AppButton size="small" :disabled="busy" @click="flow.initialize">{{ t('auth.actions.recheck') }}</AppButton></template>
            </AppAlert>

            <div v-if="phase === 'loading' || (!config && !error)" class="login-loading">
              <AppLoading :label="t('auth.loading')" />
            </div>

            <template v-else-if="user">
              <AppStatusTag :tone="user.identitySource === 'DEVELOPMENT' ? 'warning' : 'success'">
                {{ user.identitySource === 'DEVELOPMENT' ? t('auth.development.badge') : t('auth.session.verified') }}
              </AppStatusTag>
              <div class="login-person">
                <span class="login-avatar" aria-hidden="true">{{ user.displayName.slice(0, 1) }}</span>
                <div>
                  <h3 ref="signedInHeading" tabindex="-1">{{ user.displayName }}</h3>
                  <p>{{ t('auth.session.welcome') }}</p>
                </div>
              </div>
              <dl class="login-identity">
                <div><dt>{{ t('auth.session.role') }}</dt><dd>{{ roleLabel }}</dd></div>
                <div><dt>{{ t('auth.session.identity') }}</dt><dd>{{ user.identityType === 'STUDENT' ? t('auth.session.student') : t('auth.session.staff') }}</dd></div>
                <div><dt>{{ t('auth.session.account') }}</dt><dd>{{ user.loginName }}</dd></div>
              </dl>
              <p v-if="user.identitySource === 'DEVELOPMENT'" class="login-caption">{{ t('auth.development.notice') }}</p>
              <AppButton block :loading="phase === 'signing-out'" :disabled="busy" @click="flow.signOut">{{ t('auth.actions.signOut') }}</AppButton>
            </template>

            <template v-else-if="config">
              <div>
                <p class="login-card-description">{{ t('auth.card.description') }}</p>
                <p class="login-caption">{{ t('auth.card.eligibility') }}</p>
              </div>

              <div class="login-official">
                <AppButton variant="primary" size="large" block :disabled="!officialAvailable || busy" :loading="redirecting" @click="startFudanLogin">
                  <template #icon><svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M4 20h16M6 20V9m12 11V9M4 9l8-5 8 5H4Zm6 4v4m4-4v4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" /></svg></template>
                  {{ t('auth.official.action') }}
                </AppButton>
                <p v-if="!officialAvailable" class="login-caption">{{ t('auth.official.unavailable') }}</p>
                <p v-else class="login-caption">{{ t('auth.official.password') }}</p>
              </div>

              <form v-if="development" class="login-development" @submit.prevent="flow.signInDevelopment(selectedAccount)">
                <div class="login-development-heading">
                  <AppStatusTag tone="warning">{{ t('auth.development.badge') }}</AppStatusTag>
                  <span>{{ t('auth.development.localOnly') }}</span>
                </div>
                <p class="login-caption">{{ t('auth.development.notice') }}</p>
                <label for="development-account">{{ t('auth.development.label') }}</label>
                <select id="development-account" :value="selectedAccount" :disabled="busy" @change="chooseAccount">
                  <option v-for="account in config.developmentAccounts" :key="account.key" :value="account.key">
                    {{ t(`auth.development.accounts.${account.key}`) }}
                  </option>
                </select>
                <AppButton block html-type="submit" :loading="phase === 'signing-in'" :disabled="busy || !config.available">
                  {{ phase === 'signing-in' ? t('auth.actions.signingIn') : t('auth.development.action') }}
                </AppButton>
              </form>

              <details class="login-help">
                <summary>{{ t('auth.help.title') }}</summary>
                <p>{{ t('auth.help.description') }}</p>
              </details>
            </template>
          </div>
        </AppPanel>
        <p class="login-privacy"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="m12 3 8 3v6c0 4-4 7-8 9-4-2-8-5-8-9V6l8-3Z" stroke="currentColor" stroke-width="1.6" /><path d="m8 12 3 3 5-6" stroke="currentColor" stroke-width="1.6" /></svg>{{ t('auth.privacy') }}</p>
      </section>
    </main>
    <footer class="login-footer"><span>FduStudyRoom</span><span>{{ t('auth.footer') }}</span></footer>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  padding-inline: var(--space-8);
  background: radial-gradient(ellipse at 8% 25%, var(--color-primary-soft), transparent 52%), radial-gradient(ellipse at 95% 100%, var(--color-surface-muted), transparent 46%), var(--color-canvas);
}
.login-header, .login-main, .login-footer { width: min(100%, 1100px); margin-inline: auto; }
.login-header { display: flex; align-items: center; justify-content: space-between; gap: var(--space-5); min-height: 100px; }
.login-brand { display: inline-flex; align-items: center; gap: var(--space-3); color: var(--color-text); font-size: 18px; font-weight: 650; text-decoration: none; }
.login-brand span { display: grid; gap: 1px; }
.login-brand small { color: var(--color-text-secondary); font-size: var(--font-size-caption); font-weight: 400; }
.login-main { flex: 1; display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 430px); align-items: center; gap: var(--space-12); padding-block: var(--space-12); }
.login-intro { position: relative; min-width: 0; padding: var(--space-5) 0; }
.login-eyebrow { display: flex; align-items: center; gap: var(--space-2); margin: 0 0 var(--space-5); color: var(--color-primary); font-size: var(--font-size-small); font-weight: 600; }
.login-eyebrow span { width: 6px; height: 6px; border-radius: var(--radius-pill); background: currentColor; }
h1 { margin: 0; color: var(--color-text); font-size: clamp(32px, 4vw, 48px); font-weight: 600; line-height: 1.38; letter-spacing: -0.03em; }
h1 span { color: var(--color-primary); }
.login-description { max-width: 420px; margin: var(--space-5) 0 0; color: var(--color-text-secondary); font-size: 16px; line-height: 1.9; }
.login-benefits { display: grid; gap: var(--space-3); padding: 0; margin: var(--space-8) 0 0; list-style: none; color: var(--color-text-secondary); font-size: var(--font-size-small); }
.login-benefits li { display: flex; gap: var(--space-3); align-items: center; }
.login-benefits svg { flex-shrink: 0; color: var(--color-primary); }
.study-illustration { position: relative; width: 270px; height: 130px; margin-top: var(--space-10); border-bottom: 1px solid var(--color-primary-border); }
.study-window { display: grid; grid-template-columns: repeat(3, 1fr); gap: 5px; width: 144px; height: 56px; margin-left: 38px; border: 5px solid var(--color-surface); border-radius: var(--radius-control); box-shadow: var(--shadow-panel); }
.study-window span { background: linear-gradient(160deg, var(--color-primary-soft), var(--color-border)); border-radius: 2px; }
.study-desk { position: absolute; left: 12px; bottom: 15px; display: flex; gap: 10px; padding: 8px; border: 1px solid var(--color-primary-border); border-radius: var(--radius-control); background: var(--color-surface); transform: perspective(200px) rotateX(10deg); }
.study-desk i { display: block; width: 22px; height: 20px; border-radius: 4px; background: var(--color-surface-muted); }
.study-desk .study-seat-active { background: var(--color-primary); }
.study-book { position: absolute; right: 17px; bottom: 0; width: 11px; height: 37px; border-radius: 2px 2px 0 0; background: var(--color-primary-border); transform: rotate(-12deg); transform-origin: bottom; }
.study-book-second { right: 0; height: 44px; background: var(--color-primary); transform: rotate(7deg); opacity: .7; }
.login-card-area { min-width: 0; }
.login-card { box-shadow: var(--shadow-overlay); }
.login-card :deep(.app-panel__header) { padding: var(--space-8) var(--space-8) 0; }
.login-card :deep(.app-panel__heading h2) { font-size: 24px; color: var(--color-text); }
.login-card :deep(.app-panel__body) { padding: var(--space-6) var(--space-8) var(--space-8); }
.login-stack { display: grid; gap: var(--space-6); }
.login-card-description { margin: 0 0 var(--space-2); color: var(--color-text); }
.login-caption { margin: 0; color: var(--color-text-secondary); font-size: var(--font-size-caption); line-height: 1.8; }
.login-official { display: grid; gap: var(--space-3); }
.login-development { display: grid; gap: var(--space-3); padding-top: var(--space-5); border-top: 1px solid var(--color-border); }
.login-development-heading { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); }
.login-development-heading > span { color: var(--color-text-muted); font-size: var(--font-size-caption); }
.login-development label { color: var(--color-text); font-size: var(--font-size-small); font-weight: 600; }
.login-development select { width: 100%; min-width: 0; height: var(--control-height-medium); padding-inline: var(--space-3); border: 1px solid var(--color-border); border-radius: var(--radius-control); background: var(--color-surface); color: var(--color-text); }
.login-development select:disabled { cursor: wait; }
.login-loading { display: flex; align-items: center; min-height: 200px; }
.login-help { padding-top: var(--space-4); border-top: 1px solid var(--color-border); color: var(--color-text-secondary); font-size: var(--font-size-caption); }
.login-help summary { width: fit-content; cursor: pointer; color: var(--color-primary); }
.login-help p { margin: var(--space-3) 0 0; line-height: 1.8; }
.login-privacy { display: flex; justify-content: center; align-items: flex-start; gap: var(--space-2); margin: var(--space-5) var(--space-2) 0; color: var(--color-text-muted); font-size: var(--font-size-caption); line-height: 1.7; }
.login-privacy svg { flex-shrink: 0; margin-top: 3px; }
.login-person { display: flex; gap: var(--space-4); align-items: center; min-width: 0; }
.login-avatar { display: grid; place-items: center; flex-shrink: 0; width: 48px; height: 48px; border-radius: var(--radius-panel); background: var(--color-primary-soft); color: var(--color-primary); font-size: 22px; }
.login-person h3 { margin: 0; color: var(--color-text); font-size: var(--font-size-title); overflow-wrap: anywhere; }
.login-person p { margin: var(--space-1) 0 0; color: var(--color-text-secondary); font-size: var(--font-size-small); }
.login-identity { display: grid; gap: var(--space-3); padding: var(--space-4); margin: 0; border-radius: var(--radius-control); background: var(--color-surface-muted); font-size: var(--font-size-small); }
.login-identity div { display: flex; justify-content: space-between; gap: var(--space-4); }
.login-identity dt { flex-shrink: 0; color: var(--color-text-secondary); }
.login-identity dd { margin: 0; color: var(--color-text); text-align: right; overflow-wrap: anywhere; }
.login-footer { display: flex; flex-wrap: wrap; justify-content: space-between; gap: var(--space-3); padding-block: var(--space-6); border-top: 1px solid var(--color-border); color: var(--color-text-muted); font-size: var(--font-size-caption); }
@media (max-width: 800px) {
  .login-main { max-width: 520px; grid-template-columns: minmax(0, 1fr); gap: var(--space-8); padding-block: var(--space-6) var(--space-10); }
  .login-intro { padding: 0; }
  .login-benefits { display: none; }
  .study-illustration { display: none; }
  .login-description { max-width: none; font-size: var(--font-size-small); line-height: 1.8; }
}
@media (max-width: 480px) {
  .login-page { padding-inline: var(--space-4); }
  .login-header { min-height: 88px; flex-wrap: wrap; gap: var(--space-3); padding-block: var(--space-4); }
  .login-brand { font-size: var(--font-size-body); gap: var(--space-2); }
  .login-brand img { width: 32px; height: 32px; }
  .login-header :deep(.language-switcher > span) { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }
  .login-header :deep(.language-switcher select) { min-width: 92px; }
  h1 { font-size: 32px; }
  .login-eyebrow { margin-bottom: var(--space-3); }
  .login-main { padding-top: var(--space-5); }
  .login-card :deep(.app-panel__header) { padding: var(--space-6) var(--space-5) 0; }
  .login-card :deep(.app-panel__body) { padding: var(--space-5); }
  .login-footer { justify-content: center; text-align: center; }
}
</style>
