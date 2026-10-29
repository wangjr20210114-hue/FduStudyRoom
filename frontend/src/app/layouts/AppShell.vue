<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { AppAlert, AppButton, LanguageSwitcher } from '@/shared/components'
import { useAuthSession } from '@/modules/auth/model/auth-session'
import { getLoginErrorMessage } from '@/modules/auth/model/error-message'
import { getRoleLabel } from '@/modules/auth/model/role-label'
import { hasPermission, visibleNavigation } from '../router/navigation'

const { t } = useI18n({ useScope: 'global' })
const session = useAuthSession()
const { user, error, busy, phase } = session
const route = useRoute()
const router = useRouter()
const userMenu = ref<HTMLDetailsElement>()
const menuOpen = ref(false)
const navigation = computed(() => visibleNavigation(user.value))
const roleLabel = computed(() => user.value ? getRoleLabel(user.value.role) : '')
const errorMessage = computed(() => error.value ? getLoginErrorMessage(error.value) : '')

watch(() => route.fullPath, () => {
  menuOpen.value = false
  if (userMenu.value) userMenu.value.open = false
})
async function closeNavigation() {
  menuOpen.value = false
  await nextTick()
  document.getElementById('navigation-toggle')?.focus()
}
function focusPage(element: Element) {
  element.querySelector<HTMLElement>('h1')?.focus({ preventScroll: true })
}
async function signOut() {
  await session.signOut()
  if (!user.value) await router.replace({ name: 'login' })
}
</script>

<template>
  <div v-if="user" class="app-shell">
    <a class="skip-link" href="#main-content">{{ t('shell.skip') }}</a>
    <header class="shell-header">
      <div class="shell-identity">
        <RouterLink class="shell-brand" to="/workspace">{{ t('app.title') }}</RouterLink>
        <span class="shell-role">{{ roleLabel }}</span>
      </div>
      <AppButton id="navigation-toggle" class="navigation-toggle" size="small" aria-controls="main-navigation" :aria-expanded="menuOpen" @click="menuOpen = !menuOpen">{{ t('shell.menu') }}</AppButton>
      <div class="shell-tools">
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
    <div class="shell-body">
      <aside class="shell-sidebar" :class="{ 'is-open': menuOpen }" @keydown.esc="closeNavigation">
        <nav id="main-navigation" :aria-label="t('shell.navigationLabel')" class="shell-navigation">
          <RouterLink v-for="item in navigation" :key="item.path" :to="item.path">{{ t(item.label) }}</RouterLink>
        </nav>
      </aside>
      <main id="main-content" class="shell-main" :aria-busy="phase === 'loading'">
        <AppAlert v-if="error" type="error" :title="t('auth.errors.title')" :description="errorMessage">
          <template #actions><AppButton size="small" :disabled="busy" @click="signOut">{{ t('common.retry') }}</AppButton></template>
        </AppAlert>
        <div class="shell-pages">
          <RouterView v-slot="{ Component, route: pageRoute }">
            <Transition name="page" @after-enter="focusPage">
              <div v-if="Component" :key="pageRoute.path" class="shell-page">
                <component :is="Component" />
              </div>
            </Transition>
          </RouterView>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.app-shell { min-height: 100vh; }
.skip-link { position: fixed; top: var(--space-3); left: var(--space-3); z-index: 20; padding: var(--space-3); background: var(--color-surface); transform: translateY(-200%); }
.skip-link:focus { transform: none; }
.shell-header { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-5); min-height: 72px; padding: var(--space-4) var(--space-6); border-bottom: 1px solid var(--color-border); background: var(--color-surface); }
.shell-identity { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-3); }
.shell-brand { font-weight: 600; color: var(--color-text); text-decoration: none; }
.shell-role { font-size: var(--font-size-small); color: var(--color-text-secondary); }
.navigation-toggle { display: none; }
.shell-tools { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-4); margin-left: auto; }
.user-menu { position: relative; max-width: 220px; }
.user-menu summary { cursor: pointer; overflow-wrap: anywhere; }
.user-menu-content { position: absolute; z-index: 10; top: calc(100% + var(--space-3)); right: 0; display: grid; gap: var(--space-3); min-width: 150px; padding: var(--space-4); border: 1px solid var(--color-border); border-radius: var(--radius-control); background: var(--color-surface); }
.user-menu-content a { padding-inline: var(--space-2); text-decoration: none; }
.shell-body { display: grid; grid-template-columns: 224px minmax(0, 1fr); align-items: start; }
.shell-sidebar { min-height: calc(100vh - 72px); padding: var(--space-4); border-right: 1px solid var(--color-border); background: var(--color-surface); }
.shell-navigation { display: grid; gap: var(--space-1); }
.shell-navigation a { display: block; min-width: 0; min-height: 40px; padding: var(--space-2) var(--space-3); border-radius: var(--radius-control); text-decoration: none; color: var(--color-text-secondary); overflow-wrap: anywhere; transition: color 160ms ease, background-color 160ms ease; }
.shell-navigation a:hover { color: var(--color-primary); background: var(--color-surface-muted); }
.shell-navigation .router-link-active { color: var(--color-primary); background: var(--color-primary-soft); font-weight: 600; }
.shell-main { display: grid; align-content: start; gap: var(--space-5); width: 100%; min-width: 0; max-width: 1200px; padding: var(--space-8) var(--space-6); margin-inline: auto; }
.shell-pages { display: grid; min-width: 0; }
.shell-page { grid-area: 1 / 1; min-width: 0; align-self: start; }
.page-enter-active, .page-leave-active { transition: opacity 160ms ease, transform 160ms ease; }
.page-enter-from { opacity: 0; transform: translateY(8px); }
.page-leave-to { opacity: 0; transform: translateY(-4px); }
.page-leave-active { pointer-events: none; }
@media (prefers-reduced-motion: reduce) {
  .page-enter-from, .page-leave-to { transform: none; }
}
@media (max-width: 760px) {
  .shell-header { padding: var(--space-4); gap: var(--space-3); }
  .shell-identity { flex: 1; }
  .navigation-toggle { display: inline-flex; }
  .shell-tools { width: 100%; margin-left: 0; justify-content: space-between; gap: var(--space-3); }
  .user-menu { max-width: 140px; }
  .shell-body { grid-template-columns: minmax(0, 1fr); }
  .shell-sidebar { display: none; min-height: 0; border-right: 0; border-bottom: 1px solid var(--color-border); }
  .shell-sidebar.is-open { display: block; }
  .shell-navigation { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .shell-main { padding: var(--space-6) var(--space-4); }
}
</style>
