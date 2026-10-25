<script setup lang="ts">
import { computed } from 'vue'
import { NButton } from 'naive-ui'
import type { ButtonVariant, ComponentSize } from './types'

defineOptions({ inheritAttrs: false })

const props = withDefaults(
  defineProps<{
    variant?: ButtonVariant
    size?: ComponentSize
    loading?: boolean
    disabled?: boolean
    block?: boolean
    htmlType?: 'button' | 'submit' | 'reset'
  }>(),
  { variant: 'secondary', size: 'medium', htmlType: 'button' },
)
const emit = defineEmits<{ click: [event: MouseEvent] }>()
const buttonType = computed(() => {
  if (props.variant === 'primary') return 'primary'
  if (props.variant === 'danger') return 'error'
  return 'default'
})
</script>

<template>
  <NButton
    v-bind="$attrs"
    :type="buttonType"
    :size="size"
    :loading="loading"
    :disabled="disabled || loading"
    :block="block"
    :quaternary="variant === 'ghost'"
    :attr-type="htmlType"
    :aria-busy="loading ? 'true' : undefined"
    @click="emit('click', $event)"
  >
    <template v-if="$slots.icon" #icon><slot name="icon" /></template>
    <slot />
  </NButton>
</template>
