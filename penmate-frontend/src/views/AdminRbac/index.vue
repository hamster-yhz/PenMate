<template>
  <div class="admin-rbac-page">
    <header class="rbac-header">
      <div>
        <p class="eyebrow">Admin Console</p>
        <h1>RBAC 管理</h1>
        <p class="subtitle">面向管理员账号的用户、角色、权限与菜单绑定视图</p>
      </div>
      <div class="header-actions">
        <button class="ghost-btn" type="button" @click="router.push('/mybooks')">返回书架</button>
      </div>
    </header>

    <section class="summary-grid">
      <article class="summary-card current-admin">
        <span class="summary-label">当前管理员</span>
        <strong data-testid="rbac-active-user-name">{{ activeUser?.displayName || session.userName || '未识别用户' }}</strong>
        <span>{{ session.userEmail || activeUser?.email || '无邮箱信息' }}</span>
      </article>
      <article class="summary-card">
        <span class="summary-label">用户数</span>
        <strong>{{ users.length }}</strong>
      </article>
      <article class="summary-card">
        <span class="summary-label">角色数</span>
        <strong>{{ roles.length }}</strong>
      </article>
      <article class="summary-card">
        <span class="summary-label">权限 / 菜单</span>
        <strong>{{ permissions.length }} / {{ menus.length }}</strong>
      </article>
    </section>

    <section v-if="errorMessage" class="error-banner" data-testid="rbac-error-state">
      <strong>RBAC 数据加载失败</strong>
      <span>{{ errorMessage }}</span>
    </section>

    <main class="rbac-layout">
      <section class="panel">
        <div class="panel-header">
          <h2>用户列表</h2>
          <span class="muted">点击用户查看该账号的可见菜单</span>
        </div>

        <div class="sub-panel create-user-panel">
          <h3>创建用户</h3>
          <div class="form-grid">
            <input
              v-model="createUserForm.email"
              data-testid="rbac-create-user-email"
              class="field-input"
              type="email"
              placeholder="邮箱"
            />
            <input
              v-model="createUserForm.displayName"
              data-testid="rbac-create-user-display-name"
              class="field-input"
              type="text"
              placeholder="展示名"
            />
            <input
              v-model="createUserForm.authMethod"
              data-testid="rbac-create-user-auth-method"
              class="field-input"
              type="text"
              placeholder="认证方式"
            />
            <button
              data-testid="rbac-create-user-submit"
              class="primary-btn"
              type="button"
              @click="createUser"
            >
              创建用户
            </button>
          </div>
        </div>

        <div class="sub-panel">
          <h3>用户详情</h3>
          <div class="form-grid">
            <input
              v-model="userDetailForm.displayName"
              data-testid="rbac-user-detail-display-name"
              class="field-input"
              type="text"
              placeholder="展示名"
            />
            <select
              v-model="userDetailForm.status"
              data-testid="rbac-user-detail-status"
              class="field-input"
            >
              <option :value="1">启用</option>
              <option :value="0">停用</option>
            </select>
            <button
              data-testid="rbac-user-detail-submit"
              class="primary-btn"
              type="button"
              @click="updateSelectedUser"
            >
              保存用户
            </button>
            <button
              data-testid="rbac-user-delete-submit"
              class="ghost-btn"
              type="button"
              @click="deleteSelectedUser"
            >
              删除用户
            </button>
          </div>
        </div>

        <div class="sub-panel">
          <h3>绑定角色</h3>
          <div class="form-grid">
            <select
              v-model="assignRoleForm.roleId"
              data-testid="rbac-assign-role-role-id"
              class="field-input"
            >
              <option value="">请选择角色</option>
              <option
                v-for="role in roles"
                :key="`assignable-role-${role.roleId || role.id}`"
                :value="String(role.roleId || role.id || '')"
              >
                {{ role.name }} · {{ role.code }}
              </option>
            </select>
            <button
              data-testid="rbac-assign-role-submit"
              class="primary-btn"
              type="button"
              @click="assignRoleToSelectedUser"
            >
              绑定角色
            </button>
          </div>
        </div>

        <div class="user-list">
          <button
            v-for="user in users"
            :key="user.userId"
            :data-testid="`rbac-user-select-${user.userId}`"
            class="user-card"
            :class="{ active: user.userId === activeUserId }"
            type="button"
            @click="selectUser(user.userId)"
          >
            <div class="user-card-top">
              <strong>{{ user.displayName }}</strong>
              <span class="status-badge" :class="{ enabled: user.status === 1 }">
                {{ user.status === 1 ? '启用' : '停用' }}
              </span>
            </div>
            <span>{{ user.email }}</span>
            <small>userId: {{ user.userId }} · auth: {{ user.authMethod || 'local' }}</small>
          </button>
        </div>
      </section>

      <section class="panel panel-stack">
        <div class="panel-header">
          <h2>角色与权限</h2>
          <span class="muted">对照后端 RBAC 接口只读展示基础资源</span>
        </div>

        <div class="sub-panel">
          <h3>角色</h3>
          <ul class="token-list">
            <li v-for="role in roles" :key="role.roleId || role.id">
              <button
                :data-testid="`rbac-role-select-${role.roleId || role.id}`"
                class="role-select-btn"
                :class="{ active: (role.roleId || role.id) === activeRoleId }"
                type="button"
                @click="selectRole(Number(role.roleId || role.id))"
              >
                <strong>{{ role.name }}</strong>
                <span>{{ role.code }}</span>
              </button>
            </li>
          </ul>
        </div>

        <div class="sub-panel">
          <h3>创建角色</h3>
          <div class="form-grid">
            <input
              v-model="createRoleForm.name"
              data-testid="rbac-create-role-name"
              class="field-input"
              type="text"
              placeholder="角色名称"
            />
            <input
              v-model="createRoleForm.code"
              data-testid="rbac-create-role-code"
              class="field-input"
              type="text"
              placeholder="角色编码"
            />
            <input
              v-model="createRoleForm.description"
              data-testid="rbac-create-role-description"
              class="field-input"
              type="text"
              placeholder="角色描述"
            />
            <button
              data-testid="rbac-create-role-submit"
              class="primary-btn"
              type="button"
              @click="createRole"
            >
              创建角色
            </button>
          </div>
        </div>

        <div class="sub-panel">
          <h3>角色详情</h3>
          <div class="form-grid">
            <input
              v-model="roleDetailForm.name"
              data-testid="rbac-role-detail-name"
              class="field-input"
              type="text"
              placeholder="角色名称"
            />
            <input
              v-model="roleDetailForm.description"
              data-testid="rbac-role-detail-description"
              class="field-input"
              type="text"
              placeholder="角色描述"
            />
            <button
              data-testid="rbac-role-detail-submit"
              class="primary-btn"
              type="button"
              @click="updateActiveRole"
            >
              保存角色
            </button>
            <button
              data-testid="rbac-role-delete-submit"
              class="ghost-btn"
              type="button"
              @click="deleteActiveRole"
            >
              删除角色
            </button>
          </div>
        </div>

        <div class="sub-panel">
          <h3>已绑定角色</h3>
          <ul class="token-list">
            <li v-for="role in userRoles" :key="`assigned-role-${role.roleId || role.id}`">
              <div class="token-item-main">
                <strong>{{ role.name }}</strong>
                <span>{{ role.code }}</span>
              </div>
              <button
                :data-testid="`rbac-remove-user-role-${role.roleId || role.id}`"
                class="ghost-btn inline-action-btn"
                type="button"
                @click="removeRoleFromSelectedUser(Number(role.roleId || role.id))"
              >
                解绑
              </button>
            </li>
          </ul>
        </div>

        <div class="sub-panel">
          <h3>权限</h3>
          <ul class="token-list">
            <li v-for="permission in permissions" :key="permission.permissionId || permission.id">
              <strong>{{ permission.name }}</strong>
              <span>{{ permission.code }}</span>
            </li>
          </ul>
        </div>

        <div class="sub-panel">
          <h3>绑定权限</h3>
          <div class="form-grid">
            <select
              v-model="assignPermissionForm.permissionId"
              data-testid="rbac-assign-permission-permission-id"
              class="field-input"
            >
              <option value="">请选择权限</option>
              <option
                v-for="permission in permissions"
                :key="`assignable-permission-${permission.permissionId || permission.id}`"
                :value="String(permission.permissionId || permission.id || '')"
              >
                {{ permission.name }} · {{ permission.code }}
              </option>
            </select>
            <button
              data-testid="rbac-assign-permission-submit"
              class="primary-btn"
              type="button"
              @click="assignPermissionToActiveRole"
            >
              绑定权限
            </button>
          </div>
        </div>

        <div class="sub-panel">
          <h3>已绑定权限</h3>
          <ul class="token-list">
            <li v-for="permission in rolePermissions" :key="`assigned-permission-${permission.permissionId || permission.id}`">
              <div class="token-item-main">
                <strong>{{ permission.name }}</strong>
                <span>{{ permission.code }}</span>
              </div>
              <button
                :data-testid="`rbac-remove-role-permission-${permission.permissionId || permission.id}`"
                class="ghost-btn inline-action-btn"
                type="button"
                @click="removePermissionFromActiveRole(Number(permission.permissionId || permission.id))"
              >
                解绑
              </button>
            </li>
          </ul>
        </div>
      </section>

      <section class="panel panel-stack">
        <div class="panel-header">
          <h2>菜单可见性</h2>
          <span class="muted">当前选中用户：{{ activeUser?.displayName || '未选择' }}</span>
        </div>

        <div class="sub-panel">
          <h3>系统菜单</h3>
          <ul class="menu-list">
            <li v-for="menu in menus" :key="menu.menuId || menu.id">
              <strong>{{ menu.title }}</strong>
              <span>{{ menu.path }}</span>
            </li>
          </ul>
        </div>

        <div class="sub-panel">
          <h3>用户可见菜单</h3>
          <ul class="menu-list">
            <li v-for="menu in profileMenus" :key="menu.menuId || menu.id">
              <strong>{{ menu.title }}</strong>
              <span>{{ menu.path }}</span>
            </li>
          </ul>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { rbacApi } from '@/api/modules/rbac.api'
