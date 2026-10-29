<script setup lang="ts">
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { AppPanel } from '@/shared/components'

const route = useRoute()
const { t } = useI18n({ useScope: 'global' })
</script>

<template>
  <AppPanel>
    <h1 tabindex="-1">{{ t(route.meta.titleKey ?? 'shell.notFound') }}</h1>
    <p>{{ t('shell.pending') }}</p>
  </AppPanel>
</template>

<style scoped>
h1 { margin: 0 0 var(--space-4); font-size: 24px; font-weight: 600; }
p { margin: 0; color: var(--color-text-secondary); }
</style>
