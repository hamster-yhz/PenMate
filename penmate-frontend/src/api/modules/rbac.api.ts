import request from '@/utils/request'
import type { IdLike } from '@/api/types'

type AnyRecord = Record<string, unknown>

export const rbacApi = {
  listUsers() {
    return request.get<AnyRecord[]>('/v1/users')
  },
  getUser(userId: IdLike) {
    return request.get<AnyRecord>(`/v1/users/${userId}`)
  },
  createUser(payload: AnyRecord) {
    return request.post<AnyRecord>('/v1/users', payload)
  },
  updateUser(userId: IdLike, payload: AnyRecord) {
    return request.put<AnyRecord>(`/v1/users/${userId}`, payload)
  },
  deleteUser(userId: IdLike) {
    return request.delete<AnyRecord>(`/v1/users/${userId}`)
  },
  listRoles() {
    return request.get<AnyRecord[]>('/v1/roles')
  },
  listUserRoles(userId: IdLike) {
    return request.get<AnyRecord[]>(`/v1/users/${userId}/roles`)
  },
  createRole(payload: AnyRecord) {
    return request.post<AnyRecord>('/v1/roles', payload)
  },
  updateRole(roleId: IdLike, payload: AnyRecord) {
    return request.put<AnyRecord>(`/v1/roles/${roleId}`, payload)
  },
  deleteRole(roleId: IdLike) {
    return request.delete<AnyRecord>(`/v1/roles/${roleId}`)
  },
  listPermissions() {
    return request.get<AnyRecord[]>('/v1/permissions')
  },
  listRolePermissions(roleId: IdLike) {
    return request.get<AnyRecord[]>(`/v1/roles/${roleId}/permissions`)
  },
  assignUserRole(userId: IdLike, roleId: IdLike) {
    return request.post<AnyRecord>(`/v1/users/${userId}/roles?roleId=${roleId}`)
  },
  removeUserRole(userId: IdLike, roleId: IdLike) {
    return request.delete<AnyRecord>(`/v1/users/${userId}/roles/${roleId}`)
  },
  assignRolePermission(roleId: IdLike, permissionId: IdLike) {
    return request.post<AnyRecord>(`/v1/roles/${roleId}/permissions?permissionId=${permissionId}`)
  },
  removeRolePermission(roleId: IdLike, permissionId: IdLike) {
    return request.delete<AnyRecord>(`/v1/roles/${roleId}/permissions/${permissionId}`)
  },
  listMenus() {
    return request.get<AnyRecord[]>('/v1/menus')
  },
  listProfileMenus(userId: IdLike) {
    return request.get<AnyRecord[]>(`/v1/profile/menus?userId=${userId}`)
  }
}

