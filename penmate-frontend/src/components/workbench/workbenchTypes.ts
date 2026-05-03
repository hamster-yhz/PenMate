import type { ApprovalCardData } from '@/components/workbench/ApprovalCard.vue'
import type { OutlineChapterNode, OutlineVolumeNode } from '@/composables/workbench/workbenchOutline'

export type WorkbenchCardBase = {
  cardId: number
  name: string
  summary: string
  detailJson: string
  expanded: boolean
}

export type CharacterCard = WorkbenchCardBase & {
  cardType: 'CHARACTER'
}

export type WorldCard = WorkbenchCardBase & {
  cardType: 'WORLD'
}

export type WorkbenchCard = CharacterCard | WorldCard

export type CardRelation = {
  cardRelationId: number
  fromCardId: number
  toCardId: number
  relationType: string
  description?: string
}

export type GenerationPhase = 'idle' | 'preparing' | 'streaming' | 'waiting_approval' | 'failed'

export type ConversationItem = {
  conversationId: number
  title: string
  updatedAt: string
}

export type ChatMessage = {
  id: number
  role: 'user' | 'assistant' | 'system'
  text: string
  toolCallId?: string
  approval?: ApprovalCardData
}

export type EditorWrapSelectionPayload = [before: string, after: string]

export type WorkbenchOutlineData = OutlineVolumeNode[]
export type WorkbenchOutlineChapter = OutlineChapterNode
