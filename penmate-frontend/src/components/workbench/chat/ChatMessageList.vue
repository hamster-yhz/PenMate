<script setup lang="ts">
import ChatMessageItem from './ChatMessageItem.vue'
import RunAttemptGroup from './RunAttemptGroup.vue'
import { computed } from 'vue'
import type { AgentRunAttempt, ChatMessage } from '@/components/workbench/workbenchTypes'

const props = withDefaults(
  defineProps<{
    messages?: ChatMessage[]
    isGenerating?: boolean
    streamingAssistantMsgId?: string | number | null
    isApprovalBusy?: (id: string) => boolean
    runAttempts?: AgentRunAttempt[]
    canRetryRun?: boolean
    isRetrying?: boolean
  }>(),
  {
    messages: () => [],
    isGenerating: false,
    streamingAssistantMsgId: null,
    isApprovalBusy: undefined,
    runAttempts: () => [],
    canRetryRun: false,
    isRetrying: false,
  },
)

defineEmits<{
  approve: [id: string]
  reject: [id: string]
  'open-story-bible': [nodeId: string]
  retry: []
}>()

const retryableTurnId = computed(() => {
  if (!props.canRetryRun) return ''
  return [...props.runAttempts]
    .reverse()
    .find((attempt) => ['FAILED', 'CANCELLED'].includes(attempt.runStatus.toUpperCase()))?.turnId ?? ''
})

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
        :can-retry="msg.role === 'user' && Boolean(msg.turnId) && msg.turnId === retryableTurnId"
        :is-retrying="isRetrying"
        @approve="$emit('approve', $event)"
        @reject="$emit('reject', $event)"
        @open-story-bible="$emit('open-story-bible', $event)"
        @retry="$emit('retry')"
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
