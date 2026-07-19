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
      v-if="card.toolCode === 'story_bible_update'"
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
const targetNodeId = computed(() => String(props.card.preview?.nodeId || props.card.preview?.entityId || ''))
</script>

<style lang="less" scoped>
.approval-card {
  position: relative;
  padding: 14px;
  background: rgba(201, 169, 110, 0.06);
  border: 1px solid rgba(201, 169, 110, 0.2);
  border-radius: 10px;
  transition: all 0.3s;
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
  background: linear-gradient(90deg, transparent, var(--amber-gold), transparent);
  opacity: 0.3;
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
  background: rgba(201, 169, 110, 0.12);
  border: 1px solid rgba(201, 169, 110, 0.25);
  border-radius: 10px;
  color: var(--amber-gold);
  letter-spacing: 0.05em;
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
  background: rgba(11, 17, 32, 0.5);
  border: 1px solid var(--border-subtle);
  border-radius: 6px;
}

.preview-row {
  display: flex;
  gap: 8px;
  font-size: 0.78rem;
  padding: 3px 0;

  .pk {
    color: var(--amber-gold);
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
  transition: all 0.3s;
  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}

.btn-approve {
  color: var(--jade-green);
  background: rgba(90, 158, 111, 0.1);
  border: 1px solid rgba(90, 158, 111, 0.25);

  &:hover {
    background: rgba(90, 158, 111, 0.2);
    border-color: rgba(90, 158, 111, 0.4);
  }
}

.btn-reject {
  color: #e8a87c;
  background: rgba(192, 60, 45, 0.08);
  border: 1px solid rgba(192, 60, 45, 0.2);

  &:hover {
    background: rgba(192, 60, 45, 0.15);
    border-color: rgba(192, 60, 45, 0.35);
  }
}

.ac-resolved {
  font-size: 0.78rem;

  .approved {
    color: var(--jade-green);
  }
  .rejected {
    color: #e8a87c;
  }
}

.btn-open-bible {
  width: 100%;
  height: 30px;
  margin-top: 8px;
  border: 1px solid var(--border-subtle);
  border-radius: 4px;
  color: var(--text-secondary);
  background: rgba(11, 17, 32, 0.46);
  cursor: pointer;
}
</style>
