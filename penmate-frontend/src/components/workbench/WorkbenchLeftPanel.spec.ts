import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { mount } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
import { describe, expect, it } from 'vitest'
import WorkbenchLeftPanel from './WorkbenchLeftPanel.vue'

const OutlineTreeStub = defineComponent({
  name: 'OutlineTree',
  emits: ['select-chapter', 'rename-node'],
  setup(_, { emit }) {
    return () =>
      h('div', [
        h('button', { 'data-testid': 'outline-select', onClick: () => emit('select-chapter', { chapterId: 11, key: '11' }) }),
        h('button', { 'data-testid': 'outline-rename', onClick: () => emit('rename-node', { nodeKey: 'n-1', title: '新标题' }) }),
      ])
  },
})

const CharacterCardListStub = defineComponent({
  name: 'CharacterCardList',
  emits: ['create-card'],
  setup(_, { emit }) {
    return () => h('button', { 'data-testid': 'character-create', onClick: () => emit('create-card') })
  },
})

const WorldCardListStub = defineComponent({
  name: 'WorldCardList',
  emits: ['create-card'],
  setup(_, { emit }) {
    return () => h('button', { 'data-testid': 'world-create', onClick: () => emit('create-card') })
  },
})

const CardRelationPanelStub = defineComponent({
  name: 'CardRelationPanel',
  emits: ['update:relation-from-id', 'create-relation'],
  setup(_, { emit }) {
    return () =>
      h('div', [
        h('button', { 'data-testid': 'relation-update-from', onClick: () => emit('update:relation-from-id', 'card-2') }),
        h('button', { 'data-testid': 'relation-create', onClick: () => emit('create-relation') }),
      ])
  },
})

const mountWorkbenchLeftPanel = (activeLeftTab = 'outline') =>
  mount(WorkbenchLeftPanel, {
    props: {
      collapsed: false,
      leftTabs: [
        { key: 'outline', label: '大纲', icon: '/outline.png' },
        { key: 'characters', label: '角色', icon: '/character.png' },
        { key: 'world', label: '世界', icon: '/world.png' },
      ],
      activeLeftTab,
      outlineData: [],
      activeChapter: '11',
      outlineOpBusy: false,
      characterCards: [],
      worldCards: [],
      projectCards: [{ cardId: '2', cardType: 'WORLD', name: '北境', summary: '', detailJson: '{}', expanded: false }],
      cardRelations: [],
      relationFromId: '',
      relationToId: '',
      relationType: '',
      cardNameById: (cardId: string) => cardId,
    },
    global: {
      stubs: {
        OutlineTree: OutlineTreeStub,
        CharacterCardList: CharacterCardListStub,
        WorldCardList: WorldCardListStub,
        CardRelationPanel: CardRelationPanelStub,
      },
    },
  })

const createBaseProps = (activeLeftTab = 'outline') => ({
  collapsed: false,
  leftTabs: [
    { key: 'outline', label: '大纲', icon: '/outline.png' },
    { key: 'characters', label: '角色', icon: '/character.png' },
    { key: 'world', label: '世界', icon: '/world.png' },
  ],
  activeLeftTab,
  outlineData: [],
  activeChapter: '11',
  outlineOpBusy: false,
  characterCards: [],
  worldCards: [],
  projectCards: [{ cardId: '2', cardType: 'WORLD', name: '北境', summary: '', detailJson: '{}', expanded: false }],
  cardRelations: [],
  relationFromId: '',
  relationToId: '',
  relationType: '',
  cardNameById: (cardId: string) => cardId,
})

const mountControlledWorkbenchLeftPanel = (initialTab = 'outline') => {
  const Harness = defineComponent({
    components: { WorkbenchLeftPanel },
    setup() {
      const activeLeftTab = ref(initialTab)
      const props = createBaseProps(initialTab)

      return {
        activeLeftTab,
        props,
      }
    },
    template: `
      <WorkbenchLeftPanel
        v-bind="props"
        :active-left-tab="activeLeftTab"
        @update:active-left-tab="activeLeftTab = $event"
      />
    `,
  })

  return mount(Harness, {
    attachTo: document.body,
    global: {
      stubs: {
        OutlineTree: OutlineTreeStub,
        CharacterCardList: CharacterCardListStub,
        WorldCardList: WorldCardListStub,
        CardRelationPanel: CardRelationPanelStub,
      },
    },
  })
}

const currentDir = dirname(fileURLToPath(import.meta.url))
const readWorkbenchLeftPanelSource = () => readFileSync(resolve(currentDir, 'WorkbenchLeftPanel.vue'), 'utf-8')

