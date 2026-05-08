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
    })) as unknown as UseWorkbenchCardsModule['useWorkbenchCards']
  }
}

describe('useWorkbenchCards', () => {
  it('loads_cards_and_relations_with_expanded_state_reset_and_name_lookup', async () => {
    const useWorkbenchCards = await loadUseWorkbenchCards()
    const listCards = vi.fn(async () => [
      { cardId: 'card-11', cardType: 'CHARACTER', name: '林霜', summary: '剑客', detailJson: '{}' },
      { cardId: 'card-12', cardType: 'WORLD', name: '北境', summary: '地理', detailJson: '{}' },
    ])
    const listCardRelations = vi.fn(async () => [
      { cardRelationId: 'rel-91', fromCardId: 'card-11', toCardId: 'card-12', relationType: '守护' },
    ])

    const cards = useWorkbenchCards({
      getContext: () => ({ projectId: 'project-101', operatorId: 'operator-201' }),
      listCards,
      listCardRelations,
      createCard: vi.fn(async () => ({})),
      updateCard: vi.fn(async () => ({})),
      deleteCard: vi.fn(async () => undefined),
      createCardRelation: vi.fn(async () => ({})),
      deleteCardRelation: vi.fn(async () => undefined),
      promptCardName: vi.fn(() => '新角色'),
    })

    await cards.loadCardsAndRelations('project-101')

    expect(listCards).toHaveBeenCalledWith('project-101')
    expect(listCardRelations).toHaveBeenCalledWith('project-101')
    expect(cards.projectCards.value).toEqual([
      { cardId: 'card-11', cardType: 'CHARACTER', name: '林霜', summary: '剑客', detailJson: '{}', expanded: false },
      { cardId: 'card-12', cardType: 'WORLD', name: '北境', summary: '地理', detailJson: '{}', expanded: false },
    ])
    expect(cards.cardRelations.value).toEqual([
      { cardRelationId: 'rel-91', fromCardId: 'card-11', toCardId: 'card-12', relationType: '守护', description: '' },
    ])
    expect(cards.cardNameById('card-11')).toBe('林霜')
    expect(cards.cardNameById('999')).toBe('卡片#999')
  })

  it('preserves_existing_state_and_notifies_when_reloading_cards_fails', async () => {
    const useWorkbenchCards = await loadUseWorkbenchCards()
    const notify = vi.fn()
    const listCards = vi
      .fn()
      .mockResolvedValueOnce([
        { cardId: '11', cardType: 'CHARACTER', name: '林霜', summary: '剑客', detailJson: '{}' },
      ])
      .mockRejectedValueOnce(new Error('load cards failed'))
    const listCardRelations = vi
      .fn()
      .mockResolvedValueOnce([
        { cardRelationId: '91', fromCardId: '11', toCardId: '12', relationType: '守护' },
      ])
      .mockResolvedValueOnce([])

    const cards = useWorkbenchCards({
      getContext: () => ({ projectId: 'project-101', operatorId: 'operator-201' }),
      listCards,
      listCardRelations,
      createCard: vi.fn(async () => ({})),
      updateCard: vi.fn(async () => ({})),
      deleteCard: vi.fn(async () => undefined),
      createCardRelation: vi.fn(async () => ({})),
      deleteCardRelation: vi.fn(async () => undefined),
      promptCardName: vi.fn(() => '新角色'),
      notify,
    })

    await cards.loadCardsAndRelations('project-101')
    await cards.loadCardsAndRelations('project-101')

    expect(cards.projectCards.value).toEqual([
      { cardId: '11', cardType: 'CHARACTER', name: '林霜', summary: '剑客', detailJson: '{}', expanded: false },
    ])
    expect(cards.cardRelations.value).toEqual([
      { cardRelationId: '91', fromCardId: '11', toCardId: '12', relationType: '守护', description: '' },
    ])
    expect(notify).toHaveBeenCalledWith('load cards failed')
  })

  it('creates_card_after_prompt_and_reloads_cards', async () => {
    const useWorkbenchCards = await loadUseWorkbenchCards()
    const createCard = vi.fn(async () => ({}))
    const listCards = vi.fn(async () => [
      { cardId: '31', cardType: 'CHARACTER', name: '沈砚', summary: '', detailJson: '{}' },
    ])
    const listCardRelations = vi.fn(async () => [])
    const promptCardName = vi.fn(() => '沈砚')

    const cards = useWorkbenchCards({
      getContext: () => ({ projectId: 'project-101', operatorId: 'operator-201' }),
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
    expect(createCard).toHaveBeenCalledWith('project-101', 'operator-201', {
      cardType: 'CHARACTER',
      name: '沈砚',
      summary: '',
      detailJson: '{}',
    })
    expect(cards.projectCards.value).toEqual([
      { cardId: '31', cardType: 'CHARACTER', name: '沈砚', summary: '', detailJson: '{}', expanded: false },
    ])
  })

  it('notifies_when_create_card_lacks_context_or_name_or_uses_invalid_type', async () => {
    const useWorkbenchCards = await loadUseWorkbenchCards()
    const notify = vi.fn()
    const createCard = vi.fn(async () => ({}))
    const promptCardName = vi.fn(() => '  ')

    const cardsWithoutContext = useWorkbenchCards({
      getContext: () => ({ projectId: '', operatorId: '' }),
      listCards: vi.fn(async () => []),
      listCardRelations: vi.fn(async () => []),
      createCard,
      updateCard: vi.fn(async () => ({})),
      deleteCard: vi.fn(async () => undefined),
      createCardRelation: vi.fn(async () => ({})),
      deleteCardRelation: vi.fn(async () => undefined),
      promptCardName,
      notify,
    })

    await cardsWithoutContext.createCardQuick('CHARACTER')

    const cardsWithContext = useWorkbenchCards({
      getContext: () => ({ projectId: 'project-101', operatorId: 'operator-201' }),
      listCards: vi.fn(async () => []),
      listCardRelations: vi.fn(async () => []),
      createCard,
      updateCard: vi.fn(async () => ({})),
      deleteCard: vi.fn(async () => undefined),
      createCardRelation: vi.fn(async () => ({})),
      deleteCardRelation: vi.fn(async () => undefined),
      promptCardName,
      notify,
    })

    await cardsWithContext.createCardQuick('INVALID' as any)
    await cardsWithContext.createCardQuick('CHARACTER')

    expect(createCard).not.toHaveBeenCalled()
    expect(notify).toHaveBeenNthCalledWith(1, '缺少 projectId/operatorId，无法创建资料卡')
    expect(notify).toHaveBeenNthCalledWith(2, '卡片类型非法，仅支持 CHARACTER/WORLD')
    expect(notify).toHaveBeenNthCalledWith(3, '卡片名称不能为空')
  })

  it('validates_card_before_saving_and_notifies_on_invalid_json', async () => {
    const useWorkbenchCards = await loadUseWorkbenchCards()
    const notify = vi.fn()
    const updateCard = vi.fn(async () => ({}))

    const cards = useWorkbenchCards({
      getContext: () => ({ projectId: 'project-101', operatorId: 'operator-201' }),
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
      cardId: 'card-11',
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
      getContext: () => ({ projectId: 'project-101', operatorId: 'operator-201' }),
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
      cardId: 'card-11',
      cardType: 'CHARACTER',
      name: '林霜',
      summary: '剑客',
      detailJson: '{  "trait": "冷静" }',
      expanded: true,
    })

    expect(updateCard).toHaveBeenCalledWith('project-101', 'card-11', 'operator-201', {
      cardType: 'CHARACTER',
      name: '林霜',
      summary: '剑客',
      detailJson: '{"trait":"冷静"}',
    })
    expect(notifySuccess).toHaveBeenCalledWith('资料卡已保存')
  })

  it('notifies_when_save_delete_or_delete_relation_lack_context', async () => {
    const useWorkbenchCards = await loadUseWorkbenchCards()
    const notify = vi.fn()
    const updateCard = vi.fn(async () => ({}))
    const deleteCard = vi.fn(async () => undefined)
    const deleteCardRelation = vi.fn(async () => undefined)

    const cards = useWorkbenchCards({
      getContext: () => ({ projectId: '', operatorId: '' }),
      listCards: vi.fn(async () => []),
      listCardRelations: vi.fn(async () => []),
      createCard: vi.fn(async () => ({})),
      updateCard,
      deleteCard,
      createCardRelation: vi.fn(async () => ({})),
      deleteCardRelation,
      promptCardName: vi.fn(() => '新角色'),
      notify,
    })

    await cards.saveCard({
      cardId: 'card-11',
      cardType: 'CHARACTER',
      name: '林霜',
      summary: '剑客',
      detailJson: '{}',
      expanded: false,
    })
    await cards.deleteCardById({
      cardId: 'card-11',
      cardType: 'CHARACTER',
      name: '林霜',
      summary: '剑客',
      detailJson: '{}',
      expanded: false,
    })
    await cards.deleteRelationById({ cardRelationId: 'rel-91', fromCardId: 'card-11', toCardId: 'card-12', relationType: '守护' })

    expect(updateCard).not.toHaveBeenCalled()
    expect(deleteCard).not.toHaveBeenCalled()
    expect(deleteCardRelation).not.toHaveBeenCalled()
    expect(notify).toHaveBeenNthCalledWith(1, '缺少 projectId/operatorId/cardId，无法保存资料卡')
    expect(notify).toHaveBeenNthCalledWith(2, '缺少 projectId/operatorId/cardId，无法删除资料卡')
    expect(notify).toHaveBeenNthCalledWith(3, '缺少 projectId/operatorId/relationId，无法删除关系')
  })

  it('creates_relation_and_resets_type_after_reload', async () => {
    const useWorkbenchCards = await loadUseWorkbenchCards()
    const createCardRelation = vi.fn(async () => ({}))
    const listCards = vi.fn(async () => [
      { cardId: 'card-11', cardType: 'CHARACTER', name: '林霜', summary: '', detailJson: '{}' },
      { cardId: 'card-12', cardType: 'WORLD', name: '北境', summary: '', detailJson: '{}' },
    ])
    const listCardRelations = vi.fn(async () => [
      { cardRelationId: 'rel-91', fromCardId: 'card-11', toCardId: 'card-12', relationType: '守护' },
    ])

    const cards = useWorkbenchCards({
      getContext: () => ({ projectId: 'project-101', operatorId: 'operator-201' }),
      listCards,
      listCardRelations,
      createCard: vi.fn(async () => ({})),
      updateCard: vi.fn(async () => ({})),
      deleteCard: vi.fn(async () => undefined),
      createCardRelation,
      deleteCardRelation: vi.fn(async () => undefined),
      promptCardName: vi.fn(() => '新角色'),
    })

    cards.relationFromId.value = 'card-11'
    cards.relationToId.value = 'card-12'
    cards.relationType.value = '守护'

    await cards.createRelation()

    expect(createCardRelation).toHaveBeenCalledWith('project-101', 'operator-201', {
      fromCardId: 'card-11',
      toCardId: 'card-12',
      relationType: '守护',
      description: '',
    })
    expect(cards.relationType.value).toBe('')
    expect(cards.cardRelations.value).toEqual([
      { cardRelationId: 'rel-91', fromCardId: 'card-11', toCardId: 'card-12', relationType: '守护', description: '' },
    ])
  })

  it('keeps_string_business_ids_when_creating_relations', async () => {
    const useWorkbenchCards = await loadUseWorkbenchCards()
    const createCardRelation = vi.fn(async () => ({}))

    const cards = useWorkbenchCards({
      getContext: () => ({ projectId: 'project-alpha', operatorId: 'operator-beta' }),
      listCards: vi.fn(async () => []),
      listCardRelations: vi.fn(async () => []),
      createCard: vi.fn(async () => ({})),
      updateCard: vi.fn(async () => ({})),
      deleteCard: vi.fn(async () => undefined),
      createCardRelation,
      deleteCardRelation: vi.fn(async () => undefined),
      promptCardName: vi.fn(() => '新角色'),
    })

    cards.relationFromId.value = 'card-from-A'
    cards.relationToId.value = 'card-to-B'
    cards.relationType.value = '盟友'

    await cards.createRelation()

    expect(createCardRelation).toHaveBeenCalledWith('project-alpha', 'operator-beta', {
      fromCardId: 'card-from-A',
      toCardId: 'card-to-B',
      relationType: '盟友',
      description: '',
    })
  })

  it('preserves_existing_state_and_notifies_when_relation_list_reload_fails', async () => {
    const useWorkbenchCards = await loadUseWorkbenchCards()
    const notify = vi.fn()
    const listCards = vi.fn(async () => [
      { cardId: '11', cardType: 'CHARACTER', name: '林霜', summary: '剑客', detailJson: '{}' },
    ])
    const listCardRelations = vi
      .fn()
      .mockResolvedValueOnce([
        { cardRelationId: '91', fromCardId: '11', toCardId: '12', relationType: '守护' },
      ])
      .mockRejectedValueOnce(new Error('load relations failed'))

    const cards = useWorkbenchCards({
      getContext: () => ({ projectId: 'project-101', operatorId: 'operator-201' }),
      listCards,
      listCardRelations,
      createCard: vi.fn(async () => ({})),
      updateCard: vi.fn(async () => ({})),
      deleteCard: vi.fn(async () => undefined),
      createCardRelation: vi.fn(async () => ({})),
      deleteCardRelation: vi.fn(async () => undefined),
      promptCardName: vi.fn(() => '新角色'),
      notify,
    })

    await cards.loadCardsAndRelations('project-101')
    await cards.loadCardsAndRelations('project-101')

    expect(cards.projectCards.value).toEqual([
      { cardId: '11', cardType: 'CHARACTER', name: '林霜', summary: '剑客', detailJson: '{}', expanded: false },
    ])
    expect(cards.cardRelations.value).toEqual([
      { cardRelationId: '91', fromCardId: '11', toCardId: '12', relationType: '守护', description: '' },
    ])
    expect(notify).toHaveBeenCalledWith('load relations failed')
  })

  it('notifies_when_create_relation_is_incomplete_or_request_fails', async () => {
    const useWorkbenchCards = await loadUseWorkbenchCards()
    const notify = vi.fn()
    const createCardRelation = vi
      .fn()
      .mockRejectedValueOnce(new Error('create relation failed'))

    const cards = useWorkbenchCards({
      getContext: () => ({ projectId: 'project-101', operatorId: 'operator-201' }),
      listCards: vi.fn(async () => []),
      listCardRelations: vi.fn(async () => []),
      createCard: vi.fn(async () => ({})),
      updateCard: vi.fn(async () => ({})),
      deleteCard: vi.fn(async () => undefined),
      createCardRelation,
      deleteCardRelation: vi.fn(async () => undefined),
      promptCardName: vi.fn(() => '新角色'),
      notify,
    })

    await cards.createRelation()

    cards.relationFromId.value = '11'
    cards.relationToId.value = '12'
    cards.relationType.value = '敌对'

    await cards.createRelation()

    expect(notify).toHaveBeenNthCalledWith(1, '请补全来源/目标/关系类型')
    expect(notify).toHaveBeenNthCalledWith(2, 'create relation failed')
  })

  it('deletes_card_and_relation_then_reloads_state', async () => {
    const useWorkbenchCards = await loadUseWorkbenchCards()
    const deleteCard = vi.fn(async () => undefined)
    const deleteCardRelation = vi.fn(async () => undefined)
    const listCards = vi.fn(async () => [])
    const listCardRelations = vi.fn(async () => [])

    const cards = useWorkbenchCards({
      getContext: () => ({ projectId: 'project-101', operatorId: 'operator-201' }),
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
      cardId: 'card-11',
      cardType: 'CHARACTER',
      name: '林霜',
      summary: '',
      detailJson: '{}',
      expanded: false,
    })
    await cards.deleteRelationById({ cardRelationId: 'rel-91', fromCardId: 'card-11', toCardId: 'card-12', relationType: '守护' })

    expect(deleteCard).toHaveBeenCalledWith('project-101', 'card-11', 'operator-201')
    expect(deleteCardRelation).toHaveBeenCalledWith('project-101', 'rel-91', 'operator-201')
    expect(listCards).toHaveBeenCalledTimes(2)
    expect(listCardRelations).toHaveBeenCalledTimes(2)
  })
})
