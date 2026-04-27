<script setup lang="ts">
import ApprovalCard from '@/components/workbench/ApprovalCard.vue'
import type { ChatMessage } from '@/components/workbench/workbenchTypes'

const props = withDefaults(defineProps<{
  msg: ChatMessage
  isGenerating?: boolean
  streamingAssistantMsgId?: number | null
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
    />
  </div>
</template>
