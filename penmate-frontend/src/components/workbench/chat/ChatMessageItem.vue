<script setup lang="ts">
import ApprovalCard from '@/components/workbench/ApprovalCard.vue'
import type { ChatMessage } from '@/components/workbench/workbenchTypes'

const props = withDefaults(defineProps<{
  msg: ChatMessage
  isGenerating?: boolean
  streamingAssistantMsgId?: string | number | null
  approvalBusy?: boolean
}>(), {
  isGenerating: false,
  streamingAssistantMsgId: null,
  approvalBusy: false,
})

const emit = defineEmits<{
  'merge-to-editor': [msg: ChatMessage]
  'replace-selected': [msg: ChatMessage]
  approve: [id: string]
  reject: [id: string]
  'open-story-bible': [nodeId: string]
}>()

const emitMerge = () => {
  emit('merge-to-editor', props.msg)
}

const emitReplace = () => {
  emit('replace-selected', props.msg)
}
</script>

<template>
  <div
    class="chat-msg"
    :class="[
      msg.role,
      { generating: msg.role === 'assistant' && msg.id === streamingAssistantMsgId && isGenerating },
    ]"
  >
    <div class="msg-bubble">
      <div class="msg-text" data-testid="message-html" v-html="msg.text"></div>

      <div
        v-if="msg.role === 'assistant' && msg.id === streamingAssistantMsgId && isGenerating && !msg.text"
        class="msg-inline-typing"
        data-testid="inline-typing"
      >
        <span class="t-dot"></span>
        <span class="t-dot"></span>
        <span class="t-dot"></span>
        <span class="t-label">AI正在创作中...</span>
      </div>

      <div v-if="msg.role === 'assistant' && msg.text" class="msg-actions">
        <button
          type="button"
          class="msg-btn"
          data-testid="merge-to-editor"
          title="合并至编辑器"
          @click="emitMerge"
        >
          📥 合并
        </button>
        <button
          type="button"
          class="msg-btn"
          data-testid="replace-selected"
          title="替换所选文本"
          @click="emitReplace"
        >
          🔄 替换所选
        </button>
      </div>
    </div>

    <ApprovalCard
      v-if="msg.approval"
      :card="msg.approval"
      :busy="approvalBusy"
      @approve="$emit('approve', $event)"
      @reject="$emit('reject', $event)"
      @open-story-bible="$emit('open-story-bible', $event)"
    />
  </div>
</template>

<style scoped lang="less">
.chat-msg {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.msg-bubble {
  max-width: 92%;
  padding: 14px 16px;
  border: 1px solid var(--border-subtle);
  border-radius: 16px;
  background: rgba(17, 24, 39, 0.58);
  color: var(--text-primary);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.02);
}

.chat-msg.user {
  align-items: flex-end;
}

.chat-msg.user .msg-bubble {
  background: rgba(90, 158, 111, 0.12);
  border-color: rgba(90, 158, 111, 0.28);
}

.chat-msg.assistant .msg-bubble {
  background: rgba(201, 169, 110, 0.08);
  border-color: rgba(201, 169, 110, 0.18);
}

.msg-text {
  line-height: 1.8;
  word-break: break-word;
}

.msg-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 12px;
}

.msg-btn {
  padding: 6px 12px;
  border: 1px solid var(--border-subtle);
  border-radius: 999px;
  background: rgba(11, 17, 32, 0.54);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.25s var(--ease-silk);
}

.msg-btn:hover {
  color: var(--amber-gold);
  border-color: var(--border-gold);
  box-shadow: var(--shadow-gold);
}

.msg-inline-typing {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--text-secondary);
}

.t-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--amber-gold);
  animation: typingPulse 1.2s ease-in-out infinite;
}

.t-dot:nth-child(2) {
  animation-delay: 0.15s;
}

.t-dot:nth-child(3) {
  animation-delay: 0.3s;
}

.t-label {
  margin-left: 4px;
  font-size: 0.78rem;
  color: var(--text-muted);
}

@keyframes typingPulse {
  0%,
  80%,
  100% {
    transform: scale(0.8);
    opacity: 0.45;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}
</style>
