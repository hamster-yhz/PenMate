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
