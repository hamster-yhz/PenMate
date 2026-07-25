import type { ApprovalCardData } from '@/components/workbench/approvalCard.types'
import type { OutlineChapterNode, OutlineVolumeNode } from '@/composables/workbench/workbenchOutline'

export type GenerationPhase = 'idle' | 'preparing' | 'streaming' | 'waiting_approval' | 'failed'

export type ConversationItem = {
  conversationId: string
  title: string
  updatedAt: string
  lastMessageAt?: string
  status?: string
  lastRunStatus?: string
  deletedAt?: string | null
}

export type WorkbenchSkillCatalogItem = {
  name: string
  description: string
}

export type ChatMessage = {
  id: number | string
  role: 'user' | 'assistant' | 'system'
  text: string
  turnId?: string
  runId?: string
  createdAt?: string
  toolCallId?: string
  approval?: ApprovalCardData
}

export type RunConnectionState = 'idle' | 'connecting' | 'connected' | 'reconnecting' | 'gap' | 'closed'

export type AgentTimelineEvent = {
  eventId?: string
  runId: string
  turnId: string
  sequence: number
  liveOrder?: number
  type: string
  payload: Record<string, unknown>
  createdAt?: string
}

export type AgentRunOutput = {
  text: string
  offset: number
  sequence: number | null
  state: string
  updatedAt?: string | null
}

export type AgentRunAttempt = {
  runId: string
  turnId: string
  predecessorRunId?: string | null
  runStatus: string
  runPhase: string
  attemptCount: number
  lastErrorCode?: string | null
  lastErrorMessage?: string | null
  latestSequence: number
  startedAt?: string | null
  finishedAt?: string | null
  connectionState: RunConnectionState
  output?: AgentRunOutput | null
  events: AgentTimelineEvent[]
}

export type EditorWrapSelectionPayload = [before: string, after: string]

export type WorkbenchOutlineData = OutlineVolumeNode[]
export type WorkbenchOutlineChapter = OutlineChapterNode
