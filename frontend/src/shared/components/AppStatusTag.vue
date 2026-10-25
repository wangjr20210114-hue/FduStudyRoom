<script setup lang="ts">
import { NTag } from 'naive-ui'
import type { StatusTone } from './types'

withDefaults(defineProps<{ tone?: StatusTone }>(), { tone: 'neutral' })
</script>

<template>
  <NTag :type="tone === 'neutral' ? 'default' : tone" :bordered="false" round size="small">
    <span class="app-status-tag"><span class="app-status-tag__dot" aria-hidden="true" /><slot /></span>
  </NTag>
</template>

<style scoped>
.app-status-tag { display: inline-flex; align-items: center; gap: 6px; }
.app-status-tag__dot { width: 5px; height: 5px; border-radius: var(--radius-pill); background: currentColor; }
</style>
