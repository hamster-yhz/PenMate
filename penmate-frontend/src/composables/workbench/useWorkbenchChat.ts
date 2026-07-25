import { computed, ref } from 'vue'
import type { AgentSkillCatalogItem, AppError, WorkbenchRecoverySnapshot, WorkbenchRuntimeEventSource } from '@/api/types'
import type { AgentRunEventStream } from '@/api/agentRunStream'
import type { ChatMessage, ConversationItem, GenerationPhase } from '@/components/workbench/workbenchTypes'
import { applyAssistantEventMetadata, createChatTimeline, type ChatRecord } from './useWorkbenchChatTimeline'
import { createAgentRunRuntime, normalizeRunStatus, type AgentRunStatus } from './useAgentRunRuntime'
import { useWorkbenchRunTimeline } from './useWorkbenchRunTimeline'
import { pickBusinessArray, pickBusinessRecord } from '@/utils/apiPayload'

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
  listSkills?: (projectId: string) => Promise<AgentSkillCatalogItem[]>
  ensureModelConfigId: (projectId: string) => Promise<string | null>
  refreshActiveModelInfo?: (projectId: string) => Promise<string | null | void>
  listSessions: (projectId: string) => Promise<unknown>
  createSession: (projectId: string, payload: Record<string, unknown>) => Promise<unknown>
  getSessionRecovery: (projectId: string, sessionId: string) => Promise<unknown>
  listSessionRuns: (projectId: string, sessionId: string) => Promise<unknown>
  createTurn: (projectId: string, sessionId: string, payload: Record<string, unknown>) => Promise<unknown>
  cancelRun: (projectId: string, runId: string, payload: Record<string, unknown>) => Promise<unknown>
  retryRun: (projectId: string, runId: string, payload: Record<string, unknown>) => Promise<unknown>
  openRunStream: (projectId: string, runId: string, after?: string) => AgentRunEventStream
  addStreamListener: (stream: AgentRunEventStream, eventName: string, listener: StreamListener) => void
  closeRunStream?: (stream: AgentRunEventStream | null) => void
  revealAssistantText?: (assistantMsg: ChatMessage, rawText: string) => Promise<void>
  scrollChat: () => void
  forceScrollChat?: () => void
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
  const skillCatalog = ref<AgentSkillCatalogItem[]>([])
  const activeSkills = ref<string[]>([])
  const skillCatalogLoading = ref(false)
  const isGenerating = ref(false)
  const isCancelling = ref(false)
  const isRetrying = ref(false)
  const generationPhase = ref<GenerationPhase>('idle')
  const generationTaskStatus = ref<AgentRunStatus | ''>('')
  const agentStatusDetailText = ref('')
  const streamingAssistantMsgId = ref<string | number | null>(null)
  const runtimeEventSource = ref<WorkbenchRuntimeEventSource | null>(null)
  const currentConversationId = ref<string | null>(null)
  const preferredConversationId = ref<string | null>(null)
  const currentModelName = ref('')
  const runTimeline = useWorkbenchRunTimeline()
  const currentActiveRun = ref<{
    sessionId: string | null
    turnId: string | null
    runId: string | null
    latestSequence: string
    runStatus: AgentRunStatus | ''
  }>({
    sessionId: null,
    turnId: null,
    runId: null,
    latestSequence: '0',
    runStatus: '',
  })
  const recoveredSelectedText = ref('')

  const canCancelRun = computed(() => {
    if (!currentActiveRun.value.runId) return false
    return (
      isGenerating.value || ['pending', 'running', 'waiting_approval', 'suspended'].includes(generationTaskStatus.value)
    )
  })
  const canRetryRun = computed(() => {
    if (!currentActiveRun.value.runId || isGenerating.value || isRetrying.value) return false
    return ['failed', 'cancelled'].includes(currentActiveRun.value.runStatus)
  })

  let msgIdCounter = 1
  let runStream: AgentRunEventStream | null = null
  let foregroundEpoch = 0

  const generationStatusText = computed(() => {
    if (isGenerating.value && generationTaskStatus.value) return `运行中 · ${generationTaskStatus.value}`
    if (generationPhase.value === 'preparing') return '准备中'
    if (generationPhase.value === 'streaming') return '生成中'
    if (generationPhase.value === 'waiting_approval') return '等待审批'
    if (generationPhase.value === 'failed') return '异常'
    return '就绪'
  })

  const scrollChat = async (force = false) => {
    await deps.nextTick()
    if (force) deps.forceScrollChat?.()
    else deps.scrollChat()
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

  const loadSkillCatalog = async () => {
    const projectId = deps.getCurrentProjectId()
    if (!projectId || !deps.listSkills || skillCatalogLoading.value) return
    skillCatalogLoading.value = true
    try {
      const items = await deps.listSkills(projectId)
      skillCatalog.value = (Array.isArray(items) ? items : [])
        .map((item) => ({ name: String(item.name || ''), description: String(item.description || '') }))
        .filter((item) => item.name)
        .sort((left, right) => left.name.localeCompare(right.name))
    } catch (error) {
      deps.notifyWarning?.(runtime.getErrorMessage(error))
    } finally {
      skillCatalogLoading.value = false
    }
  }

  const addActiveSkill = (name: string) => {
    const normalized = String(name || '').trim()
    if (!normalized || activeSkills.value.includes(normalized)) return
    if (activeSkills.value.length >= 4) {
      deps.notifyWarning?.('最多同时激活 4 个 Skill')
      return
    }
    activeSkills.value = [...activeSkills.value, normalized].sort()
  }

  const removeActiveSkill = (name: string) => {
    activeSkills.value = activeSkills.value.filter((item) => item !== name)
  }

  const getSessionRecovery = async (projectId: string, sessionId: string) => {
    return pickBusinessRecord(await deps.getSessionRecovery(projectId, sessionId))
  }

  const createTurn = (projectId: string, sessionId: string, payload: Record<string, unknown>) =>
    deps.createTurn(projectId, sessionId, payload)

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
    scrollChat: () => scrollChat(true),
  })

  const runtime = createAgentRunRuntime({
    getRunStatus: () => generationTaskStatus.value,
    setRunStatus: (value: AgentRunStatus | '') => {
      generationTaskStatus.value = value
      currentActiveRun.value.runStatus = value
    },
    setAgentStatusDetailText: (value: string) => {
      agentStatusDetailText.value = value
    },
    getRunPhase: () => generationPhase.value,
    setRunPhase: (value: GenerationPhase) => {
      generationPhase.value = value
    },
    getRunStream: () => runStream,
    setRunStream: (stream: AgentRunEventStream | null) => {
      runStream = stream
    },
    openRunStream: deps.openRunStream,
    addStreamListener: deps.addStreamListener,
    closeRunStream: deps.closeRunStream,
    scrollChat: () => {
      void scrollChat()
    },
    setRuntimeEventSource: (value) => {
      runtimeEventSource.value = value
    },
    onEvent: (eventName, payload) => {
      runTimeline.appendEvent(
        eventName,
        payload,
        currentActiveRun.value.runId || '',
        currentActiveRun.value.turnId || '',
      )
    },
    onConnectionState: (state) => {
      const runId = currentActiveRun.value.runId
      if (runId) runTimeline.setConnectionState(runId, state)
    },
    setLatestSequence: (value) => {
      currentActiveRun.value.latestSequence = value
    },
    onToken: (token, payload) => {
      const assistantMsg = messages.value.find((item) => String(item.id) === String(streamingAssistantMsgId.value))
      if (!assistantMsg) return
      const offset = Number(payload.offset)
      if (!Number.isSafeInteger(offset) || offset < 0) {
        assistantMsg.text += token
        return
      }
      if (offset > assistantMsg.text.length) return
      if (assistantMsg.text.slice(offset, offset + token.length) === token) return
      assistantMsg.text = `${assistantMsg.text.slice(0, offset)}${token}`
    },
    onMessageSnapshot: (text) => {
      const assistantMsg = messages.value.find((item) => String(item.id) === String(streamingAssistantMsgId.value))
      if (assistantMsg) assistantMsg.text = text
    },
    onMessageCompleted: (text) => {
      const assistantMsg = messages.value.find((item) => String(item.id) === String(streamingAssistantMsgId.value))
      if (!assistantMsg) return
      assistantMsg.text = text
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
      const runId = currentActiveRun.value.runId || ''
      if (!runId) return ''
      return (await reconcileRunAfterStreamError(runId)).status
    },
    onStreamError: async (_payload, runId) => reconcileRunAfterStreamError(runId),
  })

  const loadConversationList = async (projectId: string) => {
    await timeline.loadConversationList(projectId)
  }

  const loadRunHistory = async (projectId: string, sessionId: string) => {
    const history = await deps.listSessionRuns(projectId, sessionId)
    if (currentConversationId.value === sessionId) runTimeline.replaceHistory(history)
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
    const created = pickBusinessRecord(
      await deps.createSession(projectId, {
        userId: operatorId,
        title: '新会话',
      }),
    ) as ChatRecord
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

  function hydrateFromRecoverySnapshot(snapshot: WorkbenchRecoverySnapshot | null | undefined) {
    foregroundEpoch += 1
    runtimeEventSource.value = null
    const normalizedSnapshot = pickBusinessRecord(snapshot) as {
      session?: { sessionId?: string | number | null; activeSkills?: unknown[] | null }
      activeRun?: {
        turnId?: string | number | null
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
    currentConversationId.value =
      normalizedSnapshot?.session?.sessionId == null ? null : String(normalizedSnapshot.session.sessionId)
    preferredConversationId.value = currentConversationId.value
    activeSkills.value = Array.isArray(normalizedSnapshot?.session?.activeSkills)
      ? normalizedSnapshot.session.activeSkills.map(String).filter(Boolean).sort()
      : []
    recoveredSelectedText.value = String(normalizedSnapshot?.workbenchContext?.selectedText ?? '')
    currentActiveRun.value = {
      sessionId: currentConversationId.value,
      turnId: normalizedSnapshot?.activeRun?.turnId == null ? null : String(normalizedSnapshot.activeRun.turnId),
      runId: normalizedSnapshot?.activeRun?.runId == null ? null : String(normalizedSnapshot.activeRun.runId),
      latestSequence: String(normalizedSnapshot?.activeRun?.latestSequence ?? '0'),
      runStatus: normalizeRunStatus(normalizedSnapshot?.activeRun?.runStatus),
    }
    const runStatus = normalizeRunStatus(normalizedSnapshot?.activeRun?.runStatus)
    if (runStatus === 'waiting_approval') {
      generationPhase.value = 'waiting_approval'
      generationTaskStatus.value = 'waiting_approval'
      isGenerating.value = false
      streamingAssistantMsgId.value = null
      return
    }
    if (runStatus === 'pending' || runStatus === 'running' || runStatus === 'suspended') {
      generationPhase.value = 'idle'
      generationTaskStatus.value = runStatus
      isGenerating.value = runStatus === 'running'
      return
    }
    generationPhase.value = 'idle'
    generationTaskStatus.value = ''
    agentStatusDetailText.value = ''
    isGenerating.value = false
    streamingAssistantMsgId.value = null
  }

  const detachCurrentSession = () => {
    foregroundEpoch += 1
    runtime.closeRunStream()
    isGenerating.value = false
    isCancelling.value = false
    isRetrying.value = false
    generationPhase.value = 'idle'
    generationTaskStatus.value = ''
    agentStatusDetailText.value = ''
    streamingAssistantMsgId.value = null
    runtimeEventSource.value = null
    activeSkills.value = []
    currentActiveRun.value = {
      sessionId: null,
      turnId: null,
      runId: null,
      latestSequence: '0',
      runStatus: '',
    }
  }

  const activateEmptySession = (sessionId: string) => {
    detachCurrentSession()
    messages.value = []
    runTimeline.attempts.value = []
    chatInput.value = ''
    recoveredSelectedText.value = ''
    activeSkills.value = []
    currentConversationId.value = sessionId
    preferredConversationId.value = sessionId
  }

  const selectConversation = async (conversationId: string) => {
    const projectId = deps.getCurrentProjectId()
    if (!projectId || !conversationId) return
    detachCurrentSession()
    hydrateFromRecoverySnapshot(
      ((await getSessionRecovery(projectId, conversationId)) || null) as WorkbenchRecoverySnapshot | null,
    )
    await loadRunHistory(projectId, conversationId)
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
      hydrateFromRecoverySnapshot(
        ((await getSessionRecovery(projectId, latestSessionId)) || null) as WorkbenchRecoverySnapshot | null,
      )
      await loadRunHistory(projectId, latestSessionId)
      await loadConversationList(projectId)
    } catch {
      messages.value = []
      currentConversationId.value = null
      preferredConversationId.value = null
    }
  }

  async function reconcileRunAfterStreamError(runId: string) {
    const projectId = deps.getCurrentProjectId()
    const sessionId = currentActiveRun.value.sessionId || currentConversationId.value || ''
    if (!projectId || !sessionId || !runId) return { status: '' as const }

    const historyPayload = await deps.listSessionRuns(projectId, sessionId)
    if (currentActiveRun.value.sessionId !== sessionId || currentActiveRun.value.runId !== runId) {
      return { status: '' as const }
    }
    runTimeline.mergeHistory(historyPayload)
    const exactRun = pickBusinessArray<ChatRecord>(historyPayload).find(
      (item) => String(item.runId ?? '') === runId,
    )
    const status = normalizeRunStatus(exactRun?.runStatus ?? exactRun?.status)
    if (
      status === 'pending' ||
      status === 'running' ||
      status === 'suspended' ||
      status === 'completed' ||
      status === 'cancelled' ||
      status === 'superseded'
    ) {
      const turnId = String(exactRun?.turnId ?? currentActiveRun.value.turnId ?? '')
      const output = exactRun?.output && typeof exactRun.output === 'object' && !Array.isArray(exactRun.output)
        ? exactRun.output as ChatRecord
        : null
      const recoveredText = String(output?.text ?? '')
      const recoveredOffset = Number(output?.offset)
      const assistantMsg = messages.value.find(
        (item) => String(item.id) === String(streamingAssistantMsgId.value),
      )
      const terminal = status === 'completed' || status === 'cancelled' || status === 'superseded'
      const isCurrentRun = currentActiveRun.value.runId === runId && currentActiveRun.value.turnId === turnId
      const isExactMessage = assistantMsg?.runId === runId && assistantMsg.turnId === turnId
      const hasFreshOffset = Boolean(
        assistantMsg && Number.isSafeInteger(recoveredOffset) && recoveredOffset >= assistantMsg.text.length,
      )
      if (assistantMsg && isCurrentRun && isExactMessage && recoveredText && (terminal || hasFreshOffset)) {
        assistantMsg.text = recoveredText
      }
    }
    return {
      status,
      errorMessage: String(exactRun?.lastErrorMessage ?? exactRun?.errorMessage ?? '').trim() || undefined,
    }
  }

  const resolveAssistantMessageForResume = (
    reuseExisting: boolean,
    runId: string,
    turnId: string,
  ): ChatMessage => {
    if (reuseExisting && turnId) {
      const existingForTurn = [...messages.value]
        .reverse()
        .find(
          (item) =>
            item.role === 'assistant' &&
            item.turnId === turnId &&
            (!item.runId || item.runId === runId),
        )
      if (existingForTurn) {
        existingForTurn.runId = runId
        return existingForTurn
      }
    }
    const assistantMsg: ChatMessage = { id: msgIdCounter++, role: 'assistant', text: '', runId }
    if (turnId) assistantMsg.turnId = turnId
    messages.value.push(assistantMsg)
    return assistantMsg
  }

  const consumeRun = async (
    projectId: string,
    sessionId: string,
    runId: string,
    after = '0',
    turnId = '',
    reuseExistingAssistant = false,
    preparedAssistant?: ChatMessage,
  ) => {
    const assistantMsg = preparedAssistant ?? resolveAssistantMessageForResume(reuseExistingAssistant, runId, turnId)
    if (turnId) assistantMsg.turnId = turnId
    assistantMsg.runId = runId
    runTimeline.ensureAttempt(runId, turnId)
    isGenerating.value = true
    generationPhase.value = 'idle'
    generationTaskStatus.value = ''
    agentStatusDetailText.value = ''
    streamingAssistantMsgId.value = assistantMsg.id
    currentActiveRun.value = {
      sessionId,
      turnId: turnId || currentActiveRun.value.turnId,
      runId,
      latestSequence: after,
      runStatus: currentActiveRun.value.runStatus,
    }
    const isForegroundRun = () =>
      currentActiveRun.value.sessionId === sessionId &&
      currentActiveRun.value.runId === runId &&
      (!turnId || currentActiveRun.value.turnId === turnId)
    await scrollChat(true)
    try {
      const finalStatus = await runtime.consumeRunStream(projectId, runId, after)
      if (!isForegroundRun()) return
      if (finalStatus === 'cancelled' || finalStatus === 'superseded') {
        generationPhase.value = 'idle'
        generationTaskStatus.value = finalStatus
        agentStatusDetailText.value = ''
        return
      }
      if (finalStatus === 'failed') {
        throw new Error(`运行结束: ${finalStatus}`)
      }
      const hasPendingApproval = !!assistantMsg.approval && !assistantMsg.approval?.resolved
      if (hasPendingApproval) {
        generationPhase.value = 'waiting_approval'
        generationTaskStatus.value = 'waiting_approval'
      }
      if (!assistantMsg.text && !hasPendingApproval) {
        await reconcileRunAfterStreamError(runId)
      }
    } catch (error: unknown) {
      if (!isForegroundRun()) return
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
      const failureText = assistantMsg.text
        ? `生成中断：${resolvedErrorMessage}`
        : `运行失败：${resolvedErrorMessage}`
      assistantMsg.text = assistantMsg.text ? `${assistantMsg.text}\n\n${failureText}` : failureText
    } finally {
      if (isForegroundRun()) {
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
  }

  const sendMessage = async () => {
    if (!chatInput.value.trim() || isGenerating.value || canCancelRun.value) return
    const sendEpoch = foregroundEpoch
    const userText = chatInput.value.trim()
    const userMessage: ChatMessage = { id: msgIdCounter++, role: 'user', text: userText }
    const assistantMessage: ChatMessage = { id: msgIdCounter++, role: 'assistant', text: '' }
    messages.value.push(userMessage, assistantMessage)
    chatInput.value = ''
    runtimeEventSource.value = null
    generationPhase.value = 'preparing'
    generationTaskStatus.value = ''
    agentStatusDetailText.value = ''
    isGenerating.value = true
    streamingAssistantMsgId.value = assistantMessage.id
    currentActiveRun.value = {
      sessionId: currentConversationId.value,
      turnId: null,
      runId: null,
      latestSequence: '0',
      runStatus: '',
    }
    debugChatState('user-send-start', { userTextLength: userText.length })
    await scrollChat(true)

    const { projectId, operatorId } = deps.getContext()
    if (!projectId || !operatorId) {
      assistantMessage.text = '缺少 projectId/operatorId，无法发送。'
      isGenerating.value = false
      streamingAssistantMsgId.value = null
      generationPhase.value = 'failed'
      return
    }

    let resolvedSessionId = currentConversationId.value || ''
    try {
      const sessionId = await ensureSessionIdForSend(projectId, operatorId)
      resolvedSessionId = sessionId || resolvedSessionId
      if (sendEpoch !== foregroundEpoch) return
      if (!sessionId) throw new Error('会话初始化失败')
      const modelConfigId = await deps.ensureModelConfigId(projectId)
      if (sendEpoch !== foregroundEpoch) return
      if (!modelConfigId) {
        deps.onRequireModelSelection?.()
        throw new Error('未选择可用模型，请先保存并切换模型')
      }
      const selectedText = String(deps.getSelectedText?.() ?? '').trim() || recoveredSelectedText.value
      const created = pickBusinessRecord(
        await createTurn(projectId, sessionId, {
          operatorId,
          userMessage: userText,
          activeSkills: [...activeSkills.value],
          taskRequest: {
            taskType: 'WRITE',
            chapterId: deps.getActiveChapterKey() || null,
            selectedText,
            modelConfigId,
            activePlugins: deps.getActivePlugins() || [],
          },
        }),
      ) as ChatRecord & {
        activeRun?: {
          turnId?: string | number | null
          runId?: string | number | null
          latestSequence?: string | number | null
          runStatus?: string | null
          runPhase?: string | null
        }
      }
      if (sendEpoch !== foregroundEpoch) return
      const runId = created.activeRun?.runId
      if (runId == null || String(runId).trim() === '' || String(runId) === '0') {
        throw new Error('运行创建失败，缺少 runId')
      }
      const turnId = String(created.activeRun?.turnId ?? '')
      if (userMessage && turnId) userMessage.turnId = turnId
      assistantMessage.turnId = turnId
      assistantMessage.runId = String(runId)
      const attempt = runTimeline.ensureAttempt(String(runId), turnId)
      attempt.runStatus = String(created.activeRun?.runStatus ?? 'PENDING').toUpperCase()
      attempt.runPhase = String(created.activeRun?.runPhase ?? 'created')
      const latestSequence = Number(created.activeRun?.latestSequence ?? 0)
      if (Number.isSafeInteger(latestSequence)) {
        attempt.latestSequence = Math.max(attempt.latestSequence, latestSequence)
      }
      await scrollChat(true)
      // The creation response only reports the durable cursor; it does not contain
      // those events. Replay from zero so run.started reaches the live timeline.
      await consumeRun(projectId, sessionId, String(runId), '0', turnId, false, assistantMessage)
    } catch (error: unknown) {
      if (sendEpoch !== foregroundEpoch) return
      generationPhase.value = 'failed'
      generationTaskStatus.value = 'failed'
      const resolvedErrorMessage = runtime.getErrorMessage(error)
      const appError = error as AppError
      if (appError.errorCode === 'SKILL_NOT_FOUND') void loadSkillCatalog()
      const hasCreatedRun = assistantMessage.runId != null && String(assistantMessage.runId).trim() !== ''
      if (!hasCreatedRun) {
        messages.value = messages.value.filter((item) => item.id !== userMessage.id && item.id !== assistantMessage.id)
        if (!chatInput.value.trim()) chatInput.value = userText
      }
      if (appError.errorCode === 'SESSION_RUN_ACTIVE' && projectId && resolvedSessionId) {
        const snapshot = pickBusinessRecord(
          await deps.getSessionRecovery(projectId, resolvedSessionId),
        ) as WorkbenchRecoverySnapshot
        hydrateFromRecoverySnapshot(snapshot)
        const activeRun = snapshot.activeRun
        const runId = String(activeRun?.runId ?? '')
        if (runId) {
          await consumeRun(
            projectId,
            resolvedSessionId,
            runId,
            String(activeRun?.latestSequence ?? '0'),
            String(activeRun?.turnId ?? ''),
            true,
          )
        }
        return
      }
      agentStatusDetailText.value = resolvedErrorMessage
      if (hasCreatedRun) assistantMessage.text = `运行失败: ${resolvedErrorMessage}`
      isGenerating.value = false
      streamingAssistantMsgId.value = null
      await scrollChat()
    }
  }

  const resumeRunningRun = async (projectId: string, runId: string, after = '0') => {
    const sessionId = currentActiveRun.value.sessionId || currentConversationId.value || ''
    await consumeRun(projectId, sessionId, runId, after, currentActiveRun.value.turnId || '', true)
  }

  const cancelCurrentRun = async () => {
    if (!canCancelRun.value || isCancelling.value) return
    const { projectId, operatorId } = deps.getContext()
    const runId = currentActiveRun.value.runId
    if (!projectId || !operatorId || !runId) return
    const actionEpoch = foregroundEpoch
    const awaitingRunStream = isGenerating.value
    isCancelling.value = true
    try {
      await deps.cancelRun(projectId, runId, { operatorId })
      if (actionEpoch !== foregroundEpoch) return
      generationTaskStatus.value = 'cancelled'
      currentActiveRun.value.runStatus = 'cancelled'
      generationPhase.value = 'idle'
      agentStatusDetailText.value = ''
      if (!awaitingRunStream) isGenerating.value = false
    } catch (error: unknown) {
      if (actionEpoch !== foregroundEpoch) return
      deps.notifyWarning?.(runtime.getErrorMessage(error))
    } finally {
      isCancelling.value = false
    }
  }

  const retryCurrentRun = async () => {
    if (!canRetryRun.value) return
    const { projectId, operatorId } = deps.getContext()
    const predecessorRunId = currentActiveRun.value.runId
    const sessionId = currentActiveRun.value.sessionId || currentConversationId.value || ''
    if (!projectId || !operatorId || !predecessorRunId || !sessionId) return
    const actionEpoch = foregroundEpoch

    isRetrying.value = true
    try {
      const predecessorAssistant = [...messages.value]
        .reverse()
        .find(
          (item) =>
            item.role === 'assistant' &&
            item.turnId === currentActiveRun.value.turnId &&
            !item.runId,
        )
      if (predecessorAssistant) predecessorAssistant.runId = predecessorRunId
      const retried = pickBusinessRecord(await deps.retryRun(projectId, predecessorRunId, {
        operatorId,
        activeSkills: [...activeSkills.value],
      })) as ChatRecord
      if (actionEpoch !== foregroundEpoch) return
      const successorRunId = String(retried.runId ?? '').trim()
      if (!successorRunId || successorRunId === '0') {
        throw new Error('Retry did not return a successor Run')
      }
      const runStatus = normalizeRunStatus(retried.runStatus) || 'pending'
      const latestSequence = String(retried.latestSequence ?? '0')
      const turnId = String(retried.turnId ?? currentActiveRun.value.turnId ?? '')
      runtimeEventSource.value = null
      generationTaskStatus.value = runStatus
      currentActiveRun.value = { sessionId, turnId, runId: successorRunId, latestSequence, runStatus }
      await consumeRun(projectId, sessionId, successorRunId, '0', turnId)
    } catch (error: unknown) {
      if (actionEpoch !== foregroundEpoch) return
      deps.notifyWarning?.(runtime.getErrorMessage(error))
    } finally {
      isRetrying.value = false
    }
  }

  return {
    messages,
    showConversationPanel,
    conversationLoading,
    conversationList,
    runAttempts: runTimeline.attempts,
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
    currentModelName,
    loadConversationList,
    loadRunHistory,
    loadConversationHistory,
    selectConversation,
    toggleConversationPanel,
    sendMessage,
    cancelCurrentRun,
    retryCurrentRun,
    loadSkillCatalog,
    addActiveSkill,
    removeActiveSkill,
    detachCurrentSession,
    activateEmptySession,
    dispose: runtime.closeRunStream,
    resumeRunningRun,
    consumeRunStream: runtime.consumeRunStream,
    scrollChat,
    applyAssistantEventMetadata,
    hydrateFromRecoverySnapshot,
  }
}
