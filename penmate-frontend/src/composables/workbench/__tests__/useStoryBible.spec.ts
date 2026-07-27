import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  storyBibleApi,
  type StoryBibleNode,
  type StoryBibleProgression,
  type StoryBibleRelation,
} from '@/api/modules/storyBible.api'
import { useStoryBible } from '../useStoryBible'

const node: StoryBibleNode = {
  nodeId: '71',
  storyBibleId: '11',
  typeId: '21',
  title: 'Mira',
  summary: 'Pilot',
  bodyMarkdown: 'Body',
  attributesJson: '{}',
  inclusionPolicy: 'AUTO_RETRIEVE',
  canonStatus: 'CANON',
  revision: 3,
}
const relation: StoryBibleRelation = {
  relationId: '91',
  storyBibleId: '11',
  sourceNodeId: '71',
  targetNodeId: '72',
  relationType: 'ALLY_OF',
  description: 'Old',
  attributesJson: '{}',
  revision: 2,
}
const progression: StoryBibleProgression = {
  progressionId: '92',
  storyBibleId: '11',
  nodeId: '71',
  anchorChapterId: '301',
  endChapterId: null,
  storyEventNodeId: null,
  patchJson: '[]',
  summary: 'Old',
  revision: 4,
}

describe('useStoryBible', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(storyBibleApi, 'bootstrap').mockResolvedValue({
      storyBibleId: '11',
      projectId: '101',
      title: 'Bible',
      contentRevision: 3,
    })
    vi.spyOn(storyBibleApi, 'get').mockResolvedValue({
      storyBibleId: '11',
      projectId: '101',
      title: 'Bible',
      contentRevision: 3,
    })
    vi.spyOn(storyBibleApi, 'listViews').mockResolvedValue([])
    vi.spyOn(storyBibleApi, 'listNodeTypes').mockResolvedValue([
      {
        typeId: '21',
        storyBibleId: '11',
        typeCode: 'CHARACTER',
        semanticFamily: 'CHARACTER',
        displayName: '角色',
        fieldSchemaJson: '{}',
        system: true,
        sortOrder: 1,
      },
    ])
    vi.spyOn(storyBibleApi, 'listNodes').mockResolvedValue([node])
    vi.spyOn(storyBibleApi, 'listCategories').mockResolvedValue([])
    vi.spyOn(storyBibleApi, 'listTags').mockResolvedValue([])
    vi.spyOn(storyBibleApi, 'listRelations').mockResolvedValue([])
    vi.spyOn(storyBibleApi, 'listProgressions').mockResolvedValue([])
    vi.spyOn(storyBibleApi, 'listChanges').mockResolvedValue({ items: [], nextBeforeRevision: null })
    vi.spyOn(storyBibleApi, 'listNodeChanges').mockResolvedValue([])
    vi.spyOn(storyBibleApi, 'getNode').mockResolvedValue({
      node,
      aliases: [{ aliasId: '81', nodeId: '71', alias: 'Captain' }],
      categoryIds: [],
      tagIds: [],
    })
    vi.spyOn(storyBibleApi, 'getEffectiveState').mockResolvedValue({ state: { title: 'Mira' } })
  })

  it('loads the current bible and creates an editable node draft', async () => {
    const story = useStoryBible({
      getContext: () => ({ projectId: '101', operatorId: '7', userId: '7', sessionId: '9001', chapterId: '301' }),
    })
    await story.loadWorkspace()
    await story.selectNode('71')

    expect(story.root.value?.storyBibleId).toBe('11')
    expect(storyBibleApi.bootstrap).toHaveBeenCalledWith('101', '7', 'Story Bible')
    expect(story.draft.value).toMatchObject({ nodeId: '71', revision: 3, title: 'Mira', aliases: ['Captain'] })
    expect(story.effectiveState.value).toEqual({ state: { title: 'Mira' } })
    expect(storyBibleApi.listNodeChanges).toHaveBeenCalledWith('101', '71')
  })

  it('delegates category tag and alias filtering to the backend without hiding alias matches locally', async () => {
    const aliasMatch = { ...node, title: 'Mira' }
    const story = useStoryBible({ getContext: () => ({ projectId: '101', operatorId: '7' }) })
    await story.loadWorkspace()
    vi.mocked(storyBibleApi.listNodes).mockResolvedValueOnce([aliasMatch])
    story.searchQuery.value = 'Captain'
    story.selectedCategoryId.value = '31'
    story.selectedTagId.value = '41'

    await story.refreshNodes()

    expect(storyBibleApi.listNodes).toHaveBeenLastCalledWith('101', {
      typeId: undefined,
      status: undefined,
      query: 'Captain',
      categoryId: '31',
      tagId: '41',
    })
    expect(story.filteredNodes.value).toEqual([aliasMatch])
  })

  it('keeps the local draft intact when optimistic update conflicts', async () => {
    const story = useStoryBible({ getContext: () => ({ projectId: '101', operatorId: '7' }) })
    await story.loadWorkspace()
    await story.selectNode('71')
    story.draft.value!.title = 'Local edit'
    vi.spyOn(storyBibleApi, 'updateNode').mockRejectedValue(
      Object.assign(new Error('revision conflict'), { status: 409 }),
    )

    await story.saveNode()

    expect(story.draft.value?.title).toBe('Local edit')
    expect(story.errorMessage.value).toContain('revision conflict')
  })

  it('updates relations and progressions with expected revisions and refreshes audit metadata', async () => {
    vi.mocked(storyBibleApi.listRelations).mockResolvedValueOnce([relation])
    vi.mocked(storyBibleApi.listProgressions).mockResolvedValueOnce([progression])
    const updatedRelation = { ...relation, description: 'New', revision: 3 }
    const updatedProgression = { ...progression, summary: 'New', revision: 5 }
    const updateRelation = vi.spyOn(storyBibleApi, 'updateRelation').mockResolvedValue(updatedRelation)
    const updateProgression = vi.spyOn(storyBibleApi, 'updateProgression').mockResolvedValue(updatedProgression)
    const story = useStoryBible({ getContext: () => ({ projectId: '101', operatorId: '7', chapterId: '301' }) })
    await story.loadWorkspace()

    await story.updateRelation('91', {
      expectedRevision: 2,
      targetNodeId: '72',
      relationType: 'ALLY_OF',
      description: 'New',
      attributesJson: '{}',
    })
    await story.updateProgression('92', {
      expectedRevision: 4,
      anchorChapterId: '301',
      endChapterId: null,
      storyEventNodeId: null,
      patchJson: '[]',
      summary: 'New',
    })

    expect(updateRelation).toHaveBeenCalledWith('101', '91', '7', expect.objectContaining({ expectedRevision: 2 }))
    expect(updateProgression).toHaveBeenCalledWith('101', '92', '7', expect.objectContaining({ expectedRevision: 4 }))
    expect(story.relations.value[0]).toEqual(updatedRelation)
    expect(story.progressions.value[0]).toEqual(updatedProgression)
    expect(storyBibleApi.get).toHaveBeenCalledTimes(2)
    expect(storyBibleApi.listChanges).toHaveBeenCalledTimes(3)
  })

  it('appends unique archived history records from older pages', async () => {
    const recent = {
      changesetId: '501',
      storyBibleId: '11',
      contentRevision: 50,
      actorType: 'AGENT' as const,
      sourceRunId: '801',
      changeSummary: 'Recent update',
      createdAt: new Date().toISOString(),
    }
    const archived = {
      changesetId: '499',
      storyBibleId: '11',
      contentRevision: 48,
      actorType: 'AGENT' as const,
      sourceRunId: '799',
      changeSummary: 'Archived update',
      createdAt: new Date(Date.now() - 8 * 24 * 60 * 60 * 1000).toISOString(),
      archivedAt: new Date().toISOString(),
    }
    vi.mocked(storyBibleApi.listChanges)
      .mockResolvedValueOnce({ items: [recent], nextBeforeRevision: 50 })
      .mockResolvedValueOnce({ items: [recent, archived], nextBeforeRevision: null })
    const story = useStoryBible({ getContext: () => ({ projectId: '101', operatorId: '7' }) })

    await story.loadWorkspace()
    await story.loadMoreHistory()

    expect(storyBibleApi.listChanges).toHaveBeenLastCalledWith('101', 50, 50)
    expect(story.history.value).toEqual([recent, archived])
    expect(story.historyBeforeRevision.value).toBeNull()
  })
})
