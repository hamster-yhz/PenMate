import { describe, it, expect, vi, beforeEach } from 'vitest'

const { postMock, putMock, deleteMock, getMock } = vi.hoisted(() => ({
  postMock: vi.fn(),
  putMock: vi.fn(),
  deleteMock: vi.fn(),
  getMock: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  default: {
    get: getMock,
    post: postMock,
    put: putMock,
    delete: deleteMock,
  },
}))

import { novelApi } from './novel.api'

describe('novel.api', () => {
  beforeEach(() => {
    getMock.mockReset()
    postMock.mockReset()
    putMock.mockReset()
    deleteMock.mockReset()
  })

  it('should_only_accept_owner_user_id_without_owner_id_alias_when_create_project_invoked', async () => {
    postMock.mockResolvedValue({ id: 1 })

    await novelApi.createProject({
      ownerUserId: 'user-8',
      title: 'T',
      summary: 'S',
      status: 'DRAFT',
    })

    expect(postMock).toHaveBeenCalledWith('/v1/novels', {
      ownerUserId: 'user-8',
      title: 'T',
      summary: 'S',
      status: 'DRAFT',
    })
  })

  it('should_not_fallback_owner_id_or_description_when_update_project_invoked', async () => {
    putMock.mockResolvedValue({ ok: true })

    await novelApi.updateProject('project-1', {
      ownerUserId: 'user-9',
      ownerId: 'legacy-owner',
      title: 'Updated',
      description: 'legacy-description',
    })

    expect(putMock).toHaveBeenCalledWith('/v1/novels/project-1', {
      ownerUserId: 'user-9',
      title: 'Updated',
    })
    expect(putMock.mock.calls[0][1]).not.toHaveProperty('ownerId')
    expect(putMock.mock.calls[0][1]).not.toHaveProperty('description')
    expect(putMock.mock.calls[0][1]).not.toHaveProperty('summary')
  })

  it('should_call_delete_project_endpoint_with_string_operator_id_query_when_delete_project_invoked', async () => {
    deleteMock.mockResolvedValue('deleted')

    await novelApi.deleteProject('project-100', 'operator-9')

    expect(deleteMock).toHaveBeenCalledWith('/v1/novels/project-100?operatorId=operator-9')
  })

  it('should_throw_error_when_delete_project_rejected', async () => {
    const error = new Error('delete failed')
    deleteMock.mockRejectedValue(error)

    await expect(novelApi.deleteProject('project-100', 'operator-9')).rejects.toThrow('delete failed')

    expect(deleteMock).toHaveBeenCalledWith('/v1/novels/project-100?operatorId=operator-9')
  })

  it('should_keep_version_no_numeric_semantics_while_project_and_chapter_ids_are_strings', async () => {
    getMock.mockResolvedValue({ versionNo: 7 })

    await novelApi.getChapterVersion('project-1', 'chapter-1', 7)

    expect(getMock).toHaveBeenCalledWith('/v1/novels/project-1/chapters/chapter-1/versions/7')
  })

  it('should_reject_number_business_ids_at_compile_time', () => {
    // @ts-expect-error business IDs must be string-only
    novelApi.deleteProject(100, 'operator-9')
    // @ts-expect-error business IDs must be string-only
    novelApi.getProject(100)
  })
})