describe('WorkbenchLeftPanel', () => {
  it('renders_tabs_and_forwards_outline_events', async () => {
    const wrapper = mountWorkbenchLeftPanel('outline')

    expect(wrapper.get('.panel-left').classes()).toContain('glass-panel')

    expect(wrapper.text()).toContain('大纲')
    expect(wrapper.text()).toContain('角色')
    expect(wrapper.text()).toContain('世界')

    await wrapper.get('.panel-toggle').trigger('click')
    await wrapper.get('[data-testid="outline-select"]').trigger('click')
    await wrapper.get('[data-testid="outline-rename"]').trigger('click')

    expect(wrapper.emitted('toggle-collapse')).toEqual([[]])
    expect(wrapper.emitted('select-chapter')).toEqual([[{ chapterId: 11, key: '11' }]])
    expect(wrapper.emitted('rename-node')).toEqual([[{ nodeKey: 'n-1', title: '新标题' }]])
  })

  it('keeps_shared_tab_button_class_and_moves_active_state_with_active_left_tab', async () => {
    const wrapper = mountWorkbenchLeftPanel('outline')

    const tabButtons = wrapper.findAll('button.ltab')

    expect(tabButtons).toHaveLength(3)
    expect(tabButtons.map((button) => button.text())).toEqual(['大纲', '角色', '世界'])
    expect(tabButtons.every((button) => button.classes().includes('ltab'))).toBe(true)
    expect(tabButtons[0]?.classes()).toContain('active')
    expect(tabButtons[1]?.classes()).not.toContain('active')
    expect(tabButtons[2]?.classes()).not.toContain('active')

    await wrapper.setProps({ activeLeftTab: 'world' })

    const updatedTabButtons = wrapper.findAll('button.ltab')
    expect(updatedTabButtons[0]?.classes()).not.toContain('active')
    expect(updatedTabButtons[1]?.classes()).not.toContain('active')
    expect(updatedTabButtons[2]?.classes()).toContain('active')
  })

  it('exposes_active_tab_state_through_tab_semantics_and_debug_attributes', async () => {
    const wrapper = mountWorkbenchLeftPanel('characters')

    expect(wrapper.get('.left-tabs').attributes('role')).toBe('tablist')

    const initialButtons = wrapper.findAll('button.ltab')

    expect(initialButtons[0]?.attributes('role')).toBe('tab')
    expect(initialButtons[0]?.attributes('aria-selected')).toBe('false')
    expect(initialButtons[0]?.attributes('data-active')).toBe('false')
    expect(initialButtons[1]?.attributes('aria-selected')).toBe('true')
    expect(initialButtons[1]?.attributes('data-active')).toBe('true')
    expect(initialButtons[1]?.attributes('tabindex')).toBe('0')
    expect(initialButtons[1]?.attributes('aria-controls')).toBe('workbench-left-panel-characters')
    expect(initialButtons[2]?.attributes('aria-selected')).toBe('false')
    expect(initialButtons[2]?.attributes('data-active')).toBe('false')

    expect(wrapper.get('#workbench-left-panel-characters').attributes('role')).toBe('tabpanel')
    expect(wrapper.get('#workbench-left-panel-characters').attributes('aria-labelledby')).toBe('workbench-left-tab-characters')

    await wrapper.setProps({ activeLeftTab: 'world' })

    const updatedButtons = wrapper.findAll('button.ltab')

    expect(updatedButtons[1]?.attributes('aria-selected')).toBe('false')
    expect(updatedButtons[1]?.attributes('data-active')).toBe('false')
    expect(updatedButtons[1]?.attributes('tabindex')).toBe('-1')
    expect(updatedButtons[2]?.attributes('aria-selected')).toBe('true')
    expect(updatedButtons[2]?.attributes('data-active')).toBe('true')
    expect(updatedButtons[2]?.attributes('tabindex')).toBe('0')
  })

  it('supports_keyboard_navigation_for_roving_tabindex_tabs', async () => {
    const wrapper = mountControlledWorkbenchLeftPanel('outline')

    const getTabs = () => wrapper.findAll('button.ltab')

    ;(getTabs()[0]?.element as HTMLButtonElement | undefined)?.focus()
    expect(document.activeElement).toBe(getTabs()[0]?.element)

    await getTabs()[0]!.trigger('keydown', { key: 'ArrowRight' })
    expect(getTabs()[1]?.attributes('aria-selected')).toBe('true')
    expect(getTabs()[1]?.attributes('tabindex')).toBe('0')
    expect(document.activeElement).toBe(getTabs()[1]?.element)

    await getTabs()[1]!.trigger('keydown', { key: 'End' })
    expect(getTabs()[2]?.attributes('aria-selected')).toBe('true')
    expect(getTabs()[2]?.attributes('tabindex')).toBe('0')
    expect(document.activeElement).toBe(getTabs()[2]?.element)

    await getTabs()[2]!.trigger('keydown', { key: 'Home' })
    expect(getTabs()[0]?.attributes('aria-selected')).toBe('true')
    expect(getTabs()[0]?.attributes('tabindex')).toBe('0')
    expect(document.activeElement).toBe(getTabs()[0]?.element)

    await getTabs()[0]!.trigger('keydown', { key: 'ArrowLeft' })
    expect(getTabs()[2]?.attributes('aria-selected')).toBe('true')
    expect(getTabs()[2]?.attributes('tabindex')).toBe('0')
    expect(document.activeElement).toBe(getTabs()[2]?.element)

    wrapper.unmount()
  })

  it('keeps the left column scroll inside the active tab panel instead of the page shell', () => {
    const source = readWorkbenchLeftPanelSource()

    expect(source).toMatch(/\.panel-left\s*\{[\s\S]*?min-width:\s*0;[\s\S]*?min-height:\s*0;/)
    expect(source).toMatch(/\.tab-content\s*\{[\s\S]*?flex:\s*1;[\s\S]*?overflow-y:\s*auto;/)
  })

  it('forwards_character_and_relation_events_from_nested_shell_content', async () => {
    const characterWrapper = mountWorkbenchLeftPanel('characters')
    await characterWrapper.get('[data-testid="character-create"]').trigger('click')
    expect(characterWrapper.emitted('create-character-card')).toEqual([[]])

    const worldWrapper = mountWorkbenchLeftPanel('world')
    await worldWrapper.get('[data-testid="world-create"]').trigger('click')
    await worldWrapper.get('[data-testid="relation-update-from"]').trigger('click')
    await worldWrapper.get('[data-testid="relation-create"]').trigger('click')

    expect(worldWrapper.emitted('create-world-card')).toEqual([[]])
    expect(worldWrapper.emitted('update:relation-from-id')).toEqual([['card-2']])
    expect(worldWrapper.emitted('create-relation')).toEqual([[]])
  })
})
