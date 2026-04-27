<script setup lang="ts">
import type { BookFormState } from '@/composables/bookshelf/useBookshelf'

const props = defineProps<{
  visible: boolean
  editing: boolean
  form: BookFormState
  genres: string[]
  canSubmit: boolean
  saving: boolean
}>()

const emit = defineEmits<{
  'update:visible': [boolean]
  submit: []
}>()

const close = () => {
  if (props.saving) {
    return
  }
  emit('update:visible', false)
}
</script>

<template>
  <div v-if="visible" class="modal-overlay" data-testid="book-editor-modal" @click.self="close">
    <div class="modal-card glass-panel" role="dialog" aria-modal="true">
      <div class="modal-glow"></div>
      <h3 class="modal-title">{{ editing ? '编辑作品' : '创建新书' }}</h3>

      <div class="modal-form">
        <div class="form-row">
          <label>书名</label>
          <input v-model="props.form.title" type="text" class="f-input" placeholder="为你的作品取一个名字" />
        </div>
        <div class="form-row">
          <label>简介</label>
          <textarea
            v-model="props.form.description"
            class="f-input f-textarea"
            placeholder="简要描述你的故事..."
            rows="3"
          ></textarea>
        </div>
        <div class="form-row">
          <label>类型</label>
          <div class="genre-options">
            <button
              v-for="genre in genres"
              :key="genre"
              type="button"
              class="genre-btn"
              :class="{ active: props.form.genre === genre }"
              :disabled="saving"
              @click="props.form.genre = genre"
            >
              {{ genre }}
            </button>
          </div>
        </div>
        <div class="form-row">
          <label>标签（逗号分隔）</label>
          <input v-model="props.form.tagsStr" type="text" class="f-input" placeholder="修仙, 热血, 轻松" />
        </div>
      </div>

      <div class="modal-actions">
        <button type="button" class="btn-cancel" :disabled="saving" @click="close">取消</button>
        <button type="button" class="btn-confirm" :disabled="!canSubmit" @click="$emit('submit')">
          {{ saving ? '保存中...' : editing ? '保存' : '创建' }}
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
  position: relative;
  width: 520px;
  max-width: 92vw;
  padding: 28px 32px;
  background: rgba(17, 24, 39, 0.92);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(201, 169, 110, 0.2);
  border-radius: 16px;
  animation: fadeInUp 0.3s ease;
}

.modal-glow {
  position: absolute;
  top: 0;
  left: 15%;
  right: 15%;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--amber-gold), transparent);
  opacity: 0.4;
}

.modal-title {
  font-family: var(--font-heading);
  font-size: 1.2rem;
  color: var(--xuan-paper);
  letter-spacing: 0.15em;
  margin-bottom: 20px;
}

.modal-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-row {
  display: flex;
  flex-direction: column;
  gap: 6px;

  label {
    font-size: 0.82rem;
    color: var(--text-secondary);
    letter-spacing: 0.08em;
  }
}

.f-input {
  padding: 10px 14px;
  background: rgba(11, 17, 32, 0.6);
  border: 1px solid var(--border-subtle);
  border-radius: 8px;
  color: var(--text-primary);
  font-family: var(--font-body);
  font-size: 0.9rem;
  outline: none;
  transition: border-color 0.3s;

  &:focus {
    border-color: var(--border-gold);
  }

  &::placeholder {
    color: var(--text-muted);
  }
}

.f-textarea {
  resize: vertical;
}

.genre-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.genre-btn {
  padding: 5px 14px;
  font-size: 0.78rem;
  color: var(--text-secondary);
  background: rgba(11, 17, 32, 0.5);
  border: 1px solid var(--border-subtle);
  border-radius: 14px;
  cursor: pointer;
  transition: all 0.3s;

  &:hover {
    border-color: var(--border-gold);
    color: var(--amber-gold);
  }

  &.active {
    color: var(--amber-gold);
    background: rgba(201, 169, 110, 0.12);
    border-color: var(--border-gold);
  }
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

  &:hover {
    border-color: var(--border-gold);
    color: var(--text-secondary);
  }
}

.btn-confirm {
  color: var(--amber-gold);
  background: linear-gradient(135deg, rgba(201, 169, 110, 0.15), rgba(201, 169, 110, 0.05));
  border: 1px solid var(--border-gold);

  &:hover {
    box-shadow: var(--shadow-gold);
    color: var(--xuan-paper);
  }
}
</style>
