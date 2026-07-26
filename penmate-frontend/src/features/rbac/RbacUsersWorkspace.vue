<template>
  <div class="user-workspace" data-testid="rbac-user-workspace">
    <header class="workspace-toolbar">
      <label class="search-field">
        <SearchOutlined />
        <input
          v-model="userSearchQuery"
          data-testid="rbac-user-search-input"
          type="search"
          aria-label="搜索用户"
          placeholder="搜索姓名或邮箱"
        />
      </label>
      <select
        v-model="userStatusFilter"
        data-testid="rbac-user-status-filter"
        class="filter-select"
        aria-label="用户状态筛选"
      >
        <option value="all">全部状态</option>
        <option value="1">已启用</option>
        <option value="0">已停用</option>
      </select>
      <span class="result-count">{{ users.length }} 个用户</span>
      <button
        data-testid="rbac-toggle-create-user"
        class="primary-btn create-user-trigger"
        type="button"
        @click="createUserExpanded = !createUserExpanded"
      >
        <CloseOutlined v-if="createUserExpanded" />
        <UserAddOutlined v-else />
        {{ createUserExpanded ? '取消创建' : '新增用户' }}
      </button>
    </header>

    <div class="user-directory-grid">
      <section class="directory-panel" aria-label="用户目录">
        <div class="directory-heading">
          <div>
            <h2>用户目录</h2>
            <span>选择用户后在右侧维护账号和访问权限</span>
          </div>
          <span>{{ userPaginationText }}</span>
        </div>

        <div class="user-table" role="table" aria-label="用户列表">
          <div class="user-table-header" role="row">
            <span role="columnheader">用户</span>
            <span role="columnheader">状态</span>
            <span role="columnheader">账号类型</span>
            <span role="columnheader" aria-label="打开详情"></span>
          </div>
          <button
            v-for="user in paginatedUsers"
            :key="user.userId"
            :data-testid="`rbac-user-select-${user.userId}`"
            class="user-row"
            :class="{ active: toBusinessId(user.userId) === activeUserId }"
            type="button"
            role="row"
            @click="selectUser(String(user.userId))"
          >
            <span class="user-identity" role="cell">
              <i>{{ initials(user.displayName) }}</i>
              <span><strong>{{ user.displayName }}</strong><small>{{ user.email }}</small></span>
            </span>
            <span role="cell">
              <span class="status-badge" :class="userStatusClass(user)">
                {{ userStatusLabel(user) }}
              </span>
            </span>
            <span class="auth-method" role="cell">本地账号</span>
            <RightOutlined role="cell" />
          </button>
          <div v-if="!paginatedUsers.length" class="empty-state">没有匹配的用户</div>
        </div>

        <footer class="pagination-bar">
          <span>{{ userPaginationText }}</span>
          <div>
            <button type="button" :disabled="userPage <= 1" aria-label="上一页" @click="previousUserPage">
              <LeftOutlined />
            </button>
            <button
              data-testid="rbac-user-page-next"
              type="button"
              :disabled="userPage >= userTotalPages"
              aria-label="下一页"
              @click="nextUserPage"
            >
              <RightOutlined />
            </button>
          </div>
        </footer>
      </section>

      <section v-if="createUserExpanded" class="detail-panel create-user-panel" aria-label="新增用户">
        <header class="detail-header">
          <div class="detail-avatar"><UserAddOutlined /></div>
          <div><p>新增身份</p><h2>创建用户</h2><span>创建后可继续分配一个或多个角色</span></div>
        </header>
        <div class="detail-section">
          <div class="section-title"><div><h3>账号信息</h3><p>邮箱用于登录，创建后不可在此修改。</p></div></div>
          <div class="form-grid">
            <label>
              <span>邮箱</span>
              <input
                v-model="createUserForm.email"
                data-testid="rbac-create-user-email"
                class="field-input"
                type="email"
                placeholder="name@example.com"
              />
            </label>
            <label>
              <span>展示名</span>
              <input
                v-model="createUserForm.displayName"
                data-testid="rbac-create-user-display-name"
                class="field-input"
                type="text"
                placeholder="用户在系统中的名称"
              />
            </label>
            <label>
              <span>初始密码</span>
              <input
                v-model="createUserForm.initialPassword"
                data-testid="rbac-create-user-password"
                class="field-input"
                type="password"
                autocomplete="new-password"
                placeholder="至少 8 个字符"
              />
            </label>
            <label>
              <span>确认密码</span>
              <input
                v-model="createUserForm.confirmPassword"
                data-testid="rbac-create-user-password-confirm"
                class="field-input"
                type="password"
                autocomplete="new-password"
                placeholder="再次输入初始密码"
              />
            </label>
          </div>
        </div>
        <footer class="detail-actions">
          <button type="button" class="ghost-btn" @click="createUserExpanded = false">取消</button>
          <button data-testid="rbac-create-user-submit" class="primary-btn" type="button" @click="createUser">
            <UserAddOutlined />创建用户
          </button>
        </footer>
      </section>

      <section v-else-if="activeUser" class="detail-panel" aria-label="用户详情">
        <header class="detail-header">
          <div class="detail-avatar">{{ initials(activeUser.displayName) }}</div>
          <div>
            <p>用户详情</p>
            <h2 data-testid="rbac-active-user-name">{{ activeUser.displayName }}</h2>
            <span>{{ activeUser.email }} · ID {{ activeUser.userId }}</span>
          </div>
          <span class="status-badge detail-status" :class="userStatusClass(activeUser)">
            {{ userStatusLabel(activeUser) }}
          </span>
        </header>

        <div v-if="activeUser.deletionRequestedAt" class="pending-deletion-notice">
          <ExclamationCircleOutlined />
          <div><strong>账户处于待删除期</strong><span>预计于 {{ formatDate(activeUser.deletionDueAt) }} 永久删除</span></div>
          <button type="button" data-testid="rbac-user-restore-deletion" @click="restoreSelectedUser">恢复账户</button>
        </div>

        <div class="detail-section account-section">
          <div class="section-title">
            <div><h3>账号状态</h3><p>停用后现有登录态会失效，用户无法重新登录。</p></div>
            <span v-if="activeUser.lastLoginAt">最近登录 {{ formatDate(activeUser.lastLoginAt) }}</span>
          </div>
          <div class="account-fields">
            <label>
              <span>展示名</span>
              <input
                v-model="userDetailForm.displayName"
                data-testid="rbac-user-detail-display-name"
                class="field-input"
                type="text"
              />
            </label>
            <label>
              <span>状态</span>
              <select v-model="userDetailForm.status" data-testid="rbac-user-detail-status" class="field-input">
                <option :value="1">启用</option>
                <option :value="0">停用</option>
              </select>
            </label>
            <button data-testid="rbac-user-detail-submit" class="secondary-btn save-account" type="button" @click="updateSelectedUser">
              <SaveOutlined />保存账号
            </button>
          </div>
        </div>

        <div class="detail-section role-assignment-section">
          <div class="section-title assignment-title">
            <div>
              <h3>角色分配</h3>
              <p>角色决定用户可执行的操作；变更将在统一保存后生效。</p>
            </div>
            <span :class="userRolesDirty ? 'unsaved-state' : 'saved-state'">
              {{ userRolesDirty ? '有未保存变更' : `已保存 · r${userRolesRevision}` }}
            </span>
          </div>
          <div v-if="!userRoles.length" class="no-role-notice">
            <ExclamationCircleOutlined />
            <span><strong>当前没有角色</strong><small>该用户只能登录、维护个人账号与会话，不能进入业务工作区。</small></span>
          </div>
          <div class="role-picker">
            <select v-model="assignRoleForm.roleId" data-testid="rbac-assign-role-role-id" class="field-input" aria-label="待绑定角色">
              <option value="">选择要添加的角色</option>
              <option
                v-for="role in roles"
                :key="`assignable-role-${getRoleBusinessId(role)}`"
                :value="String(getRoleBusinessId(role) ?? '')"
                :disabled="userRoles.some((assigned) => getRoleBusinessId(assigned) === getRoleBusinessId(role))"
              >
                {{ role.name }} · {{ role.code }}
              </option>
            </select>
            <button data-testid="rbac-assign-role-submit" class="secondary-btn" type="button" @click="assignRoleToSelectedUser">
              <PlusOutlined />添加
            </button>
          </div>
          <h4>已绑定角色</h4>
          <ul class="assignment-list">
            <li v-for="role in userRoles" :key="`assigned-role-${getRoleBusinessId(role)}`">
              <span><strong>{{ role.name }}</strong><small>{{ role.code }}</small></span>
              <span v-if="role.isSystem" class="system-label">系统角色</span>
              <button
                :data-testid="`rbac-remove-user-role-${getRoleBusinessId(role)}`"
                type="button"
                :aria-label="`移除角色 ${role.name}`"
                :title="`移除角色 ${role.name}`"
                @click="removeRoleFromSelectedUser(getRoleBusinessId(role) ?? '')"
              >
                <CloseOutlined />
              </button>
            </li>
            <li v-if="!userRoles.length" class="empty-assignment">尚未分配角色</li>
          </ul>
          <div class="assignment-actions">
            <button
              data-testid="rbac-user-roles-discard"
              class="ghost-btn"
              type="button"
              :disabled="!userRolesDirty"
              @click="discardUserRoleChanges"
            >
              <UndoOutlined />撤销
            </button>
            <button
              data-testid="rbac-user-roles-save"
              class="primary-btn"
              type="button"
              :disabled="!userRolesDirty"
              @click="saveUserRoles"
            >
              <SaveOutlined />保存角色
            </button>
          </div>
        </div>

        <div class="detail-section access-result">
          <div class="section-title"><div><h3>有效访问结果</h3><p>由当前已保存角色计算出的可见菜单。</p></div><span>{{ profileMenus.length }} 项</span></div>
          <ul class="menu-list">
            <li v-for="menu in profileMenus" :key="`user-menu-${getMenuBusinessId(menu)}`">
              <CheckCircleOutlined />
              <span><strong>{{ menu.title }}</strong><small>{{ menu.path }}</small></span>
            </li>
            <li v-if="!profileMenus.length" class="empty-assignment">没有可见菜单</li>
          </ul>
        </div>

        <div
          v-if="pendingDeleteUserId === activeUserId"
          class="danger-confirmation"
          data-testid="rbac-user-delete-confirmation"
        >
          <div><strong>永久删除 {{ activeUser.displayName }}？</strong><span>账户和关联授权将不可恢复。</span></div>
          <button data-testid="rbac-user-delete-cancel" class="ghost-btn" type="button" @click="cancelDeleteSelectedUser">取消</button>
          <button data-testid="rbac-user-delete-confirm" class="danger-btn" type="button" @click="deleteSelectedUser">确认删除</button>
        </div>
        <footer v-else-if="!activeUser.deletionRequestedAt" class="danger-footer">
          <span>删除用户</span>
          <button data-testid="rbac-user-delete-trigger" class="danger-link" type="button" @click="requestDeleteSelectedUser">
            <DeleteOutlined />删除账户
          </button>
        </footer>
      </section>

      <section v-else class="detail-panel empty-detail">
        <UserOutlined />
        <strong>选择一个用户</strong>
        <span>在此查看账号状态、角色和最终访问范围。</span>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  CheckCircleOutlined,
  CloseOutlined,
  DeleteOutlined,
  ExclamationCircleOutlined,
  LeftOutlined,
  PlusOutlined,
  RightOutlined,
  SaveOutlined,
  SearchOutlined,
  UndoOutlined,
  UserAddOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import type { RbacUser } from '@/features/rbac/rbacModel'
