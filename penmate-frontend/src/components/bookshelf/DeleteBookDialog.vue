<script setup lang="ts">
import type { BookshelfBook } from '@/composables/bookshelf/useBookshelf'
import { useEscapeKey } from '@/composables/useEscapeKey'

const props = defineProps<{
  visible: boolean
  deleting: boolean
  book: BookshelfBook | null
}>()

const emit = defineEmits<{
  'update:visible': [boolean]
  confirm: []
}>()

const close = () => {
  if (props.deleting) {
    return
  }
  emit('update:visible', false)
}

const confirm = () => {
  if (props.deleting) {
    return
  }
  emit('confirm')
}

useEscapeKey(() => props.visible, close)
</script>

<template>
  <div v-if="visible" class="modal-overlay" data-testid="delete-book-dialog">
    <div
      class="modal-card glass-panel small"
      role="dialog"
      aria-modal="true"
      aria-labelledby="delete-book-title"
      tabindex="-1"
    >
      <h3 id="delete-book-title" class="modal-title">确认删除</h3>
      <p class="delete-msg">确定要删除「{{ book?.title }}」吗？此操作不可撤销。</p>
      <div class="modal-actions">
        <button type="button" class="btn-cancel" :disabled="deleting" @click="close">取消</button>
        <button type="button" class="btn-confirm danger" :disabled="deleting" @click="confirm">
          {{ deleting ? '删除中...' : '删除' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped lang="less">
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(6px);
}

.modal-card {
  width: 380px;
  max-width: 92vw;
  padding: 28px 32px;
  background: rgba(17, 24, 39, 0.92);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(201, 169, 110, 0.2);
  border-radius: 16px;
}

.modal-title {
  font-family: var(--font-heading);
  font-size: 1.2rem;
  color: var(--xuan-paper);
  letter-spacing: 0.15em;
  margin-bottom: 20px;
}

.delete-msg {
  font-size: 0.9rem;
  color: var(--text-secondary);
  line-height: 1.6;
  margin-bottom: 8px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
}

.btn-cancel,
.btn-confirm {
  padding: 8px 22px;
  font-size: 0.88rem;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.3s;
  letter-spacing: 0.1em;
}

.btn-cancel {
  background: none;
  border: 1px solid var(--border-subtle);
  color: var(--text-muted);
}

.btn-confirm {
  color: #e8a87c;
  background: rgba(192, 60, 45, 0.15);
  border: 1px solid rgba(192, 60, 45, 0.4);

  &:hover {
    background: rgba(192, 60, 45, 0.25);
  }
}
</style>