import { getSession } from '@/stores/session'

type RbacUser = {
  userId: number
  email: string
  displayName: string
  status: number
  authMethod?: string
}

type RbacRole = {
  id?: number
  roleId?: number
  code: string
  name: string
  description?: string
  isSystem?: boolean
}

type RbacPermission = {
  id?: number
  permissionId?: number
  code: string
  name: string
  module?: string
  description?: string
}

type RbacMenu = {
  id?: number
  menuId?: number
  parentId?: number | null
  title: string
  path: string
  sortOrder?: number
  permissionCode?: string
  visible?: boolean
}

const router = useRouter()
const session = getSession()

const users = ref<RbacUser[]>([])
const roles = ref<RbacRole[]>([])
const permissions = ref<RbacPermission[]>([])
const menus = ref<RbacMenu[]>([])
const profileMenus = ref<RbacMenu[]>([])
const userRoles = ref<RbacRole[]>([])
const rolePermissions = ref<RbacPermission[]>([])
const activeUserId = ref<number | null>(session.userId ?? null)
const activeRoleId = ref<number | null>(null)
const errorMessage = ref('')
const createUserForm = ref({
  email: '',
  displayName: '',
  authMethod: 'local',
})
const userDetailForm = ref({
  displayName: '',
  status: 1,
})
const assignRoleForm = ref({
  roleId: '',
})
const assignPermissionForm = ref({
  permissionId: '',
})
const createRoleForm = ref({
  name: '',
  code: '',
  description: '',
})
const roleDetailForm = ref({
  name: '',
  description: '',
})