import type { RbacConsoleController } from '@/features/rbac/useRbacConsole'

const { controller } = defineProps<{ controller: RbacConsoleController }>()
const {
  users,
  roles,
  profileMenus,
  userRoles,
  userRolesRevision,
  userRolesDirty,
  activeUserId,
  createUserForm,
  userDetailForm,
  assignRoleForm,
  activeUser,
  createUserExpanded,
  pendingDeleteUserId,
  userSearchQuery,
  userStatusFilter,
  userPage,
  paginatedUsers,
  userTotalPages,
  userPaginationText,
  toBusinessId,
  getRoleBusinessId,
  getMenuBusinessId,
  selectUser,
  createUser,
  requestDeleteSelectedUser,
  cancelDeleteSelectedUser,
  updateSelectedUser,
  deleteSelectedUser,
  restoreSelectedUser,
  assignRoleToSelectedUser,
  removeRoleFromSelectedUser,
  saveUserRoles,
  discardUserRoleChanges,
  previousUserPage,
  nextUserPage,
} = controller

const initials = (name: string) => name.trim().slice(0, 2).toUpperCase() || '?'
const userStatusLabel = (user: RbacUser) => user.deletionRequestedAt ? '待删除' : user.status === 1 ? '已启用' : '已停用'
const userStatusClass = (user: RbacUser) => ({ enabled: user.status === 1 && !user.deletionRequestedAt, pending: Boolean(user.deletionRequestedAt) })
const formatDate = (value?: string) => value ? new Date(value).toLocaleString() : '未记录'
</script>

