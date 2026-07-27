import { defineComponent, h } from 'vue'
import { shallowMount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import OutlineChapterNode from './OutlineChapterNode.vue'
import OutlineVolumeNode from './OutlineVolumeNode.vue'

const MenuStub = defineComponent({
  name: 'AMenu',
  emits: ['click'],
  setup(_, { slots }) {
    return () => h('div', { class: 'menu-stub' }, slots.default?.())
  },
})

const DropdownStub = defineComponent({
  name: 'ADropdown',
  setup(_, { slots }) {
    return () => h('div', [slots.default?.(), slots.overlay?.()])
  },
})

const menuStubs = {
  ADropdown: DropdownStub,
  AMenu: MenuStub,
  AMenuItem: defineComponent({ setup: (_, { slots }) => () => h('div', slots.default?.()) }),
  AMenuDivider: true,
}

const chapter = { key: 'chapter-1', chapterId: '101', title: '第一章' }
const volume = { key: 'volume-1', title: '第一卷', expanded: true, children: [chapter] }

describe('outline context menus', () => {
  it('offers chapter creation from the current chapter context', () => {
    const wrapper = shallowMount(OutlineChapterNode, {
      props: { chapter, parentKey: volume.key, isActive: false, displayNo: 1 },
      global: { stubs: menuStubs },
    })
    const stopPropagation = vi.fn()

    wrapper.findComponent(MenuStub).vm.$emit('click', {
      key: 'add',
      domEvent: { stopPropagation },
    })

    expect(stopPropagation).toHaveBeenCalled()
    expect(wrapper.emitted('add-chapter')).toEqual([[]])
    expect(wrapper.text()).toContain('在本卷新建章节')
  })

  it('maps chapter creation back to its volume', () => {
    const wrapper = shallowMount(OutlineVolumeNode, {
      props: { volume, displayIndex: 0, activeChapterKey: '' },
      global: { stubs: menuStubs },
    })

    wrapper.findComponent(OutlineChapterNode).vm.$emit('add-chapter')

    expect(wrapper.emitted('add-chapter')).toEqual([[volume]])
  })

  it('uses Ctrl click for chapter multi-selection without navigation', async () => {
    const wrapper = shallowMount(OutlineChapterNode, {
      props: { chapter, parentKey: volume.key, isActive: false, isSelected: true, selectedChapterIds: ['101'], displayNo: 1 },
      global: { stubs: menuStubs },
    })

    await wrapper.get('[data-testid="chapter-node-chapter-1"]').trigger('click', { ctrlKey: true })

    expect(wrapper.emitted('toggle-chapter-selection')).toEqual([[chapter]])
    expect(wrapper.emitted('select-chapter')).toBeUndefined()
  })
})
