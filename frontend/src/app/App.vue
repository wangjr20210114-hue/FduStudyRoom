<script setup lang="ts">
import { defineAsyncComponent } from 'vue'
import AppThemeProvider from '@/shared/components/AppThemeProvider.vue'

const ComponentPreview = import.meta.env.DEV
  ? defineAsyncComponent(() => import('./dev/ComponentPreview.vue'))
  : undefined
const showPreview =
  import.meta.env.DEV && ['/dev/components', '/dev/components/'].includes(window.location.pathname)
</script>

<template>
  <AppThemeProvider>
    <ComponentPreview v-if="showPreview && ComponentPreview" />
    <main v-else class="welcome">
      <img class="welcome__brand" src="/favicon.svg" alt="" width="48" height="48" />
      <p class="welcome__eyebrow">校园自习室</p>
      <h1>为下一段专注，<br />找到一个好位置。</h1>
      <p class="welcome__description">查询校园自习空间，安排你的学习时段。</p>
    </main>
  </AppThemeProvider>
</template>

<style scoped>
.welcome {
  display: flex;
  min-height: 100vh;
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
