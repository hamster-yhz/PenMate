<template>
  <a-modal
    :open="visible"
    title="新建作品"
    :footer="null"
    :mask-closable="!saving"
    :closable="!saving"
    data-testid="book-editor-modal"
    @cancel="close"
  >
    <form class="book-form" @submit.prevent="$emit('submit')">
      <label class="form-field">
        <span>作品名 <b aria-hidden="true">*</b></span>
        <input v-model="form.title" type="text" maxlength="200" required placeholder="输入作品名" />
      </label>

      <label class="form-field">
        <span>简介</span>
        <textarea v-model="form.description" rows="4" maxlength="2000" placeholder="简要记录故事方向"></textarea>
      </label>

      <label class="form-field">
        <span>类型</span>
        <select v-model="form.genre">
          <option v-for="genre in genres" :key="genre" :value="genre">{{ genre }}</option>
        </select>
      </label>

      <label class="form-field">
        <span>标签</span>
        <input v-model="form.tagsStr" type="text" placeholder="用逗号分隔，最多 10 个" />
      </label>

      <p v-if="error" class="form-error" role="alert">{{ error }}</p>

      <div class="form-actions">
        <button type="button" class="secondary-button" :disabled="saving" @click="close">取消</button>
        <button type="submit" class="primary-button" :disabled="!canSubmit">
          <LoadingOutlined v-if="saving" spin />
          <span>{{ saving ? '正在创建' : '创建并开始写作' }}</span>
        </button>
      </div>
    </form>
  </a-modal>
</template>

<script setup lang="ts">
import { Modal as AModal } from 'ant-design-vue'
import { LoadingOutlined } from '@ant-design/icons-vue'
import type { BookFormState } from '@/composables/bookshelf/useBookshelf'

const props = defineProps<{
  visible: boolean
  form: BookFormState
  genres: string[]
  canSubmit: boolean
  saving: boolean
  error?: string
}>()

const emit = defineEmits<{
  'update:visible': [boolean]
  submit: []
}>()

const close = () => {
  if (!props.saving) emit('update:visible', false)
}
</script>

<style scoped>
.book-form {
  display: grid;
  gap: 16px;
  padding-top: 8px;
}

.form-field {
  display: grid;
  gap: 7px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.form-field b {
  color: var(--danger);
}

.form-field input,
.form-field textarea,
.form-field select {
  width: 100%;
  padding: 9px 11px;
  color: var(--text-primary);
  background: var(--bg-surface);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-md);
  outline: 0;
}

.form-field input:focus,
.form-field textarea:focus,
.form-field select:focus {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--focus-ring);
}

.form-field textarea {
  resize: vertical;
}

.form-error {
  padding: 10px 12px;
  color: var(--danger);
  background: var(--danger-soft);
  border-radius: var(--radius-md);
  font-size: 13px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 9px;
  margin-top: 4px;
}

.secondary-button,
.primary-button {
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

.primary-button {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--text-inverse);
  font-weight: 650;
  background: var(--accent);
  border: 1px solid var(--accent);
}

.primary-button:disabled,
.secondary-button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}
</style>
