<script setup lang="ts">
import { useId } from 'vue'

defineProps<{ title?: string; description?: string }>()
const headingId = `panel-${useId()}`
</script>

<template>
  <section class="app-panel" :aria-labelledby="title ? headingId : undefined">
    <header v-if="title || description || $slots.actions" class="app-panel__header">
      <div class="app-panel__heading">
        <h2 v-if="title" :id="headingId">{{ title }}</h2>
        <p v-if="description">{{ description }}</p>
      </div>
      <div v-if="$slots.actions" class="app-panel__actions"><slot name="actions" /></div>
    </header>
    <div class="app-panel__body"><slot /></div>
    <footer v-if="$slots.footer" class="app-panel__footer"><slot name="footer" /></footer>
  </section>
</template>

<style scoped>
.app-panel {
  min-width: 0;
  overflow-wrap: anywhere;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-panel);
  background: var(--color-surface);
  box-shadow: var(--shadow-panel);
}
.app-panel__header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-6) var(--space-6) 0;
}
.app-panel__heading { min-width: 0; }
.app-panel__heading h2 {
  margin: 0;
  font-size: var(--font-size-title);
  font-weight: 600;
  line-height: 1.4;
}
.app-panel__heading p {
  margin: var(--space-2) 0 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-small);
}
.app-panel__actions { flex-shrink: 0; }
.app-panel__body { padding: var(--space-6); }
.app-panel__footer {
  padding: var(--space-4) var(--space-6);
  border-top: 1px solid var(--color-border);
}
@media (max-width: 480px) {
  .app-panel__header { padding: var(--space-5) var(--space-5) 0; }
  .app-panel__body { padding: var(--space-5); }
  .app-panel__footer { padding: var(--space-4) var(--space-5); }
}
</style>
