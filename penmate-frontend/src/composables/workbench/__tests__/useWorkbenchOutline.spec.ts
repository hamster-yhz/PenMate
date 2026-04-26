import { describe, expect, it, vi } from 'vitest'

import { useWorkbenchOutline } from '../useWorkbenchOutline'

describe('useWorkbenchOutline', () => {
  it('loads_outline_data_via_map_outline_tree_bridge', () => {
    const outline = useWorkbenchOutline({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      reloadOutline: vi.fn(async () => undefined),
      createOutlineNode: vi.fn(async () => ({})),
      createChapter: vi.fn(async () => ({})),
      deleteOutlineNode: vi.fn(async () => undefined),
      deleteChapter: vi.fn(async () => undefined),
      updateOutlineNode: vi.fn(async () => undefined),
      moveOutlineNode: vi.fn(async () => undefined),
    })

    const volumes = outline.loadOutline(
      [
        { outlineNodeId: 10, title: '第一卷', nodeType: 'VOLUME' },
        { outlineNodeId: 11, title: '第一章', nodeType: 'CHAPTER', parentId: 10 },
      ],
      { '11': '1011' },
    )

    expect(volumes).toEqual([
      {
        key: '10',
        title: '第一卷',
        expanded: true,
        children: [
          {
            key: '11',
            title: '第一章',
            chapterId: '1011',
          },
        ],
      },
    ])
    expect(outline.outlineData.value).toEqual(volumes)
  })

  it('updates_active_chapter_state_when_selecting_a_chapter', () => {
    const outline = useWorkbenchOutline({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      reloadOutline: vi.fn(async () => undefined),
      createOutlineNode: vi.fn(async () => ({})),
      createChapter: vi.fn(async () => ({})),
      deleteOutlineNode: vi.fn(async () => undefined),
      deleteChapter: vi.fn(async () => undefined),
      updateOutlineNode: vi.fn(async () => undefined),
      moveOutlineNode: vi.fn(async () => undefined),
    })

    outline.selectChapter({
      key: '11',
      title: '第一章',
      chapterId: '301',
    })

    expect(outline.activeChapter.value).toBe('301')
    expect(outline.currentChapterTitle.value).toBe('第一章')
  })

  it('renames_node_and_syncs_current_title_when_active_chapter_matches', async () => {
    const updateOutlineNode = vi.fn(async () => undefined)
    const outline = useWorkbenchOutline({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      reloadOutline: vi.fn(async () => undefined),
      createOutlineNode: vi.fn(async () => ({})),
      createChapter: vi.fn(async () => ({})),
      deleteOutlineNode: vi.fn(async () => undefined),
      deleteChapter: vi.fn(async () => undefined),
      updateOutlineNode,
      moveOutlineNode: vi.fn(async () => undefined),
    })

    outline.outlineData.value = [
      {
        key: '10',
        title: '第一卷',
        expanded: true,
        children: [
          { key: '11', title: '旧章名', chapterId: '301' },
        ],
      },
    ]
    outline.activeChapter.value = '301'
    outline.currentChapterTitle.value = '旧章名'

    await outline.renameNode({ nodeKey: '11', title: '新章名' })

    expect(outline.outlineData.value[0].children[0].title).toBe('新章名')
    expect(outline.currentChapterTitle.value).toBe('新章名')
    expect(updateOutlineNode).toHaveBeenCalledWith(101, 11, 201, { title: '新章名' })
  })

  it('moves_chapter_inside_volume_and_calls_move_api', async () => {
    const moveOutlineNode = vi.fn(async () => undefined)
    const outline = useWorkbenchOutline({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      reloadOutline: vi.fn(async () => undefined),
      createOutlineNode: vi.fn(async () => ({})),
      createChapter: vi.fn(async () => ({})),
      deleteOutlineNode: vi.fn(async () => undefined),
      deleteChapter: vi.fn(async () => undefined),
      updateOutlineNode: vi.fn(async () => undefined),
      moveOutlineNode,
    })

    outline.outlineData.value = [
      {
        key: '10',
        title: '第一卷',
        expanded: true,
        children: [
          { key: '11', title: '第一章', chapterId: '301' },
          { key: '12', title: '第二章', chapterId: '302' },
        ],
      },
    ]

    await outline.moveNode({ nodeKey: '12', parentKey: '10', direction: -1 })

    expect(outline.outlineData.value[0].children.map((item) => item.key)).toEqual(['12', '11'])
    expect(moveOutlineNode).toHaveBeenCalledWith(101, 12, 201, {
      parentId: 10,
      sortOrder: 1,
    })
  })

  it('rolls_back_created_outline_node_when_create_chapter_fails', async () => {
    const createOutlineNode = vi.fn(async () => ({ outlineNodeId: 88 }))
    const createChapter = vi.fn(async () => {
      throw new Error('create chapter failed')
    })
    const deleteOutlineNode = vi.fn(async () => undefined)
    const notify = vi.fn()
    const outline = useWorkbenchOutline({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      reloadOutline: vi.fn(async () => undefined),
      createOutlineNode,
      createChapter,
      deleteOutlineNode,
      deleteChapter: vi.fn(async () => undefined),
      updateOutlineNode: vi.fn(async () => undefined),
      moveOutlineNode: vi.fn(async () => undefined),
      notify,
    })

    await outline.addChapter({
      key: '10',
      title: '第一卷',
      expanded: true,
      children: [],
    })

    expect(deleteOutlineNode).toHaveBeenCalledWith(101, 88, 201)
    expect(notify).toHaveBeenCalledWith('create chapter failed')
  })

  it('clears_active_chapter_when_deleting_the_selected_chapter', async () => {
    const outline = useWorkbenchOutline({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      reloadOutline: vi.fn(async () => undefined),
      createOutlineNode: vi.fn(async () => ({})),
      createChapter: vi.fn(async () => ({})),
      deleteOutlineNode: vi.fn(async () => undefined),
      deleteChapter: vi.fn(async () => undefined),
      updateOutlineNode: vi.fn(async () => undefined),
      moveOutlineNode: vi.fn(async () => undefined),
    })

    outline.outlineData.value = [
      {
        key: '10',
        title: '第一卷',
        expanded: true,
        children: [
          { key: '11', title: '第一章', chapterId: '301' },
        ],
      },
    ]
    outline.activeChapter.value = '301'
    outline.currentChapterTitle.value = '第一章'

    await outline.deleteChapter({ nodeKey: '11', parentKey: '10' })

    expect(outline.activeChapter.value).toBe('')
    expect(outline.currentChapterTitle.value).toBe('')
  })

  it('clears_active_chapter_when_deleting_the_selected_volume', async () => {
    const outline = useWorkbenchOutline({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      reloadOutline: vi.fn(async () => undefined),
      createOutlineNode: vi.fn(async () => ({})),
      createChapter: vi.fn(async () => ({})),
      deleteOutlineNode: vi.fn(async () => undefined),
      deleteChapter: vi.fn(async () => undefined),
      updateOutlineNode: vi.fn(async () => undefined),
      moveOutlineNode: vi.fn(async () => undefined),
    })

    outline.outlineData.value = [
      {
        key: '10',
        title: '第一卷',
        expanded: true,
        children: [
          { key: '11', title: '第一章', chapterId: '301' },
        ],
      },
    ]
    outline.activeChapter.value = '301'
    outline.currentChapterTitle.value = '第一章'

    await outline.deleteVolume('10')

    expect(outline.activeChapter.value).toBe('')
    expect(outline.currentChapterTitle.value).toBe('')
  })

  it('prevents_concurrent_add_chapter_calls_while_outline_operation_is_busy', async () => {
    let releaseCreateOutlineNode!: () => void
    const createOutlineNode = vi.fn(
      () =>
        new Promise((resolve) => {
          releaseCreateOutlineNode = () => resolve({ outlineNodeId: 88 })
        }),
    )

    const outline = useWorkbenchOutline({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      reloadOutline: vi.fn(async () => undefined),
      createOutlineNode,
      createChapter: vi.fn(async () => ({})),
      deleteOutlineNode: vi.fn(async () => undefined),
      deleteChapter: vi.fn(async () => undefined),
      updateOutlineNode: vi.fn(async () => undefined),
      moveOutlineNode: vi.fn(async () => undefined),
    })

    const volume = {
      key: '10',
      title: '第一卷',
      expanded: true,
      children: [],
    }

    const first = outline.addChapter(volume)
    const second = outline.addChapter(volume)
    releaseCreateOutlineNode()
    await Promise.all([first, second])

    expect(createOutlineNode).toHaveBeenCalledTimes(1)
  })

  it('rolls_back_local_title_and_notifies_when_rename_fails', async () => {
    const notify = vi.fn()
    const outline = useWorkbenchOutline({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      reloadOutline: vi.fn(async () => undefined),
      createOutlineNode: vi.fn(async () => ({})),
      createChapter: vi.fn(async () => ({})),
      deleteOutlineNode: vi.fn(async () => undefined),
      deleteChapter: vi.fn(async () => undefined),
      updateOutlineNode: vi.fn(async () => {
        throw new Error('rename failed')
      }),
      moveOutlineNode: vi.fn(async () => undefined),
      notify,
    })

    outline.outlineData.value = [
      {
        key: '10',
        title: '第一卷',
        expanded: true,
        children: [
          { key: '11', title: '旧章名', chapterId: '301' },
        ],
      },
    ]
    outline.activeChapter.value = '301'
    outline.currentChapterTitle.value = '旧章名'

    await outline.renameNode({ nodeKey: '11', title: '新章名' })

    expect(outline.outlineData.value[0].children[0].title).toBe('旧章名')
    expect(outline.currentChapterTitle.value).toBe('旧章名')
    expect(notify).toHaveBeenCalledWith('rename failed')
  })

  it('does_not_reorder_and_notifies_when_move_fails', async () => {
    const notify = vi.fn()
    const outline = useWorkbenchOutline({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      reloadOutline: vi.fn(async () => undefined),
      createOutlineNode: vi.fn(async () => ({})),
      createChapter: vi.fn(async () => ({})),
      deleteOutlineNode: vi.fn(async () => undefined),
      deleteChapter: vi.fn(async () => undefined),
      updateOutlineNode: vi.fn(async () => undefined),
      moveOutlineNode: vi.fn(async () => {
        throw new Error('move failed')
      }),
      notify,
    })

    outline.outlineData.value = [
      {
        key: '10',
        title: '第一卷',
        expanded: true,
        children: [
          { key: '11', title: '第一章', chapterId: '301' },
          { key: '12', title: '第二章', chapterId: '302' },
        ],
      },
    ]

    await outline.moveNode({ nodeKey: '12', parentKey: '10', direction: -1 })

    expect(outline.outlineData.value[0].children.map((item) => item.key)).toEqual(['11', '12'])
    expect(notify).toHaveBeenCalledWith('move failed')
  })
})
