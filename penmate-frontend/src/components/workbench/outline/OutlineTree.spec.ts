import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import type { OutlineVolumeNode } from '../../../composables/workbench/workbenchOutline'
import OutlineTree from './OutlineTree.vue'

const buildOutline = (): OutlineVolumeNode[] => [
  {
    key: '11',
    title: '第一卷',
    expanded: true,
    children: [
      {
        key: '21',
        title: '第一章',
        chapterId: '301',
      },
      {
        key: '22',
        title: '第二章',
        chapterId: '302',
      },
    ],
  },
  {
    key: '12',
    title: '第二卷',
    expanded: false,
    children: [],
  },
]

const mountOutlineTree = (overrides?: Partial<InstanceType<typeof OutlineTree>['$props']>) => {
  return mount(OutlineTree, {
    props: {
      volumes: buildOutline(),
      activeChapterKey: '302',
      busy: false,
      ...overrides,
    },
  })
}

describe('OutlineTree', () => {
  it('renders_volume_and_chapter_nodes_with_active_state', () => {
    const wrapper = mountOutlineTree()

    expect(wrapper.get('[data-testid="volume-label-11"]').text()).toBe('第一卷')
    expect(wrapper.get('[data-testid="volume-label-12"]').text()).toBe('第二卷')
    expect(wrapper.get('[data-testid="chapter-label-21"]').text()).toBe('第一章')
    expect(wrapper.get('[data-testid="chapter-label-22"]').text()).toBe('第二章')
    expect(wrapper.get('[data-testid="chapter-node-22"]').classes()).toContain('active')
  })

  it('emits_select_chapter_when_clicking_a_chapter_node', async () => {
    const wrapper = mountOutlineTree()

    await wrapper.get('[data-testid="chapter-node-21"]').trigger('click')

    expect(wrapper.emitted('select-chapter')).toEqual([
      [
        {
          key: '21',
          title: '第一章',
          chapterId: '301',
        },
      ],
    ])
  })

  it('emits_rename_node_when_submitting_a_new_title', async () => {
    const wrapper = mountOutlineTree()

    await wrapper.get('[data-testid="rename-node-21"]').trigger('click')
    await wrapper.get('[data-testid="rename-input-21"]').setValue('新的章节名')
    await wrapper.get('[data-testid="rename-input-21"]').trigger('keydown.enter')

    expect(wrapper.emitted('rename-node')).toEqual([
      [
        {
          nodeKey: '21',
          title: '新的章节名',
        },
      ],
    ])
  })

  it('emits_move_node_when_clicking_move_up_for_a_chapter', async () => {
    const wrapper = mountOutlineTree()

    await wrapper.get('[data-testid="move-up-node-22"]').trigger('click')

    expect(wrapper.emitted('move-node')).toEqual([
      [
        {
          nodeKey: '22',
          direction: -1,
          parentKey: '11',
        },
      ],
    ])
  })
})
