import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
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
      projectCards: [{ cardId: 2, cardType: 'WORLD', name: '北境', summary: '', detailJson: '{}', expanded: false }],
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

describe('WorkbenchLeftPanel', () => {
  it('renders_tabs_and_forwards_outline_events', async () => {
    const wrapper = mountWorkbenchLeftPanel('outline')

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
