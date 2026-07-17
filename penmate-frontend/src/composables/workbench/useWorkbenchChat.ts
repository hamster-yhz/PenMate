import { computed, ref } from 'vue'
import type { WorkbenchRuntimeEventSource } from '@/api/types'
import type { ChatMessage, ConversationItem, GenerationPhase } from '@/components/workbench/workbenchTypes'
import {
  applyAssistantEventMetadata,
  createChatTimeline,
  escapeHtml,
  type ChatRecord,
} from './useWorkbenchChatTimeline'
import {
  createAgentRunRuntime,
  normalizeRunStatus,
  type AgentRunStatus,
} from './useAgentRunRuntime'
import { pickBusinessRecord } from '@/utils/apiPayload'

type ContextProfile = {
  projectId?: string | null
  operatorId?: string | null
}

type StreamListener = (event: MessageEvent<string>) => void

type UseWorkbenchChatDeps = {
  getContext: () => ContextProfile
  getCurrentProjectId: () => string
  getActiveChapterKey: () => string
  getSelectedText: () => string
  getActivePlugins: () => string[]
  ensureModelConfigId: (projectId: string) => Promise<string | null>
  refreshActiveModelInfo?: (projectId: string) => Promise<string | null | void>
  listSessions: (projectId: string) => Promise<unknown>
  createSession: (projectId: string, payload: Record<string, unknown>) => Promise<unknown>
  getSessionRecovery: (projectId: string, sessionId: string) => Promise<unknown>
  createTurn: (projectId: string, sessionId: string, payload: Record<string, unknown>) => Promise<unknown>
  openRunStream: (projectId: string, runId: string, after?: string) => EventSource
  addStreamListener: (stream: EventSource, eventName: string, listener: StreamListener) => void
  closeRunStream?: (stream: EventSource | null) => void
  revealAssistantText?: (assistantMsg: ChatMessage, rawText: string) => Promise<void>
  scrollChat: () => void
  nextTick: () => Promise<void>
  notifyWarning?: (message: string) => void
  debugChatState?: (stage: string, extra?: Record<string, unknown>) => void
  onRequireModelSelection?: () => void
}

