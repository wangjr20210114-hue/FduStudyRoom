<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppAlert, AppButton, AppLoading, AppPanel, AppStatusTag, LanguageSwitcher } from '@/shared/components'
import { useRoute, useRouter } from 'vue-router'
import { useAuthSession } from '../model/auth-session'
import { safeDestination } from '@/app/router/navigation'
import { getLoginErrorMessage } from '../model/error-message'
import { isDevelopmentAccountKey, type DevelopmentAccountKey } from '../types'

const { t } = useI18n({ useScope: 'global' })
const flow = useAuthSession()
const route = useRoute()
const router = useRouter()
const { config, user, error, phase, busy } = flow
const selectedAccount = ref<DevelopmentAccountKey>('student')
const redirecting = ref(false)
const errorMessage = computed(() => error.value ? getLoginErrorMessage(error.value) : '')
const development = computed(() => import.meta.env.DEV && config.value?.mode === 'development')
const officialAvailable = computed(() => config.value?.mode === 'fudan' && config.value.available)

watch(config, value => {
  if (value && !value.developmentAccounts.some(option => option.key === selectedAccount.value)) {
    selectedAccount.value = value.developmentAccounts[0]?.key ?? 'student'
  }
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
async function signIn() {
  await flow.signInDevelopment(selectedAccount.value)
  if (user.value) await router.replace(safeDestination(route.query.redirect))
}
async function recheck() {
  await flow.initialize()
  if (user.value) await router.replace(safeDestination(route.query.redirect))
}
</script>

<template>
  <div class="login-page">
    <header class="login-header"><span>{{ t('app.title') }}</span><LanguageSwitcher /></header>
    <main class="login-main">
      <AppPanel>
        <h1 tabindex="-1">{{ t('auth.card.title') }}</h1>
        <div class="login-stack">
          <AppAlert v-if="error" type="error" :title="t('auth.errors.title')" :description="errorMessage">
            <template #actions><AppButton size="small" :disabled="busy" @click="recheck">{{ t('auth.actions.recheck') }}</AppButton></template>
          </AppAlert>
          <AppLoading v-if="phase === 'loading' || (!config && !error)" :label="t('auth.loading')" />
          <template v-else-if="config && !user">
            <AppButton variant="primary" block :disabled="!officialAvailable || busy" :loading="redirecting" @click="startFudanLogin">{{ t('auth.official.action') }}</AppButton>
            <p v-if="!officialAvailable" class="login-note">{{ t('auth.official.unavailable') }}</p>
            <form v-if="development" class="login-development" @submit.prevent="signIn">
              <AppStatusTag tone="warning">{{ t('auth.development.badge') }}</AppStatusTag>
              <label for="development-account">{{ t('auth.development.label') }}</label>
              <select id="development-account" :value="selectedAccount" :disabled="busy" @change="chooseAccount">
                <option v-for="account in config.developmentAccounts" :key="account.key" :value="account.key">{{ t(`auth.development.accounts.${account.key}`) }}</option>
              </select>
              <AppButton block html-type="submit" :loading="phase === 'signing-in'" :disabled="busy || !config.available">{{ phase === 'signing-in' ? t('auth.actions.signingIn') : t('auth.development.action') }}</AppButton>
            </form>
          </template>
        </div>
      </AppPanel>
    </main>
  </div>
</template>

<style scoped>
.login-page { min-height: 100vh; padding: var(--space-5); }
.login-header { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: var(--space-4); max-width: 1000px; margin-inline: auto; }
.login-header > span { font-weight: 600; }
.login-main { width: min(100%, 380px); margin: 12vh auto var(--space-8); }
h1 { margin: 0 0 var(--space-5); font-size: 24px; font-weight: 600; }
.login-stack, .login-development { display: grid; gap: var(--space-4); }
.login-stack > :deep(.n-tag), .login-development > :deep(.n-tag) { justify-self: start; }
.login-note { margin: 0; color: var(--color-text-secondary); font-size: var(--font-size-small); }
.login-development { padding-top: var(--space-5); border-top: 1px solid var(--color-border); }
.login-development label { font-size: var(--font-size-small); }
.login-development select { width: 100%; min-width: 0; height: var(--control-height-medium); padding-inline: var(--space-3); border: 1px solid var(--color-border); border-radius: var(--radius-control); background: var(--color-surface); color: var(--color-text); }
@media (max-width: 480px) { .login-page { padding: var(--space-4); } .login-main { margin-top: var(--space-10); } }
</style>
