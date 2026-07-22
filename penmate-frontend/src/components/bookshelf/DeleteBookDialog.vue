<template>
  <a-modal
    :open="visible"
    title="移入回收站"
    :footer="null"
    :closable="!deleting"
    :mask-closable="!deleting"
    data-testid="delete-book-dialog"
    @cancel="close"
  >
    <p class="delete-message">“{{ book?.title }}”将移入回收站，并在 30 天后永久删除。</p>
    <p v-if="error" class="delete-error" role="alert">{{ error }}</p>
    <div class="dialog-actions">
      <button type="button" class="secondary-button" :disabled="deleting" @click="close">取消</button>
      <button type="button" class="danger-button" :disabled="deleting" @click="$emit('confirm')">
        <LoadingOutlined v-if="deleting" spin />
        <span>{{ deleting ? '正在处理' : '移入回收站' }}</span>
      </button>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { Modal as AModal } from 'ant-design-vue'
import { LoadingOutlined } from '@ant-design/icons-vue'
import type { BookshelfBook } from '@/composables/bookshelf/useBookshelf'

const props = defineProps<{
  visible: boolean
  deleting: boolean
  book: BookshelfBook | null
  error?: string
}>()

const emit = defineEmits<{ 'update:visible': [boolean]; confirm: [] }>()
const close = () => {
  if (!props.deleting) emit('update:visible', false)
}
</script>

<style scoped>
.delete-message {
  color: var(--text-secondary);
  line-height: 1.65;
}

.delete-error {
  margin-top: 12px;
  padding: 9px 11px;
  color: var(--danger);
  background: var(--danger-soft);
  border-radius: var(--radius-md);
  font-size: 13px;
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 9px;
  margin-top: 22px;
}

.secondary-button,
.danger-button {
  min-height: 36px;
  padding: 0 14px;
  border-radius: var(--radius-md);
  cursor: pointer;
}

.secondary-button {
  color: var(--text-primary);
  background: var(--bg-surface);
  border: 1px solid var(--border-strong);
}

.danger-button {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: #ffffff;
  background: var(--danger);
  border: 1px solid var(--danger);
}
</style>