const activeUser = computed(() => users.value.find((item) => item.userId === activeUserId.value) ?? null)
const activeRole = computed(
  () => roles.value.find((item) => (item.roleId || item.id) === activeRoleId.value)
    ?? userRoles.value.find((item) => (item.roleId || item.id) === activeRoleId.value)
    ?? null
)

watch(
  activeUser,
  (user) => {
    userDetailForm.value = {
      displayName: user?.displayName || '',
      status: user?.status ?? 1,
    }
  },
  { immediate: true }
)

watch(
  activeRole,
  (role) => {
    roleDetailForm.value = {
      name: role?.name || '',
      description: role?.description || '',
    }
  },
  { immediate: true }
)

const loadProfileMenus = async (userId: number) => {
  profileMenus.value = ((await rbacApi.listProfileMenus(userId)) || []) as RbacMenu[]
}

const loadRolePermissions = async (roleId: number) => {
  rolePermissions.value = ((await rbacApi.listRolePermissions(roleId)) || []) as RbacPermission[]
}

const loadUserRoles = async (userId: number) => {
  userRoles.value = ((await rbacApi.listUserRoles(userId)) || []) as RbacRole[]
  const existingRoleIds = new Set(userRoles.value.map((item) => item.roleId ?? item.id).filter((item): item is number => item != null))
  const defaultRoleId = existingRoleIds.has(activeRoleId.value ?? -1)
    ? activeRoleId.value
    : (userRoles.value[0]?.roleId ?? userRoles.value[0]?.id ?? null)

  activeRoleId.value = defaultRoleId

  if (defaultRoleId != null) {
    await loadRolePermissions(defaultRoleId)
  } else {
    rolePermissions.value = []
  }
}

