import { describe, it, expect, vi, beforeEach } from 'vitest'

const { getMock, postMock, deleteMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
  deleteMock: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  default: {
    get: getMock,
    post: postMock,
    delete: deleteMock,
  },
}))

import { rbacApi } from './rbac.api'

describe('rbac.api', () => {
  beforeEach(() => {
    getMock.mockReset()
    postMock.mockReset()
    deleteMock.mockReset()
  })

  it('should_call_assign_user_role_endpoint_with_role_id_query_when_assign_user_role_invoked', async () => {
    postMock.mockResolvedValue({ ok: true })

    await rbacApi.assignUserRole(11, 22)

    expect(postMock).toHaveBeenCalledWith('/v1/users/11/roles?roleId=22')
  })

  it('should_call_profile_menus_endpoint_with_user_id_query_when_list_profile_menus_invoked', async () => {
    getMock.mockResolvedValue([])

    await rbacApi.listProfileMenus(9)

    expect(getMock).toHaveBeenCalledWith('/v1/profile/menus?userId=9')
  })

  it('should_throw_error_when_assign_role_permission_rejected', async () => {
    const error = new Error('assign failed')
    postMock.mockRejectedValue(error)

    await expect(rbacApi.assignRolePermission(3, 7)).rejects.toThrow('assign failed')

    expect(postMock).toHaveBeenCalledWith('/v1/roles/3/permissions?permissionId=7')
  })
})

