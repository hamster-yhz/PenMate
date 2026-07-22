<script setup lang="ts">
import { UserOutlined } from '@ant-design/icons-vue'
import { computed } from 'vue'
import ApprovalCard from '@/components/workbench/ApprovalCard.vue'
import type { ChatMessage } from '@/components/workbench/workbenchTypes'
import { renderChatMarkdown } from '@/utils/chatMarkdown'

const props = withDefaults(defineProps<{ msg: ChatMessage; isGenerating?: boolean; streamingAssistantMsgId?: string | number | null; approvalBusy?: boolean }>(), {
  isGenerating: false, streamingAssistantMsgId: null, approvalBusy: false,
})
const emit = defineEmits<{
  approve: [id: string]; reject: [id: string]; 'open-story-bible': [nodeId: string];
}>()
const isStreaming = () => props.msg.role === 'assistant' && String(props.msg.id) === String(props.streamingAssistantMsgId) && props.isGenerating
const renderedMarkdown = computed(() => renderChatMarkdown(props.msg.text, isStreaming()))
</script>

<template>
  <article class="chat-message" :class="msg.role">
    <div class="message-heading">
      <span v-if="msg.role === 'user'" class="message-avatar"><UserOutlined /></span>
      <strong>{{ msg.role === 'user' ? '你' : msg.role === 'assistant' ? 'Agent' : '系统' }}</strong>
      <time v-if="msg.createdAt">{{ new Date(msg.createdAt).toLocaleTimeString('zh-CN', { hour12: false, hour: '2-digit', minute: '2-digit' }) }}</time>
    </div>
    <div class="message-body">
      <div
        v-if="msg.text && msg.role === 'assistant'"
        class="message-text"
        data-testid="message-text"
        v-html="renderedMarkdown"
      ></div>
      <div v-else-if="msg.text" class="message-text" data-testid="message-text">{{ msg.text }}</div>
      <div v-if="isStreaming() && !msg.text" class="typing" data-testid="inline-typing" role="status">
        <span></span><span></span><span></span><em>AI正在创作中</em>
      </div>
    </div>
    <ApprovalCard v-if="msg.approval" :card="msg.approval" :busy="approvalBusy" @approve="emit('approve', $event)" @reject="emit('reject', $event)" @open-story-bible="emit('open-story-bible', $event)" />
  </article>
</template>

<style scoped lang="less">
.chat-message { display: grid; gap: 7px; min-width: 0; }
.message-heading { display: flex; align-items: center; gap: 7px; color: var(--text-muted); font-size: 11px; }
.message-heading strong { color: var(--text-secondary); font-size: 12px; }
.message-heading time { margin-left: auto; }
.message-avatar { width: 22px; height: 22px; display: grid; place-items: center; border-radius: var(--radius-sm); background: var(--accent-soft); color: var(--accent); }
.message-body { min-width: 0; }
.user .message-body { margin-left: 29px; padding: 10px 12px; border-left: 2px solid var(--accent); background: var(--accent-soft); }
.assistant .message-body { padding: 0 0 2px; }
.system .message-body { padding: 8px 10px; border-left: 2px solid var(--info); background: var(--info-soft); }
.message-text { color: var(--text-primary); line-height: 1.75; white-space: pre-wrap; overflow-wrap: anywhere; }
.assistant .message-text { font-size: 14px; line-height: 1.5; white-space: normal; }
.message-text :deep(p) { margin: 0 0 0.3em; }
.message-text :deep(p:last-child) { margin-bottom: 0; }
.message-text :deep(h1), .message-text :deep(h2), .message-text :deep(h3), .message-text :deep(h4), .message-text :deep(h5), .message-text :deep(h6) { margin: 0.58em 0 0.18em; color: var(--text-primary); line-height: 1.25; letter-spacing: 0; }
.message-text :deep(h1) { font-size: 17px; } .message-text :deep(h2) { font-size: 15px; } .message-text :deep(h3), .message-text :deep(h4), .message-text :deep(h5), .message-text :deep(h6) { font-size: 14px; }
.message-text :deep(ul), .message-text :deep(ol) { margin: 0.18em 0; padding-left: 1.4em; }
.message-text :deep(ul ul), .message-text :deep(ul ol), .message-text :deep(ol ul), .message-text :deep(ol ol) { margin: 0.08em 0; }
.message-text :deep(li + li) { margin-top: 0.06em; }
.message-text :deep(li > p) { margin-bottom: 0.12em; }
.message-text :deep(blockquote) { margin: 0.3em 0; padding-left: 8px; border-left: 2px solid var(--accent-border); color: var(--text-secondary); }
.message-text :deep(code) { padding: 1px 4px; border-radius: var(--radius-sm); background: var(--bg-subtle); font-family: var(--font-mono); font-size: 0.9em; }
.message-text :deep(pre) { max-width: 100%; overflow: auto; margin: 0.35em 0; padding: 7px 9px; border: 1px solid var(--border-subtle); border-radius: var(--radius-sm); background: var(--bg-subtle); color: var(--text-primary); line-height: 1.4; }
.message-text :deep(pre code) { padding: 0; background: transparent; }
.message-text :deep(a) { color: var(--accent); text-decoration: underline; text-underline-offset: 2px; }
.message-text :deep(table) { width: 100%; border-collapse: collapse; margin: 0.35em 0; font-size: 13px; line-height: 1.4; }
.message-text :deep(th), .message-text :deep(td) { padding: 4px 6px; border: 1px solid var(--border-subtle); text-align: left; }
.message-text :deep(hr) { margin: 0.5em 0; border: 0; border-top: 1px solid var(--border-subtle); }
.message-text :deep(.markdown-stream-tail) { white-space: pre-wrap; }
.typing { display: flex; align-items: center; gap: 5px; min-height: 28px; color: var(--text-muted); }
.typing span { width: 5px; height: 5px; border-radius: 50%; background: var(--accent); animation: pulse 1s ease-in-out infinite; }
.typing span:nth-child(2) { animation-delay: 120ms; } .typing span:nth-child(3) { animation-delay: 240ms; }
.typing em { margin-left: 4px; font-style: normal; font-size: 11px; }
@keyframes pulse { 0%, 100% { opacity: .3; transform: translateY(0); } 50% { opacity: 1; transform: translateY(-2px); } }
@media (prefers-reduced-motion: reduce) { .typing span { animation: none; } }
</style>
