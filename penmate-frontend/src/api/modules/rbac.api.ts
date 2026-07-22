import request from '@/utils/request'

type AnyRecord = Record<string, unknown>
export type RbacAssignmentSnapshot = { revision: number; items: AnyRecord[] }

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

const normalizeAssignmentSnapshot = (
  payload: AnyRecord,
  semanticIdKey: string,
  errorMessage: string,
): RbacAssignmentSnapshot => {
  const revision = payload?.revision
  const rawItems = payload?.items
  if (!Number.isSafeInteger(revision) || Number(revision) < 0 || !Array.isArray(rawItems)) {
    throw new Error(errorMessage)
  }
  const items = rawItems.filter((item): item is AnyRecord => item != null && typeof item === 'object')
  assertNoLegacyOnlyEntities(items, semanticIdKey, errorMessage)
  return {
    revision: Number(revision),
    items: items
      .map((item) => normalizeNamedEntity(item, semanticIdKey))
      .filter((item): item is AnyRecord => item !== null),
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
  restorePendingUserDeletion(userId: string) {
    return request.post<AnyRecord>(`/v1/users/${userId}/restore-deletion`)
  },
  listRoles() {
    return request.get<AnyRecord[]>('/v1/roles')
  },
  async listUserRoles(userId: string) {
    const snapshot = await request.get<AnyRecord>(`/v1/users/${userId}/roles`)
    return normalizeAssignmentSnapshot(snapshot, 'roleId', 'Invalid user role assignment contract')
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
    const snapshot = await request.get<AnyRecord>(`/v1/roles/${roleId}/permissions`)
    return normalizeAssignmentSnapshot(snapshot, 'permissionId', 'Invalid role permission assignment contract')
  },
  async replaceUserRoles(userId: string, expectedRevision: number, roleIds: string[]) {
    const snapshot = await request.put<AnyRecord>(`/v1/users/${userId}/roles`, {
      expectedRevision,
      assignmentIds: roleIds,
    })
    return normalizeAssignmentSnapshot(snapshot, 'roleId', 'Invalid user role assignment contract')
  },
  async replaceRolePermissions(roleId: string, expectedRevision: number, permissionIds: string[]) {
    const snapshot = await request.put<AnyRecord>(`/v1/roles/${roleId}/permissions`, {
      expectedRevision,
      assignmentIds: permissionIds,
    })
    return normalizeAssignmentSnapshot(snapshot, 'permissionId', 'Invalid role permission assignment contract')
  },
  listMenus() {
    return request.get<AnyRecord[]>('/v1/menus')
  },
  listProfileMenus(userId: string) {
    return request.get<AnyRecord[]>(`/v1/profile/menus?userId=${userId}`)
  },
}
