import { computed, ref } from 'vue'
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
  getActivePlugins: () => string[]
  ensureModelConfigId: (projectId: string) => Promise<string | null>
  refreshActiveModelInfo?: (projectId: string) => Promise<string | null | void>
  listSessions: (projectId: string) => Promise<unknown>
  createSession: (projectId: string, payload: Record<string, unknown>) => Promise<unknown>
  getSessionRecovery: (projectId: string, sessionId: string) => Promise<unknown>
  createTurn: (projectId: string, sessionId: string, payload: Record<string, unknown>) => Promise<unknown>
  getTask: (projectId: string, taskId: string) => Promise<unknown>
  openTurnStream: (projectId: string, sessionId: string, turnId: string) => EventSource
  addStreamListener: (stream: EventSource, eventName: string, listener: StreamListener) => void
  closeTaskStream?: (stream: EventSource | null) => void
  revealAssistantText?: (assistantMsg: ChatMessage, rawText: string) => Promise<void>
  scrollChat: () => void
  nextTick: () => Promise<void>
  waitForPolling?: () => Promise<void>
  notifyWarning?: (message: string) => void
  debugChatState?: (stage: string, extra?: Record<string, unknown>) => void
  onRequireModelSelection?: () => void
  enablePollingFallback?: boolean
}

