import request from '@/utils/request'
import type { IdLike } from '@/api/types'

type AnyRecord = Record<string, unknown>

export const rbacApi = {
  listUsers() {
    return request.get<AnyRecord[]>('/v1/users')
  },
  createUser(payload: AnyRecord) {
    return request.post<AnyRecord>('/v1/users', payload)
  },
  listRoles() {
    return request.get<AnyRecord[]>('/v1/roles')
  },
  createRole(payload: AnyRecord) {
    return request.post<AnyRecord>('/v1/roles', payload)
  },
  listPermissions() {
    return request.get<AnyRecord[]>('/v1/permissions')
  },
  assignUserRole(userId: IdLike, roleId: IdLike) {
    return request.post<AnyRecord>(`/v1/users/${userId}/roles?roleId=${roleId}`)
  },
  assignRolePermission(roleId: IdLike, permissionId: IdLike) {
    return request.post<AnyRecord>(`/v1/roles/${roleId}/permissions?permissionId=${permissionId}`)
  }
}

