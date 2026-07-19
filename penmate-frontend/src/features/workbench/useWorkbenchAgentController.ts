import { computed, nextTick, ref, type Ref } from 'vue'
import { message } from 'ant-design-vue'
import { agentApi } from '@/api/modules/agent.api'
import { approvalApi } from '@/api/modules/approval.api'
import type { ChatMessage } from '@/components/workbench/workbenchTypes'
import { useWorkbenchApprovals } from '@/composables/workbench/useWorkbenchApprovals'
import { useWorkbenchChat } from '@/composables/workbench/useWorkbenchChat'
import { useWorkbenchSessionRecovery } from '@/composables/workbench/useWorkbenchSessionRecovery'
import { pickBusinessRecord } from '@/utils/apiPayload'

type NullableContext = { projectId: string | null; operatorId: string | null }

type WorkbenchAgentOptions = {
  getContext: () => NullableContext
  getProjectId: () => string
  getOperatorId: () => string
  getActiveChapterKey: () => string
  getSelectedText: () => string
  activePlugins: Ref<string[]>
  ensureModelConfigId: () => Promise<string>
  refreshActiveModelInfo: () => Promise<string | null>
  requestModelSelection: () => void
  mergeToEditor: (message: ChatMessage) => void
  replaceSelected: (message: ChatMessage) => void
  onRecoveryContext: (context: Record<string, unknown>) => void
}

