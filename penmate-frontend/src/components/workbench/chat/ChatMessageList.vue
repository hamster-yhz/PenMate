 <script setup lang="ts">
import ChatMessageItem from './ChatMessageItem.vue'
import type { ChatMessage } from '@/components/workbench/workbenchTypes'

withDefaults(defineProps<{
  messages?: ChatMessage[]
  isGenerating?: boolean
  streamingAssistantMsgId?: number | null
  isApprovalBusy?: (id: string) => boolean
}>(), {
  messages: () => [],
  isGenerating: false,
  streamingAssistantMsgId: null,
  isApprovalBusy: undefined,
})

const emit = defineEmits<{
  'merge-to-editor': [msg: ChatMessage]
  'replace-selected': [msg: ChatMessage]
  approve: [id: string]
  reject: [id: string]
}>()

const handleMergeToEditor = (payload: ChatMessage) => {
  emit('merge-to-editor', payload)
}

const handleReplaceSelected = (payload: ChatMessage) => {
  emit('replace-selected', payload)
}
</script>

<template>
  <div class="chat-messages">
    <ChatMessageItem
      v-for="msg in messages"
      :key="msg.id"
      :msg="msg"
      :is-generating="isGenerating"
      :streaming-assistant-msg-id="streamingAssistantMsgId"
      :approval-busy="msg.approval ? Boolean(isApprovalBusy?.(msg.approval.id)) : false"
      @merge-to-editor="handleMergeToEditor"
      @replace-selected="handleReplaceSelected"
      @approve="$emit('approve', $event)"
      @reject="$emit('reject', $event)"
    />
  </div>
</template>
