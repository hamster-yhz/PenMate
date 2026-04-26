import { mount } from '@vue/test-utils'
import { defineComponent, type Component } from 'vue'
import { describe, expect, it } from 'vitest'

type CardOption = {
  cardId: number
  name: string
}

type CardRelation = {
  cardRelationId: number
  fromCardId: number
  toCardId: number
  relationType: string
}

const MissingCardRelationPanel = defineComponent({
  name: 'MissingCardRelationPanel',
  template: '<div data-testid="missing-card-relation-panel"></div>',
})

const loadCardRelationPanel = async (): Promise<Component> => {
  try {
    const componentPath = './CardRelationPanel.vue'
    return (await import(/* @vite-ignore */ componentPath)).default
  } catch {
    return MissingCardRelationPanel
  }
}

const mountCardRelationPanel = async () => {
  const CardRelationPanel = await loadCardRelationPanel()
  return mount(CardRelationPanel, {
    props: {
      cards: [
        { cardId: 11, name: '林霜' },
        { cardId: 12, name: '北境' },
      ] satisfies CardOption[],
      relations: [
        { cardRelationId: 91, fromCardId: 11, toCardId: 12, relationType: '守护' },
      ] satisfies CardRelation[],
      relationFromId: '11',
      relationToId: '12',
      relationType: '守护',
      cardNameById: (idLike: string) => ({ '11': '林霜', '12': '北境' }[idLike] || `卡片#${idLike}`),
    },
  })
}

describe('CardRelationPanel', () => {
  it('renders_relation_items_with_card_name_lookup', async () => {
    const wrapper = await mountCardRelationPanel()

    expect(wrapper.get('[data-testid="relation-item-91"]').text()).toContain('林霜')
    expect(wrapper.get('[data-testid="relation-item-91"]').text()).toContain('北境')
    expect(wrapper.get('[data-testid="relation-item-91"]').text()).toContain('守护')
  })

  it('emits_input_updates_for_relation_form', async () => {
    const wrapper = await mountCardRelationPanel()

    await wrapper.get('[data-testid="relation-from-select"]').setValue('12')
    await wrapper.get('[data-testid="relation-to-select"]').setValue('11')
    await wrapper.get('[data-testid="relation-type-input"]').setValue('敌对')

    expect(wrapper.emitted('update:relationFromId')).toEqual([['12']])
    expect(wrapper.emitted('update:relationToId')).toEqual([['11']])
    expect(wrapper.emitted('update:relationType')).toEqual([['敌对']])
  })

  it('emits_create_and_delete_relation_events', async () => {
    const wrapper = await mountCardRelationPanel()

    await wrapper.get('[data-testid="create-relation-button"]').trigger('click')
    await wrapper.get('[data-testid="delete-relation-91"]').trigger('click')

    expect(wrapper.emitted('create-relation')).toEqual([[]])
    expect(wrapper.emitted('delete-relation')).toEqual([[{ cardRelationId: 91, fromCardId: 11, toCardId: 12, relationType: '守护' }]])
  })
})
