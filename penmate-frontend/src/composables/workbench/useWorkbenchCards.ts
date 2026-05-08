import { computed, ref } from 'vue'
import type { CardRelation, CharacterCard, WorldCard } from '@/components/workbench/workbenchTypes'

type ContextProfile = {
  projectId?: string | null
  operatorId?: string | null
}

type CardRecord = Record<string, any>
type RelationRecord = Record<string, any>
type CardApiPayload = Record<string, unknown>

type UseWorkbenchCardsDeps = {
  getContext: () => ContextProfile
  listCards: (projectId: string) => Promise<CardRecord[]>
  listCardRelations: (projectId: string) => Promise<RelationRecord[]>
  createCard: (projectId: string, operatorId: string, payload: CardApiPayload) => Promise<unknown>
  updateCard: (projectId: string, cardId: string, operatorId: string, payload: CardApiPayload) => Promise<unknown>
  deleteCard: (projectId: string, cardId: string, operatorId: string) => Promise<unknown>
  createCardRelation: (projectId: string, operatorId: string, payload: CardApiPayload) => Promise<unknown>
  deleteCardRelation: (projectId: string, relationId: string, operatorId: string) => Promise<unknown>
  promptCardName: (defaultName: string) => string | null | undefined
  notify?: (message: string) => void
  notifySuccess?: (message: string) => void
}

export type WorkbenchCardType = 'CHARACTER' | 'WORLD'

const toBusinessId = (value: unknown) => {
  if (typeof value !== 'string') {
    return ''
  }
  const normalized = value.trim()
  return normalized || ''
}

const pickCardId = (item: CardRecord | CharacterCard | WorldCard) => toBusinessId(item.cardId)
const pickRelationId = (item: RelationRecord | CardRelation) => toBusinessId(item.cardRelationId)

const normalizeCardType = (value: unknown): WorkbenchCardType | '' => {
  const normalized = String(value || '').trim().toUpperCase()
  return normalized === 'CHARACTER' || normalized === 'WORLD' ? normalized : ''
}

const normalizeDetailJsonInput = (value: unknown) => {
  const text = String(value ?? '').trim()
  if (!text) return ''

  try {
    return JSON.stringify(JSON.parse(text))
  } catch {
    return null
  }
}

const toWorkbenchCard = (item: CardRecord): CharacterCard | WorldCard | null => {
  const cardId = toBusinessId(item.cardId)
  const cardType = normalizeCardType(item.cardType)
  if (!cardId || !cardType) return null

  const baseCard = {
    cardId,
    cardType,
    name: String(item.name ?? ''),
    summary: String(item.summary ?? ''),
    detailJson: String(item.detailJson ?? ''),
    expanded: Boolean(item.expanded),
  }

  return cardType === 'CHARACTER'
    ? ({ ...baseCard, cardType: 'CHARACTER' } satisfies CharacterCard)
    : ({ ...baseCard, cardType: 'WORLD' } satisfies WorldCard)
}

const withExpandedState = (cards: CardRecord[]) => cards
  .map((item) => toWorkbenchCard({ ...item, expanded: false }))
  .filter((item): item is CharacterCard | WorldCard => item !== null)

const toCardRelation = (item: RelationRecord): CardRelation | null => {
  const cardRelationId = toBusinessId(item.cardRelationId)
  const fromCardId = toBusinessId(item.fromCardId)
  const toCardId = toBusinessId(item.toCardId)
  const relationType = String(item.relationType ?? '').trim()
  if (!cardRelationId || !fromCardId || !toCardId || !relationType) return null

  return {
    cardRelationId,
    fromCardId,
    toCardId,
    relationType,
    description: String(item.description ?? ''),
  }
}

