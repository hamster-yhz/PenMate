import request from '@/utils/request'

type AnyRecord = Record<string, unknown>

const normalizeNamedEntity = (payload: AnyRecord, semanticIdKey: string): AnyRecord | null => {
  const semanticId = payload[semanticIdKey]
  if (typeof semanticId !== 'string' || semanticId.trim() === '') {
    return null
  }

  const next: AnyRecord = { ...payload }
  delete next.id
  next[semanticIdKey] = semanticId.trim()
  return next
}

const assertNoLegacyOnlyEntities = (items: AnyRecord[], semanticIdKey: string, errorMessage: string) => {
  const hasLegacyOnlyEntry = items.some(
    (item) =>
      item != null &&
      typeof item === 'object' &&
      'id' in item &&
      (typeof item[semanticIdKey] !== 'string' || item[semanticIdKey].trim() === ''),
  )
  if (hasLegacyOnlyEntry) {
    throw new Error(errorMessage)
  }
}

export const rbacApi = {
  listUsers() {
    return request.get<AnyRecord[]>('/v1/users')
  },
  getUser(userId: string) {
    return request.get<AnyRecord>(`/v1/users/${userId}`)
  },
  createUser(payload: AnyRecord) {
    return request.post<AnyRecord>('/v1/users', payload)
  },
  updateUser(userId: string, payload: AnyRecord) {
    return request.put<AnyRecord>(`/v1/users/${userId}`, payload)
  },
  deleteUser(userId: string) {
    return request.delete<AnyRecord>(`/v1/users/${userId}`)
  },
  listRoles() {
    return request.get<AnyRecord[]>('/v1/roles')
  },
  async listUserRoles(userId: string) {
    const roles = await request.get<AnyRecord[]>(`/v1/users/${userId}/roles`)
    const normalizedRoles = Array.isArray(roles) ? roles : []
    assertNoLegacyOnlyEntities(normalizedRoles, 'roleId', 'Invalid role contract')
    return normalizedRoles
      .map((item) => normalizeNamedEntity(item, 'roleId'))
      .filter((item): item is AnyRecord => item !== null)
  },
  createRole(payload: AnyRecord) {
    return request.post<AnyRecord>('/v1/roles', payload)
  },
  updateRole(roleId: string, payload: AnyRecord) {
    return request.put<AnyRecord>(`/v1/roles/${roleId}`, payload)
  },
  deleteRole(roleId: string) {
    return request.delete<AnyRecord>(`/v1/roles/${roleId}`)
  },
  listPermissions() {
    return request.get<AnyRecord[]>('/v1/permissions')
  },
  async listRolePermissions(roleId: string) {
    const permissions = await request.get<AnyRecord[]>(`/v1/roles/${roleId}/permissions`)
    const normalizedPermissions = Array.isArray(permissions) ? permissions : []
    assertNoLegacyOnlyEntities(normalizedPermissions, 'permissionId', 'Invalid permission contract')
    return normalizedPermissions
      .map((item) => normalizeNamedEntity(item, 'permissionId'))
      .filter((item): item is AnyRecord => item !== null)
  },
  assignUserRole(userId: string, roleId: string) {
    return request.post<AnyRecord>(`/v1/users/${userId}/roles?roleId=${roleId}`)
  },
  removeUserRole(userId: string, roleId: string) {
    return request.delete<AnyRecord>(`/v1/users/${userId}/roles/${roleId}`)
  },
  assignRolePermission(roleId: string, permissionId: string) {
    return request.post<AnyRecord>(`/v1/roles/${roleId}/permissions?permissionId=${permissionId}`)
  },
  removeRolePermission(roleId: string, permissionId: string) {
    return request.delete<AnyRecord>(`/v1/roles/${roleId}/permissions/${permissionId}`)
  },
  listMenus() {
    return request.get<AnyRecord[]>('/v1/menus')
  },
  listProfileMenus(userId: string) {
    return request.get<AnyRecord[]>(`/v1/profile/menus?userId=${userId}`)
  },
}
