<template>
  <label class="field">
    <span>{{ label }}</span>
    <select :value="modelValue" @change="updateValue">
      <option value="">{{ inheritLabel || '继承账号默认' }}</option>
      <option v-for="option in options" :key="option.id" :value="option.id">
        {{ option.label }} · {{ option.modelName }}
      </option>
    </select>
  </label>
</template>

<script setup lang="ts">
import type { ModelOption } from '@/features/project-settings/useProjectSettings'

defineProps<{
  modelValue: string
  label: string
  options: ModelOption[]
  inheritLabel?: string
}>()

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const updateValue = (event: Event) => emit('update:modelValue', (event.target as HTMLSelectElement).value)
</script>
