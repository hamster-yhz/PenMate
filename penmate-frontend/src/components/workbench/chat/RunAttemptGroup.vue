<script setup lang="ts">
import { LeftOutlined, RightOutlined } from '@ant-design/icons-vue'
import { computed, ref, watch } from 'vue'
import ChatMessageItem from './ChatMessageItem.vue'
import RunEventTimeline from './RunEventTimeline.vue'
import type { AgentRunAttempt, ChatMessage } from '@/components/workbench/workbenchTypes'

const props = withDefaults(
  defineProps<{
    attempts: AgentRunAttempt[]
    assistantMessages?: ChatMessage[]
    isGenerating?: boolean
    streamingAssistantMsgId?: string | number | null
    isApprovalBusy?: (id: string) => boolean
  }>(),
  {
    assistantMessages: () => [],
    isGenerating: false,
    streamingAssistantMsgId: null,
    isApprovalBusy: undefined,
  },
)

const emit = defineEmits<{
  approve: [id: string]
  reject: [id: string]
  'open-story-bible': [nodeId: string]
}>()

const selectedIndex = ref(Math.max(0, props.attempts.length - 1))

watch(
  () => props.attempts.length,
  (length, previousLength) => {
    if (length > previousLength || selectedIndex.value >= length) {
      selectedIndex.value = Math.max(0, length - 1)
    }
  },
)

const selectedAttempt = computed(() => props.attempts[selectedIndex.value] ?? null)

const eventOutput = (attempt: AgentRunAttempt) => {
  const exactOutput = String(attempt.output?.text ?? '').trim()
  if (exactOutput) return exactOutput
  const completed = [...attempt.events]
    .reverse()
    .find((event) => event.type === 'message.completed')
  const completedText = String(completed?.payload.text ?? completed?.payload.content ?? '').trim()
  if (completedText) return completedText
  const failed = [...attempt.events].reverse().find((event) => event.type === 'run.failed')
  return String(failed?.payload.outputText ?? failed?.payload.message ?? '').trim()
}

const selectedMessage = computed<ChatMessage | null>(() => {
  const attempt = selectedAttempt.value
  if (!attempt) return null
  const candidates = props.assistantMessages.filter((message) => message.runId === attempt.runId)
  if (candidates.length) {
    const textMessage = [...candidates].reverse().find((message) => String(message.text || '').trim())
    const approvalMessage = [...candidates].reverse().find((message) => message.approval)
    const primary = textMessage ?? approvalMessage ?? candidates[candidates.length - 1]
    if (!primary) return null
    return approvalMessage && approvalMessage !== primary
      ? { ...primary, approval: approvalMessage.approval }
      : primary
  }

  const text = eventOutput(attempt)
  return text
    ? {
        id: `run-output-${attempt.runId}`,
        role: 'assistant',
        text,
        turnId: attempt.turnId,
        runId: attempt.runId,
        createdAt: attempt.finishedAt ?? undefined,
      }
    : null
})

const selectPrevious = () => {
  selectedIndex.value = Math.max(0, selectedIndex.value - 1)
}

const selectNext = () => {
  selectedIndex.value = Math.min(props.attempts.length - 1, selectedIndex.value + 1)
}
</script>

<template>
  <section v-if="selectedAttempt" class="attempt-group" data-testid="run-attempt-group">
    <nav v-if="attempts.length > 1" class="attempt-pagination" aria-label="运行版本">
      <button
        type="button"
        :disabled="selectedIndex === 0"
        title="上一次尝试"
        aria-label="上一次尝试"
        @click="selectPrevious"
      >
        <LeftOutlined />
      </button>
      <span>{{ selectedIndex + 1 }} / {{ attempts.length }}</span>
      <button
        type="button"
        :disabled="selectedIndex === attempts.length - 1"
        title="下一次尝试"
        aria-label="下一次尝试"
        @click="selectNext"
      >
        <RightOutlined />
      </button>
    </nav>

    <RunEventTimeline
      :attempt="selectedAttempt"
      :latest="selectedIndex === attempts.length - 1"
      :display-index="selectedIndex + 1"
    />
    <ChatMessageItem
      v-if="selectedMessage"
      :msg="selectedMessage"
      :is-generating="isGenerating"
      :streaming-assistant-msg-id="streamingAssistantMsgId"
      :approval-busy="selectedMessage.approval ? Boolean(isApprovalBusy?.(selectedMessage.approval.id)) : false"
      @approve="emit('approve', $event)"
      @reject="emit('reject', $event)"
      @open-story-bible="emit('open-story-bible', $event)"
    />
  </section>
</template>

<style scoped lang="less">
.attempt-group { display: grid; gap: 10px; min-width: 0; }
.attempt-pagination { min-height: 28px; display: flex; align-items: center; justify-content: flex-end; gap: 5px; color: var(--text-muted); font-size: 11px; }
.attempt-pagination button { width: 26px; height: 26px; display: grid; place-items: center; padding: 0; border: 1px solid transparent; background: transparent; color: var(--text-secondary); cursor: pointer; }
.attempt-pagination button:hover:not(:disabled), .attempt-pagination button:focus-visible { border-color: var(--accent-border); background: var(--accent-soft); color: var(--accent); outline: 0; }
.attempt-pagination button:disabled { color: var(--text-muted); cursor: default; opacity: 0.4; }
.attempt-pagination span { min-width: 34px; text-align: center; }
</style>
