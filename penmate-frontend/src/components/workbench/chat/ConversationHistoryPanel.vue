<script setup lang="ts">
import type { ConversationItem } from '@/components/workbench/workbenchTypes'

withDefaults(defineProps<{
  visible?: boolean
  loading?: boolean
  conversations?: ConversationItem[]
  currentConversationId?: string | null
}>(), {
  visible: false,
  loading: false,
  conversations: () => [],
  currentConversationId: null,
})

const emit = defineEmits<{
  'select-conversation': [conversationId: string]
  close: []
}>()

const handleSelect = (conversationId: string) => {
  emit('select-conversation', conversationId)
}
</script>

<template>
  <div v-if="visible" class="conversation-panel" data-testid="conversation-panel">
    <div class="conversation-panel-header">
      <button
        type="button"
        class="conversation-back"
        data-testid="conversation-back"
        @click="emit('close')"
      >
        ← 返回
      </button>
      <div class="conversation-panel-title">会话历史</div>
    </div>

    <div class="conversation-panel-body">
      <div v-if="loading" class="conversation-empty" data-testid="conversation-loading">
        加载中...
      </div>

      <div
        v-else-if="!conversations.length"
        class="conversation-empty"
        data-testid="conversation-empty"
      >
        暂无历史会话
      </div>

      <button
        v-for="conversation in conversations"
        v-else
        :key="String(conversation.conversationId)"
        type="button"
        class="conversation-item"
        data-testid="conversation-item"
        :class="{ active: currentConversationId === conversation.conversationId }"
        @click="handleSelect(conversation.conversationId)"
      >
        <div class="conversation-name">{{ conversation.title || `会话#${conversation.conversationId}` }}</div>
        <div class="conversation-meta">{{ conversation.updatedAt }}</div>
      </button>
    </div>
  </div>
</template>

<style scoped lang="less">
.conversation-panel {
  position: absolute;
  inset: 0;
  z-index: 3;
  display: flex;
  flex-direction: column;
  background: rgba(8, 12, 24, 0.96);
  backdrop-filter: blur(16px);
}

.conversation-panel-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border-bottom: 1px solid var(--border-subtle);
  background: linear-gradient(180deg, rgba(17, 24, 39, 0.96), rgba(11, 17, 32, 0.82));
}

.conversation-back {
  padding: 0 12px;
  min-height: 34px;
  border: 1px solid var(--border-subtle);
  border-radius: 999px;
  background: rgba(11, 17, 32, 0.56);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.25s var(--ease-silk);
}

.conversation-back:hover {
  color: var(--amber-gold);
  border-color: var(--border-gold);
  box-shadow: var(--shadow-gold);
}

.conversation-panel-title {
  font-family: var(--font-heading);
  font-size: 0.82rem;
  color: var(--amber-gold);
  letter-spacing: 0.08em;
}

.conversation-panel-body {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
}

.conversation-empty {
  padding: 14px;
  border: 1px dashed rgba(201, 169, 110, 0.18);
  border-radius: 12px;
  background: rgba(17, 24, 39, 0.42);
  color: var(--text-muted);
  text-align: center;
}

.conversation-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px;
  text-align: left;
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  background: rgba(17, 24, 39, 0.56);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.25s var(--ease-silk);
}

.conversation-item:hover,
.conversation-item.active {
  border-color: var(--border-gold);
  background: rgba(201, 169, 110, 0.08);
  box-shadow: var(--shadow-gold);
}

.conversation-name {
  color: var(--text-primary);
}

.conversation-meta {
  font-size: 0.76rem;
  color: var(--text-muted);
}
</style>
