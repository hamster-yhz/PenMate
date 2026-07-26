<template>
  <ModelPicker
    :model-value="modelValue"
    :label="label"
    :description="description"
    :options="pickerOptions"
    @update:model-value="emit('update:modelValue', $event || '')"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import ModelPicker, { type ModelPickerOption } from '@/components/model/ModelPicker.vue'
import type { ModelOption } from '@/features/project-settings/useProjectSettings'

const props = defineProps<{
  modelValue: string
  label: string
  description?: string
  options: ModelOption[]
}>()

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const pickerOptions = computed<ModelPickerOption[]>(() =>
  props.options.map((option) => ({
    id: option.id,
    displayName: option.label,
    modelName: option.modelName,
    providerName: option.providerName,
    type: option.type,
    official: option.scope === 'SYSTEM',
    usable: option.usable,
    unavailableReason: option.unavailableReason,
  })),
)
</script>
