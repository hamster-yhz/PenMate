import type { ApprovalCardData } from '@/components/workbench/approvalCard.types'
import type { OutlineChapterNode, OutlineVolumeNode } from '@/composables/workbench/workbenchOutline'

export type GenerationPhase = 'idle' | 'preparing' | 'streaming' | 'waiting_approval' | 'failed'

export type ConversationItem = {
  conversationId: string
  title: string
  updatedAt: string
}

export type ChatMessage = {
  id: number | string
  role: 'user' | 'assistant' | 'system'
  text: string
  toolCallId?: string
  approval?: ApprovalCardData
}

export type EditorWrapSelectionPayload = [before: string, after: string]

export type WorkbenchOutlineData = OutlineVolumeNode[]
export type WorkbenchOutlineChapter = OutlineChapterNode
