import { computed, nextTick, ref, type Ref } from 'vue'
import { message } from 'ant-design-vue'
import { agentApi } from '@/api/modules/agent.api'
import { approvalApi } from '@/api/modules/approval.api'
import type { ConversationItem } from '@/components/workbench/workbenchTypes'
import { useWorkbenchApprovals } from '@/composables/workbench/useWorkbenchApprovals'
import { useWorkbenchChat } from '@/composables/workbench/useWorkbenchChat'
import { useWorkbenchSessionRecovery } from '@/composables/workbench/useWorkbenchSessionRecovery'
import { pickBusinessRecord } from '@/utils/apiPayload'
import type { AgentSafetyMode } from '@/entities/agent/model'

type NullableContext = { projectId: string | null; operatorId: string | null }

type WorkbenchAgentOptions = {
  getContext: () => NullableContext
  getProjectId: () => string
  getOperatorId: () => string
  getActiveChapterKey: () => string
  getAttachedChapterIds: () => string[]
  getSelectedText: () => string
  activePlugins: Ref<string[]>
  ensureModelConfigId: () => Promise<string>
  refreshActiveModelInfo: () => Promise<string | null>
  requestModelSelection: () => void
  onRecoveryContext: (context: Record<string, unknown>) => void
  onMessageRegistered: () => void
}

