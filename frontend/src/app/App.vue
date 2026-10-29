<script setup lang="ts">
import { onBeforeUnmount, onMounted, watch } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import AppThemeProvider from '@/shared/components/AppThemeProvider.vue'
import AppLoading from '@/shared/components/AppLoading.vue'
import { useI18n } from 'vue-i18n'
import { useAuthSession } from '@/modules/auth/model/auth-session'
import { canAccessPage, safeDestination } from './router/navigation'

const { t, locale } = useI18n({ useScope: 'global' })
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
  else if (!canAccessPage(user.value, route.meta)) await router.replace({ name: 'forbidden' })
}
function onVisibilityChange() { if (document.visibilityState === 'visible') void verifyActiveSession() }
function onPageShow(event: PageTransitionEvent) { if (event.persisted) void verifyActiveSession() }
watch([() => route.fullPath, locale], () => {
  if (typeof document === 'undefined') return
  document.title = route.meta.titleKey ? t(route.meta.titleKey) + ' · ' + t('app.title') : t('app.title')
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
    <div v-if="!user && (phase === 'loading' || route.meta.requiresAuth)" class="session-loading"><AppLoading :label="t('auth.loading')" /></div>
    <RouterView v-else />
  </AppThemeProvider>
</template>

<style scoped>
.session-loading { display: grid; place-items: center; min-height: 100vh; padding: var(--space-6); }
</style>
