import { mount } from '@vue/test-utils'
import { defineComponent, h, type Component } from 'vue'
import { describe, expect, it } from 'vitest'

type WorldCard = {
  cardId: number
  cardType: 'WORLD'
  name: string
  summary: string
  detailJson: string
  expanded: boolean
}

const MissingWorldCardList = defineComponent({
  name: 'MissingWorldCardList',
  template: '<div data-testid="missing-world-card-list"></div>',
})

const WorldCardItemStub = defineComponent({
  name: 'WorldCardItem',
  props: {
    card: {
      type: Object,
      required: true,
    },
  },
  emits: ['toggle-expand', 'update:card', 'save', 'delete'],
  setup(props, { emit }) {
    return () =>
      h('div', { 'data-testid': `world-card-item-${String((props.card as WorldCard).cardId)}` }, [
        h('button', { 'data-testid': `toggle-world-${String((props.card as WorldCard).cardId)}`, onClick: () => emit('toggle-expand', { cardId: (props.card as WorldCard).cardId, expanded: true }) }),
        h('button', { 'data-testid': `update-world-${String((props.card as WorldCard).cardId)}`, onClick: () => emit('update:card', { ...(props.card as WorldCard), name: '更新世界' }) }),
        h('button', { 'data-testid': `save-world-${String((props.card as WorldCard).cardId)}`, onClick: () => emit('save', props.card) }),
        h('button', { 'data-testid': `delete-world-${String((props.card as WorldCard).cardId)}`, onClick: () => emit('delete', props.card) }),
      ])
  },
})

const loadWorldCardList = async (): Promise<Component> => {
  try {
    const componentPath = './WorldCardList.vue'
    return (await import(/* @vite-ignore */ componentPath)).default
  } catch {
    return MissingWorldCardList
  }
}

const buildCards = (): WorldCard[] => [
  {
    cardId: 21,
    cardType: 'WORLD',
    name: '北境',
    summary: '极寒国度',
    detailJson: '{}',
    expanded: false,
  },
]

describe('WorldCardList', () => {
  it('renders_empty_hint_when_no_cards_exist', async () => {
    const WorldCardList = await loadWorldCardList()
    const wrapper = mount(WorldCardList, {
      props: {
        cards: [],
      },
    })

    expect(wrapper.get('[data-testid="create-world-card"]').text()).toContain('新世界观卡')
    expect(wrapper.get('[data-testid="world-empty-hint"]').text()).toContain('暂无资料卡')
  })

  it('emits_create_card_and_forwards_item_events', async () => {
    const WorldCardList = await loadWorldCardList()
    const wrapper = mount(WorldCardList, {
      props: {
        cards: buildCards(),
      },
      global: {
        stubs: {
          WorldCardItem: WorldCardItemStub,
        },
      },
    })

    await wrapper.get('[data-testid="create-world-card"]').trigger('click')
    await wrapper.get('[data-testid="toggle-world-21"]').trigger('click')
    await wrapper.get('[data-testid="update-world-21"]').trigger('click')
    await wrapper.get('[data-testid="save-world-21"]').trigger('click')
    await wrapper.get('[data-testid="delete-world-21"]').trigger('click')

    expect(wrapper.emitted('create-card')).toEqual([[]])
    expect(wrapper.emitted('toggle-expand')).toEqual([[{ cardId: 21, expanded: true }]])
    expect(wrapper.emitted('update:card')).toEqual([[{ cardId: 21, cardType: 'WORLD', name: '更新世界', summary: '极寒国度', detailJson: '{}', expanded: false }]])
    expect(wrapper.emitted('save')).toEqual([[{ cardId: 21, cardType: 'WORLD', name: '北境', summary: '极寒国度', detailJson: '{}', expanded: false }]])
    expect(wrapper.emitted('delete')).toEqual([[{ cardId: 21, cardType: 'WORLD', name: '北境', summary: '极寒国度', detailJson: '{}', expanded: false }]])
  })
})
