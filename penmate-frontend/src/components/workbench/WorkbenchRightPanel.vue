<template>
  <aside class="panel panel-right glass-panel" :class="{ collapsed }">
    <button
      type="button"
      class="panel-toggle right-toggle"
      :aria-label="collapsed ? '展开对话面板' : '收起对话面板'"
      @click="emit('toggle-collapse')"
    >
      {{ collapsed ? '◂' : '▸' }}
    </button>

    <div v-show="!collapsed" class="panel-content">
      <AgentSessionHeader
        :current-model-name="currentModelName"
        :generation-status-text="generationStatusText"
        :agent-status-detail-text="agentStatusDetailText"
        :is-generating="isGenerating"
        :can-cancel-run="canCancelRun"
        :is-cancelling="isCancelling"
        :generation-phase="generationPhase"
        :bound-style-name="boundStyleName"
        @toggle-history="emit('toggle-history')"
        @create-session="emit('create-session')"
      />

      <ConversationHistoryPanel
        :visible="showConversationPanel"
        :loading="conversationLoading"
        :conversations="conversationList"
        :current-conversation-id="currentConversationId"
        @close="emit('toggle-history')"
        @select-conversation="emit('select-conversation', $event)"
      />

      <div ref="chatContainerRef" class="chat-messages">
        <ChatMessageList
          :messages="messages"
          :is-generating="isGenerating"
          :streaming-assistant-msg-id="streamingAssistantMsgId"
          :is-approval-busy="isApprovalBusy"
          @merge-to-editor="emit('merge-to-editor', $event)"
          @replace-selected="emit('replace-selected', $event)"
          @approve="emit('approve', $event)"
          @reject="emit('reject', $event)"
          @open-story-bible="emit('open-story-bible', $event)"
        />
      </div>

      <ChatComposer
        :model-value="chatInput"
        :is-generating="isGenerating"
        :can-cancel-run="canCancelRun"
        :is-cancelling="isCancelling"
        :can-retry-run="canRetryRun"
        :is-retrying="isRetrying"
        :current-model-name="currentModelName"
        :active-plugins="activePlugins"
        @update:model-value="emit('update:chat-input', $event)"
        @send="emit('send')"
        @cancel="emit('cancel-run')"
        @retry="emit('retry-run')"
        @open-model-settings="emit('open-model-settings')"
      />
    </div>
  </aside>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import AgentSessionHeader from '@/components/workbench/chat/AgentSessionHeader.vue'
import ConversationHistoryPanel from '@/components/workbench/chat/ConversationHistoryPanel.vue'
import ChatMessageList from '@/components/workbench/chat/ChatMessageList.vue'
import ChatComposer from '@/components/workbench/chat/ChatComposer.vue'
import type { ChatMessage, ConversationItem, GenerationPhase } from '@/components/workbench/workbenchTypes'

const props = withDefaults(
  defineProps<{
    collapsed: boolean
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
    currentConversationId?: string | null
    bindChatContainer: (element: HTMLElement | null) => void
    messages?: ChatMessage[]
    streamingAssistantMsgId?: string | number | null
    isApprovalBusy: (approvalId: string) => boolean
    chatInput?: string
    activePlugins?: string[]
  }>(),
  {
    currentModelName: '',
    generationStatusText: '',
    agentStatusDetailText: '',
    isGenerating: false,
    canCancelRun: false,
    isCancelling: false,
    canRetryRun: false,
    isRetrying: false,
    generationPhase: 'idle',
    boundStyleName: '',
    showConversationPanel: false,
    conversationLoading: false,
    conversationList: () => [],
    currentConversationId: null,
    messages: () => [],
    streamingAssistantMsgId: null,
    chatInput: '',
    activePlugins: () => [],
  },
)

const emit = defineEmits<{
  (event: 'toggle-collapse'): void
  (event: 'toggle-history'): void
  (event: 'create-session'): void
  (event: 'select-conversation', payload: string): void
  (event: 'merge-to-editor', payload: ChatMessage): void
  (event: 'replace-selected', payload: ChatMessage): void
  (event: 'approve', payload: string): void
  (event: 'reject', payload: string): void
  (event: 'open-story-bible', payload: string): void
  (event: 'update:chat-input', payload: string): void
  (event: 'send'): void
  (event: 'cancel-run'): void
  (event: 'retry-run'): void
  (event: 'open-model-settings'): void
}>()

const chatContainerRef = ref<HTMLElement | null>(null)

watch(
  chatContainerRef,
  (value) => {
    props.bindChatContainer(value)
  },
  { immediate: true },
)
</script>

<style lang="less" scoped>
.panel {
  position: relative;
  display: flex;
  flex-direction: column;
  transition: width 0.3s var(--ease-silk);
}

.panel-toggle {
  position: absolute;
  top: 50%;
  z-index: 10;
  width: 16px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(17, 24, 39, 0.9);
  border: 1px solid var(--border-subtle);
  color: var(--text-muted);
  font-size: 0.7rem;
  cursor: pointer;
  transition: all 0.3s;

  &:hover {
    color: var(--amber-gold);
    border-color: var(--border-gold);
  }
}

.panel-content {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.panel-right {
  width: clamp(320px, 26vw, 420px);
  min-width: 0;
  border-left: 1px solid var(--border-subtle);
  background: linear-gradient(180deg, rgba(17, 24, 39, 0.72), rgba(11, 17, 32, 0.56));
  box-shadow: var(--shadow-lg), var(--shadow-gold);

  &.collapsed {
    width: 0;
    border-left: none;

    .right-toggle {
      left: -16px;
      border-radius: 4px 0 0 4px;
    }
  }

  .right-toggle {
    left: 0;
    top: 50%;
    transform: translateY(-50%) translateX(-100%);
    border-radius: 4px 0 0 4px;
    border-right: none;
  }
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

@media (max-width: 1360px) {
  .panel-right {
    width: 320px;
  }
}

@media (max-width: 1120px) {
  .panel-right {
    width: 300px;
  }
}
</style>
