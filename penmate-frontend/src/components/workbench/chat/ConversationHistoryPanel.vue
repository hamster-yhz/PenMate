<script setup lang="ts">
import type { ConversationItem } from '@/components/workbench/workbenchTypes'

withDefaults(defineProps<{
  visible?: boolean
  loading?: boolean
  conversations?: ConversationItem[]
  currentConversationId?: number | null
}>(), {
  visible: false,
  loading: false,
  conversations: () => [],
  currentConversationId: null,
})

const emit = defineEmits<{
  'select-conversation': [conversationId: number]
}>()

const handleSelect = (conversationId: number) => {
  emit('select-conversation', conversationId)
}
</script>

<template>
  <div v-if="visible" class="conversation-panel">
    <div class="conversation-panel-title">会话历史</div>

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
</template>

<style scoped lang="less">
.conversation-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
  border-bottom: 1px solid var(--border-subtle);
  background: rgba(11, 17, 32, 0.42);
}

.conversation-panel-title {
  font-family: var(--font-heading);
  font-size: 0.82rem;
  color: var(--amber-gold);
  letter-spacing: 0.08em;
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
