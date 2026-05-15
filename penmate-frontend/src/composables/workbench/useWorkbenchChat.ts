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
  createTaskRuntime,
  normalizeGenerationStatus,
  type GenerationTaskStatus,
} from './useWorkbenchTaskRuntime'
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
  openTurnStream: (projectId: string, sessionId: string, turnId: string) => EventSource
  addStreamListener: (stream: EventSource, eventName: string, listener: StreamListener) => void
  closeTaskStream?: (stream: EventSource | null) => void
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
  const generationTaskStatus = ref<GenerationTaskStatus | ''>('')
  const agentStatusDetailText = ref('')
  const streamingAssistantMsgId = ref<string | number | null>(null)
  const runtimeEventSource = ref<WorkbenchRuntimeEventSource | null>(null)
  const currentConversationId = ref<string | null>(null)
  const preferredConversationId = ref<string | null>(null)
  const currentModelName = ref('')
  const currentActiveTask = ref<{ sessionId: string | null; turnId: string | null; taskId: string | null }>({
    sessionId: null,
    turnId: null,
    taskId: null,
  })
  const recoveredSelectedText = ref('')

  let msgIdCounter = 1
  let generationStream: EventSource | null = null

  const generationStatusText = computed(() => {
    if (isGenerating.value && generationTaskStatus.value) return `生成中 · ${generationTaskStatus.value}`
    if (generationPhase.value === 'preparing') return '准备中'
    if (generationPhase.value === 'streaming') return '流式生成中'
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
      lastMessageRole: messages.value[messages.value.length - 1]?.role || '',
      lastMessageLength: messages.value[messages.value.length - 1]?.text?.length || 0,
      ...extra,
    })
  }

  const listSessions = (projectId: string) => deps.listSessions(projectId)

  const getSessionRecovery = async (projectId: string, sessionId: string) => {
    return pickBusinessRecord(await deps.getSessionRecovery(projectId, sessionId))
  }

  const createTurn = (projectId: string, sessionId: string, payload: Record<string, unknown>) => deps.createTurn(projectId, sessionId, payload)

  const openTurnStream = (projectId: string, sessionId: string, turnId: string) => deps.openTurnStream(projectId, sessionId, turnId)

  const closeTaskStream = deps.closeTaskStream

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
    listMessages: async () => {
      return []
    },
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

  const runtime = createTaskRuntime({
    getGenerationTaskStatus: () => generationTaskStatus.value,
    setGenerationTaskStatus: (value: GenerationTaskStatus | '') => {
      generationTaskStatus.value = value
    },
    setAgentStatusDetailText: (value: string) => {
      agentStatusDetailText.value = value
    },
    getGenerationPhase: () => generationPhase.value,
    setGenerationPhase: (value: GenerationPhase) => {
      generationPhase.value = value
    },
    getGenerationStream: () => generationStream,
    setGenerationStream: (stream: EventSource | null) => {
      generationStream = stream
    },
    openGenerationStream: openTurnStream,
    addStreamListener: deps.addStreamListener,
    closeGenerationStream: closeTaskStream,
    scrollChat: deps.scrollChat,
    setRuntimeEventSource: (value) => {
      runtimeEventSource.value = value
    },
    onToken: (token) => {
      const assistantMsg = messages.value.find((item) => String(item.id) === String(streamingAssistantMsgId.value))
      if (!assistantMsg) return
      assistantMsg.text += escapeHtml(token)
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
  })

  const loadConversationList = async (projectId: string) => {
    await timeline.loadConversationList(projectId)
  }

  const resolveCurrentSessionId = async (projectId: string) => {
    if (currentConversationId.value) {
      return currentConversationId.value
    }
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
    if (sessionId) {
      currentConversationId.value = sessionId
      preferredConversationId.value = sessionId
      if (showConversationPanel.value) {
        await loadConversationList(projectId)
      }
      return sessionId
    }
    return null
  }

  const ensureSessionIdForSend = async (projectId: string, operatorId: string) => {
    const existingSessionId = await resolveCurrentSessionId(projectId)
    if (existingSessionId) {
      return existingSessionId
    }
    return createSessionForSend(projectId, operatorId)
  }

  const selectConversation = async (conversationId: string) => {
    const projectId = deps.getCurrentProjectId()
    if (!projectId || !conversationId) return
    const snapshot = await getSessionRecovery(projectId, conversationId)
    hydrateFromRecoverySnapshot((snapshot || null) as Record<string, any> | null)
  }

  const toggleConversationPanel = async () => {
    showConversationPanel.value = !showConversationPanel.value
    if (!showConversationPanel.value) return
    const projectId = deps.getCurrentProjectId()
    if (!projectId) return
    await loadConversationList(projectId)
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
      const snapshot = await getSessionRecovery(projectId, latestSessionId)
      hydrateFromRecoverySnapshot((snapshot || null) as Record<string, any> | null)
      await loadConversationList(projectId)
    } catch {
      messages.value = []
      currentConversationId.value = null
      preferredConversationId.value = null
    }
  }

  const sendMessage = async () => {
    if (!chatInput.value.trim() || isGenerating.value) return

    const userText = chatInput.value.trim()
    messages.value.push({ id: msgIdCounter++, role: 'user', text: escapeHtml(userText) })
    chatInput.value = ''
    isGenerating.value = true
    generationPhase.value = 'preparing'
    generationTaskStatus.value = ''
    agentStatusDetailText.value = ''
    runtimeEventSource.value = null
    debugChatState('user-send-start', { userTextLength: userText.length })
    await scrollChat()

    const { projectId, operatorId } = deps.getContext()
    if (!projectId || !operatorId) {
      messages.value.push({
        id: msgIdCounter++,
        role: 'assistant',
        text: '缺少 projectId/operatorId，当前仅可本地预览消息。',
      })
      isGenerating.value = false
      generationPhase.value = 'idle'
      generationTaskStatus.value = ''
      await scrollChat()
      return
    }

    let assistantMsg: ChatMessage | null = null

    try {
      const conversationId = await ensureSessionIdForSend(projectId, operatorId)
      if (!conversationId) throw new Error('会话初始化失败')

      const modelConfigId = await deps.ensureModelConfigId(projectId)
      if (!modelConfigId) {
        deps.onRequireModelSelection?.()
        throw new Error('未选择可用模型，请先在模型设置中保存并切换模型')
      }

      const selectedText = String(deps.getSelectedText?.() ?? '').trim() || recoveredSelectedText.value
      const generation = pickBusinessRecord(await createTurn(projectId, conversationId, {
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
        session?: { sessionId?: string | number | null }
        activeTask?: {
          turnId?: string | number | null
          taskId?: string | number | null
          taskStatus?: string | null
        }
      }

      const taskId = generation.activeTask?.taskId ?? generation.taskId
      const turnId = generation.activeTask?.turnId
      if (turnId == null || String(turnId).trim() === '' || String(turnId) === '0') {
        throw new Error('任务创建失败，缺少 turnId')
      }
      if (taskId == null || String(taskId).trim() === '' || String(taskId) === '0') {
        throw new Error('任务创建失败，缺少 taskId')
      }
      currentActiveTask.value = {
        sessionId: conversationId,
        turnId: String(turnId),
        taskId: String(taskId),
      }

      generationTaskStatus.value = normalizeGenerationStatus(generation.activeTask?.taskStatus ?? generation.status) || 'pending'
      generationPhase.value = 'streaming'

      assistantMsg = { id: msgIdCounter++, role: 'assistant', text: '' }
      messages.value.push(assistantMsg)
      streamingAssistantMsgId.value = assistantMsg.id
      await scrollChat()

      const finalStatus = await runtime.consumeGenerationStream(projectId, conversationId, String(turnId))

      if (finalStatus === 'failed' || finalStatus === 'cancelled') {
        throw new Error(`生成任务结束：${finalStatus}`)
      }

      const assistantState = assistantMsg as ChatMessage
      const hasPendingApproval = !!assistantState.approval && !assistantState.approval?.resolved
      if (hasPendingApproval) {
        generationPhase.value = 'waiting_approval'
        generationTaskStatus.value = 'waiting_approval'
      }
      if (!assistantMsg.text && !hasPendingApproval) {
        assistantMsg.text = `生成任务已完成，状态：${finalStatus || generationTaskStatus.value || 'unknown'}`
      }
    } catch (error: any) {
      generationPhase.value = 'failed'
      generationTaskStatus.value = 'failed'
      agentStatusDetailText.value = runtime.getErrorMessage(error)
      runtimeEventSource.value = {
        eventName: 'generation.failed',
        phase: 'failed',
        message: '执行失败',
        errorMsg: runtime.getErrorMessage(error),
        nextAction: 'retry_generation',
        recoverable: true,
      }
      const failureText = `生成失败：${runtime.getErrorMessage(error)}`
      if (assistantMsg) {
        assistantMsg.text = assistantMsg.text ? `${assistantMsg.text}\n\n${failureText}` : failureText
      } else {
        messages.value.push({
          id: msgIdCounter++,
          role: 'assistant',
          text: failureText,
        })
      }
    } finally {
      runtime.closeGenerationStream()
      isGenerating.value = false
      streamingAssistantMsgId.value = null
      if (generationPhase.value !== 'failed' && generationPhase.value !== 'waiting_approval') {
        generationPhase.value = 'idle'
        generationTaskStatus.value = ''
        agentStatusDetailText.value = ''
      }
      debugChatState('send-flow-finished')
      await scrollChat()
    }
  }

  const hydrateFromRecoverySnapshot = (snapshot: Record<string, any> | null | undefined) => {
    runtimeEventSource.value = null
    const normalizedSnapshot = pickBusinessRecord(snapshot) as {
      session?: { sessionId?: string | number | null }
      activeTask?: {
        turnId?: string | number | null
        taskId?: string | number | null
        taskStatus?: string | null
      }
      workbenchContext?: {
        selectedText?: string | null
      } | null
      messages?: Array<Record<string, unknown>>
    }
    const recoveryMessages = Array.isArray(normalizedSnapshot.messages) ? normalizedSnapshot.messages : []
    const mappedMessages = recoveryMessages.map((item) => timeline.mapApiMessage(item as ChatRecord))
    messages.value = mappedMessages
    const maxId = mappedMessages.reduce((max, item) => {
      const numericId = Number(item.id)
      return Number.isFinite(numericId) && numericId > max ? numericId : max
    }, 0)
    if (maxId >= msgIdCounter) {
      msgIdCounter = maxId + 1
    }

    currentConversationId.value = normalizedSnapshot?.session?.sessionId == null ? null : String(normalizedSnapshot.session.sessionId)
    preferredConversationId.value = currentConversationId.value
    recoveredSelectedText.value = String(normalizedSnapshot?.workbenchContext?.selectedText ?? '')
    currentActiveTask.value = {
      sessionId: currentConversationId.value,
      turnId: normalizedSnapshot?.activeTask?.turnId == null ? null : String(normalizedSnapshot.activeTask.turnId),
      taskId: normalizedSnapshot?.activeTask?.taskId == null ? null : String(normalizedSnapshot.activeTask.taskId),
    }

    const taskStatus = normalizeGenerationStatus(normalizedSnapshot?.activeTask?.taskStatus)
    if (taskStatus === 'waiting_approval') {
      generationPhase.value = 'waiting_approval'
      generationTaskStatus.value = 'waiting_approval'
      agentStatusDetailText.value = ''
      isGenerating.value = false
      streamingAssistantMsgId.value = null
      return
    }
    if (taskStatus === 'running') {
      generationPhase.value = 'streaming'
      generationTaskStatus.value = 'running'
      agentStatusDetailText.value = ''
      isGenerating.value = true
      return
    }
    generationPhase.value = 'idle'
    generationTaskStatus.value = ''
    agentStatusDetailText.value = ''
    isGenerating.value = false
    streamingAssistantMsgId.value = null
  }

  const resolveAssistantMessageForResume = () => {
    const existingAssistant = [...messages.value].reverse().find((item) => {
      if (item.role !== 'assistant') {
        return false
      }
      const hasText = String(item.text || '').trim().length > 0
      const hasApproval = !!item.approval
      const hasToolCallBinding = !!item.toolCallId
      return !hasText && !hasApproval && !hasToolCallBinding
    }) || null
    if (existingAssistant) {
      return existingAssistant
    }
    const assistantMsg = { id: msgIdCounter++, role: 'assistant' as const, text: '' }
    messages.value.push(assistantMsg)
    return assistantMsg
  }

  const resumeRunningTask = async (projectId: string, sessionId: string, turnId: string) => {
    const assistantMsg = resolveAssistantMessageForResume()
    isGenerating.value = true
    generationPhase.value = 'streaming'
    generationTaskStatus.value = 'running'
    agentStatusDetailText.value = ''
    streamingAssistantMsgId.value = assistantMsg.id
    debugChatState('resume-stream-start', { projectId, sessionId, turnId })
    await scrollChat()

    try {
      const finalStatus = await runtime.consumeGenerationStream(projectId, sessionId, turnId)

      if (finalStatus === 'failed' || finalStatus === 'cancelled') {
        throw new Error(`生成任务结束：${finalStatus}`)
      }

      const assistantState = assistantMsg as ChatMessage
      const hasPendingApproval = !!assistantState.approval && !assistantState.approval?.resolved
      if (hasPendingApproval) {
        generationPhase.value = 'waiting_approval'
        generationTaskStatus.value = 'waiting_approval'
      }
      if (!assistantMsg.text && !hasPendingApproval) {
        assistantMsg.text = `生成任务已完成，状态：${finalStatus || generationTaskStatus.value || 'unknown'}`
      }
    } catch (error: any) {
      generationPhase.value = 'failed'
      generationTaskStatus.value = 'failed'
      agentStatusDetailText.value = runtime.getErrorMessage(error)
      runtimeEventSource.value = {
        eventName: 'generation.failed',
        phase: 'failed',
        message: '执行失败',
        errorMsg: runtime.getErrorMessage(error),
        nextAction: 'retry_generation',
        recoverable: true,
      }
      const failureText = `生成失败：${runtime.getErrorMessage(error)}`
      assistantMsg.text = assistantMsg.text ? `${assistantMsg.text}\n\n${failureText}` : failureText
    } finally {
      runtime.closeGenerationStream()
      isGenerating.value = false
      streamingAssistantMsgId.value = null
      if (generationPhase.value !== 'failed' && generationPhase.value !== 'waiting_approval') {
        generationPhase.value = 'idle'
        generationTaskStatus.value = ''
        agentStatusDetailText.value = ''
      }
      debugChatState('resume-stream-finished', { projectId, sessionId, turnId })
      await scrollChat()
    }
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
    resumeRunningTask,
    consumeGenerationStream: runtime.consumeGenerationStream,
    scrollChat,
    applyAssistantEventMetadata,
    hydrateFromRecoverySnapshot,
  }
}
