<script setup lang="ts">
import { ArrowDownOutlined } from '@ant-design/icons-vue'
import { computed, onUnmounted, ref, watch } from 'vue'
import ApprovalCard from '@/components/workbench/ApprovalCard.vue'
import AgentSessionHeader from '@/components/workbench/chat/AgentSessionHeader.vue'
import ChatComposer from '@/components/workbench/chat/ChatComposer.vue'
import ChatMessageList from '@/components/workbench/chat/ChatMessageList.vue'
import AiEditActivity from '@/components/workbench/chat/AiEditActivity.vue'
import ConversationHistoryPanel from '@/components/workbench/chat/ConversationHistoryPanel.vue'
import type { ChapterAiUndoOperation } from '@/entities/chapter/model'
import type { AgentRunAttempt, ChatMessage, ConversationItem, GenerationPhase, WorkbenchSkillCatalogItem } from '@/components/workbench/workbenchTypes'

const props = withDefaults(defineProps<{
  collapsed: boolean
  focused?: boolean
  panelWidth?: number
  currentModelName?: string
  generationStatusText?: string
  agentStatusDetailText?: string
  isGenerating?: boolean
  canCancelRun?: boolean
  isCancelling?: boolean
  canRetryRun?: boolean
  isRetrying?: boolean
  generationPhase?: GenerationPhase
  boundStyleName?: string
  showConversationPanel?: boolean
  conversationLoading?: boolean
  conversationList?: ConversationItem[]
  deletedConversationList?: ConversationItem[]
  recentlyDeletedConversation?: ConversationItem | null
  currentConversationId?: string | null
  bindChatContainer: (element: HTMLElement | null) => void
  showScrollToBottom?: boolean
  messages?: ChatMessage[]
  runAttempts?: AgentRunAttempt[]
  streamingAssistantMsgId?: string | number | null
  isApprovalBusy: (approvalId: string) => boolean
  chatInput?: string
  skillCatalog?: WorkbenchSkillCatalogItem[]
  activeSkills?: string[]
  skillCatalogLoading?: boolean
  activePlugins?: string[]
  activeChapterTitle?: string
  selectedText?: string
  aiUndoOperations?: ChapterAiUndoOperation[]
  aiUndoBusyOperationId?: string
  aiUndoBusyRunId?: string
  aiUndoDismissBusyOperationId?: string
  aiUndoDismissAllBusy?: boolean
}>(), {
  focused: false, panelWidth: 440, currentModelName: '', generationStatusText: '', agentStatusDetailText: '',
  isGenerating: false, canCancelRun: false, isCancelling: false, canRetryRun: false, isRetrying: false,
  generationPhase: 'idle', boundStyleName: '', showConversationPanel: false, conversationLoading: false,
  conversationList: () => [], deletedConversationList: () => [], currentConversationId: null,
  recentlyDeletedConversation: null,
  messages: () => [], runAttempts: () => [], streamingAssistantMsgId: null, chatInput: '', activePlugins: () => [],
  skillCatalog: () => [], activeSkills: () => [], skillCatalogLoading: false,
  activeChapterTitle: '', selectedText: '', showScrollToBottom: false,
  aiUndoOperations: () => [], aiUndoBusyOperationId: '', aiUndoBusyRunId: '',
  aiUndoDismissBusyOperationId: '', aiUndoDismissAllBusy: false,
})

const emit = defineEmits<{
  'toggle-collapse': []; 'toggle-focus': []; 'update:panel-width': [width: number]; 'reset-panel-width': []; 'toggle-history': [];
  'create-session': []; 'select-conversation': [payload: string]; 'load-deleted-conversations': [];
  'rename-conversation': [payload: { conversationId: string; title: string }]; 'delete-conversation': [payload: string];
  'restore-conversation': [payload: string]; approve: [payload: string]; reject: [payload: string];
  'open-story-bible': [payload: string]; 'update:chat-input': [payload: string]; send: [];
  'add-skill': [payload: string]; 'remove-skill': [payload: string]; 'refresh-skill-catalog': [];
  'cancel-run': []; 'retry-run': []; 'open-model-settings': [];
  'clear-selected-text': [];
  'scroll-to-bottom': [];
  'undo-ai': [operationId: string]; 'undo-ai-run': [runId: string];
  'dismiss-ai-undo': [operationId: string]; 'dismiss-all-ai-undo': [];
}>()

