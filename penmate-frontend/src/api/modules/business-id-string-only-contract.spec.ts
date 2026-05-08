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

  it('builds approval, chapter, outline, card, style, rag and profile endpoints with string ids only', async () => {
    const { approvalApi } = await import('./approval.api')
    const { chapterApi } = await import('./chapter.api')
    const { outlineApi } = await import('./outline.api')
    const { cardApi } = await import('./card.api')
    const { styleApi } = await import('./style.api')
    const { ragApi } = await import('./rag.api')
    const { profileApi } = await import('./profile.api')

    await approvalApi.getApproval('101', '88001')
    await chapterApi.restoreVersion('101', '301', '9', '1001')
    await outlineApi.moveNode('101', '11', '1001', { direction: 'before' })
    await cardApi.deleteCardRelation('101', '8', '1001')
    await styleApi.switchStyle('101', '1001', { toStyleId: '81' }, '90001')
    await ragApi.indexStatus('101', '9001')
    await profileApi.profileMenus('1001')

    expect(requestMock.get).toHaveBeenNthCalledWith(1, '/v1/novels/101/approvals/88001')
    expect(requestMock.post).toHaveBeenNthCalledWith(1, '/v1/novels/101/chapters/301/versions/9/restore?operatorId=1001')
    expect(requestMock.patch).toHaveBeenNthCalledWith(1, '/v1/novels/101/outlines/nodes/11/move?operatorId=1001', { direction: 'before' })
    expect(requestMock.delete).toHaveBeenNthCalledWith(1, '/v1/novels/101/card-relations/8?operatorId=1001')
    expect(requestMock.post).toHaveBeenNthCalledWith(2, '/v1/novels/101/styles/switch?operatorId=1001&sessionId=90001', { toStyleId: '81' })
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
    chapterApi.restoreVersion('101', '301', '9', 1001)
    // @ts-expect-error business ids must stay string-only
    styleApi.switchStyle(101, '1001', { toStyleId: '81' }, '90001')
  })
})