export const useWorkbenchCards = (deps: UseWorkbenchCardsDeps) => {
  const projectCards = ref<Array<CharacterCard | WorldCard>>([])
  const cardRelations = ref<CardRelation[]>([])
  const relationFromId = ref('')
  const relationToId = ref('')
  const relationType = ref('')

  const loadCardsAndRelations = async (projectId: string) => {
    if (!projectId) return

    try {
      const [cards, relations] = await Promise.all([
        deps.listCards(projectId),
        deps.listCardRelations(projectId),
      ])
      projectCards.value = withExpandedState((cards || []) as CardRecord[])
      cardRelations.value = (relations || [])
        .map((item) => toCardRelation(item as RelationRecord))
        .filter((item): item is CardRelation => item !== null)
    } catch (error: unknown) {
      deps.notify?.(error instanceof Error ? error.message : '加载资料卡失败')
    }
  }

  const cardNameById = (idLike: string) => {
    const hit = projectCards.value.find((item) => pickCardId(item) === idLike)
    return String(hit?.name || `卡片#${idLike}`)
  }

  const updateCardDraft = (nextCard: CharacterCard | WorldCard) => {
    const cardId = pickCardId(nextCard)
    if (!cardId) return

    const index = projectCards.value.findIndex((item) => pickCardId(item) === cardId)
    if (index < 0) return

    projectCards.value.splice(index, 1, {
      ...projectCards.value[index],
      ...nextCard,
    })
  }

  const toggleCardExpanded = ({ cardId, expanded }: { cardId: string; expanded: boolean }) => {
    const index = projectCards.value.findIndex((item) => pickCardId(item) === cardId)
    if (index < 0) return

    projectCards.value.splice(index, 1, {
      ...projectCards.value[index],
      expanded,
    })
  }

  const createCardQuick = async (cardType: WorkbenchCardType) => {
    const { projectId, operatorId } = deps.getContext()
    if (!projectId || !operatorId) {
      deps.notify?.('缺少 projectId/operatorId，无法创建资料卡')
      return
    }

    const normalizedType = normalizeCardType(cardType)
    if (!normalizedType) {
      deps.notify?.('卡片类型非法，仅支持 CHARACTER/WORLD')
      return
    }

    const defaultName = normalizedType === 'CHARACTER' ? '新角色' : '新世界设定'
    const enteredName = deps.promptCardName(defaultName)
    const trimmedName = String(enteredName ?? '').trim()
    if (!trimmedName) {
      deps.notify?.('卡片名称不能为空')
      return
    }

    try {
      await deps.createCard(projectId, operatorId, {
        cardType: normalizedType,
        name: trimmedName,
        summary: '',
        detailJson: '{}',
      })
      await loadCardsAndRelations(projectId)
    } catch (error: unknown) {
      deps.notify?.(error instanceof Error ? error.message : '创建资料卡失败')
    }
  }

  const saveCard = async (card: CharacterCard | WorldCard) => {
    const { projectId, operatorId } = deps.getContext()
    const cardId = pickCardId(card)
    if (!projectId || !operatorId || !cardId) {
      deps.notify?.('缺少 projectId/operatorId/cardId，无法保存资料卡')
      return
    }

    const cardType = normalizeCardType(card.cardType)
    if (!cardType) {
      deps.notify?.('卡片类型非法，仅支持 CHARACTER/WORLD')
      return
    }

    const cardName = String(card.name || '').trim()
    if (!cardName) {
      deps.notify?.('卡片名称不能为空')
      return
    }

    const detailJson = normalizeDetailJsonInput(card.detailJson)
    if (detailJson === null) {
      deps.notify?.('详情(JSON)格式不合法，请输入合法 JSON')
      return
    }

    try {
      await deps.updateCard(projectId, cardId, operatorId, {
        cardType,
        name: cardName,
        summary: card.summary,
        detailJson,
      })
      deps.notifySuccess?.('资料卡已保存')
    } catch (error: unknown) {
      deps.notify?.(error instanceof Error ? error.message : '保存资料卡失败')
    }
  }

  const deleteCardById = async (card: CharacterCard | WorldCard) => {
    const { projectId, operatorId } = deps.getContext()
    const cardId = pickCardId(card)
    if (!projectId || !operatorId || !cardId) {
      deps.notify?.('缺少 projectId/operatorId/cardId，无法删除资料卡')
      return
    }

    try {
      await deps.deleteCard(projectId, cardId, operatorId)
      await loadCardsAndRelations(projectId)
    } catch (error: unknown) {
      deps.notify?.(error instanceof Error ? error.message : '删除资料卡失败')
    }
  }

  const createRelation = async () => {
    const { projectId, operatorId } = deps.getContext()
    const fromCardId = relationFromId.value.trim()
    const toCardId = relationToId.value.trim()
    const relationTypeValue = relationType.value.trim()

    if (!projectId || !operatorId || !fromCardId || !toCardId || !relationTypeValue) {
      deps.notify?.('请补全来源/目标/关系类型')
      return
    }

    try {
      await deps.createCardRelation(projectId, operatorId, {
        fromCardId,
        toCardId,
        relationType: relationTypeValue,
        description: '',
      })
      relationType.value = ''
      await loadCardsAndRelations(projectId)
    } catch (error: unknown) {
      deps.notify?.(error instanceof Error ? error.message : '创建关系失败')
    }
  }

  const deleteRelationById = async (relation: CardRelation) => {
    const { projectId, operatorId } = deps.getContext()
    const relationId = pickRelationId(relation)
    if (!projectId || !operatorId || !relationId) {
      deps.notify?.('缺少 projectId/operatorId/relationId，无法删除关系')
      return
    }

    try {
      await deps.deleteCardRelation(projectId, relationId, operatorId)
      await loadCardsAndRelations(projectId)
    } catch (error: unknown) {
      deps.notify?.(error instanceof Error ? error.message : '删除关系失败')
    }
  }

  const characterCards = computed(() => projectCards.value.filter((item): item is CharacterCard => item.cardType === 'CHARACTER'))
  const worldCards = computed(() => projectCards.value.filter((item): item is WorldCard => item.cardType === 'WORLD'))

  return {
    projectCards,
    characterCards,
    worldCards,
    cardRelations,
    relationFromId,
    relationToId,
    relationType,
    loadCardsAndRelations,
    createCardQuick,
    saveCard,
    deleteCardById,
    createRelation,
    deleteRelationById,
    cardNameById,
    updateCardDraft,
    toggleCardExpanded,
  }
}
