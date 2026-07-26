import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { rbacApi } from '@/api/modules/rbac.api'
import { getSession } from '@/stores/session'
import {
  normalizeRbacMenuList,
  normalizeRbacPermissionList,
  normalizeRbacRoleList,
  normalizeRbacUserList,
  toBusinessId,
  type RbacMenu,
  type RbacPermission,
  type RbacRole,
  type RbacUser,
  type RbacWorkspaceKey,
} from './rbacModel'

const USER_PAGE_SIZE = 8

export const useRbacConsole = () => {
  const router = useRouter()
  const session = getSession()
  const users = ref<RbacUser[]>([])
  const roles = ref<RbacRole[]>([])
  const permissions = ref<RbacPermission[]>([])
  const menus = ref<RbacMenu[]>([])
  const profileMenus = ref<RbacMenu[]>([])
  const userRoles = ref<RbacRole[]>([])
  const rolePermissions = ref<RbacPermission[]>([])
  const userRolesRevision = ref(0)
  const rolePermissionsRevision = ref(0)
  const savedUserRoleIds = ref<string[]>([])
  const savedRolePermissionIds = ref<string[]>([])
  const activeUserId = ref<string | null>(toBusinessId(session.userId))
  const activeRoleId = ref<string | null>(null)
  const errorMessage = ref('')
  const loading = ref(false)
  const loadFailed = ref(false)
  const createUserForm = ref({ email: '', displayName: '', initialPassword: '', confirmPassword: '' })
  const userDetailForm = ref({ displayName: '', status: 1 })
  const assignRoleForm = ref({ roleId: '' })
  const assignPermissionForm = ref({ permissionId: '' })
  const createRoleForm = ref({ name: '', code: '', description: '' })
  const roleDetailForm = ref({ name: '', description: '' })
  const activeWorkspace = ref<RbacWorkspaceKey>('users')
  const createUserExpanded = ref(false)
  const pendingDeleteUserId = ref<string | null>(null)
  const pendingDeleteRoleId = ref<string | null>(null)
  const userSearchQuery = ref('')
  const userStatusFilter = ref<'all' | '1' | '0'>('all')
  const userPage = ref(1)
  let latestUserSelectionToken = 0
  let latestRoleSelectionToken = 0

  const getRoleBusinessId = (role: RbacRole) => toBusinessId(role.roleId)
  const getPermissionBusinessId = (permission: RbacPermission) => toBusinessId(permission.permissionId)
  const getMenuBusinessId = (menu: RbacMenu) => toBusinessId(menu.menuId)
  const sortedIds = (values: Array<string | null>) =>
    values.filter((value): value is string => value != null).sort((left, right) => left.localeCompare(right))
  const sameIds = (left: string[], right: string[]) =>
    left.length === right.length && left.every((value, index) => value === right[index])

  const activeUser = computed(
    () => users.value.find((item) => toBusinessId(item.userId) === activeUserId.value) ?? null,
  )
  const activeRole = computed(
    () =>
      roles.value.find((item) => getRoleBusinessId(item) === activeRoleId.value) ??
      userRoles.value.find((item) => getRoleBusinessId(item) === activeRoleId.value) ??
      null,
  )
  const currentUserRoleIds = computed(() => sortedIds(userRoles.value.map(getRoleBusinessId)))
  const currentRolePermissionIds = computed(() =>
    sortedIds(rolePermissions.value.map(getPermissionBusinessId)),
  )
  const userRolesDirty = computed(() => !sameIds(currentUserRoleIds.value, savedUserRoleIds.value))
  const rolePermissionsDirty = computed(
    () => !sameIds(currentRolePermissionIds.value, savedRolePermissionIds.value),
  )
  const filteredUsers = computed(() => {
    const keyword = userSearchQuery.value.trim().toLowerCase()
    return users.value
      .filter((user) => {
        const matchesKeyword =
          !keyword ||
          user.displayName.toLowerCase().includes(keyword) ||
          user.email.toLowerCase().includes(keyword)
        const matchesStatus = userStatusFilter.value === 'all' || String(user.status) === userStatusFilter.value
        return matchesKeyword && matchesStatus
      })
      .sort((left, right) => {
        if (left.status !== right.status) return right.status - left.status
        return String(left.userId).localeCompare(String(right.userId), undefined, { numeric: true })
      })
  })
  const userTotalPages = computed(() => Math.max(1, Math.ceil(filteredUsers.value.length / USER_PAGE_SIZE)))
  const paginatedUsers = computed(() => {
    const start = (userPage.value - 1) * USER_PAGE_SIZE
    return filteredUsers.value.slice(start, start + USER_PAGE_SIZE)
  })
  const userPaginationText = computed(() => {
    if (!filteredUsers.value.length) return '0 / 0'
    const start = (userPage.value - 1) * USER_PAGE_SIZE + 1
    const end = Math.min(userPage.value * USER_PAGE_SIZE, filteredUsers.value.length)
    return `${start}-${end} / ${filteredUsers.value.length}`
  })

  watch(
    activeUser,
    (user) => {
      userDetailForm.value = { displayName: user?.displayName || '', status: user?.status ?? 1 }
    },
    { immediate: true },
  )
  watch(activeUserId, () => {
    pendingDeleteUserId.value = null
  })
  watch(activeRoleId, () => {
    pendingDeleteRoleId.value = null
  })
  watch([userSearchQuery, userStatusFilter], () => {
    userPage.value = 1
  })
  watch(filteredUsers, () => {
    if (userPage.value > userTotalPages.value) userPage.value = userTotalPages.value
  })
  watch(
    activeRole,
    (role) => {
      roleDetailForm.value = { name: role?.name || '', description: role?.description || '' }
    },
    { immediate: true },
  )

  const loadProfileMenus = async (userId: string) => {
    profileMenus.value = normalizeRbacMenuList(await rbacApi.listProfileMenus(userId))
  }
  const loadRolePermissions = async (roleId: string) => {
    const snapshot = await rbacApi.listRolePermissions(roleId)
    rolePermissions.value = normalizeRbacPermissionList(snapshot.items)
    rolePermissionsRevision.value = snapshot.revision
    savedRolePermissionIds.value = sortedIds(rolePermissions.value.map(getPermissionBusinessId))
  }
  const loadUserRoles = async (userId: string) => {
    const snapshot = await rbacApi.listUserRoles(userId)
    userRoles.value = normalizeRbacRoleList(snapshot.items)
    userRolesRevision.value = snapshot.revision
    savedUserRoleIds.value = sortedIds(userRoles.value.map(getRoleBusinessId))
    const existingRoleIds = new Set(
      userRoles.value.map(getRoleBusinessId).filter((item): item is string => item != null),
    )
    const defaultRoleId =
      activeRoleId.value && existingRoleIds.has(activeRoleId.value)
        ? activeRoleId.value
        : (getRoleBusinessId(userRoles.value[0] as RbacRole) ?? null)
    activeRoleId.value = defaultRoleId
    if (defaultRoleId != null) await loadRolePermissions(defaultRoleId)
    else {
      rolePermissions.value = []
      rolePermissionsRevision.value = 0
      savedRolePermissionIds.value = []
    }
  }

  const loadUserContext = async (userId: string, selectionToken: number) => {
    const roleSelectionTokenAtStart = latestRoleSelectionToken
    const nextProfileMenus = normalizeRbacMenuList(await rbacApi.listProfileMenus(userId))
    if (selectionToken !== latestUserSelectionToken) return false

    const nextUserRoleSnapshot = await rbacApi.listUserRoles(userId)
    const nextUserRoles = normalizeRbacRoleList(nextUserRoleSnapshot.items)
    if (selectionToken !== latestUserSelectionToken) return false

    profileMenus.value = nextProfileMenus
    userRoles.value = nextUserRoles
    userRolesRevision.value = nextUserRoleSnapshot.revision
    savedUserRoleIds.value = sortedIds(nextUserRoles.map(getRoleBusinessId))
    if (roleSelectionTokenAtStart !== latestRoleSelectionToken) return true

    const existingRoleIds = new Set(nextUserRoles.map(getRoleBusinessId).filter((item): item is string => item != null))
    const nextRoleId =
      activeRoleId.value && existingRoleIds.has(activeRoleId.value)
        ? activeRoleId.value
        : (getRoleBusinessId(nextUserRoles[0] as RbacRole) ?? null)
    const roleCommitToken = ++latestRoleSelectionToken
    let nextRolePermissions: RbacPermission[] = []
    let nextRolePermissionsRevision = 0
    if (nextRoleId != null) {
      const nextRolePermissionSnapshot = await rbacApi.listRolePermissions(nextRoleId)
      nextRolePermissions = normalizeRbacPermissionList(nextRolePermissionSnapshot.items)
      nextRolePermissionsRevision = nextRolePermissionSnapshot.revision
      if (selectionToken !== latestUserSelectionToken || roleCommitToken !== latestRoleSelectionToken) return true
    }
    if (roleCommitToken !== latestRoleSelectionToken) return true
    activeRoleId.value = nextRoleId
    rolePermissions.value = nextRolePermissions
    rolePermissionsRevision.value = nextRolePermissionsRevision
    savedRolePermissionIds.value = sortedIds(nextRolePermissions.map(getPermissionBusinessId))
    return true
  }

  const loadRoleContext = async (roleId: string, selectionToken: number) => {
    const snapshot = await rbacApi.listRolePermissions(roleId)
    const nextRolePermissions = normalizeRbacPermissionList(snapshot.items)
    if (selectionToken !== latestRoleSelectionToken) return false
    rolePermissions.value = nextRolePermissions
    rolePermissionsRevision.value = snapshot.revision
    savedRolePermissionIds.value = sortedIds(nextRolePermissions.map(getPermissionBusinessId))
    return true
  }

  const selectUser = async (userId: string) => {
    if (userId !== activeUserId.value && (userRolesDirty.value || rolePermissionsDirty.value)) {
      errorMessage.value = '当前授权有未保存变更，请先保存或撤销后再切换用户'
      return
    }
    const previous = {
      userId: activeUserId.value,
      profileMenus: [...profileMenus.value],
      userRoles: [...userRoles.value],
      rolePermissions: [...rolePermissions.value],
      roleId: activeRoleId.value,
      userRolesRevision: userRolesRevision.value,
      rolePermissionsRevision: rolePermissionsRevision.value,
      savedUserRoleIds: [...savedUserRoleIds.value],
      savedRolePermissionIds: [...savedRolePermissionIds.value],
    }
    const selectionToken = ++latestUserSelectionToken
    errorMessage.value = ''
    try {
      activeUserId.value = userId
      await loadUserContext(userId, selectionToken)
    } catch (error) {
      if (selectionToken !== latestUserSelectionToken) return
      activeUserId.value = previous.userId
      profileMenus.value = previous.profileMenus
      userRoles.value = previous.userRoles
      rolePermissions.value = previous.rolePermissions
      activeRoleId.value = previous.roleId
      userRolesRevision.value = previous.userRolesRevision
      rolePermissionsRevision.value = previous.rolePermissionsRevision
      savedUserRoleIds.value = previous.savedUserRoleIds
      savedRolePermissionIds.value = previous.savedRolePermissionIds
      errorMessage.value = error instanceof Error ? error.message : 'profile menus failed'
    }
  }

  const selectRole = async (roleId: string) => {
    if (roleId !== activeRoleId.value && rolePermissionsDirty.value) {
      errorMessage.value = '当前角色权限有未保存变更，请先保存或撤销后再切换角色'
      return
    }
    const previousRoleId = activeRoleId.value
    const selectionToken = ++latestRoleSelectionToken
    errorMessage.value = ''
    try {
      activeRoleId.value = roleId
      await loadRoleContext(roleId, selectionToken)
    } catch (error) {
      if (selectionToken !== latestRoleSelectionToken) return
      activeRoleId.value = previousRoleId
      errorMessage.value = error instanceof Error ? error.message : 'role permissions failed'
    }
  }

  let loadPage: () => Promise<void> = async () => undefined

  const createUser = async () => {
    errorMessage.value = ''
    if (createUserForm.value.initialPassword.length < 8) {
      errorMessage.value = '初始密码至少需要 8 个字符'
      return
    }
    if (createUserForm.value.initialPassword !== createUserForm.value.confirmPassword) {
      errorMessage.value = '两次输入的密码不一致'
      return
    }
    try {
      const createdUser = await rbacApi.createUser({
        email: createUserForm.value.email,
        displayName: createUserForm.value.displayName,
        status: 1,
        initialPassword: createUserForm.value.initialPassword,
      })
      createUserForm.value = { email: '', displayName: '', initialPassword: '', confirmPassword: '' }
      createUserExpanded.value = false
      await loadPage()
      const createdUserId = toBusinessId(createdUser?.userId)
      if (createdUserId == null) return
      activeUserId.value = createdUserId
      const createdUserIndex = filteredUsers.value.findIndex((user) => toBusinessId(user.userId) === createdUserId)
      if (createdUserIndex >= 0) userPage.value = Math.floor(createdUserIndex / USER_PAGE_SIZE) + 1
      await loadProfileMenus(createdUserId)
      await loadUserRoles(createdUserId)
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : 'create user failed'
    }
  }

  const requestDeleteSelectedUser = () => {
    if (activeUserId.value != null) pendingDeleteUserId.value = activeUserId.value
  }
  const cancelDeleteSelectedUser = () => {
    pendingDeleteUserId.value = null
  }
  const requestDeleteActiveRole = () => {
    if (activeRoleId.value != null) pendingDeleteRoleId.value = activeRoleId.value
  }
  const cancelDeleteActiveRole = () => {
    pendingDeleteRoleId.value = null
  }

  const updateSelectedUser = async () => {
    if (activeUserId.value == null) return
    errorMessage.value = ''
    try {
      await rbacApi.updateUser(activeUserId.value, {
        displayName: userDetailForm.value.displayName,
        status: Number(userDetailForm.value.status),
      })
      await loadPage()
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : 'update user failed'
    }
  }

  const deleteSelectedUser = async () => {
    if (activeUserId.value == null || pendingDeleteUserId.value !== activeUserId.value) return
    errorMessage.value = ''
    try {
      await rbacApi.deleteUser(activeUserId.value)
      pendingDeleteUserId.value = null
      await loadPage()
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : 'delete user failed'
    }
  }

  const restoreSelectedUser = async () => {
    if (activeUserId.value == null || !activeUser.value?.deletionRequestedAt) return
    errorMessage.value = ''
    try {
      await rbacApi.restorePendingUserDeletion(activeUserId.value)
      await loadPage()
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : 'restore user failed'
    }
  }

  const assignRoleToSelectedUser = () => {
    if (activeUserId.value == null || !assignRoleForm.value.roleId) return
    const selected = roles.value.find((role) => getRoleBusinessId(role) === assignRoleForm.value.roleId)
    if (selected && !userRoles.value.some((role) => getRoleBusinessId(role) === assignRoleForm.value.roleId)) {
      userRoles.value = [...userRoles.value, selected]
    }
    assignRoleForm.value.roleId = ''
  }

  const removeRoleFromSelectedUser = (roleId: string) => {
    if (activeUserId.value == null) return
    userRoles.value = userRoles.value.filter((role) => getRoleBusinessId(role) !== roleId)
  }

  const assignPermissionToActiveRole = () => {
    if (activeRoleId.value == null || !assignPermissionForm.value.permissionId) return
    const selected = permissions.value.find(
      (permission) => getPermissionBusinessId(permission) === assignPermissionForm.value.permissionId,
    )
    if (
      selected &&
      !rolePermissions.value.some(
        (permission) => getPermissionBusinessId(permission) === assignPermissionForm.value.permissionId,
      )
    ) {
      rolePermissions.value = [...rolePermissions.value, selected]
    }
    assignPermissionForm.value.permissionId = ''
  }

  const saveUserRoles = async () => {
    if (activeUserId.value == null || !userRolesDirty.value) return
    errorMessage.value = ''
    const userId = activeUserId.value
    try {
      const snapshot = await rbacApi.replaceUserRoles(userId, userRolesRevision.value, currentUserRoleIds.value)
      if (activeUserId.value !== userId) return
      userRoles.value = normalizeRbacRoleList(snapshot.items)
      userRolesRevision.value = snapshot.revision
      savedUserRoleIds.value = sortedIds(userRoles.value.map(getRoleBusinessId))
      await loadProfileMenus(userId)
    } catch (error) {
      const code = (error as { errorCode?: string })?.errorCode
      errorMessage.value =
        code === 'RBAC_REVISION_CONFLICT'
          ? '角色分配已被其他管理员修改，请刷新后重新操作'
          : error instanceof Error
            ? error.message
            : '保存用户角色失败'
    }
  }

  const discardUserRoleChanges = () => {
    const byId = new Map(roles.value.map((role) => [getRoleBusinessId(role), role]))
    userRoles.value = savedUserRoleIds.value
      .map((id) => byId.get(id))
      .filter((role): role is RbacRole => role != null)
  }

  const saveRolePermissions = async () => {
    if (activeRoleId.value == null || !rolePermissionsDirty.value) return
    errorMessage.value = ''
    const roleId = activeRoleId.value
    try {
      const snapshot = await rbacApi.replaceRolePermissions(
        roleId,
        rolePermissionsRevision.value,
        currentRolePermissionIds.value,
      )
      if (activeRoleId.value !== roleId) return
      rolePermissions.value = normalizeRbacPermissionList(snapshot.items)
      rolePermissionsRevision.value = snapshot.revision
      savedRolePermissionIds.value = sortedIds(rolePermissions.value.map(getPermissionBusinessId))
      if (activeUserId.value != null) await loadProfileMenus(activeUserId.value)
    } catch (error) {
      const code = (error as { errorCode?: string })?.errorCode
      errorMessage.value =
        code === 'RBAC_REVISION_CONFLICT'
          ? '角色权限已被其他管理员修改，请刷新后重新操作'
          : error instanceof Error
            ? error.message
            : '保存角色权限失败'
    }
  }

  const discardRolePermissionChanges = () => {
    const byId = new Map(permissions.value.map((permission) => [getPermissionBusinessId(permission), permission]))
    rolePermissions.value = savedRolePermissionIds.value
      .map((id) => byId.get(id))
      .filter((permission): permission is RbacPermission => permission != null)
  }

  const createRole = async () => {
    errorMessage.value = ''
    try {
      await rbacApi.createRole({ ...createRoleForm.value, isSystem: false })
      createRoleForm.value = { name: '', code: '', description: '' }
      roles.value = normalizeRbacRoleList(await rbacApi.listRoles())
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : 'create role failed'
    }
  }

  const updateActiveRole = async () => {
    if (activeRoleId.value == null) return
    errorMessage.value = ''
    try {
      await rbacApi.updateRole(activeRoleId.value, { ...roleDetailForm.value })
      roles.value = normalizeRbacRoleList(await rbacApi.listRoles())
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : 'update role failed'
    }
  }

  const deleteActiveRole = async () => {
    if (activeRoleId.value == null || pendingDeleteRoleId.value !== activeRoleId.value) return
    errorMessage.value = ''
    try {
      await rbacApi.deleteRole(activeRoleId.value)
      pendingDeleteRoleId.value = null
      roles.value = normalizeRbacRoleList(await rbacApi.listRoles())
      if (activeUserId.value != null) {
        await loadUserRoles(activeUserId.value)
        await loadProfileMenus(activeUserId.value)
      }
      const nextRoleId = getRoleBusinessId(roles.value[0] as RbacRole) ?? null
      activeRoleId.value = nextRoleId
      if (nextRoleId != null) await loadRolePermissions(nextRoleId)
      else rolePermissions.value = []
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : 'delete role failed'
    }
  }

  const removePermissionFromActiveRole = (permissionId: string) => {
    if (activeRoleId.value == null) return
    rolePermissions.value = rolePermissions.value.filter(
      (permission) => getPermissionBusinessId(permission) !== permissionId,
    )
  }

  const previousUserPage = () => {
    if (userPage.value > 1) userPage.value -= 1
  }
  const nextUserPage = () => {
    if (userPage.value < userTotalPages.value) userPage.value += 1
  }

  loadPage = async () => {
    if (loading.value) return
    loading.value = true
    loadFailed.value = false
    errorMessage.value = ''
    try {
      const [userList, roleList, permissionList, menuList] = await Promise.all([
        rbacApi.listUsers(),
        rbacApi.listRoles(),
        rbacApi.listPermissions(),
        rbacApi.listMenus(),
      ])
      users.value = normalizeRbacUserList(userList)
      roles.value = normalizeRbacRoleList(roleList)
      permissions.value = normalizeRbacPermissionList(permissionList)
      menus.value = normalizeRbacMenuList(menuList)

      const existingUserIds = new Set(
        users.value.map((item) => toBusinessId(item.userId)).filter((item): item is string => item != null),
      )
      const defaultUserId =
        activeUserId.value && existingUserIds.has(activeUserId.value)
          ? activeUserId.value
          : toBusinessId(users.value[0]?.userId)
      activeUserId.value = defaultUserId
      if (defaultUserId != null) {
        latestUserSelectionToken += 1
        await loadUserContext(defaultUserId, latestUserSelectionToken)
      } else {
        profileMenus.value = []
        userRoles.value = []
        rolePermissions.value = []
        userRolesRevision.value = 0
        rolePermissionsRevision.value = 0
        savedUserRoleIds.value = []
        savedRolePermissionIds.value = []
      }
    } catch (error) {
      users.value = []
      roles.value = []
      permissions.value = []
      menus.value = []
      profileMenus.value = []
      userRoles.value = []
      rolePermissions.value = []
      userRolesRevision.value = 0
      rolePermissionsRevision.value = 0
      savedUserRoleIds.value = []
      savedRolePermissionIds.value = []
      loadFailed.value = true
      errorMessage.value = error instanceof Error ? error.message : 'RBAC 数据加载失败'
    } finally {
      loading.value = false
    }
  }

  onMounted(() => void loadPage())

  return {
    router,
    session,
    users,
    roles,
    permissions,
    menus,
    profileMenus,
    userRoles,
    rolePermissions,
    userRolesRevision,
    rolePermissionsRevision,
    userRolesDirty,
    rolePermissionsDirty,
    activeUserId,
    activeRoleId,
    errorMessage,
    loading,
    loadFailed,
    loadPage,
    createUserForm,
    userDetailForm,
    assignRoleForm,
    assignPermissionForm,
    createRoleForm,
    roleDetailForm,
    activeWorkspace,
    createUserExpanded,
    pendingDeleteUserId,
    pendingDeleteRoleId,
    userSearchQuery,
    userStatusFilter,
    userPage,
    activeUser,
    activeRole,
    paginatedUsers,
    userTotalPages,
    userPaginationText,
    toBusinessId,
    getRoleBusinessId,
    getPermissionBusinessId,
    getMenuBusinessId,
    selectUser,
    selectRole,
    createUser,
    requestDeleteSelectedUser,
    cancelDeleteSelectedUser,
    requestDeleteActiveRole,
    cancelDeleteActiveRole,
    updateSelectedUser,
    deleteSelectedUser,
    restoreSelectedUser,
    assignRoleToSelectedUser,
    removeRoleFromSelectedUser,
    saveUserRoles,
    discardUserRoleChanges,
    assignPermissionToActiveRole,
    createRole,
    updateActiveRole,
    deleteActiveRole,
    removePermissionFromActiveRole,
    saveRolePermissions,
    discardRolePermissionChanges,
    previousUserPage,
    nextUserPage,
  }
}

export type RbacConsoleController = ReturnType<typeof useRbacConsole>
