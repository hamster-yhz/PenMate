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

type ContextProfile = {
  projectId?: number | string | null
  operatorId?: number | string | null
}

type StreamListener = (event: MessageEvent<string>) => void

type UseWorkbenchChatDeps = {
  getContext: () => ContextProfile
  getCurrentProjectId: () => number
  getActiveChapterKey: () => string
  getActivePlugins: () => string[]
  ensureModelConfigId: (projectId: number) => Promise<string | null>
  refreshActiveModelInfo?: (projectId: number) => Promise<string | null | void>
  listSessions: (projectId: number) => Promise<unknown>
  createSession?: (projectId: number, payload: Record<string, unknown>) => Promise<unknown>
  getSessionRecovery: (projectId: number, sessionId: number) => Promise<unknown>
  createTurn: (projectId: number, sessionId: number, payload: Record<string, unknown>) => Promise<unknown>
  getTask: (projectId: number, taskId: number) => Promise<unknown>
  openTaskStream: (projectId: number, taskId: number) => EventSource
  ensureConversationId?: (projectId: number, operatorId: number) => Promise<number | null>
  listConversations?: (projectId: number) => Promise<unknown>
  listMessages?: (projectId: number, conversationId: number) => Promise<unknown>
  createMessage?: (projectId: number, conversationId: number, operatorId: number, payload: Record<string, unknown>) => Promise<unknown>
  createGeneration?: (projectId: number, operatorId: number, payload: Record<string, unknown>) => Promise<unknown>
  getGeneration?: (projectId: number, taskId: number) => Promise<unknown>
  openGenerationStream?: (projectId: number, taskId: number) => EventSource
  addStreamListener: (stream: EventSource, eventName: string, listener: StreamListener) => void
  closeTaskStream?: (stream: EventSource | null) => void
  closeGenerationStream?: (stream: EventSource | null) => void
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
  const streamingAssistantMsgId = ref<number | null>(null)
  const currentConversationId = ref<number | null>(null)
  const preferredConversationId = ref<number | null>(null)
  const currentModelName = ref('')

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

  const listSessions = (projectId: number) => deps.listConversations
    ? deps.listConversations(projectId)
    : deps.listSessions(projectId)

  const getSessionRecovery = async (projectId: number, sessionId: number) => {
    if (deps.listMessages) {
      const messages = await deps.listMessages(projectId, sessionId)
      return {
        session: { sessionId },
        messages,
      }
    }
    return deps.getSessionRecovery(projectId, sessionId)
  }

  const createTurn = (projectId: number, sessionId: number, payload: Record<string, unknown>) => {
    if (deps.createGeneration) {
      return deps.createGeneration(projectId, Number(payload.operatorId ?? 0), {
        conversationId: sessionId,
        chapterId: (payload.taskRequest as Record<string, unknown> | undefined)?.chapterId ?? null,
        modelConfigId: (payload.taskRequest as Record<string, unknown> | undefined)?.modelConfigId,
        taskType: (payload.taskRequest as Record<string, unknown> | undefined)?.taskType ?? 'WRITE',
        promptSnapshot: payload.userMessage,
        pluginSnapshot: JSON.stringify((payload.taskRequest as Record<string, unknown> | undefined)?.activePlugins ?? []),
      })
    }
    return deps.createTurn(projectId, sessionId, payload)
  }

  const getTask = (projectId: number, taskId: number) => deps.getGeneration
    ? deps.getGeneration(projectId, taskId)
    : deps.getTask(projectId, taskId)

  const openTaskStream = (projectId: number, taskId: number) => deps.openGenerationStream
    ? deps.openGenerationStream(projectId, taskId)
    : deps.openTaskStream(projectId, taskId)

  const closeTaskStream = deps.closeGenerationStream || deps.closeTaskStream

  const normalizeSessionRecord = (item: ChatRecord) => ({
    ...item,
    conversationId: Number(item.sessionId ?? item.conversationId ?? 0),
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
    listConversations: async (projectId: number) => {
      const sessions = (await listSessions(projectId)) as Array<ChatRecord>
      return (Array.isArray(sessions) ? sessions : []).map(normalizeSessionRecord)
    },
    listMessages: async (projectId: number, conversationId: number) => {
      if (deps.listMessages) {
        return deps.listMessages(projectId, conversationId)
      }
      return []
    },
    setConversationList: (items) => {
      conversationList.value = items
    },
    setConversationLoading: (value) => {
      conversationLoading.value = value
    },
    setCurrentConversationId: (value) => {
      currentConversationId.value = value
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
    setGenerationTaskStatus: (value) => {
      generationTaskStatus.value = value
    },
    getGenerationPhase: () => generationPhase.value,
    setGenerationPhase: (value) => {
      generationPhase.value = value
    },
    getGenerationStream: () => generationStream,
    setGenerationStream: (stream) => {
      generationStream = stream
    },
    openGenerationStream: openTaskStream,
    addStreamListener: deps.addStreamListener,
    closeGenerationStream: closeTaskStream,
    getGeneration: getTask,
    waitForPolling: deps.waitForPolling,
    scrollChat: deps.scrollChat,
  })

  const loadConversationList = async (projectId: number) => {
    await timeline.loadConversationList(projectId)
  }

  const resolveCurrentSessionId = async (projectId: number) => {
    if (currentConversationId.value) {
      return currentConversationId.value
    }
    if (preferredConversationId.value) {
      currentConversationId.value = preferredConversationId.value
      return preferredConversationId.value
    }
    const sessions = (await listSessions(projectId)) as Array<ChatRecord>
    const latestSessionId = Number((Array.isArray(sessions) ? sessions[0] : null)?.sessionId ?? (Array.isArray(sessions) ? sessions[0] : null)?.conversationId ?? 0)
    if (latestSessionId > 0) {
      currentConversationId.value = latestSessionId
      preferredConversationId.value = latestSessionId
      return latestSessionId
    }
    return null
  }

  const createSessionForSend = async (projectId: number, operatorId: number) => {
    if (!deps.createSession) {
      return null
    }
    const created = (await deps.createSession(projectId, {
      userId: operatorId,
      title: '新会话',
    })) as ChatRecord
    const sessionId = Number(created?.sessionId ?? created?.conversationId ?? 0)
    if (sessionId > 0) {
      currentConversationId.value = sessionId
      preferredConversationId.value = sessionId
      if (showConversationPanel.value) {
        await loadConversationList(projectId)
      }
      return sessionId
    }
    return null
  }

  const ensureSessionIdForSend = async (projectId: number, operatorId: number) => {
    if (deps.ensureConversationId) {
      return deps.ensureConversationId(projectId, operatorId)
    }
    const existingSessionId = await resolveCurrentSessionId(projectId)
    if (existingSessionId) {
      return existingSessionId
    }
    return createSessionForSend(projectId, operatorId)
  }

  const selectConversation = async (conversationId: number) => {
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

  const loadConversationHistory = async (projectId: number, operatorId: number) => {
    if (!projectId || !operatorId) {
      messages.value = []
      currentConversationId.value = null
      return
    }

    try {
      if (deps.ensureConversationId && deps.listMessages) {
        const conversationId = await deps.ensureConversationId(projectId, operatorId)
        if (!conversationId) {
          messages.value = []
          currentConversationId.value = null
          preferredConversationId.value = null
          return
        }
        currentConversationId.value = conversationId
        preferredConversationId.value = conversationId
        await timeline.loadConversationMessages(projectId, conversationId)
        await loadConversationList(projectId)
        return
      }

      const sessions = (await listSessions(projectId)) as Array<ChatRecord>
      const latestSessionId = Number((Array.isArray(sessions) ? sessions[0] : null)?.sessionId ?? (Array.isArray(sessions) ? sessions[0] : null)?.conversationId ?? 0)
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
      const conversationId = await ensureSessionIdForSend(Number(projectId), Number(operatorId))
      if (!conversationId) throw new Error('会话初始化失败')

      const modelConfigId = await deps.ensureModelConfigId(Number(projectId))
      if (!modelConfigId) {
        deps.onRequireModelSelection?.()
        throw new Error('未选择可用模型，请先在模型设置中保存并切换模型')
      }

      if (deps.createMessage) {
        await deps.createMessage(Number(projectId), conversationId, Number(operatorId), {
          role: 'user',
          userMessageType: 'COMMAND',
          contentMd: userText,
          attachmentsJson: '[]',
          toolCallsJson: '[]',
        })
      }

      const generation = (await createTurn(Number(projectId), conversationId, {
        operatorId: Number(operatorId),
        userMessage: userText,
        taskRequest: {
          taskType: 'WRITE',
          chapterId: Number(deps.getActiveChapterKey()) || null,
          selectedText: '',
          modelConfigId,
          activePlugins: deps.getActivePlugins() || [],
        },
      })) as ChatRecord & { activeTask?: { taskId?: number | string; taskStatus?: string } }

      const taskId = Number(generation.activeTask?.taskId ?? generation.taskId ?? 0)
      if (!taskId) throw new Error('任务创建失败，缺少 taskId')

      generationTaskStatus.value = normalizeGenerationStatus(generation.activeTask?.taskStatus ?? generation.status) || 'pending'
      generationPhase.value = 'streaming'

      assistantMsg = { id: msgIdCounter++, role: 'assistant', text: '' }
      messages.value.push(assistantMsg)
      streamingAssistantMsgId.value = assistantMsg.id
      await scrollChat()

      let finalStatus: GenerationTaskStatus | ''
      try {
        finalStatus = await runtime.consumeGenerationStream(Number(projectId), taskId, assistantMsg)
      } catch (streamError: any) {
        if (!deps.enablePollingFallback) {
          throw streamError
        }
        finalStatus = await runtime.pollGenerationAsFallback(Number(projectId), taskId, assistantMsg)
      }

      if (finalStatus === 'failed' || finalStatus === 'cancelled') {
        throw new Error(`生成任务结束：${finalStatus}`)
      }

      const hasPendingApproval = !!assistantMsg.approval && !assistantMsg.approval.resolved
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
    const recoveryMessages = Array.isArray(snapshot?.messages) ? snapshot.messages : []
    const mappedMessages = recoveryMessages.map((item) => timeline.mapApiMessage(item as ChatRecord))
    messages.value = mappedMessages
    const maxId = mappedMessages.reduce((max, item) => (item.id > max ? item.id : max), 0)
    if (maxId >= msgIdCounter) {
      msgIdCounter = maxId + 1
    }

    currentConversationId.value = Number(snapshot?.session?.sessionId ?? 0) || null
    preferredConversationId.value = currentConversationId.value

    const taskStatus = normalizeGenerationStatus(snapshot?.activeTask?.taskStatus)
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
    const existingAssistant = [...messages.value].reverse().find((item) => item.role === 'assistant') || null
    if (existingAssistant) {
      return existingAssistant
    }
    const assistantMsg = { id: msgIdCounter++, role: 'assistant' as const, text: '' }
    messages.value.push(assistantMsg)
    return assistantMsg
  }

  const resumeRunningTask = async (projectId: number, taskId: number) => {
    const assistantMsg = resolveAssistantMessageForResume()
    isGenerating.value = true
    generationPhase.value = 'streaming'
    generationTaskStatus.value = 'running'
    streamingAssistantMsgId.value = assistantMsg.id
    debugChatState('resume-stream-start', { projectId, taskId })
    await scrollChat()

    try {
      let finalStatus: GenerationTaskStatus | ''
      try {
        finalStatus = await runtime.consumeGenerationStream(projectId, taskId, assistantMsg)
      } catch (streamError: any) {
        debugChatState('resume-stream-error', {
          projectId,
          taskId,
          errorMessage: runtime.getErrorMessage(streamError),
          fallbackEnabled: !!deps.enablePollingFallback,
        })
        if (!deps.enablePollingFallback) {
          throw streamError
        }
        finalStatus = await runtime.pollGenerationAsFallback(projectId, taskId, assistantMsg)
      }

      if (finalStatus === 'failed' || finalStatus === 'cancelled') {
        throw new Error(`生成任务结束：${finalStatus}`)
      }

      const hasPendingApproval = !!assistantMsg.approval && !assistantMsg.approval.resolved
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
      debugChatState('resume-stream-finished', { projectId, taskId })
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