const selectUser = async (userId: number) => {
  const previousUserId = activeUserId.value
  const previousProfileMenus = [...profileMenus.value]
  const previousUserRoles = [...userRoles.value]
  const previousRolePermissions = [...rolePermissions.value]
  const previousRoleId = activeRoleId.value
  errorMessage.value = ''
  try {
    activeUserId.value = userId
    await loadProfileMenus(userId)
    await loadUserRoles(userId)
  } catch (error) {
    activeUserId.value = previousUserId
    profileMenus.value = previousProfileMenus
    userRoles.value = previousUserRoles
    rolePermissions.value = previousRolePermissions
    activeRoleId.value = previousRoleId
    errorMessage.value = error instanceof Error ? error.message : 'profile menus failed'
  }
}

const selectRole = async (roleId: number) => {
  const previousRoleId = activeRoleId.value
  errorMessage.value = ''
  try {
    activeRoleId.value = roleId
    await loadRolePermissions(roleId)
  } catch (error) {
    activeRoleId.value = previousRoleId
    errorMessage.value = error instanceof Error ? error.message : 'role permissions failed'
  }
}

const createUser = async () => {
  errorMessage.value = ''
  try {
    await rbacApi.createUser({
      email: createUserForm.value.email,
      displayName: createUserForm.value.displayName,
      status: 1,
      authMethod: createUserForm.value.authMethod || 'local',
    })
    createUserForm.value = {
      email: '',
      displayName: '',
      authMethod: 'local',
    }
    await loadPage()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'create user failed'
  }
}

const updateSelectedUser = async () => {
  if (activeUserId.value == null) {
    return
  }

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
  if (activeUserId.value == null) {
    return
  }

  errorMessage.value = ''
  try {
    await rbacApi.deleteUser(activeUserId.value)
    await loadPage()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'delete user failed'
  }
}

const assignRoleToSelectedUser = async () => {
  if (activeUserId.value == null || !assignRoleForm.value.roleId) {
    return
  }

  errorMessage.value = ''
  try {
    await rbacApi.assignUserRole(activeUserId.value, Number(assignRoleForm.value.roleId))
    assignRoleForm.value.roleId = ''
    await loadUserRoles(activeUserId.value)
    await loadProfileMenus(activeUserId.value)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'assign role failed'
  }
}

