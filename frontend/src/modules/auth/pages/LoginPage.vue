<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { AppAlert, AppButton, AppLoading, AppPanel, AppTextField, LanguageSwitcher } from '@/shared/components'
import { useAuthSession } from '../model/auth-session'
import { safeDestination } from '@/app/router/navigation'
import { getLoginErrorMessage } from '../model/error-message'

const { t } = useI18n({ useScope: 'global' })
const flow = useAuthSession()
const route = useRoute()
const router = useRouter()
const { config, user, error, phase, busy } = flow
const registration = computed(() => route.name === 'register')
const loginName = ref('')
const password = ref('')
const confirmation = ref('')
const mismatch = ref(false)
const errorMessage = computed(() => mismatch.value ? t('auth.errors.mismatch') : error.value ? getLoginErrorMessage(error.value) : '')

watch(() => route.name, () => {
  password.value = ''; confirmation.value = ''; mismatch.value = false; flow.clearError()
})
async function submit() {
  mismatch.value = registration.value && password.value !== confirmation.value
  if (mismatch.value) return
  const credentials = { loginName: loginName.value, password: password.value }
  try {
    if (registration.value) await flow.register(credentials)
    else await flow.signIn(credentials)
  } finally { password.value = ''; confirmation.value = '' }
  if (user.value) await router.replace(safeDestination(route.query.redirect))
}
async function recheck() {
  mismatch.value = false
  await flow.initialize()
  if (user.value) await router.replace(safeDestination(route.query.redirect))
}
</script>

<template>
  <div class="login-page">
    <header class="login-header"><span>{{ t('app.title') }}</span><LanguageSwitcher /></header>
    <main class="login-main">
      <AppPanel>
        <h1 tabindex="-1">{{ registration ? t('auth.register') : t('auth.login') }}</h1>
        <div class="login-stack">
          <AppAlert v-if="errorMessage" type="error" :title="t('auth.errors.title')" :description="errorMessage">
            <template v-if="error" #actions><AppButton size="small" :disabled="busy" @click="recheck">{{ t('common.retry') }}</AppButton></template>
          </AppAlert>
          <AppLoading v-if="phase === 'loading' || (!config && !error)" :label="t('auth.loading')" />
          <form v-else-if="config && !user" class="login-form" @submit.prevent="submit">
            <AppTextField id="login-name" v-model="loginName" name="username" :label="t('auth.fields.account')" autocomplete="username" :hint="registration ? t('auth.fields.accountHint') : undefined" :min-length="3" :max-length="32" pattern="[A-Za-z0-9][A-Za-z0-9_.\-]{2,31}" required :disabled="busy" />
            <AppTextField id="login-password" v-model="password" name="password" type="password" :label="t('auth.fields.password')" :autocomplete="registration ? 'new-password' : 'current-password'" :hint="registration ? t('auth.fields.passwordHint') : undefined" :min-length="8" :max-length="64" required :disabled="busy" />
            <AppTextField v-if="registration" id="login-confirmation" v-model="confirmation" name="confirmation" type="password" :label="t('auth.fields.confirmation')" autocomplete="new-password" :min-length="8" :max-length="64" required :disabled="busy" />
            <AppButton variant="primary" block html-type="submit" :loading="phase === 'signing-in' || phase === 'registering'" :disabled="busy || !config.available || (registration && !config.registrationOpen)">{{ registration ? t('auth.register') : t('auth.login') }}</AppButton>
            <RouterLink v-if="!busy && (!registration ? config.registrationOpen : true)" :to="{ name: registration ? 'login' : 'register', query: route.query }">{{ registration ? t('auth.toLogin') : t('auth.toRegister') }}</RouterLink>
          </form>
        </div>
      </AppPanel>
    </main>
  </div>
</template>

<style scoped>
.login-page { min-height: 100vh; padding: var(--space-5); }
.login-header { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: var(--space-4); max-width: 1000px; margin-inline: auto; }
.login-header > span { font-weight: 600; }
.login-main { width: min(100%, 380px); margin: 10vh auto var(--space-8); }
h1 { margin: 0 0 var(--space-5); font-size: 24px; font-weight: 600; }
.login-stack, .login-form { display: grid; gap: var(--space-4); }
.login-form > a { justify-self: center; font-size: var(--font-size-small); }
@media (max-width: 480px) { .login-page { padding: var(--space-4); } .login-main { margin-top: var(--space-8); } }
</style>
