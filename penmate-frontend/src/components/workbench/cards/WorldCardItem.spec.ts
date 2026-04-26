import { mount } from '@vue/test-utils'
import { defineComponent, type Component } from 'vue'
import { describe, expect, it } from 'vitest'

type WorldCard = {
  cardId: number
  cardType: 'WORLD'
  name: string
  summary: string
  detailJson: string
  expanded: boolean
}

const MissingWorldCardItem = defineComponent({
  name: 'MissingWorldCardItem',
  template: '<div data-testid="missing-world-card-item"></div>',
})

const loadWorldCardItem = async (): Promise<Component> => {
  try {
    const componentPath = './WorldCardItem.vue'
    return (await import(/* @vite-ignore */ componentPath)).default
  } catch {
    return MissingWorldCardItem
  }
}

const buildCard = (overrides: Partial<WorldCard> = {}): WorldCard => ({
  cardId: 201,
  cardType: 'WORLD',
  name: '北境',
  summary: '极寒国度',
  detailJson: '{"climate":"cold"}',
  expanded: false,
  ...overrides,
})

const mountWorldCardItem = async (overrides: Partial<WorldCard> = {}) => {
  const WorldCardItem = await loadWorldCardItem()
  return mount(WorldCardItem, {
    props: {
      card: buildCard(overrides),
    },
  })
}

describe('WorldCardItem', () => {
  it('renders_collapsed_state_and_emits_expand_toggle', async () => {
    const wrapper = await mountWorldCardItem()
    const header = wrapper.find('[data-testid="world-card-header"]')

    expect(header.exists()).toBe(true)
    expect(wrapper.find('[data-testid="world-card-body"]').exists()).toBe(false)

    await header.trigger('click')

    expect(wrapper.emitted('toggle-expand')).toEqual([[{ cardId: 201, expanded: true }]])
  })

  it('emits_updated_card_when_editing_name_summary_and_detail', async () => {
    const wrapper = await mountWorldCardItem({ expanded: true })

    await wrapper.get('[data-testid="world-card-name-input"]').setValue('南陆')
    await wrapper.get('[data-testid="world-card-summary-input"]').setValue('雨林国度')
    await wrapper.get('[data-testid="world-card-detail-input"]').setValue('{"climate":"wet"}')

    expect(wrapper.emitted('update:card')).toEqual([
      [{ cardId: 201, cardType: 'WORLD', name: '南陆', summary: '极寒国度', detailJson: '{"climate":"cold"}', expanded: true }],
      [{ cardId: 201, cardType: 'WORLD', name: '南陆', summary: '雨林国度', detailJson: '{"climate":"cold"}', expanded: true }],
      [{ cardId: 201, cardType: 'WORLD', name: '南陆', summary: '雨林国度', detailJson: '{"climate":"wet"}', expanded: true }],
    ])
  })

  it('emits_save_and_delete_with_current_draft', async () => {
    const wrapper = await mountWorldCardItem({ expanded: true })

    await wrapper.get('[data-testid="world-card-name-input"]').setValue('西荒')
    await wrapper.get('[data-testid="world-card-save"]').trigger('click')
    await wrapper.get('[data-testid="world-card-delete"]').trigger('click')

    expect(wrapper.emitted('save')).toEqual([
      [{ cardId: 201, cardType: 'WORLD', name: '西荒', summary: '极寒国度', detailJson: '{"climate":"cold"}', expanded: true }],
    ])
    expect(wrapper.emitted('delete')).toEqual([
      [{ cardId: 201, cardType: 'WORLD', name: '西荒', summary: '极寒国度', detailJson: '{"climate":"cold"}', expanded: true }],
    ])
  })
})
