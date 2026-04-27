<script setup lang="ts">
import { ref } from 'vue'

withDefaults(defineProps<{
  modelValue: string
  placeholder?: string
}>(), {
  modelValue: '',
  placeholder: '',
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'input'): void
  (e: 'cursor-activity'): void
  (e: 'save'): void
  (e: 'undo'): void
  (e: 'redo'): void
  (e: 'wrap-selection', before: string, after: string): void
}>()

const textareaRef = ref<HTMLTextAreaElement | null>(null)

const onInput = (event: Event) => {
  emit('update:modelValue', (event.target as HTMLTextAreaElement).value)
  emit('input')
}

const onKeydown = (event: KeyboardEvent) => {
  if (!event.ctrlKey) return

  const key = event.key.toLowerCase()
  if (key === 's') {
    event.preventDefault()
    emit('save')
    return
  }
  if (key === 'z') {
    event.preventDefault()
    emit('undo')
    return
  }
  if (key === 'y') {
    event.preventDefault()
    emit('redo')
    return
  }
  if (key === 'b') {
    event.preventDefault()
    emit('wrap-selection', '**', '**')
    return
  }
  if (key === 'i') {
    event.preventDefault()
    emit('wrap-selection', '*', '*')
  }
}

defineExpose({
  textarea: textareaRef,
})
</script>

<template>
  <textarea
    ref="textareaRef"
    data-testid="editor-textarea"
    class="main-editor"
    :value="modelValue"
    :placeholder="placeholder"
    @input="onInput"
    @keyup="emit('cursor-activity')"
    @click="emit('cursor-activity')"
    @keydown="onKeydown"
  ></textarea>
</template>
