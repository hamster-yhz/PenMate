import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = {
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  patch: vi.fn(),
  delete: vi.fn(),
}
vi.mock('@/utils/request', () => ({ default: requestMock }))

describe('storyBibleApi', () => {
  beforeEach(() => Object.values(requestMock).forEach((mock) => mock.mockReset().mockResolvedValue({})))

  it('uses string business ids and optimistic revisions in mutation endpoints', async () => {
    const { storyBibleApi } = await import('./storyBible.api')
    await storyBibleApi.updateNode('project-101', 'node-22', 'user-7', {
      expectedRevision: 4,
      typeId: 'type-2',
      title: 'Mira',
      summary: '',
      bodyMarkdown: '',
      attributesJson: '{}',
      inclusionPolicy: 'AUTO_RETRIEVE',
      canonStatus: 'CANON',
      aliases: [],
      categoryIds: [],
      tagIds: [],
    })
    await storyBibleApi.deleteProgression('project-101', 'progression-9', 'user-7', 3)

    expect(requestMock.patch).toHaveBeenCalledWith(
      '/v1/novels/project-101/story-bible/nodes/node-22',
      expect.objectContaining({ expectedRevision: 4, typeId: 'type-2' }),
    )
    expect(requestMock.delete).toHaveBeenCalledWith(
      '/v1/novels/project-101/story-bible/progressions/progression-9?expectedRevision=3',
    )
  })

  it('stores Story Bible routing at project scope', async () => {
    const { storyBibleApi } = await import('./storyBible.api')
    await storyBibleApi.updateProjectRoutingPreference('101', { mode: 'RETRIEVAL' })

    expect(requestMock.put).toHaveBeenCalledWith('/v1/novels/101/agent/routing-preference', {
      mode: 'RETRIEVAL',
    })
  })

  it('uses canonical filtered-node and changeset query routes', async () => {
    const { storyBibleApi } = await import('./storyBible.api')

    await storyBibleApi.listNodes('101', {
      query: 'Captain',
      categoryId: '31',
      tagId: '41',
      status: 'CANON',
    })
    await storyBibleApi.listChanges('101', 25)
    await storyBibleApi.getChangeset('101', '51')
    await storyBibleApi.listNodeChanges('101', '71', 20)

    expect(requestMock.get).toHaveBeenNthCalledWith(
      1,
      '/v1/novels/101/story-bible/nodes?status=CANON&query=Captain&categoryId=31&tagId=41',
    )
    expect(requestMock.get).toHaveBeenNthCalledWith(2, '/v1/novels/101/story-bible/changesets?limit=25')
    expect(requestMock.get).toHaveBeenNthCalledWith(3, '/v1/novels/101/story-bible/changesets/51')
    expect(requestMock.get).toHaveBeenNthCalledWith(4, '/v1/novels/101/story-bible/nodes/71/changesets?limit=20')
  })
})
