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
              <strong>{{ role.name }}</strong>
              <span>{{ role.code }}</span>
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
import { computed, onMounted, ref } from 'vue'
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
const activeUserId = ref<number | null>(session.userId ?? null)
const errorMessage = ref('')

const activeUser = computed(() => users.value.find((item) => item.userId === activeUserId.value) ?? null)

const loadProfileMenus = async (userId: number) => {
  profileMenus.value = ((await rbacApi.listProfileMenus(userId)) || []) as RbacMenu[]
}

const selectUser = async (userId: number) => {
  const previousUserId = activeUserId.value
  errorMessage.value = ''
  try {
    activeUserId.value = userId
    await loadProfileMenus(userId)
  } catch (error) {
    activeUserId.value = previousUserId
    errorMessage.value = error instanceof Error ? error.message : 'profile menus failed'
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
    } else {
      profileMenus.value = []
    }
  } catch (error) {
    users.value = []
    roles.value = []
    permissions.value = []
    menus.value = []
    profileMenus.value = []
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
