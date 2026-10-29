<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { AppAlert, AppButton, AppStatusTag, LanguageSwitcher } from '@/shared/components'
import { useAuthSession } from '@/modules/auth/model/auth-session'
import { getLoginErrorMessage } from '@/modules/auth/model/error-message'
import { hasPermission, visibleNavigation } from '../router/navigation'

const { t } = useI18n({ useScope: 'global' })
const session = useAuthSession()
const { user, error, busy, phase } = session
const route = useRoute()
const router = useRouter()
const userMenu = ref<HTMLDetailsElement>()
const navigation = computed(() => visibleNavigation(user.value))
const errorMessage = computed(() => error.value ? getLoginErrorMessage(error.value) : '')

watch(() => route.fullPath, () => { if (userMenu.value) userMenu.value.open = false })
async function signOut() {
  await session.signOut()
  if (!user.value) await router.replace({ name: 'login' })
}
</script>

<template>
  <div v-if="user" class="app-shell">
    <a class="skip-link" href="#main-content">{{ t('shell.skip') }}</a>
    <header class="shell-header">
      <span class="shell-brand">{{ t('app.title') }}</span>
      <nav :aria-label="t('shell.navigationLabel')" class="shell-navigation">
        <RouterLink v-for="item in navigation" :key="item.path" :to="item.path">{{ t(item.label) }}</RouterLink>
      </nav>
      <div class="shell-tools">
        <AppStatusTag v-if="user.identitySource === 'DEVELOPMENT'" tone="warning">{{ t('auth.development.badge') }}</AppStatusTag>
        <LanguageSwitcher />
        <details ref="userMenu" class="user-menu" @keydown.esc="userMenu && (userMenu.open = false)">
          <summary>{{ user.displayName }}</summary>
          <div class="user-menu-content">
            <RouterLink v-if="hasPermission(user, 'identity:read')" to="/account">{{ t('shell.account') }}</RouterLink>
            <AppButton variant="ghost" :loading="phase === 'signing-out'" :disabled="busy" @click="signOut">{{ t('auth.actions.signOut') }}</AppButton>
          </div>
        </details>
      </div>
    </header>
    <main id="main-content" class="shell-main">
      <AppAlert v-if="error" type="error" :title="t('auth.errors.title')" :description="errorMessage">
        <template #actions><AppButton size="small" :disabled="busy" @click="signOut">{{ t('common.retry') }}</AppButton></template>
      </AppAlert>
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.app-shell { min-height: 100vh; }
.skip-link { position: fixed; top: var(--space-3); left: var(--space-3); z-index: 20; padding: var(--space-3); background: var(--color-surface); transform: translateY(-200%); }
.skip-link:focus { transform: none; }
.shell-header { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-5); padding: var(--space-4) var(--space-6); border-bottom: 1px solid var(--color-border); background: var(--color-surface); }
.shell-brand { font-weight: 600; }
.shell-navigation { display: flex; flex-wrap: wrap; gap: var(--space-4); }
.shell-navigation a { padding-block: var(--space-2); text-decoration: none; color: var(--color-text-secondary); }
.shell-navigation .router-link-active { color: var(--color-primary); text-decoration: underline; text-underline-offset: 6px; }
.shell-tools { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-4); margin-left: auto; }
.user-menu { position: relative; max-width: 220px; }
.user-menu summary { cursor: pointer; overflow-wrap: anywhere; }
.user-menu-content { position: absolute; z-index: 10; top: calc(100% + var(--space-3)); right: 0; display: grid; gap: var(--space-3); min-width: 150px; padding: var(--space-4); border: 1px solid var(--color-border); border-radius: var(--radius-control); background: var(--color-surface); }
.user-menu-content a { padding-inline: var(--space-2); text-decoration: none; }
.shell-main { display: grid; align-content: start; gap: var(--space-5); max-width: 1200px; padding: var(--space-8) var(--space-6); margin-inline: auto; }
@media (max-width: 600px) { .shell-header { padding: var(--space-4); gap: var(--space-3); } .shell-tools { width: 100%; margin-left: 0; gap: var(--space-3); } .user-menu { margin-left: auto; max-width: 140px; } .shell-main { padding: var(--space-6) var(--space-4); } }
</style>
