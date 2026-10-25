<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { NConfigProvider, NGlobalStyle, dateEnUS, dateZhCN, enUS, zhCN } from 'naive-ui'
import { createThemeOverrides } from '@/shared/styles/theme'

const { locale } = useI18n({ useScope: 'global' })
const themeOverrides = createThemeOverrides()
const uiLocale = computed(() => locale.value === 'en' ? enUS : zhCN)
const dateLocale = computed(() => locale.value === 'en' ? dateEnUS : dateZhCN)
</script>

<template>
  <NConfigProvider :theme-overrides="themeOverrides" :locale="uiLocale" :date-locale="dateLocale">
    <NGlobalStyle />
    <slot />
  </NConfigProvider>
</template>
