<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, watch } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import AppThemeProvider from '@/shared/components/AppThemeProvider.vue'
import AppLoading from '@/shared/components/AppLoading.vue'
import { useI18n } from 'vue-i18n'
import { useAuthSession } from '@/modules/auth/model/auth-session'
import { hasPermission, safeDestination } from './router/navigation'

const { t } = useI18n({ useScope: 'global' })
const session = useAuthSession()
const { phase, user, busy } = session
const route = useRoute()
const router = useRouter()

async function verifyActiveSession() {
  if (!route.meta.requiresAuth || busy.value) return
  const destination = route.fullPath
  await session.initialize()
  if (route.fullPath !== destination) return
  if (!user.value) await router.replace({ name: 'login', query: { redirect: safeDestination(destination) } })
  else if (route.meta.permission && !hasPermission(user.value, route.meta.permission)) await router.replace({ name: 'forbidden' })
}
function onVisibilityChange() { if (document.visibilityState === 'visible') void verifyActiveSession() }
function onPageShow(event: PageTransitionEvent) { if (event.persisted) void verifyActiveSession() }
watch(() => route.fullPath, async () => {
  await nextTick()
  document.querySelector<HTMLElement>('main h1')?.focus({ preventScroll: true })
})
onMounted(() => {
  document.addEventListener('visibilitychange', onVisibilityChange)
  window.addEventListener('pageshow', onPageShow)
})
onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', onVisibilityChange)
  window.removeEventListener('pageshow', onPageShow)
  session.dispose()
})
</script>

<template>
  <AppThemeProvider>
    <div v-if="phase === 'loading' || (route.meta.requiresAuth && !user)" class="session-loading"><AppLoading :label="t('auth.loading')" /></div>
    <RouterView v-else />
  </AppThemeProvider>
</template>

<style scoped>
.session-loading { display: grid; place-items: center; min-height: 100vh; padding: var(--space-6); }
</style>
