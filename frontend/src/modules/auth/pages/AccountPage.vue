<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthSession } from '../model/auth-session'
import { getRoleLabel } from '../model/role-label'

const { t } = useI18n({ useScope: 'global' })
const { user } = useAuthSession()
const role = computed(() => user.value ? getRoleLabel(user.value.role) : '')
</script>

<template>
  <section v-if="user">
    <h1 tabindex="-1">{{ t('shell.account') }}</h1>
    <dl>
      <div><dt>{{ t('shell.name') }}</dt><dd>{{ user.displayName }}</dd></div>
      <div><dt>{{ t('auth.session.account') }}</dt><dd>{{ user.loginName }}</dd></div>
      <div><dt>{{ t('auth.session.role') }}</dt><dd>{{ role }}</dd></div>
      <div><dt>{{ t('auth.session.identity') }}</dt><dd>{{ user.identityType === 'STUDENT' ? t('auth.session.student') : t('auth.session.staff') }}</dd></div>
    </dl>
  </section>
</template>

<style scoped>
h1 { margin: 0 0 var(--space-5); font-size: 24px; font-weight: 600; }
dl { display: grid; gap: var(--space-4); max-width: 480px; margin: 0; }
dl > div { display: grid; grid-template-columns: 80px minmax(0, 1fr); gap: var(--space-4); }
dt { color: var(--color-text-secondary); }
dd { margin: 0; overflow-wrap: anywhere; }
</style>
