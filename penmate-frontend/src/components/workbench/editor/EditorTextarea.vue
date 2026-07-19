<script setup lang="ts">
import { ref } from 'vue'

withDefaults(
  defineProps<{
    modelValue: string
    placeholder?: string
  }>(),
  {
    modelValue: '',
    placeholder: '',
  },
)

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
    aria-label="章节正文编辑器"
    ref="textareaRef"
    data-testid="editor-textarea"
    class="main-editor workbench-editor-textarea"
    :value="modelValue"
    :placeholder="placeholder"
    @input="onInput"
    @keyup="emit('cursor-activity')"
    @click="emit('cursor-activity')"
    @keydown="onKeydown"
  ></textarea>
</template>

<style scoped lang="less">
.main-editor {
  width: 100%;
  height: 100%;
  min-height: 420px;
  padding: 24px 28px;
  border: none;
  outline: none;
  resize: none;
  background: linear-gradient(180deg, rgba(17, 24, 39, 0.3), rgba(11, 17, 32, 0.18)), rgba(11, 17, 32, 0.22);
  color: var(--text-primary);
  font-size: 1rem;
  line-height: 1.9;
  letter-spacing: 0.02em;
  caret-color: var(--amber-gold);
}

.workbench-editor-textarea {
  border-top: 1px solid rgba(201, 169, 110, 0.04);
}

.main-editor::placeholder {
  color: var(--text-muted);
}

.main-editor:focus {
  box-shadow: inset 0 0 0 1px rgba(201, 169, 110, 0.12);
}
</style>
