<script setup lang="ts">
import { computed, useId } from 'vue'
import { NInput } from 'naive-ui'

defineOptions({ inheritAttrs: false })

const props = defineProps<{
  modelValue: string
  type?: 'text' | 'password'
  minLength?: number
  maxLength?: number
  pattern?: string
  label: string
  id?: string
  name?: string
  placeholder?: string
  autocomplete?: string
  hint?: string
  error?: string
  required?: boolean
  disabled?: boolean
  clearable?: boolean
}>()
const emit = defineEmits<{
  'update:modelValue': [value: string]
  blur: [event: FocusEvent]
}>()
const generatedId = `field-${useId()}`
const inputId = computed(() => props.id ?? generatedId)
const descriptionId = computed(() =>
  props.error ? `${inputId.value}-error` : props.hint ? `${inputId.value}-hint` : undefined,
)
const inputProps = computed(() => ({
  id: inputId.value,
  name: props.name,
  autocomplete: props.autocomplete,
  required: props.required,
  pattern: props.pattern,
  'aria-invalid': props.error ? ('true' as const) : undefined,
  'aria-describedby': descriptionId.value,
}))
</script>

<template>
  <div v-bind="$attrs" class="app-text-field">
    <label class="app-text-field__label" :for="inputId">
      {{ label }}<span v-if="required" class="app-text-field__required" aria-hidden="true"> *</span>
    </label>
    <NInput
      :type="type ?? 'text'"
      :value="modelValue"
      :input-props="inputProps"
      :minlength="minLength"
      :maxlength="maxLength"
      :placeholder="placeholder"
      :disabled="disabled"
      :clearable="clearable"
      :status="error ? 'error' : undefined"
      @update:value="emit('update:modelValue', $event)"
      @blur="emit('blur', $event)"
    />
    <p v-if="error" :id="descriptionId" class="app-text-field__error" role="alert">{{ error }}</p>
    <p v-else-if="hint" :id="descriptionId" class="app-text-field__hint">{{ hint }}</p>
  </div>
</template>

<style scoped>
.app-text-field {
  display: grid;
  min-width: 0;
  gap: var(--space-2);
}
.app-text-field__label {
  width: fit-content;
  color: var(--color-text);
  font-size: var(--font-size-small);
  font-weight: 600;
}
.app-text-field__required,
.app-text-field__error { color: var(--color-error); }
.app-text-field__hint { color: var(--color-text-secondary); }
.app-text-field__hint,
.app-text-field__error {
  margin: 0;
  font-size: var(--font-size-caption);
  line-height: 1.6;
}
</style>
