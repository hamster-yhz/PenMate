export type RbacUser = {
  userId: string
  email: string
  displayName: string
  status: number
  authMethod?: string
  deletionRequestedAt?: string
  deletionDueAt?: string
}

export type RbacRole = {
  roleId: string
  code: string
  name: string
  description?: string
  isSystem?: boolean
}

export type RbacPermission = {
  permissionId: string
  code: string
  name: string
  module?: string
  description?: string
}

export type RbacMenu = {
  menuId: string
  parentId?: string | null
  title: string
  path: string
  sortOrder?: number
  permissionCode?: string
  visible?: boolean
}

export type RbacWorkspaceKey = 'users' | 'roles' | 'menus'

export const toBusinessId = (value: unknown) => {
  if (typeof value !== 'string') return null
  const normalized = value.trim()
  return normalized || null
}

const asRecord = (value: unknown) =>
  value != null && typeof value === 'object' ? (value as Record<string, unknown>) : null

export const normalizeRbacUser = (value: unknown): RbacUser | null => {
  const candidate = asRecord(value)
  if (!candidate) return null

  const userId = toBusinessId(candidate.userId)
  const email = toBusinessId(candidate.email)
  const displayName = toBusinessId(candidate.displayName)
  const status = typeof candidate.status === 'number' ? candidate.status : null
  if (userId == null || email == null || displayName == null || status == null) return null

  return {
    userId,
    email,
    displayName,
    status,
    authMethod:
      typeof candidate.authMethod === 'string' && candidate.authMethod.trim() ? candidate.authMethod : undefined,
    deletionRequestedAt:
      typeof candidate.deletionRequestedAt === 'string' && candidate.deletionRequestedAt.trim()
        ? candidate.deletionRequestedAt
        : undefined,
    deletionDueAt:
      typeof candidate.deletionDueAt === 'string' && candidate.deletionDueAt.trim()
        ? candidate.deletionDueAt
        : undefined,
  }
}

export const normalizeRbacRole = (value: unknown): RbacRole | null => {
  const candidate = asRecord(value)
  if (!candidate) return null

  const roleId = toBusinessId(candidate.roleId)
  const code = toBusinessId(candidate.code)
  const name = toBusinessId(candidate.name)
  if (roleId == null || code == null || name == null) return null

  return {
    roleId,
    code,
    name,
    description:
      typeof candidate.description === 'string' && candidate.description.trim() ? candidate.description : undefined,
    isSystem: typeof candidate.isSystem === 'boolean' ? candidate.isSystem : undefined,
  }
}

export const normalizeRbacPermission = (value: unknown): RbacPermission | null => {
  const candidate = asRecord(value)
  if (!candidate) return null

  const permissionId = toBusinessId(candidate.permissionId)
  const code = toBusinessId(candidate.code)
  const name = toBusinessId(candidate.name)
  if (permissionId == null || code == null || name == null) return null

  return {
    permissionId,
    code,
    name,
    module: typeof candidate.module === 'string' && candidate.module.trim() ? candidate.module : undefined,
    description:
      typeof candidate.description === 'string' && candidate.description.trim() ? candidate.description : undefined,
  }
}

export const normalizeRbacMenu = (value: unknown): RbacMenu | null => {
  const candidate = asRecord(value)
  if (!candidate) return null

  const menuId = toBusinessId(candidate.menuId)
  const title = toBusinessId(candidate.title)
  const path = toBusinessId(candidate.path)
  if (menuId == null || title == null || path == null) return null

  return {
    menuId,
    parentId: candidate.parentId == null ? null : toBusinessId(candidate.parentId),
    title,
    path,
    sortOrder: typeof candidate.sortOrder === 'number' ? candidate.sortOrder : undefined,
    permissionCode:
      typeof candidate.permissionCode === 'string' && candidate.permissionCode.trim()
        ? candidate.permissionCode
        : undefined,
    visible: typeof candidate.visible === 'boolean' ? candidate.visible : undefined,
  }
}

export const normalizeRbacRoleList = (value: unknown): RbacRole[] =>
  Array.isArray(value) ? value.map(normalizeRbacRole).filter((item): item is RbacRole => item != null) : []

export const normalizeRbacPermissionList = (value: unknown): RbacPermission[] =>
  Array.isArray(value) ? value.map(normalizeRbacPermission).filter((item): item is RbacPermission => item != null) : []

export const normalizeRbacUserList = (value: unknown): RbacUser[] =>
  Array.isArray(value) ? value.map(normalizeRbacUser).filter((item): item is RbacUser => item != null) : []

export const normalizeRbacMenuList = (value: unknown): RbacMenu[] =>
  Array.isArray(value) ? value.map(normalizeRbacMenu).filter((item): item is RbacMenu => item != null) : []
