import { mount } from '@vue/test-utils'
import { defineComponent, type Component } from 'vue'
import { describe, expect, it } from 'vitest'

type CharacterCard = {
  cardId: number
  cardType: 'CHARACTER'
  name: string
  summary: string
  detailJson: string
  expanded: boolean
}

const MissingCharacterCardItem = defineComponent({
  name: 'MissingCharacterCardItem',
  template: '<div data-testid="missing-character-card-item"></div>',
})

const loadCharacterCardItem = async (): Promise<Component> => {
  try {
    const componentPath = './CharacterCardItem.vue'
    return (await import(/* @vite-ignore */ componentPath)).default
  } catch {
    return MissingCharacterCardItem
  }
}

const buildCard = (overrides: Partial<CharacterCard> = {}): CharacterCard => ({
  cardId: 101,
  cardType: 'CHARACTER',
  name: '林霜',
  summary: '剑客',
  detailJson: '{"trait":"冷静"}',
  expanded: false,
  ...overrides,
})

const mountCharacterCardItem = async (overrides: Partial<CharacterCard> = {}) => {
  const CharacterCardItem = await loadCharacterCardItem()

  return mount(CharacterCardItem, {
    props: {
      card: buildCard(overrides),
    },
  })
}

describe('CharacterCardItem', () => {
  it('renders_collapsed_state_and_emits_expand_toggle', async () => {
    const wrapper = await mountCharacterCardItem({ expanded: false })
    const header = wrapper.find('[data-testid="character-card-header"]')

    expect(header.exists()).toBe(true)
    expect(wrapper.find('[data-testid="character-card-details"]').exists()).toBe(false)

    await header.trigger('click')

    expect(wrapper.emitted('toggle-expand')).toEqual([
      [
        {
          cardId: 101,
          expanded: true,
        },
      ],
    ])
  })

  it('renders_expanded_state_and_emits_collapse_toggle', async () => {
    const wrapper = await mountCharacterCardItem({ expanded: true })
    const header = wrapper.find('[data-testid="character-card-header"]')

    expect(header.exists()).toBe(true)
    expect(wrapper.find('[data-testid="character-card-details"]').exists()).toBe(true)

    await header.trigger('click')

    expect(wrapper.emitted('toggle-expand')).toEqual([
      [
        {
          cardId: 101,
          expanded: false,
        },
      ],
    ])
  })

  it('emits_updated_card_when_editing_inputs', async () => {
    const wrapper = await mountCharacterCardItem({ expanded: true })
    const nameInput = wrapper.find('[data-testid="character-card-name-input"]')
    const summaryInput = wrapper.find('[data-testid="character-card-summary-input"]')
    const detailInput = wrapper.find('[data-testid="character-card-detail-input"]')

    expect(nameInput.exists()).toBe(true)
    expect(summaryInput.exists()).toBe(true)
    expect(detailInput.exists()).toBe(true)

    await nameInput.setValue('沈砚')
    await summaryInput.setValue('谋士')
    await detailInput.setValue('{"trait":"克制"}')

    expect(wrapper.emitted('update:card')).toEqual([
      [
        {
          cardId: 101,
          cardType: 'CHARACTER',
          name: '沈砚',
          summary: '剑客',
          detailJson: '{"trait":"冷静"}',
          expanded: true,
        },
      ],
      [
        {
          cardId: 101,
          cardType: 'CHARACTER',
          name: '沈砚',
          summary: '谋士',
          detailJson: '{"trait":"冷静"}',
          expanded: true,
        },
      ],
      [
        {
          cardId: 101,
          cardType: 'CHARACTER',
          name: '沈砚',
          summary: '谋士',
          detailJson: '{"trait":"克制"}',
          expanded: true,
        },
      ],
    ])
  })

  it('keeps_rendering_local_draft_values_without_waiting_for_parent_sync', async () => {
    const wrapper = await mountCharacterCardItem({ expanded: true })
    const nameInput = wrapper.get('[data-testid="character-card-name-input"]')
    const summaryInput = wrapper.get('[data-testid="character-card-summary-input"]')

    await nameInput.setValue('沈砚')
    await summaryInput.setValue('谋士')
    await wrapper.setProps({
      card: buildCard({ expanded: true }),
    })

    expect((wrapper.get('[data-testid="character-card-name-input"]').element as HTMLInputElement).value).toBe('沈砚')
    expect((wrapper.get('[data-testid="character-card-summary-input"]').element as HTMLInputElement).value).toBe('谋士')
    expect(wrapper.get('.char-name').text()).toBe('沈砚')
    expect(wrapper.get('.char-role').text()).toBe('谋士')
  })

  it('emits_save_with_current_card_draft', async () => {
    const wrapper = await mountCharacterCardItem({ expanded: true })
    const saveButton = wrapper.find('[data-testid="character-card-save"]')

    expect(saveButton.exists()).toBe(true)

    await wrapper.get('[data-testid="character-card-name-input"]').setValue('苏九')
    await saveButton.trigger('click')

    expect(wrapper.emitted('save')).toEqual([
      [
        {
          cardId: 101,
          cardType: 'CHARACTER',
          name: '苏九',
          summary: '剑客',
          detailJson: '{"trait":"冷静"}',
          expanded: true,
        },
      ],
    ])
  })

  it('emits_delete_with_current_card', async () => {
    const wrapper = await mountCharacterCardItem({ expanded: true })
    const deleteButton = wrapper.find('[data-testid="character-card-delete"]')

    expect(deleteButton.exists()).toBe(true)

    await deleteButton.trigger('click')

    expect(wrapper.emitted('delete')).toEqual([
      [
        {
          cardId: 101,
          cardType: 'CHARACTER',
          name: '林霜',
          summary: '剑客',
          detailJson: '{"trait":"冷静"}',
          expanded: true,
        },
      ],
    ])
  })
})
