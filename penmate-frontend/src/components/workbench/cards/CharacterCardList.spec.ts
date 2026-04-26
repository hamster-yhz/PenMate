import { mount } from '@vue/test-utils'
import { defineComponent, h, type Component } from 'vue'
import { describe, expect, it } from 'vitest'

type CharacterCard = {
  cardId: number
  cardType: 'CHARACTER'
  name: string
  summary: string
  detailJson: string
  expanded: boolean
}

const MissingCharacterCardList = defineComponent({
  name: 'MissingCharacterCardList',
  template: '<div data-testid="missing-character-card-list"></div>',
})

const CharacterCardItemStub = defineComponent({
  name: 'CharacterCardItem',
  props: {
    card: {
      type: Object,
      required: true,
    },
  },
  emits: ['toggle-expand', 'update:card', 'save', 'delete'],
  setup(props, { emit }) {
    return () =>
      h('div', { 'data-testid': `character-card-item-${String((props.card as CharacterCard).cardId)}` }, [
        h('button', { 'data-testid': `toggle-character-${String((props.card as CharacterCard).cardId)}`, onClick: () => emit('toggle-expand', { cardId: (props.card as CharacterCard).cardId, expanded: true }) }),
        h('button', { 'data-testid': `update-character-${String((props.card as CharacterCard).cardId)}`, onClick: () => emit('update:card', { ...(props.card as CharacterCard), name: '更新角色' }) }),
        h('button', { 'data-testid': `save-character-${String((props.card as CharacterCard).cardId)}`, onClick: () => emit('save', props.card) }),
        h('button', { 'data-testid': `delete-character-${String((props.card as CharacterCard).cardId)}`, onClick: () => emit('delete', props.card) }),
      ])
  },
})

const loadCharacterCardList = async (): Promise<Component> => {
  try {
    const componentPath = './CharacterCardList.vue'
    return (await import(/* @vite-ignore */ componentPath)).default
  } catch {
    return MissingCharacterCardList
  }
}

const buildCards = (): CharacterCard[] => [
  {
    cardId: 11,
    cardType: 'CHARACTER',
    name: '林霜',
    summary: '剑客',
    detailJson: '{}',
    expanded: false,
  },
]

describe('CharacterCardList', () => {
  it('renders_empty_hint_when_no_cards_exist', async () => {
    const CharacterCardList = await loadCharacterCardList()
    const wrapper = mount(CharacterCardList, {
      props: {
        cards: [],
      },
    })

    expect(wrapper.get('[data-testid="create-character-card"]').text()).toContain('新角色卡')
    expect(wrapper.get('[data-testid="character-empty-hint"]').text()).toContain('暂无角色卡')
  })

  it('emits_create_card_and_forwards_item_events', async () => {
    const CharacterCardList = await loadCharacterCardList()
    const wrapper = mount(CharacterCardList, {
      props: {
        cards: buildCards(),
      },
      global: {
        stubs: {
          CharacterCardItem: CharacterCardItemStub,
        },
      },
    })

    await wrapper.get('[data-testid="create-character-card"]').trigger('click')
    await wrapper.get('[data-testid="toggle-character-11"]').trigger('click')
    await wrapper.get('[data-testid="update-character-11"]').trigger('click')
    await wrapper.get('[data-testid="save-character-11"]').trigger('click')
    await wrapper.get('[data-testid="delete-character-11"]').trigger('click')

    expect(wrapper.emitted('create-card')).toEqual([[]])
    expect(wrapper.emitted('toggle-expand')).toEqual([[{ cardId: 11, expanded: true }]])
    expect(wrapper.emitted('update:card')).toEqual([[{ cardId: 11, cardType: 'CHARACTER', name: '更新角色', summary: '剑客', detailJson: '{}', expanded: false }]])
    expect(wrapper.emitted('save')).toEqual([[{ cardId: 11, cardType: 'CHARACTER', name: '林霜', summary: '剑客', detailJson: '{}', expanded: false }]])
    expect(wrapper.emitted('delete')).toEqual([[{ cardId: 11, cardType: 'CHARACTER', name: '林霜', summary: '剑客', detailJson: '{}', expanded: false }]])
  })
})