export const useWorkbenchAgentController = (options: WorkbenchAgentOptions) => {
  const chatContainer = ref<HTMLElement | null>(null)
  const boundStyleName = ref('')

  const syncBoundStyleName = (value: Record<string, unknown> | null | undefined) => {
    const boundStyle = value?.boundStyle as Record<string, unknown> | null | undefined
    boundStyleName.value = String(boundStyle?.name || '')
  }
  const debugChatState = (stage: string, extra: Record<string, unknown> = {}) => {
    console.info('[agent-ui] chat-state', {
      stage,
      isGenerating: isGenerating.value,
      generationPhase: generationPhase.value,
      generationTaskStatus: generationTaskStatus.value,
      messageCount: messages.value.length,
      lastMessageRole: messages.value[messages.value.length - 1]?.role || '',
      lastMessageLength: messages.value[messages.value.length - 1]?.text?.length || 0,
      ...extra,
    })
  }
  const openRunStream = (projectId: string, runId: string, after = '0') => {
    console.info('[agent-ui] run-stream-open', { projectId, runId, after })
    return agentApi.openRunStream(projectId, runId, after)
  }
  const bindChatContainer = (element: HTMLElement | null) => {
    chatContainer.value = element
  }
  const scrollChat = () => {
    if (chatContainer.value) chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }

  const {
    messages,
    showConversationPanel,
    conversationLoading,
    conversationList,
    chatInput,
    isGenerating,
    isCancelling,
    isRetrying,
    canCancelRun,
    canRetryRun,
    generationPhase,
    generationTaskStatus,
    generationStatusText,
    agentStatusDetailText,
    streamingAssistantMsgId,
    runtimeEventSource,
    currentConversationId,
    loadConversationList,
    toggleConversationPanel,
    sendMessage,
    cancelCurrentRun,
    retryCurrentRun,
    dispose,
    resumeRunningRun,
    hydrateFromRecoverySnapshot,
  } = useWorkbenchChat({
    getContext: options.getContext,
    getCurrentProjectId: options.getProjectId,
    getActiveChapterKey: options.getActiveChapterKey,
    getSelectedText: options.getSelectedText,
    getActivePlugins: () => options.activePlugins.value,
    ensureModelConfigId: options.ensureModelConfigId,
    refreshActiveModelInfo: options.refreshActiveModelInfo,
    listSessions: agentApi.listSessions,
    createSession: agentApi.createSession,
    getSessionRecovery: agentApi.getSessionRecovery,
    createTurn: async (projectId, sessionId, payload) => {
      debugChatState('create-turn-request', {
        projectId,
        sessionId,
        operatorId: payload.operatorId,
        taskType: (payload.taskRequest as Record<string, unknown> | undefined)?.taskType || '',
        chapterId: (payload.taskRequest as Record<string, unknown> | undefined)?.chapterId ?? null,
        userMessageLength: String(payload.userMessage || '').length,
      })
      const result = pickBusinessRecord(await agentApi.createTurn(projectId, sessionId, payload))
      syncBoundStyleName(result.session as Record<string, unknown> | null | undefined)
      return result
    },
    cancelRun: agentApi.cancelRun,
    retryRun: agentApi.retryRun,
    openRunStream: (projectId, runId, after) => {
      const normalizedRunId = String(runId ?? '').trim()
      if (!normalizedRunId) throw new Error('缺少 sessionId，无法打开 turn stream')
      return openRunStream(projectId, normalizedRunId, after)
    },
    addStreamListener: agentApi.addStreamListener,
    scrollChat,
    nextTick,
    notifyWarning: (text) => message.warning(text),
    debugChatState,
    onRequireModelSelection: options.requestModelSelection,
  })

  const visibleMessages = computed(() =>
    messages.value.filter((item) => {
      if (item.role !== 'assistant') return true
      const hasText = String(item.text || '').trim().length > 0
      const isStreaming =
        streamingAssistantMsgId.value != null && String(item.id) === String(streamingAssistantMsgId.value)
      return hasText || !!item.approval || isStreaming
    }),
  )
  const { isApprovalBusy, handleApprove, handleReject } = useWorkbenchApprovals({
    getContext: options.getContext,
    getMessages: () => messages.value,
    approve: approvalApi.approve,
    reject: approvalApi.reject,
    notifyWarning: (text) => message.warning(text),
  })
  const recovery = useWorkbenchSessionRecovery({
    getSessionRecovery: agentApi.getSessionRecovery,
    resumeSession: agentApi.resumeSession,
    openRunStream,
    resumeRunningRun,
    hydrateStore: (snapshot) => {
      const normalized = pickBusinessRecord(snapshot)
      hydrateFromRecoverySnapshot(normalized)
      syncBoundStyleName(normalized.session as Record<string, unknown> | null | undefined)
      options.onRecoveryContext((normalized.workbenchContext || {}) as Record<string, unknown>)
    },
  })

  const resumeSession = async (sessionId: string) => {
    const projectId = options.getProjectId()
    const operatorId = options.getOperatorId()
    if (projectId && sessionId && operatorId) await recovery.restore(projectId, sessionId, operatorId)
  }
  const selectConversation = async (conversationId: string) => {
    if (!conversationId) return
    await resumeSession(conversationId)
    showConversationPanel.value = false
  }
  const createSession = async () => {
    const projectId = options.getProjectId()
    const operatorId = options.getOperatorId()
    if (!projectId || !operatorId) return
    const created = pickBusinessRecord(await agentApi.createSession(projectId, { userId: operatorId, title: '新会话' }))
    const sessionId = String(created.sessionId ?? '').trim()
    if (!sessionId) return
    currentConversationId.value = sessionId
    messages.value = []
    boundStyleName.value = ''
    if (showConversationPanel.value) await loadConversationList(projectId)
  }
  const resumeLatestSession = async () => {
    const projectId = options.getProjectId()
    if (!projectId) return
    const sessions = await agentApi.listSessions(projectId)
    const latestSessionId = String(sessions[0]?.sessionId ?? '').trim()
    if (latestSessionId) await resumeSession(latestSessionId)
  }

  return {
    messages,
    visibleMessages,
    boundStyleName,
    showConversationPanel,
    conversationLoading,
    conversationList,
    chatInput,
    isGenerating,
    isCancelling,
    isRetrying,
    canCancelRun,
    canRetryRun,
    generationPhase,
    generationStatusText,
    agentStatusDetailText,
    streamingAssistantMsgId,
    runtimeEventSource,
    currentConversationId,
    toggleConversationPanel,
    sendMessage,
    cancelCurrentRun,
    retryCurrentRun,
    isApprovalBusy,
    handleApprove,
    handleReject,
    bindChatContainer,
    resumeSession,
    selectConversation,
    createSession,
    resumeLatestSession,
    mergeMessageToEditor: options.mergeToEditor,
    replaceMessageSelection: options.replaceSelected,
    dispose,
  }
}
