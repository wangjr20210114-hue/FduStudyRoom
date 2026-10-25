<script setup lang="ts">
import { NSpin } from 'naive-ui'
import type { ComponentSize } from './types'

withDefaults(defineProps<{ label: string; size?: ComponentSize }>(), { size: 'medium' })
</script>

<template>
  <div class="app-loading" role="status" aria-live="polite" aria-busy="true">
    <NSpin :size="size" aria-hidden="true" />
    <span>{{ label }}</span>
  </div>
</template>

<style scoped>
.app-loading {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding-block: var(--space-2);
  color: var(--color-text-secondary);
  font-size: var(--font-size-small);
}
</style>
