<script setup lang="ts">
import { NConfigProvider, NGlobalStyle, dateZhCN, zhCN } from 'naive-ui'
import { createThemeOverrides } from '@/shared/styles/theme'

const themeOverrides = createThemeOverrides()
</script>

<template>
  <NConfigProvider :theme-overrides="themeOverrides" :locale="zhCN" :date-locale="dateZhCN">
    <NGlobalStyle />
    <slot />
  </NConfigProvider>
</template>
