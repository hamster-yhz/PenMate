import { ref } from 'vue'

type ContextProfile = {
  projectId?: number | string | null
  operatorId?: number | string | null
}

type CardRecord = Record<string, any>
type RelationRecord = Record<string, any>
type CardApiPayload = Record<string, unknown>

type UseWorkbenchCardsDeps = {
  getContext: () => ContextProfile
  listCards: (projectId: number | string) => Promise<CardRecord[]>
  listCardRelations: (projectId: number | string) => Promise<RelationRecord[]>
  createCard: (projectId: number | string, operatorId: number | string, payload: CardApiPayload) => Promise<unknown>
  updateCard: (projectId: number | string, cardId: number | string, operatorId: number | string, payload: CardApiPayload) => Promise<unknown>
  deleteCard: (projectId: number | string, cardId: number | string, operatorId: number | string) => Promise<unknown>
  createCardRelation: (projectId: number | string, operatorId: number | string, payload: CardApiPayload) => Promise<unknown>
  deleteCardRelation: (projectId: number | string, relationId: number | string, operatorId: number | string) => Promise<unknown>
  promptCardName: (defaultName: string) => string | null | undefined
  notify?: (message: string) => void
  notifySuccess?: (message: string) => void
}

export type WorkbenchCardType = 'CHARACTER' | 'WORLD'

const pickCardId = (item: CardRecord) => Number(item.cardId ?? 0)
const pickRelationId = (item: RelationRecord) => Number(item.cardRelationId ?? 0)

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

const withExpandedState = (cards: CardRecord[]) => cards.map((item) => ({ ...item, expanded: false }))

export const useWorkbenchCards = (deps: UseWorkbenchCardsDeps) => {
  const projectCards = ref<CardRecord[]>([])
  const cardRelations = ref<RelationRecord[]>([])
  const relationFromId = ref('')
  const relationToId = ref('')
  const relationType = ref('')

  const loadCardsAndRelations = async (projectId: number | string) => {
    if (!projectId) return

    try {
      const [cards, relations] = await Promise.all([
        deps.listCards(projectId),
        deps.listCardRelations(projectId),
      ])
      projectCards.value = withExpandedState((cards || []) as CardRecord[])
      cardRelations.value = (relations || []) as RelationRecord[]
    } catch {
      projectCards.value = []
      cardRelations.value = []
    }
  }

  const cardNameById = (idLike: string) => {
    const hit = projectCards.value.find((item) => String(pickCardId(item)) === String(idLike))
    return String(hit?.name || `卡片#${idLike}`)
  }

  const updateCardDraft = (nextCard: CardRecord) => {
    const cardId = pickCardId(nextCard)
    if (!cardId) return

    const index = projectCards.value.findIndex((item) => pickCardId(item) === cardId)
    if (index < 0) return

    projectCards.value.splice(index, 1, {
      ...projectCards.value[index],
      ...nextCard,
    })
  }

  const toggleCardExpanded = ({ cardId, expanded }: { cardId: number; expanded: boolean }) => {
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

  const saveCard = async (card: CardRecord) => {
    const { projectId, operatorId } = deps.getContext()
    const cardId = pickCardId(card)
    if (!projectId || !operatorId || !cardId) return

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

  const deleteCardById = async (card: CardRecord) => {
    const { projectId, operatorId } = deps.getContext()
    const cardId = pickCardId(card)
    if (!projectId || !operatorId || !cardId) return

    try {
      await deps.deleteCard(projectId, cardId, operatorId)
      await loadCardsAndRelations(projectId)
    } catch (error: unknown) {
      deps.notify?.(error instanceof Error ? error.message : '删除资料卡失败')
    }
  }

  const createRelation = async () => {
    const { projectId, operatorId } = deps.getContext()
    const fromCardId = Number(relationFromId.value)
    const toCardId = Number(relationToId.value)
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

  const deleteRelationById = async (relation: RelationRecord) => {
    const { projectId, operatorId } = deps.getContext()
    const relationId = pickRelationId(relation)
    if (!projectId || !operatorId || !relationId) return

    try {
      await deps.deleteCardRelation(projectId, relationId, operatorId)
      await loadCardsAndRelations(projectId)
    } catch (error: unknown) {
      deps.notify?.(error instanceof Error ? error.message : '删除关系失败')
    }
  }

  return {
    projectCards,
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
