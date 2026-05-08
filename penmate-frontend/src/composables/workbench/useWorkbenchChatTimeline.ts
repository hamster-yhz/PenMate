import type { ApprovalCardData } from '@/components/workbench/ApprovalCard.vue'
import type { ChatMessage, ConversationItem } from '@/components/workbench/workbenchTypes'

export type ChatRecord = Record<string, unknown>

const htmlEntityPrefix = String.fromCharCode(38)

export const escapeHtml = (value: string) => String(value || '')
  .replaceAll('&', `${htmlEntityPrefix}amp;`)
  .replaceAll('<', `${htmlEntityPrefix}lt;`)
  .replaceAll('>', `${htmlEntityPrefix}gt;`)
  .replaceAll('"', `${htmlEntityPrefix}quot;`)
  .replaceAll("'", `${htmlEntityPrefix}#39;`)
  .replaceAll('\n', '<br/>')

export const toChatRole = (raw: unknown): ChatMessage['role'] => {
  const role = String(raw || '').trim().toLowerCase()
  if (role === 'assistant') return 'assistant'
  if (role === 'system' || role === 'tool') return 'system'
  return 'user'
}

export const pickConversationId = (item: ChatRecord) => String(item.sessionId ?? '').trim()

export const pickToolCallId = (item: ChatRecord): string | undefined => {
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

export const buildApprovalCard = (item: ChatRecord): ApprovalCardData | undefined => {
  const approvalId = String(item.approvalId ?? '').trim()
  if (!approvalId || approvalId === '0') return undefined

  const approvalType = String(item.approvalType || '').trim()
  const toolDisplayName = String(item.toolDisplayName || '').trim() || undefined
  const toolCode = String(item.toolCode || '').trim() || undefined
  const operationCode = String(item.operationCode || '').trim() || undefined
  const riskLevel = Number(item.riskLevel)
  const approvalMessage = String(item.approvalMessage || '').trim()
    || (toolDisplayName
      ? `检测到待审批工具变更（${toolDisplayName}）`
      : approvalType
        ? `检测到待审批变更（${approvalType}）`
        : '检测到待审批变更')
  const approvalTime = String(item.approvalTime || item.reviewedAt || item.updatedAt || item.createdAt || '')
  const { resolved, resolvedAction } = normalizeApprovalResolution(item.approvalStatus ?? item.status)

  return {
    id: approvalId,
    message: approvalMessage,
    time: approvalTime,
    preview: pickApprovalPreview(item),
    ...(toolCode ? { toolCode } : {}),
    ...(toolDisplayName ? { toolDisplayName } : {}),
    ...(Number.isFinite(riskLevel) ? { riskLevel } : {}),
    ...(operationCode ? { operationCode } : {}),
    resolved,
    ...(resolvedAction ? { resolvedAction } : {}),
  }
}

export const applyAssistantEventMetadata = (assistantMsg: ChatMessage, item: ChatRecord) => {
  const toolCallId = pickToolCallId(item)
  if (toolCallId) assistantMsg.toolCallId = toolCallId

  const approval = buildApprovalCard(item)
  if (approval) assistantMsg.approval = approval
}

export const createChatTimeline = (deps: {
  getMessages: () => ChatMessage[]
  setMessages: (messages: ChatMessage[]) => void
  getMsgIdCounter: () => number
  setMsgIdCounter: (value: number) => void
  listConversations: (projectId: string) => Promise<unknown>
  listMessages: (projectId: string, conversationId: string) => Promise<unknown>
  setConversationList: (items: ConversationItem[]) => void
  setConversationLoading: (value: boolean) => void
  setCurrentConversationId: (value: string | null) => void
  scrollChat: () => Promise<void>
}) => {
  const mapApiMessage = (item: ChatRecord): ChatMessage => {
    const approval = buildApprovalCard(item)
    const toolCallId = pickToolCallId(item)

    const messageId = String(item.messageId ?? '').trim()
    return {
      id: messageId || deps.getMsgIdCounter(),
      role: toChatRole(item.role),
      text: escapeHtml(String(item.contentMd || item.content || item.text || '')),
      ...(toolCallId ? { toolCallId } : {}),
      ...(approval ? { approval } : {}),
    }
  }

  const loadConversationMessages = async (projectId: string, conversationId: string) => {
    const list = (await deps.listMessages(projectId, conversationId)) as Array<ChatRecord>
    const mapped = (Array.isArray(list) ? list : []).map(mapApiMessage)
    deps.setMessages(mapped)
    const maxNumericId = mapped.reduce((max, item) => {
      const numericId = typeof item.id === 'number' ? item.id : Number.NaN
      return Number.isFinite(numericId) && numericId > max ? numericId : max
    }, 0)
    if (maxNumericId >= deps.getMsgIdCounter()) deps.setMsgIdCounter(maxNumericId + 1)
    deps.setCurrentConversationId(conversationId)
    await deps.scrollChat()
  }

  const loadConversationList = async (projectId: string) => {
    if (!projectId) {
      deps.setConversationList([])
      return
    }

    deps.setConversationLoading(true)
    try {
      const conversations = (await deps.listConversations(projectId)) as Array<ChatRecord>
      deps.setConversationList(
        (Array.isArray(conversations) ? conversations : [])
          .map((item) => ({
            conversationId: pickConversationId(item),
            title: String(item.title || ''),
            updatedAt: String(item.updatedAt || item.createdAt || ''),
          }))
          .filter((item) => !!item.conversationId),
      )
    } finally {
      deps.setConversationLoading(false)
    }
  }

  return {
    mapApiMessage,
    loadConversationMessages,
    loadConversationList,
  }
}