export const useWorkbenchChat = (deps: UseWorkbenchChatDeps) => {
  const messages = ref<ChatMessage[]>([])
  const showConversationPanel = ref(false)
  const conversationLoading = ref(false)
  const conversationList = ref<ConversationItem[]>([])
  const chatInput = ref('')
  const isGenerating = ref(false)
  const generationPhase = ref<GenerationPhase>('idle')
  const generationTaskStatus = ref<AgentRunStatus | ''>('')
  const agentStatusDetailText = ref('')
  const streamingAssistantMsgId = ref<string | number | null>(null)
  const runtimeEventSource = ref<WorkbenchRuntimeEventSource | null>(null)
  const currentConversationId = ref<string | null>(null)
  const preferredConversationId = ref<string | null>(null)
  const currentModelName = ref('')
  const currentActiveRun = ref<{ sessionId: string | null; runId: string | null; latestSequence: string }>({
    sessionId: null,
    runId: null,
    latestSequence: '0',
  })
  const recoveredSelectedText = ref('')

  let msgIdCounter = 1
  let runStream: EventSource | null = null

  const generationStatusText = computed(() => {
    if (isGenerating.value && generationTaskStatus.value) return `运行中 · ${generationTaskStatus.value}`
    if (generationPhase.value === 'preparing') return '准备中'
    if (generationPhase.value === 'streaming') return '生成中'
    if (generationPhase.value === 'waiting_approval') return '等待审批'
    if (generationPhase.value === 'failed') return '异常'
    return '就绪'
  })

  const scrollChat = async () => {
    await deps.nextTick()
    deps.scrollChat()
  }

  const debugChatState = (stage: string, extra: Record<string, unknown> = {}) => {
    deps.debugChatState?.(stage, {
      isGenerating: isGenerating.value,
      generationPhase: generationPhase.value,
      generationTaskStatus: generationTaskStatus.value,
      messageCount: messages.value.length,
      ...extra,
    })
  }

  const listSessions = (projectId: string) => deps.listSessions(projectId)

  const getSessionRecovery = async (projectId: string, sessionId: string) => {
    return pickBusinessRecord(await deps.getSessionRecovery(projectId, sessionId))
  }

  const createTurn = (projectId: string, sessionId: string, payload: Record<string, unknown>) => deps.createTurn(projectId, sessionId, payload)

  const normalizeSessionRecord = (item: ChatRecord) => ({
    ...item,
    conversationId: String(item.sessionId ?? ''),
    title: String(item.title ?? item.sessionTitle ?? ''),
    updatedAt: String(item.updatedAt ?? item.resumedAt ?? item.createdAt ?? ''),
  })

  const timeline = createChatTimeline({
    getMessages: () => messages.value,
    setMessages: (value) => {
      messages.value = value
    },
    getMsgIdCounter: () => msgIdCounter,
    setMsgIdCounter: (value) => {
      msgIdCounter = value
    },
    listConversations: async (projectId: string) => {
      const sessions = (await listSessions(projectId)) as Array<ChatRecord>
      return (Array.isArray(sessions) ? sessions : []).map(normalizeSessionRecord)
    },
    listMessages: async () => [],
    setConversationList: (items) => {
      conversationList.value = items
    },
    setConversationLoading: (value) => {
      conversationLoading.value = value
    },
    setCurrentConversationId: (value) => {
      currentConversationId.value = value == null ? null : String(value)
    },
    scrollChat,
  })

  const runtime = createAgentRunRuntime({
    getRunStatus: () => generationTaskStatus.value,
    setRunStatus: (value: AgentRunStatus | '') => {
      generationTaskStatus.value = value
    },
    setAgentStatusDetailText: (value: string) => {
      agentStatusDetailText.value = value
    },
    getRunPhase: () => generationPhase.value,
    setRunPhase: (value: GenerationPhase) => {
      generationPhase.value = value
    },
    getRunStream: () => runStream,
    setRunStream: (stream: EventSource | null) => {
      runStream = stream
    },
    openRunStream: deps.openRunStream,
    addStreamListener: deps.addStreamListener,
    closeRunStream: deps.closeRunStream,
    scrollChat: deps.scrollChat,
    setRuntimeEventSource: (value) => {
      runtimeEventSource.value = value
    },
    setLatestSequence: (value) => {
      currentActiveRun.value.latestSequence = value
    },
    onToken: (token) => {
      const assistantMsg = messages.value.find((item) => String(item.id) === String(streamingAssistantMsgId.value))
      if (!assistantMsg) return
      assistantMsg.text += escapeHtml(token)
    },
    onMessageCompleted: (text) => {
      const assistantMsg = messages.value.find((item) => String(item.id) === String(streamingAssistantMsgId.value))
      if (!assistantMsg) return
      assistantMsg.text = escapeHtml(text)
    },
    onToolCall: (payload) => {
      const assistantMsg = messages.value.find((item) => String(item.id) === String(streamingAssistantMsgId.value))
      if (!assistantMsg) return
      applyAssistantEventMetadata(assistantMsg, payload as ChatRecord)
    },
    onWaitingApproval: (payload) => {
      const assistantMsg = messages.value.find((item) => String(item.id) === String(streamingAssistantMsgId.value))
      if (!assistantMsg) return
      applyAssistantEventMetadata(assistantMsg, payload as ChatRecord)
    },
    onStreamReset: async () => {
      const projectId = deps.getCurrentProjectId()
      const sessionId = currentActiveRun.value.sessionId || currentConversationId.value || ''
      if (!projectId || !sessionId) return ''
      const snapshot = await getSessionRecovery(projectId, sessionId) as Record<string, any>
      hydrateFromRecoverySnapshot(snapshot)
      return normalizeRunStatus(snapshot?.activeRun?.runStatus ?? snapshot?.session?.lastRunStatus)
    },
  })

  const loadConversationList = async (projectId: string) => {
    await timeline.loadConversationList(projectId)
  }

  const resolveCurrentSessionId = async (projectId: string) => {
    if (currentConversationId.value) return currentConversationId.value
    if (preferredConversationId.value) {
      currentConversationId.value = preferredConversationId.value
      return preferredConversationId.value
    }
    const sessions = (await listSessions(projectId)) as Array<ChatRecord>
    const latestSessionId = String((Array.isArray(sessions) ? sessions[0] : null)?.sessionId ?? '')
    if (latestSessionId) {
      currentConversationId.value = latestSessionId
      preferredConversationId.value = latestSessionId
      return latestSessionId
    }
    return null
  }

  const createSessionForSend = async (projectId: string, operatorId: string) => {
    const created = pickBusinessRecord(await deps.createSession(projectId, {
      userId: operatorId,
      title: '新会话',
    })) as ChatRecord
    const sessionId = String(created?.sessionId ?? '')
    if (!sessionId) return null
    currentConversationId.value = sessionId
    preferredConversationId.value = sessionId
    if (showConversationPanel.value) {
      await loadConversationList(projectId)
    }
    return sessionId
  }

  const ensureSessionIdForSend = async (projectId: string, operatorId: string) => {
    return (await resolveCurrentSessionId(projectId)) || createSessionForSend(projectId, operatorId)
  }

  function hydrateFromRecoverySnapshot(snapshot: Record<string, any> | null | undefined) {
    runtimeEventSource.value = null
    const normalizedSnapshot = pickBusinessRecord(snapshot) as {
      session?: { sessionId?: string | number | null }
      activeRun?: {
        runId?: string | number | null
        runStatus?: string | null
        runPhase?: string | null
        latestSequence?: string | number | null
      }
      workbenchContext?: { selectedText?: string | null } | null
      messages?: Array<Record<string, unknown>>
    }
    const recoveryMessages = Array.isArray(normalizedSnapshot.messages) ? normalizedSnapshot.messages : []
    messages.value = recoveryMessages.map((item) => timeline.mapApiMessage(item as ChatRecord))
    currentConversationId.value = normalizedSnapshot?.session?.sessionId == null ? null : String(normalizedSnapshot.session.sessionId)
    preferredConversationId.value = currentConversationId.value
    recoveredSelectedText.value = String(normalizedSnapshot?.workbenchContext?.selectedText ?? '')
    currentActiveRun.value = {
      sessionId: currentConversationId.value,
      runId: normalizedSnapshot?.activeRun?.runId == null ? null : String(normalizedSnapshot.activeRun.runId),
      latestSequence: String(normalizedSnapshot?.activeRun?.latestSequence ?? '0'),
    }
    const runStatus = normalizeRunStatus(normalizedSnapshot?.activeRun?.runStatus)
    if (runStatus === 'waiting_approval') {
      generationPhase.value = 'waiting_approval'
      generationTaskStatus.value = 'waiting_approval'
      isGenerating.value = false
      streamingAssistantMsgId.value = null
      return
    }
    if (runStatus === 'running') {
      generationPhase.value = 'idle'
      generationTaskStatus.value = ''
      isGenerating.value = true
      return
    }
    generationPhase.value = 'idle'
    generationTaskStatus.value = ''
    agentStatusDetailText.value = ''
    isGenerating.value = false
    streamingAssistantMsgId.value = null
  }

  const selectConversation = async (conversationId: string) => {
    const projectId = deps.getCurrentProjectId()
    if (!projectId || !conversationId) return
    hydrateFromRecoverySnapshot((await getSessionRecovery(projectId, conversationId) || null) as Record<string, any> | null)
  }

  const toggleConversationPanel = async () => {
    showConversationPanel.value = !showConversationPanel.value
    if (!showConversationPanel.value) return
    const projectId = deps.getCurrentProjectId()
    if (projectId) await loadConversationList(projectId)
  }

  const loadConversationHistory = async (projectId: string, operatorId: string) => {
    if (!projectId || !operatorId) {
      messages.value = []
      currentConversationId.value = null
      return
    }
    try {
      const sessions = (await listSessions(projectId)) as Array<ChatRecord>
      const latestSessionId = String((Array.isArray(sessions) ? sessions[0] : null)?.sessionId ?? '')
      if (!latestSessionId) {
        messages.value = []
        currentConversationId.value = null
        preferredConversationId.value = null
        return
      }
      hydrateFromRecoverySnapshot((await getSessionRecovery(projectId, latestSessionId) || null) as Record<string, any> | null)
      await loadConversationList(projectId)
    } catch {
      messages.value = []
      currentConversationId.value = null
      preferredConversationId.value = null
    }
  }

  const recoverAssistantTextFromSession = async (projectId: string, sessionId: string) => {
    try {
      const snapshot = pickBusinessRecord(await getSessionRecovery(projectId, sessionId)) as { messages?: Array<Record<string, unknown>> }
      const mappedMessages = (Array.isArray(snapshot?.messages) ? snapshot.messages : []).map((item) => timeline.mapApiMessage(item as ChatRecord))
      return [...mappedMessages].reverse().find((item) => item.role === 'assistant' && String(item.text || '').trim())?.text || ''
    } catch {
      return ''
    }
  }

  const resolveAssistantMessageForResume = (): ChatMessage => {
    const existingAssistant = [...messages.value].reverse().find((item) => item.role === 'assistant' && !String(item.text || '').trim() && !item.approval && !item.toolCallId) || null
    if (existingAssistant) return existingAssistant
    const assistantMsg: ChatMessage = { id: msgIdCounter++, role: 'assistant', text: '' }
    messages.value.push(assistantMsg)
    return assistantMsg
  }

  const consumeRun = async (projectId: string, sessionId: string, runId: string, after = '0') => {
    const assistantMsg = resolveAssistantMessageForResume()
    isGenerating.value = true
    generationPhase.value = 'idle'
    generationTaskStatus.value = ''
    agentStatusDetailText.value = ''
    streamingAssistantMsgId.value = assistantMsg.id
    currentActiveRun.value = { sessionId, runId, latestSequence: after }
    await scrollChat()
    try {
      const finalStatus = await runtime.consumeRunStream(projectId, runId, after)
      if (finalStatus === 'failed' || finalStatus === 'cancelled' || finalStatus === 'superseded') {
        throw new Error(`运行结束: ${finalStatus}`)
      }
      const hasPendingApproval = !!assistantMsg.approval && !assistantMsg.approval?.resolved
      if (hasPendingApproval) {
        generationPhase.value = 'waiting_approval'
        generationTaskStatus.value = 'waiting_approval'
      }
      if (!assistantMsg.text && !hasPendingApproval) {
        assistantMsg.text = await recoverAssistantTextFromSession(projectId, sessionId)
      }
    } catch (error: any) {
      generationPhase.value = 'failed'
      generationTaskStatus.value = 'failed'
      const resolvedErrorMessage = runtime.getErrorMessage(error)
      agentStatusDetailText.value = resolvedErrorMessage
      runtimeEventSource.value = {
        eventName: 'run.failed',
        phase: 'failed',
        message: '执行失败',
        errorMsg: resolvedErrorMessage,
        nextAction: 'retry_run',
        recoverable: true,
      }
      const failureText = `运行失败: ${resolvedErrorMessage}`
      assistantMsg.text = assistantMsg.text ? `${assistantMsg.text}\n\n${failureText}` : failureText
    } finally {
      runtime.closeRunStream()
      isGenerating.value = false
      streamingAssistantMsgId.value = null
      if (generationPhase.value !== 'failed' && generationPhase.value !== 'waiting_approval') {
        generationPhase.value = 'idle'
        generationTaskStatus.value = ''
        agentStatusDetailText.value = ''
      }
      await scrollChat()
    }
  }

  const sendMessage = async () => {
    if (!chatInput.value.trim() || isGenerating.value) return
    const userText = chatInput.value.trim()
    messages.value.push({ id: msgIdCounter++, role: 'user', text: escapeHtml(userText) })
    chatInput.value = ''
    runtimeEventSource.value = null
    debugChatState('user-send-start', { userTextLength: userText.length })
    await scrollChat()

    const { projectId, operatorId } = deps.getContext()
    if (!projectId || !operatorId) {
      messages.value.push({ id: msgIdCounter++, role: 'assistant', text: '缺少 projectId/operatorId，无法发送。' })
      return
    }

    try {
      const sessionId = await ensureSessionIdForSend(projectId, operatorId)
      if (!sessionId) throw new Error('会话初始化失败')
      const modelConfigId = await deps.ensureModelConfigId(projectId)
      if (!modelConfigId) {
        deps.onRequireModelSelection?.()
        throw new Error('未选择可用模型，请先保存并切换模型')
      }
      const selectedText = String(deps.getSelectedText?.() ?? '').trim() || recoveredSelectedText.value
      const created = pickBusinessRecord(await createTurn(projectId, sessionId, {
        operatorId,
        userMessage: userText,
        taskRequest: {
          taskType: 'WRITE',
          chapterId: deps.getActiveChapterKey() || null,
          selectedText,
          modelConfigId,
          activePlugins: deps.getActivePlugins() || [],
        },
      })) as ChatRecord & {
        activeRun?: {
          runId?: string | number | null
          latestSequence?: string | number | null
        }
      }
      const runId = created.activeRun?.runId
      if (runId == null || String(runId).trim() === '' || String(runId) === '0') {
        throw new Error('运行创建失败，缺少 runId')
      }
      await consumeRun(projectId, sessionId, String(runId), String(created.activeRun?.latestSequence ?? '0'))
    } catch (error: any) {
      generationPhase.value = 'failed'
      generationTaskStatus.value = 'failed'
      const resolvedErrorMessage = runtime.getErrorMessage(error)
      agentStatusDetailText.value = resolvedErrorMessage
      messages.value.push({ id: msgIdCounter++, role: 'assistant', text: `运行失败: ${resolvedErrorMessage}` })
      await scrollChat()
    }
  }

  const resumeRunningRun = async (projectId: string, runId: string, after = '0') => {
    const sessionId = currentActiveRun.value.sessionId || currentConversationId.value || ''
    await consumeRun(projectId, sessionId, runId, after)
  }

  return {
    messages,
    showConversationPanel,
    conversationLoading,
    conversationList,
    chatInput,
    isGenerating,
    generationPhase,
    generationTaskStatus,
    generationStatusText,
    agentStatusDetailText,
    streamingAssistantMsgId,
    runtimeEventSource,
    currentConversationId,
    currentModelName,
    loadConversationList,
    loadConversationHistory,
    selectConversation,
    toggleConversationPanel,
    sendMessage,
    resumeRunningRun,
    consumeRunStream: runtime.consumeRunStream,
    scrollChat,
    applyAssistantEventMetadata,
    hydrateFromRecoverySnapshot,
  }
}
