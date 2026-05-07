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

  it('keeps_dark_theme_style_contract_for_relation_form_and_list', async () => {
    const wrapper = await mountCardRelationPanel()
    const { readFileSync } = await import('node:fs')
    const { dirname, resolve } = await import('node:path')
    const { fileURLToPath } = await import('node:url')

    const currentDir = dirname(fileURLToPath(import.meta.url))
    const source = readFileSync(resolve(currentDir, './CardRelationPanel.vue'), 'utf-8')

    expect(wrapper.get('[data-testid="relation-from-select"]').classes()).toContain('relation-select')
    expect(wrapper.get('[data-testid="relation-to-select"]').classes()).toContain('relation-select')
    expect(wrapper.get('[data-testid="relation-type-input"]').classes()).toContain('cf-input')
    expect(wrapper.get('[data-testid="create-relation-button"]').classes()).toContain('tree-btn')
    expect(wrapper.get('[data-testid="delete-relation-91"]').classes()).toContain('tree-act-btn')
    expect(wrapper.get('[data-testid="relation-item-91"]').classes()).toContain('relation-item')

    expect(source).toMatch(/<style[^>]*scoped[^>]*>/)
    expect(source).toContain('.relation-select')
    expect(source).toContain('.cf-input')
    expect(source).toContain('.tree-btn')
    expect(source).toContain('.tree-act-btn')
    expect(source).toMatch(/\.relation-select\s*\{[\s\S]*?background:\s*rgba\(17,\s*24,\s*39,\s*0\.72\);[\s\S]*?border:\s*1px\s+solid\s+var\(--border-subtle\);[\s\S]*?color:\s*var\(--text-primary\);/)
    expect(source).toMatch(/\.cf-input\s*\{[\s\S]*?background:\s*rgba\(17,\s*24,\s*39,\s*0\.72\);[\s\S]*?border:\s*1px\s+solid\s+var\(--border-subtle\);[\s\S]*?color:\s*var\(--text-primary\);/)
    expect(source).toMatch(/\.tree-btn\s*\{[\s\S]*?background:\s*rgba\(17,\s*24,\s*39,\s*0\.72\);[\s\S]*?border:\s*1px\s+solid\s+var\(--border-subtle\);[\s\S]*?color:\s*var\(--text-primary\);/)
    expect(source).toMatch(/\.tree-act-btn\s*\{[\s\S]*?border:\s*1px\s+solid\s+var\(--border-subtle\);[\s\S]*?color:\s*var\(--text-primary\);/)
    expect(source).toMatch(/\.relation-item\s*\{[\s\S]*?background:\s*rgba\(17,\s*24,\s*39,\s*0\.52\);[\s\S]*?border:\s*1px\s+solid\s+rgba\(201,\s*169,\s*110,\s*0\.12\);/)
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
