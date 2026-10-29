<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthSession } from '@/modules/auth/model/auth-session'
import { visibleNavigation } from '../router/navigation'

const { t } = useI18n({ useScope: 'global' })
const { user } = useAuthSession()
const entries = computed(() => visibleNavigation(user.value).filter(item => item.path !== '/workspace'))
</script>

<template>
  <section>
    <h1 tabindex="-1">{{ t('shell.navigation.workspace') }}</h1>
    <div class="workspace-entries">
      <RouterLink v-for="item in entries" :key="item.path" :to="item.path">{{ t(item.label) }}</RouterLink>
    </div>
  </section>
</template>

<style scoped>
h1 { margin: 0 0 var(--space-5); font-size: 24px; font-weight: 600; }
.workspace-entries { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 200px), 1fr)); gap: var(--space-4); }
.workspace-entries a { display: flex; align-items: center; min-height: 84px; padding: var(--space-5); border: 1px solid var(--color-border); border-radius: var(--radius-panel); background: var(--color-surface); color: var(--color-text); text-decoration: none; overflow-wrap: anywhere; }
.workspace-entries a:hover, .workspace-entries a:focus-visible { color: var(--color-primary); border-color: var(--color-primary); }
</style>
