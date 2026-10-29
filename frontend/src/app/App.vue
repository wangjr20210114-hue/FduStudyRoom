<script setup lang="ts">
import { defineAsyncComponent } from 'vue'
import AppThemeProvider from '@/shared/components/AppThemeProvider.vue'
import LoginPage from '@/modules/auth/pages/LoginPage.vue'

const ComponentPreview = import.meta.env.DEV
  ? defineAsyncComponent(() => import('./dev/ComponentPreview.vue'))
  : undefined
const showPreview =
  import.meta.env.DEV && ['/dev/components', '/dev/components/'].includes(window.location.pathname)
</script>

<template>
  <AppThemeProvider>
    <ComponentPreview v-if="showPreview && ComponentPreview" />
    <LoginPage v-else />
  </AppThemeProvider>
</template>