export const useWorkbenchAgentController = (options: WorkbenchAgentOptions) => {
  const chatContainer = ref<HTMLElement | null>(null)
  const isChatFollowing = ref(true)
  const boundStyleName = ref('')
  const deletedConversationList = ref<ConversationItem[]>([])
  const recentlyDeletedConversation = ref<ConversationItem | null>(null)
  const safetyMode = ref<AgentSafetyMode>('STANDARD')
  const safetyModeSaving = ref(false)
  let clearDeletedUndoTimer: ReturnType<typeof setTimeout> | null = null

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
  const updateChatFollowState = () => {
    const container = chatContainer.value
    if (!container) return
    const distanceFromBottom = container.scrollHeight - container.scrollTop - container.clientHeight
    isChatFollowing.value = distanceFromBottom <= 96
  }
  const bindChatContainer = (element: HTMLElement | null) => {
    chatContainer.value?.removeEventListener('scroll', updateChatFollowState)
    chatContainer.value = element
    isChatFollowing.value = true
    element?.addEventListener('scroll', updateChatFollowState, { passive: true })
  }
  const scrollChat = () => {
    const container = chatContainer.value
    if (!container || !isChatFollowing.value) return
    container.scrollTop = container.scrollHeight
  }
  const scrollChatToBottom = () => {
    isChatFollowing.value = true
    const container = chatContainer.value
    if (container) container.scrollTop = container.scrollHeight
  }
  const showScrollToBottom = computed(() => !isChatFollowing.value)

  const loadSafetyMode = async () => {
    try {
      safetyMode.value = (await agentApi.getSafetyMode()).mode
    } catch {
      safetyMode.value = 'STANDARD'
    }
  }
  const saveSafetyMode = async (mode: AgentSafetyMode) => {
    if (safetyModeSaving.value) return
    const previous = safetyMode.value
    safetyMode.value = mode
    safetyModeSaving.value = true
    try {
      safetyMode.value = (await agentApi.saveSafetyMode(mode)).mode
    } catch (error) {
      safetyMode.value = previous
      message.warning(error instanceof Error ? error.message : '安全模式保存失败')
    } finally {
      safetyModeSaving.value = false
    }
  }

  const {
    messages,
    showConversationPanel,
    conversationLoading,
    conversationList,
    runAttempts,
    chatInput,
    skillCatalog,
    activeSkills,
    skillCatalogLoading,
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
    queuedRequest,
    contextUsage,
    loadConversationList,
    loadRunHistory,
    toggleConversationPanel,
    sendMessage,
    cancelCurrentRun,
    retryCurrentRun,
    dispose,
    resumeRunningRun,
    hydrateFromRecoverySnapshot,
    detachCurrentSession,
    activateEmptySession,
    loadSkillCatalog,
    addActiveSkill,
    removeActiveSkill,
    requestContextCompression,
    withdrawQueuedRequest,
    refreshSessionAuxiliary,
  } = useWorkbenchChat({
    getContext: options.getContext,
    getCurrentProjectId: options.getProjectId,
    getActiveChapterKey: options.getActiveChapterKey,
    getAttachedChapterIds: options.getAttachedChapterIds,
    getSelectedText: options.getSelectedText,
    getActivePlugins: () => options.activePlugins.value,
    listSkills: agentApi.listSkills,
    ensureModelConfigId: options.ensureModelConfigId,
    refreshActiveModelInfo: options.refreshActiveModelInfo,
    listSessions: agentApi.listSessions,
    createSession: agentApi.createSession,
    getSessionRecovery: agentApi.getSessionRecovery,
    listSessionRuns: agentApi.listSessionRuns,
    createTurn: async (projectId, sessionId, payload) => {
      debugChatState('create-turn-request', {
        projectId,
        sessionId,
        operatorId: payload.operatorId,
        chapterId: (payload.taskRequest as Record<string, unknown> | undefined)?.chapterId ?? null,
        userMessageLength: String(payload.userMessage || '').length,
      })
      const result = pickBusinessRecord(await agentApi.createTurn(projectId, sessionId, payload))
      syncBoundStyleName(result.session as Record<string, unknown> | null | undefined)
      return result
    },
    getQueuedRequest: agentApi.getQueuedRequest,
    registerQueuedRequest: agentApi.registerQueuedRequest,
    withdrawQueuedRequest: agentApi.withdrawQueuedRequest,
    getSessionTokenUsage: agentApi.getSessionTokenUsage,
    cancelRun: agentApi.cancelRun,
    retryRun: agentApi.retryRun,
    openRunStream: (projectId, runId, after) => {
      const normalizedRunId = String(runId ?? '').trim()
      if (!normalizedRunId) throw new Error('缺少 sessionId，无法打开 turn stream')
      return openRunStream(projectId, normalizedRunId, after)
    },
    addStreamListener: agentApi.addStreamListener,
    scrollChat,
    forceScrollChat: scrollChatToBottom,
    nextTick,
    notifyWarning: (text) => message.warning(text),
    debugChatState,
    onRequireModelSelection: options.requestModelSelection,
    onMessageRegistered: options.onMessageRegistered,
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
      const sessionId = String((normalized.session as Record<string, unknown> | undefined)?.sessionId ?? '')
      const projectId = options.getProjectId()
      if (projectId && sessionId) void loadRunHistory(projectId, sessionId)
      if (projectId && sessionId) void refreshSessionAuxiliary()
      syncBoundStyleName(normalized.session as Record<string, unknown> | null | undefined)
      options.onRecoveryContext((normalized.workbenchContext || {}) as Record<string, unknown>)
    },
  })

  const resumeSession = async (sessionId: string) => {
    const projectId = options.getProjectId()
    const operatorId = options.getOperatorId()
    if (projectId && sessionId && operatorId) {
      detachCurrentSession()
      await recovery.restore(projectId, sessionId, operatorId)
      await loadRunHistory(projectId, sessionId)
      await nextTick()
      scrollChatToBottom()
    }
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
    try {
      const created = pickBusinessRecord(await agentApi.createSession(projectId, { userId: operatorId, title: '新会话' }))
      const sessionId = String(created.sessionId ?? '').trim()
      if (!sessionId) throw new Error('会话创建失败')
      activateEmptySession(sessionId)
      boundStyleName.value = ''
      scrollChatToBottom()
      if (showConversationPanel.value) await loadConversationList(projectId)
    } catch (error) {
      message.warning(error instanceof Error ? error.message : '会话创建失败')
    }
  }
  const resumeLatestSession = async () => {
    const projectId = options.getProjectId()
    if (!projectId) return
    const sessions = await agentApi.listSessions(projectId)
    const latestSessionId = String(sessions[0]?.sessionId ?? '').trim()
    if (latestSessionId) await resumeSession(latestSessionId)
  }

  const normalizeConversation = (item: Record<string, unknown>): ConversationItem => ({
    conversationId: String(item.sessionId ?? ''),
    title: String(item.title ?? ''),
    updatedAt: String(item.updatedAt ?? item.createdAt ?? ''),
    lastMessageAt: String(item.lastMessageAt ?? ''),
    status: String(item.status ?? ''),
    lastRunStatus: String(item.lastRunStatus ?? ''),
    deletedAt: item.deletedAt == null ? null : String(item.deletedAt),
  })
  const loadDeletedConversations = async () => {
    const projectId = options.getProjectId()
    if (!projectId) return
    deletedConversationList.value = (await agentApi.listSessions(projectId, true)).map(normalizeConversation)
  }
  const renameConversation = async (sessionId: string, title: string, deleted = false) => {
    const projectId = options.getProjectId()
    if (!projectId) return
    try {
      await agentApi.renameSession(projectId, sessionId, title)
      if (deleted) await loadDeletedConversations()
      else await loadConversationList(projectId)
    } catch (error) {
      message.warning(error instanceof Error ? error.message : '会话重命名失败')
    }
  }
  const deleteConversation = async (sessionId: string) => {
    const projectId = options.getProjectId()
    if (!projectId) return
    const deleted = conversationList.value.find((item) => item.conversationId === sessionId) ?? null
    try {
      await agentApi.deleteSession(projectId, sessionId)
    } catch (error) {
      message.warning(error instanceof Error ? error.message : '会话删除失败')
      return
    }
    recentlyDeletedConversation.value = deleted
    if (clearDeletedUndoTimer) clearTimeout(clearDeletedUndoTimer)
    clearDeletedUndoTimer = setTimeout(() => { recentlyDeletedConversation.value = null }, 10_000)
    if (currentConversationId.value === sessionId) {
      currentConversationId.value = null
      messages.value = []
      runAttempts.value = []
    }
    await Promise.all([loadConversationList(projectId), loadDeletedConversations()])
  }
  const restoreConversation = async (sessionId: string) => {
    const projectId = options.getProjectId()
    if (!projectId) return
    try {
      await agentApi.restoreDeletedSession(projectId, sessionId)
      await Promise.all([loadConversationList(projectId), loadDeletedConversations()])
      if (recentlyDeletedConversation.value?.conversationId === sessionId) recentlyDeletedConversation.value = null
    } catch (error) {
      message.warning(error instanceof Error ? error.message : '会话恢复失败')
    }
  }
  const disposeAgent = () => {
    if (clearDeletedUndoTimer) clearTimeout(clearDeletedUndoTimer)
    chatContainer.value?.removeEventListener('scroll', updateChatFollowState)
    dispose()
  }

  return {
    messages,
    visibleMessages,
    boundStyleName,
    showConversationPanel,
    conversationLoading,
    conversationList,
    deletedConversationList,
    recentlyDeletedConversation,
    runAttempts,
    chatInput,
    skillCatalog,
    activeSkills,
    skillCatalogLoading,
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
    queuedRequest,
    contextUsage,
    safetyMode,
    safetyModeSaving,
    toggleConversationPanel,
    loadDeletedConversations,
    renameConversation,
    deleteConversation,
    restoreConversation,
    sendMessage,
    cancelCurrentRun,
    retryCurrentRun,
    loadSkillCatalog,
    addActiveSkill,
    removeActiveSkill,
    requestContextCompression,
    withdrawQueuedRequest,
    loadSafetyMode,
    saveSafetyMode,
    isApprovalBusy,
    handleApprove,
    handleReject,
    bindChatContainer,
    showScrollToBottom,
    scrollChatToBottom,
    resumeSession,
    selectConversation,
    createSession,
    resumeLatestSession,
    dispose: disposeAgent,
  }
}
