import { computed, ref } from 'vue'
import type { ApprovalCardData } from '@/components/workbench/ApprovalCard.vue'
import type { ChatMessage, ConversationItem, GenerationPhase } from '@/components/workbench/workbenchTypes'

type GenerationTaskStatus = 'pending' | 'running' | 'waiting_approval' | 'done' | 'applied' | 'failed' | 'cancelled'

type ContextProfile = {
  projectId?: number | string | null
  operatorId?: number | string | null
}

type ChatRecord = Record<string, unknown>
type StreamListener = (event: MessageEvent<string>) => void

type UseWorkbenchChatDeps = {
  getContext: () => ContextProfile
  getCurrentProjectId: () => number
  getActiveChapterKey: () => string
  getActivePlugins: () => string[]
  ensureConversationId: (projectId: number, operatorId: number) => Promise<number | null>
  ensureModelConfigId: (projectId: number) => Promise<number | null>
  refreshActiveModelInfo?: (projectId: number) => Promise<number | null | void>
  listConversations: (projectId: number) => Promise<unknown>
  listMessages: (projectId: number, conversationId: number) => Promise<unknown>
  createMessage: (projectId: number, conversationId: number, operatorId: number, payload: Record<string, unknown>) => Promise<unknown>
  createGeneration: (projectId: number, operatorId: number, payload: Record<string, unknown>) => Promise<unknown>
  getGeneration: (projectId: number, taskId: number) => Promise<unknown>
  openGenerationStream: (projectId: number, taskId: number) => EventSource
  addStreamListener: (stream: EventSource, eventName: string, listener: StreamListener) => void
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

const TERMINAL_GENERATION_STATUSES: GenerationTaskStatus[] = ['done', 'applied', 'failed', 'cancelled']
const DEFAULT_POLLING_INTERVAL_MS = 200
const htmlEntityPrefix = String.fromCharCode(38)

const escapeHtml = (value: string) => String(value || '')
  .replaceAll('&', `${htmlEntityPrefix}amp;`)
  .replaceAll('<', `${htmlEntityPrefix}lt;`)
  .replaceAll('>', `${htmlEntityPrefix}gt;`)
  .replaceAll('"', `${htmlEntityPrefix}quot;`)
  .replaceAll("'", `${htmlEntityPrefix}#39;`)
  .replaceAll('\n', '<br/>')

const toChatRole = (raw: unknown): ChatMessage['role'] => {
  const role = String(raw || '').trim().toLowerCase()
  if (role === 'assistant') return 'assistant'
  if (role === 'system' || role === 'tool') return 'system'
  return 'user'
}

const normalizeGenerationStatus = (raw: unknown): GenerationTaskStatus | '' => {
  const status = String(raw || '').trim().toLowerCase()
  return (['pending', 'running', 'waiting_approval', 'done', 'applied', 'failed', 'cancelled'] as const).includes(status as GenerationTaskStatus)
    ? (status as GenerationTaskStatus)
    : ''
}

const parseSseData = (event: MessageEvent<string>) => {
  try {
    return JSON.parse(event.data || '{}') as ChatRecord
  } catch {
    return {} as ChatRecord
  }
}

const pickConversationId = (item: ChatRecord) => Number(item.conversationId ?? 0)

const pickToolCallId = (item: ChatRecord): string | undefined => {
  const toolCallId = String(item.toolCallId ?? item.tool_call_id ?? '').trim()
  return toolCallId || undefined
}

const normalizeApprovalResolution = (raw: unknown) => {
  const status = String(raw || '').trim().toLowerCase()
  if (status === 'approved') {
    return { resolved: true, resolvedAction: 'approved' as const }
  }
  if (status === 'rejected') {
    return { resolved: true, resolvedAction: 'rejected' as const }
  }
  return { resolved: false, resolvedAction: undefined }
}

const pickApprovalPreview = (item: ChatRecord): Record<string, string> | undefined => {
  const raw = item.approvalPreview ?? item.preview
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return undefined

  const preview = Object.fromEntries(
    Object.entries(raw as Record<string, unknown>)
      .filter(([, value]) => value !== null && value !== undefined && String(value) !== '')
      .map(([key, value]) => [key, String(value)]),
  ) as Record<string, string>

  return Object.keys(preview).length ? preview : undefined
}

const buildApprovalCard = (item: ChatRecord): ApprovalCardData | undefined => {
  const approvalId = Number(item.approvalId ?? 0)
  if (!approvalId) return undefined

  const approvalType = String(item.approvalType || '').trim()
  const approvalMessage = String(item.approvalMessage || '').trim() || (approvalType ? `检测到待审批变更（${approvalType}）` : '检测到待审批变更')
  const approvalTime = String(item.approvalTime || item.reviewedAt || item.updatedAt || item.createdAt || '')
  const { resolved, resolvedAction } = normalizeApprovalResolution(item.approvalStatus ?? item.status)

  return {
    id: String(approvalId),
    message: approvalMessage,
    time: approvalTime,
    preview: pickApprovalPreview(item),
    resolved,
    ...(resolvedAction ? { resolvedAction } : {}),
  }
}

const applyAssistantEventMetadata = (assistantMsg: ChatMessage, item: ChatRecord) => {
  const toolCallId = pickToolCallId(item)
  if (toolCallId) assistantMsg.toolCallId = toolCallId

  const approval = buildApprovalCard(item)
  if (approval) assistantMsg.approval = approval
}

const getErrorMessage = (error: unknown, fallback = '未知错误') => {
  if (typeof error === 'string') return error
  if (error && typeof error === 'object' && 'message' in error) {
    const message = String((error as { message?: unknown }).message || '').trim()
    if (message) return message
  }
  return fallback
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
  const streamingAssistantMsgId = ref<number | null>(null)
  const currentConversationId = ref<number | null>(null)
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

  const closeGenerationStream = () => {
    if (generationStream) {
      deps.closeGenerationStream?.(generationStream)
      generationStream.close()
      generationStream = null
    }
  }

  const waitForPolling = () => deps.waitForPolling?.() ?? new Promise<void>((resolve) => {
    setTimeout(resolve, DEFAULT_POLLING_INTERVAL_MS)
  })

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

  const mapApiMessage = (item: ChatRecord): ChatMessage => {
    const approval = buildApprovalCard(item)
    const toolCallId = pickToolCallId(item)

    return {
      id: Number(item.messageId ?? msgIdCounter++),
      role: toChatRole(item.role),
      text: escapeHtml(String(item.contentMd || item.content || item.text || '')),
      ...(toolCallId ? { toolCallId } : {}),
      ...(approval ? { approval } : {}),
    }
  }

  const scrollChat = async () => {
    await deps.nextTick()
    deps.scrollChat()
  }

  const loadConversationMessages = async (projectId: number, conversationId: number) => {
    const list = (await deps.listMessages(projectId, conversationId)) as Array<ChatRecord>
    messages.value = (Array.isArray(list) ? list : []).map(mapApiMessage)
    const maxId = messages.value.reduce((max, item) => (item.id > max ? item.id : max), 0)
    if (maxId >= msgIdCounter) msgIdCounter = maxId + 1
    currentConversationId.value = conversationId
    await scrollChat()
  }

  const loadConversationList = async (projectId: number) => {
    if (!projectId) {
      conversationList.value = []
      return
    }

    conversationLoading.value = true
    try {
      const conversations = (await deps.listConversations(projectId)) as Array<ChatRecord>
      conversationList.value = (Array.isArray(conversations) ? conversations : [])
        .map((item) => ({
          conversationId: pickConversationId(item),
          title: String(item.title || ''),
          updatedAt: String(item.updatedAt || item.createdAt || ''),
        }))
        .filter((item) => item.conversationId > 0)
    } finally {
      conversationLoading.value = false
    }
  }

  const selectConversation = async (conversationId: number) => {
    const projectId = deps.getCurrentProjectId()
    if (!projectId || !conversationId) return
    currentConversationId.value = conversationId
    await loadConversationMessages(projectId, conversationId)
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
      const conversationId = await deps.ensureConversationId(projectId, operatorId)
      if (!conversationId) {
        messages.value = []
        currentConversationId.value = null
        return
      }
      currentConversationId.value = conversationId
      await loadConversationMessages(projectId, conversationId)
      await loadConversationList(projectId)
    } catch {
      messages.value = []
      currentConversationId.value = null
    }
  }

  const pollGenerationAsFallback = async (projectId: number, taskId: number, assistantMsg?: ChatMessage) => {
    let status: GenerationTaskStatus | '' = ''
    for (let i = 0; i < 12; i += 1) {
      const latest = (await deps.getGeneration(projectId, taskId)) as ChatRecord
      status = normalizeGenerationStatus(latest?.status)
      if (status) generationTaskStatus.value = status
      if (status === 'waiting_approval') {
        if (assistantMsg) applyAssistantEventMetadata(assistantMsg, latest)
        generationPhase.value = 'waiting_approval'
        return status
      }
      if (status && TERMINAL_GENERATION_STATUSES.includes(status)) {
        return status
      }
      if (i < 11) {
        await waitForPolling()
      }
    }
    throw new Error(`生成任务轮询超时，状态：${status || 'unknown'}`)
  }

  const consumeGenerationStream = (projectId: number, taskId: number, assistantMsg: ChatMessage) => new Promise<GenerationTaskStatus | ''>((resolve, reject) => {
    closeGenerationStream()
    generationStream = deps.openGenerationStream(projectId, taskId)
    let settled = false

    const settleResolve = (status: GenerationTaskStatus | '') => {
      if (settled) return
      settled = true
      closeGenerationStream()
      resolve(status)
    }

    const settleReject = (error: Error) => {
      if (settled) return
      settled = true
      closeGenerationStream()
      reject(error)
    }

    deps.addStreamListener(generationStream, 'generation.started', () => {
      generationPhase.value = 'streaming'
      generationTaskStatus.value = 'running'
    })
    deps.addStreamListener(generationStream, 'generation.token', (event) => {
      const payload = parseSseData(event)
      const token = String(payload.token || '')
      if (!token) return
      assistantMsg.text += escapeHtml(token)
      deps.scrollChat()
    })
    deps.addStreamListener(generationStream, 'generation.tool_call', (event) => {
      const payload = parseSseData(event)
      applyAssistantEventMetadata(assistantMsg, payload)
      if (normalizeGenerationStatus(payload.status) === 'waiting_approval') {
        generationPhase.value = 'waiting_approval'
        generationTaskStatus.value = 'waiting_approval'
      }
      deps.scrollChat()
    })
    deps.addStreamListener(generationStream, 'generation.waiting_approval', (event) => {
      const payload = parseSseData(event)
      applyAssistantEventMetadata(assistantMsg, payload)
      generationPhase.value = 'waiting_approval'
      generationTaskStatus.value = 'waiting_approval'
      deps.scrollChat()
    })
    deps.addStreamListener(generationStream, 'generation.done', (event) => {
      const payload = parseSseData(event)
      const status = normalizeGenerationStatus(payload.status) || 'done'
      generationTaskStatus.value = status
      settleResolve(status)
    })
    deps.addStreamListener(generationStream, 'generation.failed', (event) => {
      const payload = parseSseData(event)
      generationTaskStatus.value = 'failed'
      settleReject(new Error(String(payload.errorMsg || payload.errorCode || '生成失败')))
    })
  })

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
      const conversationId = await deps.ensureConversationId(Number(projectId), Number(operatorId))
      if (!conversationId) throw new Error('会话初始化失败')

      const modelConfigId = await deps.ensureModelConfigId(Number(projectId))
      if (!modelConfigId) {
        deps.onRequireModelSelection?.()
        throw new Error('未选择可用模型，请先在模型设置中保存并切换模型')
      }

      await deps.createMessage(Number(projectId), conversationId, Number(operatorId), {
        role: 'user',
        userMessageType: 'COMMAND',
        contentMd: userText,
        attachmentsJson: '[]',
        toolCallsJson: '[]',
      })

      const generation = (await deps.createGeneration(Number(projectId), Number(operatorId), {
        conversationId,
        chapterId: Number(deps.getActiveChapterKey()) || null,
        modelConfigId,
        taskType: 'WRITE',
        promptSnapshot: userText,
        styleProfileSnapshot: '',
        pluginSnapshot: JSON.stringify(deps.getActivePlugins() || []),
      })) as ChatRecord

      const taskId = Number(generation.taskId ?? 0)
      if (!taskId) throw new Error('任务创建失败，缺少 taskId')

      generationTaskStatus.value = normalizeGenerationStatus(generation.status) || 'pending'
      generationPhase.value = 'streaming'

      assistantMsg = { id: msgIdCounter++, role: 'assistant', text: '' }
      messages.value.push(assistantMsg)
      streamingAssistantMsgId.value = assistantMsg.id
      await scrollChat()

      let finalStatus: GenerationTaskStatus | ''
      try {
        finalStatus = await consumeGenerationStream(Number(projectId), taskId, assistantMsg)
      } catch (streamError: any) {
        if (!deps.enablePollingFallback) {
          throw streamError
        }
        finalStatus = await pollGenerationAsFallback(Number(projectId), taskId, assistantMsg)
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
      const failureText = `生成失败：${getErrorMessage(error)}`
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
      closeGenerationStream()
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
    pollGenerationAsFallback,
    consumeGenerationStream,
    scrollChat,
  }
}
