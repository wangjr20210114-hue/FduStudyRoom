<script setup lang="ts">
import { NAlert } from 'naive-ui'
import type { FeedbackType } from './types'

withDefaults(defineProps<{ title: string; description?: string; type?: FeedbackType }>(), {
  type: 'info',
})
</script>

<template>
  <NAlert
    :title="title"
    :type="type"
    :bordered="false"
    :role="type === 'error' ? 'alert' : 'status'"
    :aria-live="type === 'error' ? 'assertive' : 'polite'"
  >
    <p v-if="description" class="app-alert__description">{{ description }}</p>
    <slot />
    <div v-if="$slots.actions" class="app-alert__actions"><slot name="actions" /></div>
  </NAlert>
</template>

<style scoped>
.app-alert__description { margin: 0; line-height: var(--line-height-body); }
.app-alert__actions { display: flex; flex-wrap: wrap; gap: var(--space-2); margin-top: var(--space-3); }
</style>
