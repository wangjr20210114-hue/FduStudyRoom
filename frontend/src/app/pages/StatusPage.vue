<script setup lang="ts">
import { useRoute, RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthSession } from '@/modules/auth/model/auth-session'
import { hasPermission } from '../router/navigation'

const { t } = useI18n({ useScope: 'global' })
const route = useRoute()
const { user } = useAuthSession()
</script>

<template>
  <section>
    <h1 tabindex="-1">{{ t(route.meta.titleKey ?? 'shell.notFound') }}</h1>
    <p>{{ t(route.meta.messageKey ?? 'errors.notFound') }}</p>
    <RouterLink v-if="['forbidden', 'not-found'].includes(String(route.name)) && hasPermission(user, 'identity:read')" to="/workspace">{{ t('shell.returnWorkspace') }}</RouterLink>
  </section>
</template>

<style scoped>
h1 { margin: 0 0 var(--space-5); font-size: 24px; font-weight: 600; }
p { margin: 0 0 var(--space-4); color: var(--color-text-secondary); }
</style>
