<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { setAppLocale } from '@/shared/i18n'
import { isAppLocale } from '@/shared/i18n/locale'

const { t, locale } = useI18n({ useScope: 'global' })

function changeLanguage(event: Event) {
  const value = (event.target as HTMLSelectElement).value
  if (isAppLocale(value)) setAppLocale(value)
}
</script>

<template>
  <label class="language-switcher">
    <span>{{ t('common.language') }}</span>
    <select :value="locale" @change="changeLanguage">
      <option value="zh-CN">简体中文</option>
      <option value="en">English</option>
    </select>
  </label>
</template>

<style scoped>
.language-switcher { display: inline-flex; align-items: center; gap: var(--space-2); color: var(--color-text-secondary); font-size: var(--font-size-small); }
.language-switcher select { min-width: 104px; min-height: var(--control-height-medium); padding: var(--space-2); border: 1px solid var(--color-border); border-radius: var(--radius-control); color: var(--color-text); background: var(--color-surface); cursor: pointer; }
</style>