const removeRoleFromSelectedUser = async (roleId: number) => {
  if (activeUserId.value == null) {
    return
  }

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
  if (activeRoleId.value == null || !assignPermissionForm.value.permissionId) {
    return
  }

  errorMessage.value = ''
  try {
    await rbacApi.assignRolePermission(activeRoleId.value, Number(assignPermissionForm.value.permissionId))
    assignPermissionForm.value.permissionId = ''
    await loadRolePermissions(activeRoleId.value)
    if (activeUserId.value != null) {
      await loadProfileMenus(activeUserId.value)
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'assign permission failed'
  }
}

const createRole = async () => {
  errorMessage.value = ''
  try {
    await rbacApi.createRole({
      name: createRoleForm.value.name,
      code: createRoleForm.value.code,
      description: createRoleForm.value.description,
      isSystem: false,
    })
    createRoleForm.value = {
      name: '',
      code: '',
      description: '',
    }
    roles.value = ((await rbacApi.listRoles()) || []) as RbacRole[]
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'create role failed'
  }
}

const updateActiveRole = async () => {
  if (activeRoleId.value == null) {
    return
  }

  errorMessage.value = ''
  try {
    await rbacApi.updateRole(activeRoleId.value, {
      name: roleDetailForm.value.name,
      description: roleDetailForm.value.description,
    })
    roles.value = ((await rbacApi.listRoles()) || []) as RbacRole[]
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'update role failed'
  }
}

const deleteActiveRole = async () => {
  if (activeRoleId.value == null) {
    return
  }

  errorMessage.value = ''
  try {
    await rbacApi.deleteRole(activeRoleId.value)
    roles.value = ((await rbacApi.listRoles()) || []) as RbacRole[]
    if (activeUserId.value != null) {
      await loadUserRoles(activeUserId.value)
      await loadProfileMenus(activeUserId.value)
    }
    const nextRoleId = roles.value[0]?.roleId ?? roles.value[0]?.id ?? null
    if (activeRoleId.value == null) {
      activeRoleId.value = nextRoleId
    }
    if (activeRoleId.value != null) {
      await loadRolePermissions(activeRoleId.value)
    } else if (nextRoleId != null) {
      activeRoleId.value = nextRoleId
      await loadRolePermissions(nextRoleId)
    } else {
      rolePermissions.value = []
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'delete role failed'
  }
}

const removePermissionFromActiveRole = async (permissionId: number) => {
  if (activeRoleId.value == null) {
    return
  }

  errorMessage.value = ''
  try {
    await rbacApi.removeRolePermission(activeRoleId.value, permissionId)
    await loadRolePermissions(activeRoleId.value)
    if (activeUserId.value != null) {
      await loadProfileMenus(activeUserId.value)
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'remove permission failed'
  }
}

const loadPage = async () => {
  errorMessage.value = ''
  try {
    const [userList, roleList, permissionList, menuList] = await Promise.all([
      rbacApi.listUsers(),
      rbacApi.listRoles(),
      rbacApi.listPermissions(),
      rbacApi.listMenus(),
    ])

    users.value = (userList || []) as RbacUser[]
    roles.value = (roleList || []) as RbacRole[]
    permissions.value = (permissionList || []) as RbacPermission[]
    menus.value = (menuList || []) as RbacMenu[]

    const existingUserIds = new Set(users.value.map((item) => item.userId))
    const defaultUserId = existingUserIds.has(activeUserId.value ?? -1)
      ? activeUserId.value
      : users.value[0]?.userId ?? null

    activeUserId.value = defaultUserId

    if (defaultUserId != null) {
      await loadProfileMenus(defaultUserId)
      await loadUserRoles(defaultUserId)
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

onMounted(() => {
  void loadPage()
})
</script>

<style scoped lang="less">
.admin-rbac-page {
  min-height: 100vh;
  padding: 24px;
  background: #0b1120;
  color: #e5e7eb;
}

.rbac-header,
.summary-grid,
.rbac-layout {
  max-width: 1320px;
  margin: 0 auto;
}

.rbac-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 20px;
}

.eyebrow {
  margin: 0 0 8px;
  color: #f2d58b;
  text-transform: uppercase;
  letter-spacing: 0.16em;
  font-size: 12px;
}

h1,
h2,
h3,
.subtitle,
.muted,
.summary-label,
.status-badge,
.user-card,
.token-list,
.menu-list,
.ghost-btn {
  margin: 0;
}

.subtitle,
.muted,
.summary-label,
.user-card span,
.user-card small,
.token-list span,
.menu-list span {
  color: #94a3b8;
}

.ghost-btn {
  border: 1px solid #334155;
  background: transparent;
  color: #e5e7eb;
  border-radius: 999px;
  padding: 10px 16px;
  cursor: pointer;
}

.primary-btn {
  border: 0;
  background: #f2d58b;
  color: #111827;
  border-radius: 12px;
  padding: 10px 14px;
  cursor: pointer;
  font-weight: 600;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 20px;
}

.error-banner {
  max-width: 1320px;
  margin: 0 auto 20px;
  padding: 14px 16px;
  border-radius: 14px;
  border: 1px solid rgba(248, 113, 113, 0.35);
  background: rgba(127, 29, 29, 0.22);
  color: #fecaca;
  display: grid;
  gap: 6px;
}

.summary-card,
.panel,
.sub-panel {
  background: rgba(15, 23, 42, 0.88);
  border: 1px solid #1e293b;
  border-radius: 16px;
}

.summary-card {
  padding: 16px;
  display: grid;
  gap: 6px;
}

.summary-card strong {
  font-size: 24px;
}

.rbac-layout {
  display: grid;
  grid-template-columns: 1.2fr 1fr 1fr;
  gap: 16px;
}

.panel {
  padding: 18px;
}

.panel-stack {
  display: grid;
  gap: 16px;
  align-content: start;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: baseline;
  margin-bottom: 14px;
}

.user-list,
.token-list,
.menu-list {
  display: grid;
  gap: 10px;
}

.token-list li {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.token-item-main {
  display: grid;
  gap: 4px;
}

.role-select-btn {
  width: 100%;
  border: 1px solid #334155;
  background: #111827;
  color: #e5e7eb;
  border-radius: 12px;
  padding: 10px 12px;
  text-align: left;
  display: grid;
  gap: 4px;
  cursor: pointer;
}

.role-select-btn.active {
  border-color: #f2d58b;
}

.inline-action-btn {
  padding: 6px 10px;
  border-radius: 10px;
}

.form-grid {
  display: grid;
  gap: 10px;
}

.field-input {
  width: 100%;
  border: 1px solid #334155;
  background: #111827;
  color: #e5e7eb;
  border-radius: 12px;
  padding: 10px 12px;
  box-sizing: border-box;
}

.user-card {
  width: 100%;
  text-align: left;
  padding: 14px;
  border-radius: 14px;
  border: 1px solid #334155;
  background: #111827;
  cursor: pointer;
  display: grid;
  gap: 6px;
}

.user-card.active {
  border-color: #f2d58b;
  box-shadow: 0 0 0 1px rgba(242, 213, 139, 0.24) inset;
}

.user-card-top {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
}

.status-badge {
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.12);
}

.status-badge.enabled {
  color: #86efac;
  background: rgba(34, 197, 94, 0.12);
}

.sub-panel {
  padding: 14px;
}

.sub-panel h3 {
  margin-bottom: 10px;
}

.token-list,
.menu-list {
  list-style: none;
  padding: 0;
}

.token-list li,
.menu-list li {
  padding: 10px 12px;
  border-radius: 12px;
  background: #111827;
  border: 1px solid #1f2937;
  display: grid;
  gap: 4px;
}

@media (max-width: 1100px) {
  .summary-grid,
  .rbac-layout {
    grid-template-columns: 1fr;
  }
}
</style>