export const useWorkbenchChat = (deps: UseWorkbenchChatDeps) => {
  /**
   * Chat shell 的单一状态源。
   * <p>这里统一承载消息列表、历史会话面板、输入框和运行时状态，
   * 避免父组件再额外拼装一层“派生 chat 状态”导致恢复/续流错位。</p>
   */
  const messages = ref<ChatMessage[]>([])
  const showConversationPanel = ref(false)
  const conversationLoading = ref(false)
  const conversationList = ref<ConversationItem[]>([])
  const chatInput = ref('')
  const isGenerating = ref(false)
  const generationPhase = ref<GenerationPhase>('idle')
  const generationTaskStatus = ref<GenerationTaskStatus | ''>('')
  const streamingAssistantMsgId = ref<string | number | null>(null)
  const currentConversationId = ref<string | null>(null)
  const preferredConversationId = ref<string | null>(null)
  const currentModelName = ref('')
  const currentActiveTask = ref<{ sessionId: string | null; turnId: string | null; taskId: string | null }>({
    sessionId: null,
    turnId: null,
    taskId: null,
  })

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

  const getTask = (projectId: string, taskId: string) => deps.getTask(projectId, taskId)

  const openTurnStream = (projectId: string, sessionId: string, turnId: string) => deps.openTurnStream(projectId, sessionId, turnId)

  const closeTaskStream = deps.closeTaskStream

  const normalizeSessionRecord = (item: ChatRecord) => ({
    ...item,
    conversationId: String(item.sessionId ?? ''),
    title: String(item.title ?? item.sessionTitle ?? ''),
    updatedAt: String(item.updatedAt ?? item.resumedAt ?? item.createdAt ?? ''),
  })

  /**
   * timeline 只负责“消息/历史列表”投影，不直接接触任务流状态。
   * 这样可以把列表恢复与流式任务恢复拆开，降低 WAITING_APPROVAL / RUNNING 切换时的耦合复杂度。
   */
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

  /**
   * runtime 负责当前 task 的单流生命周期管理。
   * <p>恢复会话、重新发送消息、审批继续执行最终都会汇聚到这里，
   * 从而避免同一会话被重复订阅造成双流输出。</p>
   */
  const runtime = createTaskRuntime({
    getGenerationTaskStatus: () => generationTaskStatus.value,
    setGenerationTaskStatus: (value: GenerationTaskStatus | '') => {
      generationTaskStatus.value = value
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
    getGeneration: getTask,
    waitForPolling: deps.waitForPolling,
    scrollChat: deps.scrollChat,
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

      const generation = pickBusinessRecord(await createTurn(projectId, conversationId, {
        operatorId,
        userMessage: userText,
        taskRequest: {
          taskType: 'WRITE',
          chapterId: deps.getActiveChapterKey() || null,
          selectedText: '',
          modelConfigId,
          activePlugins: deps.getActivePlugins() || [],
        },
      })) as ChatRecord & { activeTask?: { taskId?: string; taskStatus?: string } }

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

      let finalStatus: GenerationTaskStatus | ''
      try {
        finalStatus = await runtime.consumeGenerationStream(projectId, conversationId, String(turnId), assistantMsg)
      } catch (streamError: any) {
        if (!deps.enablePollingFallback) {
          throw streamError
        }
        finalStatus = await runtime.pollGenerationAsFallback(projectId, String(taskId), assistantMsg)
      }

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
      }
      debugChatState('send-flow-finished')
      await scrollChat()
    }
  }

  const hydrateFromRecoverySnapshot = (snapshot: Record<string, any> | null | undefined) => {
    const normalizedSnapshot = pickBusinessRecord(snapshot)
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
    currentActiveTask.value = {
      sessionId: currentConversationId.value,
      turnId: normalizedSnapshot?.activeTask?.turnId == null ? null : String(normalizedSnapshot.activeTask.turnId),
      taskId: normalizedSnapshot?.activeTask?.taskId == null ? null : String(normalizedSnapshot.activeTask.taskId),
    }

    const taskStatus = normalizeGenerationStatus(normalizedSnapshot?.activeTask?.taskStatus)
    if (taskStatus === 'waiting_approval') {
      generationPhase.value = 'waiting_approval'
      generationTaskStatus.value = 'waiting_approval'
      isGenerating.value = false
      streamingAssistantMsgId.value = null
      return
    }
    if (taskStatus === 'running') {
      generationPhase.value = 'streaming'
      generationTaskStatus.value = 'running'
      isGenerating.value = true
      return
    }
    generationPhase.value = 'idle'
    generationTaskStatus.value = ''
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
    streamingAssistantMsgId.value = assistantMsg.id
    debugChatState('resume-stream-start', { projectId, sessionId, turnId })
    await scrollChat()

    try {
      let finalStatus: GenerationTaskStatus | ''
      try {
        finalStatus = await runtime.consumeGenerationStream(projectId, sessionId, turnId, assistantMsg)
      } catch (streamError: any) {
        debugChatState('resume-stream-error', {
          projectId,
          sessionId,
          turnId,
          errorMessage: runtime.getErrorMessage(streamError),
          fallbackEnabled: !!deps.enablePollingFallback,
        })
        if (!deps.enablePollingFallback) {
          throw streamError
        }
        const recoveryTaskId = String(currentActiveTask.value.taskId ?? '')
        if (!recoveryTaskId || recoveryTaskId === '0') {
          throw new Error('恢复续流失败，缺少 taskId 用于轮询兜底')
        }
        finalStatus = await runtime.pollGenerationAsFallback(projectId, recoveryTaskId, assistantMsg)
      }

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
      const failureText = `生成失败：${runtime.getErrorMessage(error)}`
      assistantMsg.text = assistantMsg.text ? `${assistantMsg.text}\n\n${failureText}` : failureText
    } finally {
      runtime.closeGenerationStream()
      isGenerating.value = false
      streamingAssistantMsgId.value = null
      if (generationPhase.value !== 'failed' && generationPhase.value !== 'waiting_approval') {
        generationPhase.value = 'idle'
        generationTaskStatus.value = ''
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
    streamingAssistantMsgId,
    currentConversationId,
    currentModelName,
    loadConversationList,
    loadConversationHistory,
    selectConversation,
    toggleConversationPanel,
    sendMessage,
    resumeRunningTask,
    pollGenerationAsFallback: runtime.pollGenerationAsFallback,
    consumeGenerationStream: runtime.consumeGenerationStream,
    scrollChat,
    applyAssistantEventMetadata,
    hydrateFromRecoverySnapshot,
  }
}
