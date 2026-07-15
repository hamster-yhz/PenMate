import { beforeEach, describe, expect, it, vi } from 'vitest'
import { storyBibleApi, type StoryBibleNode } from '@/api/modules/storyBible.api'
import { useStoryBible } from '../useStoryBible'

const node: StoryBibleNode = {
  nodeId: '71', storyBibleId: '11', typeId: '21', title: 'Mira', summary: 'Pilot', bodyMarkdown: 'Body',
  attributesJson: '{}', inclusionPolicy: 'AUTO_RETRIEVE', canonStatus: 'CANON', revision: 3,
}

describe('useStoryBible', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(storyBibleApi, 'get').mockResolvedValue({ storyBibleId: '11', projectId: '101', title: 'Bible', contentRevision: 3 })
    vi.spyOn(storyBibleApi, 'listViews').mockResolvedValue([])
    vi.spyOn(storyBibleApi, 'listNodeTypes').mockResolvedValue([{ typeId: '21', storyBibleId: '11', typeCode: 'CHARACTER', semanticFamily: 'CHARACTER', displayName: '角色', fieldSchemaJson: '{}', system: true, sortOrder: 1 }])
    vi.spyOn(storyBibleApi, 'listNodes').mockResolvedValue([node])
    vi.spyOn(storyBibleApi, 'listCategories').mockResolvedValue([])
    vi.spyOn(storyBibleApi, 'listTags').mockResolvedValue([])
    vi.spyOn(storyBibleApi, 'listRelations').mockResolvedValue([])
    vi.spyOn(storyBibleApi, 'listProgressions').mockResolvedValue([])
    vi.spyOn(storyBibleApi, 'listChanges').mockResolvedValue([])
    vi.spyOn(storyBibleApi, 'getUserRoutingPreference').mockResolvedValue({ mode: 'RETRIEVAL', routerModelConfigRevision: 0, inherited: false })
    vi.spyOn(storyBibleApi, 'getSessionRoutingPreference').mockResolvedValue({ mode: 'RETRIEVAL', routerModelConfigRevision: 0, inherited: true })
    vi.spyOn(storyBibleApi, 'getNode').mockResolvedValue({ node, aliases: [{ aliasId: '81', nodeId: '71', alias: 'Captain' }], categoryIds: [], tagIds: [] })
    vi.spyOn(storyBibleApi, 'getEffectiveState').mockResolvedValue({ state: { title: 'Mira' } })
  })

  it('loads the current bible and creates an editable node draft', async () => {
    const story = useStoryBible({ getContext: () => ({ projectId: '101', operatorId: '7', userId: '7', sessionId: '9001', chapterId: '301' }) })
    await story.loadWorkspace()
    await story.selectNode('71')

    expect(story.root.value?.storyBibleId).toBe('11')
    expect(story.draft.value).toMatchObject({ nodeId: '71', revision: 3, title: 'Mira', aliases: ['Captain'] })
    expect(story.effectiveState.value).toEqual({ state: { title: 'Mira' } })
  })

  it('keeps the local draft intact when optimistic update conflicts', async () => {
    const story = useStoryBible({ getContext: () => ({ projectId: '101', operatorId: '7' }) })
    await story.loadWorkspace()
    await story.selectNode('71')
    story.draft.value!.title = 'Local edit'
    vi.spyOn(storyBibleApi, 'updateNode').mockRejectedValue(Object.assign(new Error('revision conflict'), { status: 409 }))

    await story.saveNode()

    expect(story.draft.value?.title).toBe('Local edit')
    expect(story.errorMessage.value).toContain('revision conflict')
  })
})
