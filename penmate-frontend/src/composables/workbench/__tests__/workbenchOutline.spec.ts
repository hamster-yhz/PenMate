import { describe, expect, it } from 'vitest'

import { mapOutlineTree } from '../workbenchOutline'

describe('workbenchOutline', () => {
  it('should_map_volume_and_chapter_nodes_with_chapter_id_bridge', () => {
    const outlineTree = mapOutlineTree(
      [
        { outlineNodeId: 10, title: '第一卷', nodeType: 'VOLUME' },
        { outlineNodeId: 11, title: '第一章', nodeType: 'CHAPTER', parentId: 10 },
        { outlineNodeId: 12, title: '第二章', nodeType: 'chapter', parentId: 10 },
      ],
      {
        '11': '101',
        '12': '102',
      },
    )

    expect(outlineTree).toEqual([
      {
        title: '第一卷',
        key: '10',
        expanded: true,
        children: [
          { title: '第一章', key: '11', chapterId: '101' },
          { title: '第二章', key: '12', chapterId: '102' },
        ],
      },
    ])
  })

  it('should_ignore_nodes_without_outline_node_id', () => {
    const outlineTree = mapOutlineTree([
      { title: '缺少主键的卷', nodeType: 'VOLUME' },
      { outlineNodeId: '', title: '缺少主键的章', nodeType: 'CHAPTER', parentId: 1 },
    ])

    expect(outlineTree).toEqual([])
  })

  it('should_only_create_volumes_from_explicit_volume_nodes', () => {
    const outlineTree = mapOutlineTree([
      { outlineNodeId: 21, title: '孤立章节', nodeType: 'CHAPTER', parentId: 0 },
    ])

    expect(outlineTree).toEqual([])
  })

  it('should_fall_back_to_name_and_default_titles', () => {
    const outlineTree = mapOutlineTree([
      { outlineNodeId: 31, name: '卷一', type: 'volume' },
      { outlineNodeId: 32, parentId: 31 },
    ])

    expect(outlineTree).toEqual([
      {
        title: '卷一',
        key: '31',
        expanded: true,
        children: [
          { title: '未命名章节', key: '32', chapterId: undefined },
        ],
      },
    ])
  })
})
