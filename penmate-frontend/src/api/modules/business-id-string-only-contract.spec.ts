import { describe, expect, it, vi, beforeEach } from 'vitest'

const requestMock = {
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  patch: vi.fn(),
  delete: vi.fn(),
}

vi.mock('@/utils/request', () => ({
  default: requestMock,
}))

describe('business id string-only API contracts', () => {
  beforeEach(() => {
    requestMock.get.mockReset()
    requestMock.post.mockReset()
    requestMock.put.mockReset()
    requestMock.patch.mockReset()
    requestMock.delete.mockReset()
    requestMock.get.mockResolvedValue({})
    requestMock.post.mockResolvedValue({})
    requestMock.put.mockResolvedValue({})
    requestMock.patch.mockResolvedValue({})
    requestMock.delete.mockResolvedValue({})
  })

  it('builds approval, chapter, story bible, style, rag and profile endpoints with string ids only', async () => {
    const { approvalApi } = await import('./approval.api')
    const { chapterApi } = await import('./chapter.api')
    const { storyBibleApi } = await import('./storyBible.api')
    const { styleApi } = await import('./style.api')
    const { ragApi } = await import('./rag.api')
    const { profileApi } = await import('./profile.api')

    await approvalApi.getApproval('101', '88001')
    await chapterApi.acquireLease('101', '301')
    await storyBibleApi.deleteRelation('101', '8', '1001', 3)
    await styleApi.switchStyle('101', '1001', { toStyleId: '81' }, '90001')
    await ragApi.indexStatus('101', '9001')
    await profileApi.profileMenus('1001')

    expect(requestMock.get).toHaveBeenNthCalledWith(1, '/v1/novels/101/approvals/88001')
    expect(requestMock.post).toHaveBeenNthCalledWith(
      1,
      '/v1/novels/101/chapters/301/lease',
      { force: false },
    )
    expect(requestMock.delete).toHaveBeenNthCalledWith(
      1,
      '/v1/novels/101/story-bible/relations/8?expectedRevision=3',
    )
    expect(requestMock.post).toHaveBeenNthCalledWith(
      2,
      '/v1/novels/101/styles/switch?sessionId=90001',
      { toStyleId: '81' },
    )
    expect(requestMock.get).toHaveBeenNthCalledWith(2, '/v1/novels/101/rag/documents/9001/index-status')
    expect(requestMock.get).toHaveBeenNthCalledWith(3, '/v1/profile/menus?userId=1001')
  })

  it('rejects non-string business ids at compile time for core api surfaces', async () => {
    const { approvalApi } = await import('./approval.api')
    const { chapterApi } = await import('./chapter.api')
    const { styleApi } = await import('./style.api')

    void approvalApi
    void chapterApi
    void styleApi

    // @ts-expect-error business ids must stay string-only
    approvalApi.getApproval(101, '88001')
    // @ts-expect-error business ids must stay string-only
    chapterApi.acquireLease(101, '301')
    // @ts-expect-error business ids must stay string-only
    styleApi.switchStyle(101, '1001', { toStyleId: '81' }, '90001')
  })
})
