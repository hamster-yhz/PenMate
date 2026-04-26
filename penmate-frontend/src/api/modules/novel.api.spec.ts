import { describe, it, expect, vi, beforeEach } from 'vitest'

const { postMock, deleteMock } = vi.hoisted(() => ({
  postMock: vi.fn(),
  deleteMock: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  default: {
    post: postMock,
    delete: deleteMock,
  },
}))

import { novelApi } from './novel.api'

describe('novel.api', () => {
  beforeEach(() => {
    postMock.mockReset()
    deleteMock.mockReset()
  })

  it('should_map_owner_id_and_description_when_create_project_invoked', async () => {
    postMock.mockResolvedValue({ id: 1 })

    await novelApi.createProject({
      ownerId: 8,
      title: 'T',
      description: 'D',
      status: 'DRAFT',
    })

    expect(postMock).toHaveBeenCalledWith('/v1/novels', {
      ownerUserId: 8,
      title: 'T',
      summary: 'D',
      status: 'DRAFT',
    })
  })

  it('should_call_delete_project_endpoint_with_operator_id_query_when_delete_project_invoked', async () => {
    deleteMock.mockResolvedValue('deleted')

    await novelApi.deleteProject(100, 9)

    expect(deleteMock).toHaveBeenCalledWith('/v1/novels/100?operatorId=9')
  })

  it('should_throw_error_when_delete_project_rejected', async () => {
    const error = new Error('delete failed')
    deleteMock.mockRejectedValue(error)

    await expect(novelApi.deleteProject(100, 9)).rejects.toThrow('delete failed')

    expect(deleteMock).toHaveBeenCalledWith('/v1/novels/100?operatorId=9')
  })
})

