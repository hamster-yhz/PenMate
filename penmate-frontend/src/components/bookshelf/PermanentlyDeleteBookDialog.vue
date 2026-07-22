<template>
  <a-modal
    :open="visible"
    title="永久删除作品"
    :footer="null"
    :closable="!deleting"
    :mask-closable="!deleting"
    @cancel="close"
  >
    <div class="danger-callout">
      <WarningOutlined />
      <div>
        <strong>此操作无法撤销</strong>
        <p>正文、目录、Story Bible、封面和关联 AI 数据都将被永久删除。</p>
      </div>
    </div>
    <label class="confirmation-field">
      <span>输入完整作品名 <strong>{{ book?.title }}</strong> 以确认</span>
      <input v-model="confirmationTitle" type="text" autocomplete="off" :disabled="deleting" />
    </label>
    <p v-if="error" class="dialog-error" role="alert">{{ error }}</p>
    <div class="dialog-actions">
      <button type="button" class="secondary-button" :disabled="deleting" @click="close">取消</button>
      <button type="button" class="danger-button" :disabled="!canDelete" @click="confirm">
        <LoadingOutlined v-if="deleting" spin />
        <DeleteOutlined v-else />
        <span>{{ deleting ? '正在永久删除' : '永久删除' }}</span>
      </button>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Modal as AModal } from 'ant-design-vue'
import { DeleteOutlined, LoadingOutlined, WarningOutlined } from '@ant-design/icons-vue'
import type { BookshelfBook } from '@/composables/bookshelf/useBookshelf'

const props = defineProps<{
  visible: boolean
  book: BookshelfBook | null
  deleting: boolean
  error?: string
}>()
const emit = defineEmits<{
  close: []
  confirm: [string]
}>()
const confirmationTitle = ref('')
const canDelete = computed(() =>
  !props.deleting && Boolean(props.book) && confirmationTitle.value === props.book?.title,
)

watch(() => [props.visible, props.book?.id], () => {
  confirmationTitle.value = ''
})
const close = () => { if (!props.deleting) emit('close') }
const confirm = () => { if (canDelete.value) emit('confirm', confirmationTitle.value) }
</script>

<style scoped>
.danger-callout { display: flex; gap: 10px; padding: 12px; color: var(--danger); background: var(--danger-soft); border: 1px solid var(--danger-border); border-radius: var(--radius-md); }
.danger-callout > :first-child { flex: 0 0 auto; margin-top: 2px; }
.danger-callout strong { font-size: 13px; }
.danger-callout p { margin: 4px 0 0; color: var(--text-secondary); font-size: 12px; line-height: 1.55; }
.confirmation-field { display: grid; gap: 7px; margin-top: 18px; color: var(--text-secondary); font-size: 12px; }
.confirmation-field strong { color: var(--text-primary); }
.confirmation-field input { height: 38px; padding: 0 10px; color: var(--text-primary); background: var(--bg-surface); border: 1px solid var(--border-strong); border-radius: var(--radius-md); outline: 0; }
.confirmation-field input:focus { border-color: var(--accent-border); box-shadow: 0 0 0 3px var(--focus-ring); }
.dialog-error { margin: 10px 0 0; color: var(--danger); font-size: 12px; }
.dialog-actions { display: flex; justify-content: flex-end; gap: 9px; margin-top: 22px; }
.dialog-actions button { display: inline-flex; min-height: 36px; align-items: center; gap: 6px; padding: 0 13px; border-radius: var(--radius-md); cursor: pointer; }
.dialog-actions button:disabled { cursor: not-allowed; opacity: .52; }
.secondary-button { color: var(--text-primary); background: var(--bg-surface); border: 1px solid var(--border-strong); }
.danger-button { color: #fff; background: var(--danger); border: 1px solid var(--danger); }
</style>
