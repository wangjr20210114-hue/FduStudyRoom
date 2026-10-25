<script setup lang="ts">
import { defineAsyncComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import AppThemeProvider from '@/shared/components/AppThemeProvider.vue'
import LanguageSwitcher from '@/shared/components/LanguageSwitcher.vue'

const { t } = useI18n({ useScope: 'global' })
const ComponentPreview = import.meta.env.DEV
  ? defineAsyncComponent(() => import('./dev/ComponentPreview.vue'))
  : undefined
const showPreview =
  import.meta.env.DEV && ['/dev/components', '/dev/components/'].includes(window.location.pathname)
</script>

<template>
  <AppThemeProvider>
    <ComponentPreview v-if="showPreview && ComponentPreview" />
    <template v-else>
      <header class="app-header"><LanguageSwitcher /></header>
      <main class="welcome">
        <img class="welcome__brand" src="/favicon.svg" alt="" width="48" height="48" />
        <p class="welcome__eyebrow">{{ t('app.name') }}</p>
        <h1>{{ t('app.headingLine1') }}<br />{{ t('app.headingLine2') }}</h1>
        <p class="welcome__description">{{ t('app.description') }}</p>
      </main>
    </template>
  </AppThemeProvider>
</template>

<style scoped>
.app-header { display: flex; justify-content: flex-end; align-items: center; min-height: 72px; width: min(100%, 960px); margin-inline: auto; padding-inline: var(--space-8); }
.welcome {
  display: flex;
  min-height: calc(100vh - 72px);
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  width: min(100%, 960px);
  margin-inline: auto;
  padding: var(--space-12) var(--space-8);
}
.welcome__brand { margin-bottom: 28px; }
.welcome__eyebrow {
  margin: 0 0 var(--space-4);
  color: var(--color-primary);
  font-size: var(--font-size-body);
  font-weight: 600;
}
h1 {
  margin: 0;
  font-size: clamp(32px, 5.5vw, 56px);
  font-weight: 600;
  letter-spacing: -0.035em;
  line-height: 1.35;
}
.welcome__description {
  margin-top: var(--space-6);
  color: var(--color-text-secondary);
  font-size: 16px;
  line-height: 1.8;
}
</style>
