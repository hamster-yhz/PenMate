<script setup lang="ts">
import ChatMessageItem from './ChatMessageItem.vue'
import RunAttemptGroup from './RunAttemptGroup.vue'
import type { AgentRunAttempt, ChatMessage } from '@/components/workbench/workbenchTypes'

const props = withDefaults(
  defineProps<{
    messages?: ChatMessage[]
    isGenerating?: boolean
    streamingAssistantMsgId?: string | number | null
    isApprovalBusy?: (id: string) => boolean
    runAttempts?: AgentRunAttempt[]
  }>(),
  {
    messages: () => [],
    isGenerating: false,
    streamingAssistantMsgId: null,
    isApprovalBusy: undefined,
    runAttempts: () => [],
  },
)

defineEmits<{
  approve: [id: string]
  reject: [id: string]
  'open-story-bible': [nodeId: string]
}>()

const attemptsForMessage = (message: ChatMessage) =>
  message.turnId ? props.runAttempts.filter((attempt) => attempt.turnId === message.turnId) : []

const assistantMessagesForTurn = (turnId: string | undefined) =>
  turnId ? props.messages.filter((message) => message.role === 'assistant' && message.turnId === turnId) : []

const isHandledByAttemptGroup = (message: ChatMessage) =>
  message.role === 'assistant' &&
  Boolean(message.turnId) &&
  props.runAttempts.some((attempt) => attempt.turnId === message.turnId)
</script>

<template>
  <div class="chat-messages">
    <template v-for="msg in messages" :key="msg.id">
      <ChatMessageItem
        v-if="!isHandledByAttemptGroup(msg)"
        :msg="msg"
        :is-generating="isGenerating"
        :streaming-assistant-msg-id="streamingAssistantMsgId"
        :approval-busy="msg.approval ? Boolean(isApprovalBusy?.(msg.approval.id)) : false"
        @approve="$emit('approve', $event)"
        @reject="$emit('reject', $event)"
        @open-story-bible="$emit('open-story-bible', $event)"
      />
      <RunAttemptGroup
        v-if="msg.role === 'user' && attemptsForMessage(msg).length"
        :attempts="attemptsForMessage(msg)"
        :assistant-messages="assistantMessagesForTurn(msg.turnId)"
        :is-generating="isGenerating"
        :streaming-assistant-msg-id="streamingAssistantMsgId"
        :is-approval-busy="isApprovalBusy"
        @approve="$emit('approve', $event)"
        @reject="$emit('reject', $event)"
        @open-story-bible="$emit('open-story-bible', $event)"
      />
    </template>
  </div>
</template>

<style scoped lang="less">
.chat-messages {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 100%;
  padding-bottom: 4px;
  background: transparent;
}
</style>
