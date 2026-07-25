<template>
  <div class="approval-card" :class="{ resolved: card.resolved }">
    <div class="ac-glow"></div>
    <div class="ac-header">
      <span class="ac-badge">⚡ 设定审批</span>
      <span class="ac-time">{{ card.time }}</span>
    </div>
    <div class="ac-body">
      <p class="ac-message">{{ card.message }}</p>
      <div class="ac-preview" v-if="card.preview">
        <div class="preview-row" v-for="(val, key) in card.preview" :key="key">
          <span class="pk">{{ key }}</span>
          <span class="pv">{{ val }}</span>
        </div>
      </div>
    </div>
    <div class="ac-actions" v-if="!card.resolved">
      <button class="btn-approve" :disabled="busy" @click="$emit('approve', card.id)">
        {{ busy ? '处理中...' : '✅ 确认归档' }}
      </button>
      <button class="btn-reject" :disabled="busy" @click="$emit('reject', card.id)">
        {{ busy ? '处理中...' : '❌ 拒绝' }}
      </button>
    </div>
    <button
      v-if="isStoryBibleWrite"
      type="button"
      class="btn-open-bible"
      @click="emit('open-story-bible', targetNodeId)"
    >
      <BookOutlined /> 打开 Story Bible
    </button>
    <div class="ac-resolved" v-else>
      <span :class="card.resolvedAction">
        {{ card.resolvedAction === 'approved' ? '✅ 已归档至数据库' : '❌ 已拒绝' }}
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { BookOutlined } from '@ant-design/icons-vue'
import type { ApprovalCardData } from './approvalCard.types'

const props = withDefaults(defineProps<{ card: ApprovalCardData; busy?: boolean }>(), {
  busy: false,
})
const emit = defineEmits<{
  (event: 'approve', id: string): void
  (event: 'reject', id: string): void
  (event: 'open-story-bible', nodeId: string): void
}>()
const storyBibleWriteTools = new Set([
  'story_bible_node_write',
  'story_bible_relation_write',
  'story_bible_progression_write',
  'story_bible_structure_write',
])
const isStoryBibleWrite = computed(() => storyBibleWriteTools.has(props.card.toolCode || ''))
const targetNodeId = computed(() => String(
  props.card.preview?.nodeId
    || props.card.preview?.sourceNodeId
    || props.card.preview?.targetNodeId
    || '',
))
</script>

<style lang="less" scoped>
.approval-card {
  position: relative;
  padding: 14px;
  background: var(--warning-soft);
  border: 1px solid color-mix(in srgb, var(--warning) 28%, var(--border-subtle));
  border-radius: var(--radius-lg);
  transition: border-color 160ms ease, opacity 160ms ease;
  overflow: hidden;

  &.resolved {
    opacity: 0.7;
    border-color: var(--border-subtle);
  }
}

.ac-glow {
  position: absolute;
  top: 0;
  left: 10%;
  right: 10%;
  height: 1px;
  background: var(--warning);
  opacity: 0.24;
}

.ac-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.ac-badge {
  font-size: 0.75rem;
  padding: 2px 8px;
  background: var(--warning-soft);
  border: 1px solid color-mix(in srgb, var(--warning) 32%, var(--border-subtle));
  border-radius: var(--radius-sm);
  color: var(--warning);
  letter-spacing: 0;
}

.ac-time {
  font-size: 0.68rem;
  color: var(--text-muted);
}

.ac-body {
  margin-bottom: 12px;
}

.ac-message {
  font-size: 0.85rem;
  color: var(--text-primary);
  line-height: 1.6;
  margin-bottom: 8px;
}

.ac-preview {
  padding: 8px 10px;
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: 6px;
}

.preview-row {
  display: flex;
  gap: 8px;
  font-size: 0.78rem;
  padding: 3px 0;

  .pk {
    color: var(--warning);
    min-width: 60px;

    &::after {
      content: '：';
    }
  }

  .pv {
    color: var(--text-secondary);
  }
}

.ac-actions {
  display: flex;
  gap: 8px;
}

.btn-approve,
.btn-reject {
  flex: 1;
  padding: 7px 12px;
  font-size: 0.78rem;
  border-radius: 6px;
  cursor: pointer;
  transition: background 160ms ease, border-color 160ms ease;
  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}

.btn-approve {
  color: var(--accent);
  background: var(--accent-soft);
  border: 1px solid var(--accent-border);

  &:hover {
    background: color-mix(in srgb, var(--accent-soft) 70%, var(--accent) 30%);
    border-color: var(--accent);
  }
}

.btn-reject {
  color: var(--danger);
  background: var(--danger-soft);
  border: 1px solid color-mix(in srgb, var(--danger) 30%, var(--border-subtle));

  &:hover {
    background: color-mix(in srgb, var(--danger-soft) 72%, var(--danger) 28%);
    border-color: var(--danger);
  }
}

.ac-resolved {
  font-size: 0.78rem;

  .approved {
    color: var(--accent);
  }
  .rejected {
    color: var(--danger);
  }
}

.btn-open-bible {
  width: 100%;
  height: 30px;
  margin-top: 8px;
  border: 1px solid var(--border-subtle);
  border-radius: 4px;
  color: var(--text-secondary);
  background: var(--bg-surface);
  cursor: pointer;
}
</style>
