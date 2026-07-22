<template>
  <section class="panel workspace-sidebar" data-testid="rbac-user-workspace">
    <div class="panel-header">
      <div>
        <h2>用户管理</h2>
        <span class="muted">先筛选用户，再处理详情、绑定与删除</span>
      </div>
    </div>

    <div class="sub-panel toolbar-panel">
      <div class="form-grid compact-grid">
        <input
          aria-label="搜索用户"
          v-model="userSearchQuery"
          data-testid="rbac-user-search-input"
          class="field-input"
          type="text"
          placeholder="搜索展示名 / 邮箱 / 认证方式"
        />
        <select
          aria-label="用户状态筛选"
          v-model="userStatusFilter"
          data-testid="rbac-user-status-filter"
          class="field-input"
        >
          <option value="all">全部状态</option>
          <option value="1">仅启用</option>
          <option value="0">仅停用</option>
        </select>
      </div>
    </div>

    <div class="sub-panel create-user-panel">
      <div class="sub-panel-header">
        <div>
          <h3>新增用户</h3>
          <span class="muted">默认收起，避免表单占满首屏</span>
        </div>
        <button
          data-testid="rbac-toggle-create-user"
          class="ghost-btn inline-action-btn"
          type="button"
          @click="createUserExpanded = !createUserExpanded"
        >
          {{ createUserExpanded ? '收起表单' : '展开创建' }}
        </button>
      </div>

      <div v-if="createUserExpanded" class="form-grid">
        <input
          aria-label="新用户邮箱"
          v-model="createUserForm.email"
          data-testid="rbac-create-user-email"
          class="field-input"
          type="email"
          placeholder="邮箱"
        />
        <input
          aria-label="新用户展示名"
          v-model="createUserForm.displayName"
          data-testid="rbac-create-user-display-name"
          class="field-input"
          type="text"
          placeholder="展示名"
        />
        <input
          aria-label="新用户认证方式"
          v-model="createUserForm.authMethod"
          data-testid="rbac-create-user-auth-method"
          class="field-input"
          type="text"
          placeholder="认证方式"
        />
        <button data-testid="rbac-create-user-submit" class="primary-btn" type="button" @click="createUser">
          创建用户
        </button>
      </div>
    </div>

    <div v-if="paginatedUsers.length" class="user-list">
      <button
        v-for="user in paginatedUsers"
        :key="user.userId"
        :data-testid="`rbac-user-select-${user.userId}`"
        class="user-card"
        :class="{ active: toBusinessId(user.userId) === activeUserId }"
        type="button"
        @click="selectUser(String(user.userId))"
      >
        <div class="user-card-top">
          <strong>{{ user.displayName }}</strong>
          <span class="status-badge" :class="{ enabled: user.status === 1 }">
            {{ user.deletionRequestedAt ? '待删除' : user.status === 1 ? '启用' : '停用' }}
          </span>
        </div>
        <span>{{ user.email }}</span>
        <small>userId: {{ user.userId }} · auth: {{ user.authMethod || 'local' }}</small>
      </button>
    </div>
    <div v-else class="empty-state">没有匹配的用户</div>

    <div class="pagination-bar">
      <span class="muted">{{ userPaginationText }}</span>
      <div class="pagination-actions">
        <button class="ghost-btn inline-action-btn" type="button" :disabled="userPage <= 1" @click="previousUserPage">
          上一页
        </button>
        <button
          data-testid="rbac-user-page-next"
          class="ghost-btn inline-action-btn"
          type="button"
          :disabled="userPage >= userTotalPages"
          @click="nextUserPage"
        >
          下一页
        </button>
      </div>
    </div>
  </section>

  <section class="panel panel-stack workspace-main">
    <div class="panel-header">
      <div>
        <h2>用户详情</h2>
        <span class="muted">当前选中用户：{{ activeUser?.displayName || '未选择' }}</span>
      </div>
    </div>

    <div class="sub-panel">
      <h3>基本信息</h3>
      <div v-if="activeUser?.deletionRequestedAt" class="pending-deletion-notice">
        <div>
          <strong>账户处于待删除期</strong>
          <span>预计于 {{ new Date(activeUser.deletionDueAt || '').toLocaleString() }} 永久删除。</span>
        </div>
        <button class="primary-btn" type="button" data-testid="rbac-user-restore-deletion" @click="restoreSelectedUser">
          恢复账户
        </button>
      </div>
      <div class="form-grid">
        <input
          aria-label="用户展示名"
          v-model="userDetailForm.displayName"
          data-testid="rbac-user-detail-display-name"
          class="field-input"
          type="text"
          placeholder="展示名"
        />
        <select
          aria-label="用户状态"
          v-model="userDetailForm.status"
          data-testid="rbac-user-detail-status"
          class="field-input"
        >
          <option :value="1">启用</option>
          <option :value="0">停用</option>
        </select>
        <div class="inline-actions">
          <button data-testid="rbac-user-detail-submit" class="primary-btn" type="button" @click="updateSelectedUser">
            保存用户
          </button>
          <button
            v-if="!activeUser?.deletionRequestedAt"
            data-testid="rbac-user-delete-trigger"
            class="ghost-btn"
            type="button"
            @click="requestDeleteSelectedUser"
          >
            删除用户
          </button>
        </div>
      </div>
    </div>

    <div
      v-if="pendingDeleteUserId === activeUserId && activeUser"
      class="sub-panel danger-panel"
      data-testid="rbac-user-delete-confirmation"
    >
      <h3>删除确认</h3>
      <p>确认删除用户 {{ activeUser.displayName }}（{{ activeUser.email }}）？该操作不可撤销。</p>
      <div class="inline-actions">
        <button
          data-testid="rbac-user-delete-confirm"
          class="primary-btn danger-btn"
          type="button"
          @click="deleteSelectedUser"
        >
          确认删除
        </button>
        <button data-testid="rbac-user-delete-cancel" class="ghost-btn" type="button" @click="cancelDeleteSelectedUser">
          取消
        </button>
      </div>
    </div>

    <div class="sub-panel">
      <div class="assignment-heading">
        <div>
          <h3>用户角色</h3>
          <span v-if="userRolesDirty" class="unsaved-state">有未保存变更</span>
          <span v-else class="saved-state">已保存 · r{{ userRolesRevision }}</span>
        </div>
        <div class="inline-actions">
          <button
            data-testid="rbac-user-roles-discard"
            class="ghost-btn inline-action-btn"
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
      <div class="form-grid">
        <select
          aria-label="待绑定角色"
          v-model="assignRoleForm.roleId"
          data-testid="rbac-assign-role-role-id"
          class="field-input"
        >
          <option value="">请选择角色</option>
          <option
            v-for="role in roles"
            :key="`assignable-role-${getRoleBusinessId(role)}`"
            :value="String(getRoleBusinessId(role) ?? '')"
            :disabled="userRoles.some((assigned) => getRoleBusinessId(assigned) === getRoleBusinessId(role))"
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
          <PlusOutlined />添加角色
        </button>
      </div>
    </div>

    <div class="sub-panel">
      <h3>已绑定角色</h3>
      <ul class="token-list">
        <li v-for="role in userRoles" :key="`assigned-role-${getRoleBusinessId(role)}`">
          <div class="token-item-main">
            <strong>{{ role.name }}</strong>
            <span>{{ role.code }}</span>
          </div>
          <button
            :data-testid="`rbac-remove-user-role-${getRoleBusinessId(role)}`"
            class="icon-btn"
            type="button"
            :aria-label="`移除角色 ${role.name}`"
            :title="`移除角色 ${role.name}`"
            @click="removeRoleFromSelectedUser(getRoleBusinessId(role) ?? '')"
          >
            <CloseOutlined />
          </button>
        </li>
      </ul>
    </div>

    <div class="sub-panel">
      <h3>当前用户菜单预览</h3>
      <ul class="menu-list">
        <li v-for="menu in profileMenus" :key="`user-workspace-menu-${getMenuBusinessId(menu)}`">
          <strong>{{ menu.title }}</strong>
          <span>{{ menu.path }}</span>
        </li>
      </ul>
    </div>
  </section>
</template>

<script setup lang="ts">
import { CloseOutlined, PlusOutlined, SaveOutlined, UndoOutlined } from '@ant-design/icons-vue'
import type { RbacConsoleController } from '@/features/rbac/useRbacConsole'

const { controller } = defineProps<{ controller: RbacConsoleController }>()
const {
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
</script>
