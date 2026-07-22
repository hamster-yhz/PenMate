import { describe, expect, it, vi } from 'vitest'

import { useWorkbenchOutline } from '../useWorkbenchOutline'

const createDeps = () => ({
  getContext: () => ({ projectId: 'project-101', operatorId: 'operator-201' }),
  reloadOutline: vi.fn(async () => undefined),
  createVolume: vi.fn(async () => ({})),
  createChapter: vi.fn(async () => ({})),
  deleteVolume: vi.fn(async () => undefined),
  deleteChapter: vi.fn(async () => undefined),
  updateVolume: vi.fn(async () => ({})),
  updateChapter: vi.fn(async () => ({})),
  moveDirectoryItem: vi.fn(async () => ({
    structureRevision: 2,
    volumes: [{ volumeId: 'volume-10', title: '第一卷', sortOrder: 1 }],
    chapters: [
      { chapterId: 'chapter-302', volumeId: 'volume-10', title: '第二章', sortOrder: 1 },
      { chapterId: 'chapter-301', volumeId: 'volume-10', title: '第一章', sortOrder: 2 },
    ],
  })),
  notify: vi.fn(),
  notifySuccess: vi.fn(),
})

const directoryFixture = () => [
  {
    key: 'volume-10',
    title: '第一卷',
    expanded: true,
    children: [
      { key: 'chapter-301', title: '第一章', chapterId: 'chapter-301' },
      { key: 'chapter-302', title: '第二章', chapterId: 'chapter-302' },
    ],
  },
]

describe('useWorkbenchOutline', () => {
  it('maps volumes and chapters as the only directory source', () => {
    const outline = useWorkbenchOutline(createDeps())

    const volumes = outline.loadOutline(
      [{ volumeId: 'volume-10', title: '第一卷', sortOrder: 2 }],
      [
        { chapterId: 'chapter-302', volumeId: 'volume-10', title: '第二章', sortOrder: 2 },
        { chapterId: 'chapter-301', volumeId: 'volume-10', title: '第一章', sortOrder: 1 },
      ],
    )

    expect(volumes).toEqual(directoryFixture())
    expect(outline.outlineData.value).toEqual(volumes)
  })

  it('creates a chapter directly under its volume', async () => {
    const deps = createDeps()
    const outline = useWorkbenchOutline(deps)

    await outline.addChapter({ key: 'volume-10', title: '第一卷', expanded: true, children: [] })

    expect(deps.createChapter).toHaveBeenCalledWith('project-101', 'operator-201', {
      volumeId: 'volume-10',
      title: '第1章：未命名',
      sortOrder: 1,
    })
    expect(deps.reloadOutline).toHaveBeenCalledOnce()
  })

  it('renames volumes and chapters through their own APIs', async () => {
    const deps = createDeps()
    const outline = useWorkbenchOutline(deps)
    outline.outlineData.value = directoryFixture()
    outline.activeChapter.value = 'chapter-301'
    outline.currentChapterTitle.value = '第一章'

    await outline.renameNode({ nodeKey: 'volume-10', title: '新卷名' })
    await outline.renameNode({ nodeKey: 'chapter-301', title: '新章名' })

    expect(deps.updateVolume).toHaveBeenCalledWith('project-101', 'volume-10', 'operator-201', {
      title: '新卷名',
      sortOrder: 1,
      description: '',
    })
    expect(deps.updateChapter).toHaveBeenCalledWith('project-101', 'chapter-301', 'operator-201', {
      title: '新章名',
    })
    expect(outline.currentChapterTitle.value).toBe('新章名')
  })

  it('moves a chapter through the chapter position API', async () => {
    const deps = createDeps()
    const outline = useWorkbenchOutline(deps)
    outline.outlineData.value = directoryFixture()
    outline.structureRevision.value = 1

    await outline.moveNode({ nodeKey: 'chapter-302', parentKey: 'volume-10', direction: -1 })

    expect(deps.moveDirectoryItem).toHaveBeenCalledWith('project-101', {
      nodeType: 'CHAPTER',
      nodeId: 'chapter-302',
      targetVolumeId: 'volume-10',
      sortOrder: 1,
      expectedStructureRevision: 1,
    })
    expect(outline.outlineData.value[0].children.map((chapter) => chapter.key)).toEqual([
      'chapter-302',
      'chapter-301',
    ])
  })

  it('deletes a selected chapter without touching outline nodes', async () => {
    const deps = createDeps()
    const outline = useWorkbenchOutline(deps)
    outline.outlineData.value = directoryFixture()
    outline.activeChapter.value = 'chapter-301'
    outline.currentChapterTitle.value = '第一章'

    await outline.deleteChapter({ nodeKey: 'chapter-301', parentKey: 'volume-10' })

    expect(deps.deleteChapter).toHaveBeenCalledWith('project-101', 'chapter-301', 'operator-201')
    expect(outline.activeChapter.value).toBe('')
    expect(outline.currentChapterTitle.value).toBe('')
  })

  it('keeps local ordering unchanged when a move fails', async () => {
    const deps = createDeps()
    deps.moveDirectoryItem.mockRejectedValueOnce(new Error('move failed'))
    const outline = useWorkbenchOutline(deps)
    outline.outlineData.value = directoryFixture()
    outline.structureRevision.value = 1

    await outline.moveNode({ nodeKey: 'chapter-302', parentKey: 'volume-10', direction: -1 })

    expect(outline.outlineData.value[0].children.map((chapter) => chapter.key)).toEqual([
      'chapter-301',
      'chapter-302',
    ])
    expect(deps.notify).toHaveBeenCalledWith('move failed')
  })
})
