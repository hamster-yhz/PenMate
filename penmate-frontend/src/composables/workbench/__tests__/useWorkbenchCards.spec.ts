import { describe, expect, it, vi } from 'vitest'

type UseWorkbenchCardsModule = typeof import('../useWorkbenchCards')

const loadUseWorkbenchCards = async (): Promise<UseWorkbenchCardsModule['useWorkbenchCards']> => {
  try {
    const modulePath = '../useWorkbenchCards'
    return (await import(/* @vite-ignore */ modulePath)).useWorkbenchCards
  } catch {
    return ((deps: any) => ({
      projectCards: { value: [] },
      cardRelations: { value: [] },
      relationFromId: { value: '' },
      relationToId: { value: '' },
      relationType: { value: '' },
      loadCardsAndRelations: async () => undefined,
      createCardQuick: async () => undefined,
      saveCard: async () => undefined,
      deleteCardById: async () => undefined,
      createRelation: async () => undefined,
      deleteRelationById: async () => undefined,
      cardNameById: (idLike: string) => `卡片#${idLike}`,
      updateCardDraft: () => undefined,
      toggleCardExpanded: () => undefined,
      __deps: deps,
    })) as UseWorkbenchCardsModule['useWorkbenchCards']
  }
}

describe('useWorkbenchCards', () => {
  it('loads_cards_and_relations_with_expanded_state_reset_and_name_lookup', async () => {
    const useWorkbenchCards = await loadUseWorkbenchCards()
    const listCards = vi.fn(async () => [
      { cardId: 11, cardType: 'CHARACTER', name: '林霜', summary: '剑客', detailJson: '{}' },
      { cardId: 12, cardType: 'WORLD', name: '北境', summary: '地理', detailJson: '{}' },
    ])
    const listCardRelations = vi.fn(async () => [
      { cardRelationId: 91, fromCardId: 11, toCardId: 12, relationType: '守护' },
    ])

    const cards = useWorkbenchCards({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      listCards,
      listCardRelations,
      createCard: vi.fn(async () => ({})),
      updateCard: vi.fn(async () => ({})),
      deleteCard: vi.fn(async () => undefined),
      createCardRelation: vi.fn(async () => ({})),
      deleteCardRelation: vi.fn(async () => undefined),
      promptCardName: vi.fn(() => '新角色'),
    })

    await cards.loadCardsAndRelations(101)

    expect(listCards).toHaveBeenCalledWith(101)
    expect(listCardRelations).toHaveBeenCalledWith(101)
    expect(cards.projectCards.value).toEqual([
      { cardId: 11, cardType: 'CHARACTER', name: '林霜', summary: '剑客', detailJson: '{}', expanded: false },
      { cardId: 12, cardType: 'WORLD', name: '北境', summary: '地理', detailJson: '{}', expanded: false },
    ])
    expect(cards.cardRelations.value).toEqual([
      { cardRelationId: 91, fromCardId: 11, toCardId: 12, relationType: '守护' },
    ])
    expect(cards.cardNameById('11')).toBe('林霜')
    expect(cards.cardNameById('999')).toBe('卡片#999')
  })

  it('creates_card_after_prompt_and_reloads_cards', async () => {
    const useWorkbenchCards = await loadUseWorkbenchCards()
    const createCard = vi.fn(async () => ({}))
    const listCards = vi.fn(async () => [
      { cardId: 31, cardType: 'CHARACTER', name: '沈砚', summary: '', detailJson: '{}' },
    ])
    const listCardRelations = vi.fn(async () => [])
    const promptCardName = vi.fn(() => '沈砚')

    const cards = useWorkbenchCards({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      listCards,
      listCardRelations,
      createCard,
      updateCard: vi.fn(async () => ({})),
      deleteCard: vi.fn(async () => undefined),
      createCardRelation: vi.fn(async () => ({})),
      deleteCardRelation: vi.fn(async () => undefined),
      promptCardName,
    })

    await cards.createCardQuick('CHARACTER')

    expect(promptCardName).toHaveBeenCalledWith('新角色')
    expect(createCard).toHaveBeenCalledWith(101, 201, {
      cardType: 'CHARACTER',
      name: '沈砚',
      summary: '',
      detailJson: '{}',
    })
    expect(cards.projectCards.value).toEqual([
      { cardId: 31, cardType: 'CHARACTER', name: '沈砚', summary: '', detailJson: '{}', expanded: false },
    ])
  })

  it('validates_card_before_saving_and_notifies_on_invalid_json', async () => {
    const useWorkbenchCards = await loadUseWorkbenchCards()
    const notify = vi.fn()
    const updateCard = vi.fn(async () => ({}))

    const cards = useWorkbenchCards({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      listCards: vi.fn(async () => []),
      listCardRelations: vi.fn(async () => []),
      createCard: vi.fn(async () => ({})),
      updateCard,
      deleteCard: vi.fn(async () => undefined),
      createCardRelation: vi.fn(async () => ({})),
      deleteCardRelation: vi.fn(async () => undefined),
      promptCardName: vi.fn(() => '新角色'),
      notify,
    })

    await cards.saveCard({
      cardId: 11,
      cardType: 'CHARACTER',
      name: '林霜',
      summary: '剑客',
      detailJson: '{invalid-json}',
      expanded: true,
    })

    expect(updateCard).not.toHaveBeenCalled()
    expect(notify).toHaveBeenCalledWith('详情(JSON)格式不合法，请输入合法 JSON')
  })

  it('saves_card_with_normalized_json_and_success_notice', async () => {
    const useWorkbenchCards = await loadUseWorkbenchCards()
    const notifySuccess = vi.fn()
    const updateCard = vi.fn(async () => ({}))

    const cards = useWorkbenchCards({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      listCards: vi.fn(async () => []),
      listCardRelations: vi.fn(async () => []),
      createCard: vi.fn(async () => ({})),
      updateCard,
      deleteCard: vi.fn(async () => undefined),
      createCardRelation: vi.fn(async () => ({})),
      deleteCardRelation: vi.fn(async () => undefined),
      promptCardName: vi.fn(() => '新角色'),
      notifySuccess,
    })

    await cards.saveCard({
      cardId: 11,
      cardType: 'CHARACTER',
      name: '林霜',
      summary: '剑客',
      detailJson: '{  "trait": "冷静" }',
      expanded: true,
    })

    expect(updateCard).toHaveBeenCalledWith(101, 11, 201, {
      cardType: 'CHARACTER',
      name: '林霜',
      summary: '剑客',
      detailJson: '{"trait":"冷静"}',
    })
    expect(notifySuccess).toHaveBeenCalledWith('资料卡已保存')
  })

  it('creates_relation_and_resets_type_after_reload', async () => {
    const useWorkbenchCards = await loadUseWorkbenchCards()
    const createCardRelation = vi.fn(async () => ({}))
    const listCards = vi.fn(async () => [
      { cardId: 11, cardType: 'CHARACTER', name: '林霜', summary: '', detailJson: '{}' },
      { cardId: 12, cardType: 'WORLD', name: '北境', summary: '', detailJson: '{}' },
    ])
    const listCardRelations = vi.fn(async () => [
      { cardRelationId: 91, fromCardId: 11, toCardId: 12, relationType: '守护' },
    ])

    const cards = useWorkbenchCards({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      listCards,
      listCardRelations,
      createCard: vi.fn(async () => ({})),
      updateCard: vi.fn(async () => ({})),
      deleteCard: vi.fn(async () => undefined),
      createCardRelation,
      deleteCardRelation: vi.fn(async () => undefined),
      promptCardName: vi.fn(() => '新角色'),
    })

    cards.relationFromId.value = '11'
    cards.relationToId.value = '12'
    cards.relationType.value = '守护'

    await cards.createRelation()

    expect(createCardRelation).toHaveBeenCalledWith(101, 201, {
      fromCardId: 11,
      toCardId: 12,
      relationType: '守护',
      description: '',
    })
    expect(cards.relationType.value).toBe('')
    expect(cards.cardRelations.value).toEqual([
      { cardRelationId: 91, fromCardId: 11, toCardId: 12, relationType: '守护' },
    ])
  })

  it('deletes_card_and_relation_then_reloads_state', async () => {
    const useWorkbenchCards = await loadUseWorkbenchCards()
    const deleteCard = vi.fn(async () => undefined)
    const deleteCardRelation = vi.fn(async () => undefined)
    const listCards = vi.fn(async () => [])
    const listCardRelations = vi.fn(async () => [])

    const cards = useWorkbenchCards({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      listCards,
      listCardRelations,
      createCard: vi.fn(async () => ({})),
      updateCard: vi.fn(async () => ({})),
      deleteCard,
      createCardRelation: vi.fn(async () => ({})),
      deleteCardRelation,
      promptCardName: vi.fn(() => '新角色'),
    })

    await cards.deleteCardById({
      cardId: 11,
      cardType: 'CHARACTER',
      name: '林霜',
      summary: '',
      detailJson: '{}',
      expanded: false,
    })
    await cards.deleteRelationById({ cardRelationId: 91 })

    expect(deleteCard).toHaveBeenCalledWith(101, 11, 201)
    expect(deleteCardRelation).toHaveBeenCalledWith(101, 91, 201)
    expect(listCards).toHaveBeenCalledTimes(2)
    expect(listCardRelations).toHaveBeenCalledTimes(2)
  })
})