const chatContainerRef = ref<HTMLElement | null>(null)
const panelStyle = computed(() => props.focused ? { width: '100%' } : { width: props.collapsed ? '0px' : `${props.panelWidth}px` })
const pendingApprovalMessage = computed(() => [...props.messages].reverse().find((item) => item.approval && !item.approval.resolved) ?? null)

watch(chatContainerRef, (value) => props.bindChatContainer(value), { immediate: true })

let stopResize: (() => void) | null = null
const startResize = (event: PointerEvent) => {
  if (props.focused) return
  event.preventDefault()
  const startX = event.clientX
  const startWidth = props.panelWidth
  const move = (next: PointerEvent) => emit('update:panel-width', Math.min(600, Math.max(300, startWidth + startX - next.clientX)))
  const stop = () => { window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', stop); stopResize = null }
  stopResize = stop
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', stop)
}
onUnmounted(() => stopResize?.())
</script>

<template>
  <aside class="panel-right glass-panel" :class="{ collapsed, focused }" :style="panelStyle">
    <button v-if="!focused" type="button" class="panel-toggle" :aria-label="collapsed ? '展开对话面板' : '收起对话面板'" @click="emit('toggle-collapse')">
      {{ collapsed ? '‹' : '›' }}
    </button>
    <div v-if="!collapsed || focused" class="panel-content">
      <button
        v-if="!focused"
        type="button"
        class="resize-handle"
        aria-label="调整对话面板宽度"
        title="拖拽调整对话面板宽度"
        @pointerdown="startResize"
        @dblclick="emit('reset-panel-width')"
        @keydown.left.prevent="emit('update:panel-width', Math.min(600, panelWidth + 16))"
        @keydown.right.prevent="emit('update:panel-width', Math.max(300, panelWidth - 16))"
      ></button>
      <AgentSessionHeader
        :current-model-name="currentModelName" :generation-status-text="generationStatusText"
        :agent-status-detail-text="agentStatusDetailText" :is-generating="isGenerating"
        :generation-phase="generationPhase" :bound-style-name="boundStyleName" :focused="focused"
        @toggle-history="emit('toggle-history')" @create-session="emit('create-session')" @toggle-focus="emit('toggle-focus')"
      />
      <ConversationHistoryPanel
        :visible="showConversationPanel" :loading="conversationLoading" :conversations="conversationList"
        :deleted-conversations="deletedConversationList" :current-conversation-id="currentConversationId"
        @close="emit('toggle-history')" @select-conversation="emit('select-conversation', $event)"
        @load-deleted="emit('load-deleted-conversations')" @rename="emit('rename-conversation', $event)"
        @delete="emit('delete-conversation', $event)" @restore="emit('restore-conversation', $event)"
      />
      <main ref="chatContainerRef" class="chat-scroll" aria-live="polite">
        <div v-if="!messages.length" class="chat-empty">
          <strong>开始一次写作协作</strong><span>选择章节并描述你希望 Agent 完成的工作。</span>
        </div>
        <ChatMessageList
          :messages="messages" :run-attempts="runAttempts" :is-generating="isGenerating"
          :streaming-assistant-msg-id="streamingAssistantMsgId" :is-approval-busy="isApprovalBusy"
          :can-retry-run="canRetryRun" :is-retrying="isRetrying"
          @approve="emit('approve', $event)" @reject="emit('reject', $event)" @open-story-bible="emit('open-story-bible', $event)"
          @retry="emit('retry-run')"
        />
      </main>
      <button
        v-if="showScrollToBottom"
        type="button"
        class="scroll-to-bottom"
        title="回到底部"
        aria-label="回到底部"
        @click="emit('scroll-to-bottom')"
      >
        <ArrowDownOutlined />
      </button>
      <div v-if="pendingApprovalMessage?.approval" class="pending-approval-bar" role="region" aria-label="待处理审批">
        <ApprovalCard
          :card="pendingApprovalMessage.approval" :busy="isApprovalBusy(pendingApprovalMessage.approval.id)"
          @approve="emit('approve', $event)" @reject="emit('reject', $event)" @open-story-bible="emit('open-story-bible', $event)"
        />
      </div>
      <div v-if="recentlyDeletedConversation" class="undo-delete" role="status">
        <span>已删除“{{ recentlyDeletedConversation.title }}”</span>
        <button type="button" @click="emit('restore-conversation', recentlyDeletedConversation.conversationId)">撤销</button>
      </div>
      <AiEditActivity
        :operations="aiUndoOperations"
        :busy-operation-id="aiUndoBusyOperationId"
        :busy-run-id="aiUndoBusyRunId"
        :dismiss-busy-operation-id="aiUndoDismissBusyOperationId"
        :dismiss-all-busy="aiUndoDismissAllBusy"
        @undo="emit('undo-ai', $event)"
        @undo-run="emit('undo-ai-run', $event)"
        @dismiss="emit('dismiss-ai-undo', $event)"
        @dismiss-all="emit('dismiss-all-ai-undo')"
      />
      <ChatComposer
        :model-value="chatInput" :is-generating="isGenerating" :can-cancel-run="canCancelRun"
        :skill-catalog="skillCatalog" :active-skills="activeSkills" :skill-catalog-loading="skillCatalogLoading"
        :is-cancelling="isCancelling"
        :current-model-name="currentModelName" :active-plugins="activePlugins" :active-chapter-title="activeChapterTitle"
        :selected-text="selectedText" :bound-style-name="boundStyleName"
        @update:model-value="emit('update:chat-input', $event)" @send="emit('send')" @cancel="emit('cancel-run')"
        @add-skill="emit('add-skill', $event)" @remove-skill="emit('remove-skill', $event)"
        @refresh-skill-catalog="emit('refresh-skill-catalog')"
        @open-model-settings="emit('open-model-settings')"
        @clear-selected-text="emit('clear-selected-text')"
      />
    </div>
  </aside>
</template>

<style scoped lang="less">
.panel-right { position: relative; flex: 0 0 auto; min-width: 0; height: 100%; border-left: 1px solid var(--border-subtle); background: var(--bg-surface); box-shadow: -8px 0 20px color-mix(in srgb, var(--text-primary) 8%, transparent); transition: width 180ms ease; }
.panel-right.focused { position: absolute; inset: 0; z-index: 210; border-left: 0; }
.panel-right.collapsed { border-left: 0; box-shadow: none; }
.panel-content { position: relative; height: 100%; display: flex; flex-direction: column; overflow: hidden; }
.panel-toggle { position: absolute; left: -24px; top: 50%; z-index: 12; width: 24px; height: 48px; border: 1px solid var(--border-subtle); border-right: 0; background: var(--bg-surface); color: var(--text-muted); cursor: pointer; }
.resize-handle { position: absolute; inset: 0 auto 0 -4px; z-index: 15; width: 8px; border: 0; background: transparent; cursor: col-resize; }
.resize-handle:hover, .resize-handle:focus-visible { background: var(--focus-ring); outline: 0; }
.chat-scroll { flex: 1; min-height: 0; overflow-y: auto; overscroll-behavior: contain; padding: 16px 18px; scrollbar-gutter: stable; }
.chat-empty { min-height: 180px; display: grid; place-content: center; gap: 5px; color: var(--text-muted); text-align: center; }
.chat-empty strong { color: var(--text-secondary); font-size: 14px; } .chat-empty span { font-size: 12px; }
.scroll-to-bottom { position: absolute; right: 22px; bottom: 112px; z-index: 12; width: 34px; height: 34px; display: grid; place-items: center; padding: 0; border: 1px solid var(--border-subtle); background: var(--bg-elevated); color: var(--text-secondary); box-shadow: var(--shadow-sm); cursor: pointer; }
.scroll-to-bottom:hover, .scroll-to-bottom:focus-visible { border-color: var(--accent-border); color: var(--accent); outline: 0; }
.pending-approval-bar { flex: 0 0 auto; max-height: 38vh; overflow: auto; padding: 10px 14px; border-top: 1px solid color-mix(in srgb, var(--warning) 28%, var(--border-subtle)); background: var(--bg-surface); box-shadow: 0 -8px 20px color-mix(in srgb, var(--text-primary) 8%, transparent); }
.undo-delete { min-height: 38px; display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 6px 14px; border-top: 1px solid var(--border-subtle); background: var(--bg-subtle); color: var(--text-secondary); font-size: 12px; }
.undo-delete button { border: 0; background: transparent; color: var(--accent); cursor: pointer; font-weight: 650; }
@media (max-width: 900px) { .resize-handle { display: none; } .panel-right.focused { position: fixed; top: 48px; } }
@media (prefers-reduced-motion: reduce) { .panel-right { transition: none; } }
</style>
