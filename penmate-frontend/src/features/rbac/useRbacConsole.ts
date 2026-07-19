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

const USER_PAGE_SIZE = 2

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
  const activeUserId = ref<string | null>(toBusinessId(session.userId))
  const activeRoleId = ref<string | null>(null)
  const errorMessage = ref('')
  const createUserForm = ref({ email: '', displayName: '', authMethod: 'local' })
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

  const activeUser = computed(
    () => users.value.find((item) => toBusinessId(item.userId) === activeUserId.value) ?? null,
  )
  const activeRole = computed(
    () =>
      roles.value.find((item) => getRoleBusinessId(item) === activeRoleId.value) ??
      userRoles.value.find((item) => getRoleBusinessId(item) === activeRoleId.value) ??
      null,
  )
  const filteredUsers = computed(() => {
    const keyword = userSearchQuery.value.trim().toLowerCase()
    return users.value
      .filter((user) => {
        const matchesKeyword =
          !keyword ||
          user.displayName.toLowerCase().includes(keyword) ||
          user.email.toLowerCase().includes(keyword) ||
          (user.authMethod || 'local').toLowerCase().includes(keyword)
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
    rolePermissions.value = normalizeRbacPermissionList(await rbacApi.listRolePermissions(roleId))
  }
  const loadUserRoles = async (userId: string) => {
    userRoles.value = normalizeRbacRoleList(await rbacApi.listUserRoles(userId))
    const existingRoleIds = new Set(
      userRoles.value.map(getRoleBusinessId).filter((item): item is string => item != null),
    )
    const defaultRoleId =
      activeRoleId.value && existingRoleIds.has(activeRoleId.value)
        ? activeRoleId.value
        : (getRoleBusinessId(userRoles.value[0] as RbacRole) ?? null)
    activeRoleId.value = defaultRoleId
    if (defaultRoleId != null) await loadRolePermissions(defaultRoleId)
    else rolePermissions.value = []
  }

  const loadUserContext = async (userId: string, selectionToken: number) => {
    const roleSelectionTokenAtStart = latestRoleSelectionToken
    const nextProfileMenus = normalizeRbacMenuList(await rbacApi.listProfileMenus(userId))
    if (selectionToken !== latestUserSelectionToken) return false

    const nextUserRoles = normalizeRbacRoleList(await rbacApi.listUserRoles(userId))
    if (selectionToken !== latestUserSelectionToken) return false

    profileMenus.value = nextProfileMenus
    userRoles.value = nextUserRoles
    if (roleSelectionTokenAtStart !== latestRoleSelectionToken) return true

    const existingRoleIds = new Set(nextUserRoles.map(getRoleBusinessId).filter((item): item is string => item != null))
    const nextRoleId =
      activeRoleId.value && existingRoleIds.has(activeRoleId.value)
        ? activeRoleId.value
        : (getRoleBusinessId(nextUserRoles[0] as RbacRole) ?? null)
    const roleCommitToken = ++latestRoleSelectionToken
    let nextRolePermissions: RbacPermission[] = []
    if (nextRoleId != null) {
      nextRolePermissions = normalizeRbacPermissionList(await rbacApi.listRolePermissions(nextRoleId))
      if (selectionToken !== latestUserSelectionToken || roleCommitToken !== latestRoleSelectionToken) return true
    }
    if (roleCommitToken !== latestRoleSelectionToken) return true
    activeRoleId.value = nextRoleId
    rolePermissions.value = nextRolePermissions
    return true
  }

  const loadRoleContext = async (roleId: string, selectionToken: number) => {
    const nextRolePermissions = normalizeRbacPermissionList(await rbacApi.listRolePermissions(roleId))
    if (selectionToken !== latestRoleSelectionToken) return false
    rolePermissions.value = nextRolePermissions
    return true
  }

  const selectUser = async (userId: string) => {
    const previous = {
      userId: activeUserId.value,
      profileMenus: [...profileMenus.value],
      userRoles: [...userRoles.value],
      rolePermissions: [...rolePermissions.value],
      roleId: activeRoleId.value,
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
      errorMessage.value = error instanceof Error ? error.message : 'profile menus failed'
    }
  }

  const selectRole = async (roleId: string) => {
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
    try {
      const createdUser = await rbacApi.createUser({
        email: createUserForm.value.email,
        displayName: createUserForm.value.displayName,
        status: 1,
        authMethod: createUserForm.value.authMethod || 'local',
      })
      createUserForm.value = { email: '', displayName: '', authMethod: 'local' }
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

  const assignRoleToSelectedUser = async () => {
    if (activeUserId.value == null || !assignRoleForm.value.roleId) return
    errorMessage.value = ''
    try {
      await rbacApi.assignUserRole(activeUserId.value, assignRoleForm.value.roleId)
      assignRoleForm.value.roleId = ''
      await loadUserRoles(activeUserId.value)
      await loadProfileMenus(activeUserId.value)
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : 'assign role failed'
    }
  }

  const removeRoleFromSelectedUser = async (roleId: string) => {
    if (activeUserId.value == null) return
    errorMessage.value = ''
    try {
      await rbacApi.removeUserRole(activeUserId.value, roleId)
      await loadUserRoles(activeUserId.value)
      await loadProfileMenus(activeUserId.value)
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : 'remove role failed'
    }
  }

  const assignPermissionToActiveRole = async () => {
    if (activeRoleId.value == null || !assignPermissionForm.value.permissionId) return
    errorMessage.value = ''
    try {
      await rbacApi.assignRolePermission(activeRoleId.value, assignPermissionForm.value.permissionId)
      assignPermissionForm.value.permissionId = ''
      await loadRolePermissions(activeRoleId.value)
      if (activeUserId.value != null) await loadProfileMenus(activeUserId.value)
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : 'assign permission failed'
    }
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

  const removePermissionFromActiveRole = async (permissionId: string) => {
    if (activeRoleId.value == null) return
    errorMessage.value = ''
    try {
      await rbacApi.removeRolePermission(activeRoleId.value, permissionId)
      await loadRolePermissions(activeRoleId.value)
      if (activeUserId.value != null) await loadProfileMenus(activeUserId.value)
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : 'remove permission failed'
    }
  }

  const previousUserPage = () => {
    if (userPage.value > 1) userPage.value -= 1
  }
  const nextUserPage = () => {
    if (userPage.value < userTotalPages.value) userPage.value += 1
  }

  loadPage = async () => {
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
      }
    } catch (error) {
      users.value = []
      roles.value = []
      permissions.value = []
      menus.value = []
      profileMenus.value = []
      userRoles.value = []
      rolePermissions.value = []
      errorMessage.value = error instanceof Error ? error.message : 'RBAC 数据加载失败'
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
    activeUserId,
    activeRoleId,
    errorMessage,
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
    assignRoleToSelectedUser,
    removeRoleFromSelectedUser,
    assignPermissionToActiveRole,
    createRole,
    updateActiveRole,
    deleteActiveRole,
    removePermissionFromActiveRole,
    previousUserPage,
    nextUserPage,
  }
}

export type RbacConsoleController = ReturnType<typeof useRbacConsole>
