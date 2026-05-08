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

    await rbacApi.assignUserRole('user-11', 'role-22')

    expect(postMock).toHaveBeenCalledWith('/v1/users/user-11/roles?roleId=role-22')
  })

  it('should_call_profile_menus_endpoint_with_user_id_query_when_list_profile_menus_invoked', async () => {
    getMock.mockResolvedValue([])

    await rbacApi.listProfileMenus('user-9')

    expect(getMock).toHaveBeenCalledWith('/v1/profile/menus?userId=user-9')
  })

  it('should_reject_user_role_responses_that_only_expose_legacy_id', async () => {
    getMock.mockResolvedValue([
      { id: 1, name: 'Legacy role only' },
    ])

    await expect(rbacApi.listUserRoles('user-11')).rejects.toThrow('Invalid role contract')

    expect(getMock).toHaveBeenCalledWith('/v1/users/user-11/roles')
  })

  it('should_call_user_roles_endpoint_when_list_user_roles_invoked', async () => {
    getMock.mockResolvedValue([{ roleId: 'role-11', id: 2, name: 'Editor' }])

    const result = await rbacApi.listUserRoles('user-11')

    expect(getMock).toHaveBeenCalledWith('/v1/users/user-11/roles')
    expect(result).toEqual([{ roleId: 'role-11', name: 'Editor' }])
    expect(result[0]).not.toHaveProperty('id')
  })

  it('should_reject_role_permission_responses_that_only_expose_legacy_id', async () => {
    getMock.mockResolvedValue([
      { id: 7, code: 'legacy-only' },
    ])

    await expect(rbacApi.listRolePermissions('role-22')).rejects.toThrow('Invalid permission contract')

    expect(getMock).toHaveBeenCalledWith('/v1/roles/role-22/permissions')
  })

  it('should_call_role_permissions_endpoint_when_list_role_permissions_invoked', async () => {
    getMock.mockResolvedValue([{ permissionId: 'perm-22', id: 9, code: 'novel:read' }])

    const result = await rbacApi.listRolePermissions('role-22')

    expect(getMock).toHaveBeenCalledWith('/v1/roles/role-22/permissions')
    expect(result).toEqual([{ permissionId: 'perm-22', code: 'novel:read' }])
    expect(result[0]).not.toHaveProperty('id')
  })

  it('should_throw_error_when_assign_role_permission_rejected', async () => {
    const error = new Error('assign failed')
    postMock.mockRejectedValue(error)

    await expect(rbacApi.assignRolePermission('role-3', 'perm-7')).rejects.toThrow('assign failed')

    expect(postMock).toHaveBeenCalledWith('/v1/roles/role-3/permissions?permissionId=perm-7')
  })

  it('should_reject_number_business_ids_at_compile_time', () => {
    // @ts-expect-error business IDs must be string-only
    const userId: string = 11
    // @ts-expect-error business IDs must be string-only
    const roleId: string = 9

    void rbacApi.assignUserRole(userId, roleId)
  })
})