<style scoped>
.user-workspace { display: grid; gap: 12px; }
.workspace-toolbar {
  display: grid;
  grid-template-columns: minmax(260px, 440px) 132px auto 1fr;
  align-items: center;
  gap: 8px;
  min-height: 54px;
  padding: 9px 10px;
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: 6px;
}
.search-field { display: flex; min-height: 34px; align-items: center; gap: 8px; padding: 0 10px; background: var(--bg-subtle); border: 1px solid var(--border-subtle); border-radius: 5px; }
.search-field svg { color: var(--text-muted); }
.search-field input { width: 100%; min-width: 0; color: var(--text-primary); background: transparent; border: 0; outline: 0; }
.filter-select,
.field-input { min-height: 34px; color: var(--text-primary); background: var(--bg-surface); border: 1px solid var(--border-strong); border-radius: 5px; }
.filter-select { padding: 0 8px; }
.result-count { color: var(--text-muted); font-size: 12px; }
.create-user-trigger { justify-self: end; }
.primary-btn,
.secondary-btn,
.ghost-btn,
.danger-btn,
.danger-link {
  display: inline-flex;
  min-height: 34px;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0 11px;
  border-radius: 5px;
  cursor: pointer;
}
.primary-btn { color: var(--text-inverse); background: var(--accent); border: 1px solid var(--accent); }
.secondary-btn { color: var(--accent); background: var(--accent-soft); border: 1px solid var(--accent-border); }
.ghost-btn { color: var(--text-secondary); background: transparent; border: 1px solid var(--border-strong); }
.danger-btn { color: white; background: var(--danger); border: 1px solid var(--danger); }
button:disabled { cursor: not-allowed; opacity: 0.45; }
.user-directory-grid { display: grid; grid-template-columns: minmax(480px, 0.92fr) minmax(520px, 1.08fr); align-items: start; gap: 12px; }
.directory-panel,
.detail-panel { min-width: 0; background: var(--bg-surface); border: 1px solid var(--border-subtle); border-radius: 6px; }
.directory-heading { display: flex; min-height: 64px; align-items: center; justify-content: space-between; gap: 16px; padding: 10px 14px; border-bottom: 1px solid var(--border-subtle); }
.directory-heading > div { display: grid; gap: 3px; }
h2, h3, h4, p { margin: 0; letter-spacing: 0; }
.directory-heading h2,
.detail-header h2 { font-size: 15px; }
.directory-heading span,
.detail-header p,
.detail-header span,
.section-title p { color: var(--text-muted); font-size: 11px; }
.user-table { min-height: 486px; }
.user-table-header,
.user-row { display: grid; grid-template-columns: minmax(240px, 1fr) 86px 72px 20px; align-items: center; gap: 10px; padding: 0 14px; }
.user-table-header { min-height: 36px; color: var(--text-muted); background: var(--bg-subtle); border-bottom: 1px solid var(--border-subtle); font-size: 11px; }
.user-row { width: 100%; min-height: 62px; color: var(--text-primary); background: transparent; border: 0; border-bottom: 1px solid var(--border-subtle); cursor: pointer; text-align: left; }
.user-row:hover { background: var(--bg-subtle); }
.user-row.active { background: var(--accent-soft); box-shadow: inset 3px 0 var(--accent); }
.user-row > svg { color: var(--text-muted); }
.user-identity { display: grid; grid-template-columns: 32px minmax(0, 1fr); align-items: center; gap: 9px; }
.user-identity i,
.detail-avatar { display: grid; place-items: center; color: var(--accent); background: var(--accent-soft); border: 1px solid var(--accent-border); font-style: normal; font-weight: 700; }
.user-identity i { width: 32px; height: 32px; border-radius: 5px; font-size: 11px; }
.user-identity > span { display: grid; min-width: 0; gap: 2px; }
.user-identity strong,
.user-identity small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.user-identity strong { font-size: 12px; }
.user-identity small,
.auth-method { color: var(--text-muted); font-size: 10px; }
.status-badge { display: inline-flex; width: fit-content; align-items: center; padding: 3px 7px; color: var(--text-muted); background: var(--bg-muted); border: 1px solid var(--border-subtle); border-radius: 999px; font-size: 10px; }
.status-badge.enabled { color: var(--success); background: var(--success-soft); border-color: var(--success-border); }
.status-badge.pending { color: var(--warning); background: var(--warning-soft); border-color: var(--warning-border); }
.empty-state,
.empty-detail { display: grid; min-height: 220px; place-items: center; align-content: center; gap: 7px; color: var(--text-muted); font-size: 12px; }
.empty-detail > svg { font-size: 26px; }
.empty-detail strong { color: var(--text-secondary); }
.pagination-bar { display: flex; min-height: 52px; align-items: center; justify-content: space-between; padding: 8px 14px; color: var(--text-muted); font-size: 11px; }
.pagination-bar > div { display: flex; gap: 5px; }
.pagination-bar button { display: grid; width: 30px; height: 30px; place-items: center; color: var(--text-secondary); background: var(--bg-surface); border: 1px solid var(--border-strong); border-radius: 4px; cursor: pointer; }
.detail-header { display: grid; grid-template-columns: 44px minmax(0, 1fr) auto; min-height: 76px; align-items: center; gap: 11px; padding: 12px 16px; border-bottom: 1px solid var(--border-subtle); }
.detail-header > div:nth-child(2) { display: grid; gap: 2px; min-width: 0; }
.detail-avatar { width: 44px; height: 44px; border-radius: 6px; }
.detail-status { align-self: start; margin-top: 5px; }
.detail-section { padding: 16px; }
.detail-section + .detail-section { border-top: 1px solid var(--border-subtle); }
.section-title { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.section-title > div { display: grid; gap: 3px; }
.section-title h3 { font-size: 13px; }
.section-title > span { color: var(--text-muted); font-size: 11px; }
.form-grid { display: grid; gap: 11px; }
.form-grid label,
.account-fields label { display: grid; gap: 5px; color: var(--text-secondary); font-size: 11px; }
.field-input { width: 100%; padding: 0 9px; box-sizing: border-box; }
.account-fields { display: grid; grid-template-columns: minmax(0, 1fr) 120px auto; align-items: end; gap: 9px; }
.pending-deletion-notice { display: grid; grid-template-columns: 20px minmax(0, 1fr) auto; align-items: center; gap: 10px; margin: 12px 16px 0; padding: 10px 12px; color: var(--warning); background: var(--warning-soft); border: 1px solid var(--warning-border); border-radius: 5px; }
.pending-deletion-notice > div { display: grid; gap: 2px; }
.pending-deletion-notice span { color: var(--text-muted); font-size: 11px; }
.pending-deletion-notice button { min-height: 30px; color: var(--warning); background: var(--bg-surface); border: 1px solid var(--warning-border); border-radius: 4px; cursor: pointer; }
.no-role-notice { display: flex; align-items: flex-start; gap: 8px; margin: 0 0 12px; padding: 10px; color: var(--warning); background: var(--warning-soft); border: 1px solid var(--warning-border); border-radius: 5px; }
.no-role-notice > span { display: grid; gap: 2px; }
.no-role-notice small { color: var(--text-secondary); font-size: 10px; }
.unsaved-state { color: var(--warning) !important; }
.saved-state { color: var(--text-muted); }
.role-picker { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px; margin-bottom: 12px; }
.role-assignment-section h4 { margin-bottom: 7px; color: var(--text-secondary); font-size: 11px; font-weight: 600; }
.assignment-list,
.menu-list { display: grid; gap: 0; margin: 0; padding: 0; list-style: none; border: 1px solid var(--border-subtle); border-radius: 5px; overflow: hidden; }
.assignment-list li { display: grid; grid-template-columns: minmax(0, 1fr) auto 30px; min-height: 46px; align-items: center; gap: 8px; padding: 6px 9px; border-bottom: 1px solid var(--border-subtle); }
.assignment-list li:last-child,
.menu-list li:last-child { border-bottom: 0; }
.assignment-list li > span:first-child,
.menu-list li > span { display: grid; min-width: 0; gap: 2px; }
.assignment-list strong,
.menu-list strong { font-size: 11px; }
.assignment-list small,
.menu-list small { color: var(--text-muted); font-size: 10px; }
.assignment-list button { display: grid; width: 28px; height: 28px; place-items: center; color: var(--text-muted); background: transparent; border: 0; cursor: pointer; }
.system-label { color: var(--text-muted); font-size: 10px; }
.empty-assignment { display: block !important; padding: 14px !important; color: var(--text-muted); text-align: center; font-size: 11px; }
.assignment-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 10px; }
.menu-list li { display: grid; grid-template-columns: 18px minmax(0, 1fr); min-height: 42px; align-items: center; gap: 7px; padding: 6px 9px; border-bottom: 1px solid var(--border-subtle); }
.menu-list svg { color: var(--success); }
.danger-footer,
.detail-actions { display: flex; min-height: 54px; align-items: center; justify-content: space-between; gap: 10px; padding: 10px 16px; border-top: 1px solid var(--border-subtle); }
.danger-footer > span { color: var(--text-muted); font-size: 11px; }
.danger-link { min-height: 30px; color: var(--danger); background: transparent; border: 0; }
.danger-confirmation { display: grid; grid-template-columns: minmax(0, 1fr) auto auto; align-items: center; gap: 8px; padding: 12px 16px; background: var(--danger-soft); border-top: 1px solid var(--danger-border); }
.danger-confirmation > div { display: grid; gap: 2px; }
.danger-confirmation span { color: var(--text-muted); font-size: 11px; }

@media (max-width: 1180px) {
  .user-directory-grid { grid-template-columns: minmax(400px, 0.8fr) minmax(480px, 1.2fr); }
  .user-table-header,
  .user-row { grid-template-columns: minmax(210px, 1fr) 76px 20px; }
  .user-table-header > :nth-child(3),
  .user-row > :nth-child(3) { display: none; }
}
</style>
