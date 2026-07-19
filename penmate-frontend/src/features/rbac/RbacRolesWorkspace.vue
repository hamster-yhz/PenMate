<template>
  <section class="panel panel-stack workspace-single" data-testid="rbac-role-workspace">
    <div class="panel-header">
      <div>
        <h2>角色权限</h2>
        <span class="muted">集中处理角色信息、权限绑定，并实时查看菜单变化</span>
      </div>
    </div>

    <div class="sub-panel">
      <h3>角色</h3>
      <ul class="token-list">
        <li v-for="role in roles" :key="getRoleBusinessId(role) ?? `role-missing-${role.code}`">
          <button
            :data-testid="`rbac-role-select-${getRoleBusinessId(role)}`"
            class="role-select-btn"
            :class="{ active: getRoleBusinessId(role) === activeRoleId }"
            type="button"
            @click="selectRole(getRoleBusinessId(role) ?? '')"
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
          aria-label="角色名称"
          v-model="createRoleForm.name"
          data-testid="rbac-create-role-name"
          class="field-input"
          type="text"
          placeholder="角色名称"
        />
        <input
          aria-label="角色编码"
          v-model="createRoleForm.code"
          data-testid="rbac-create-role-code"
          class="field-input"
          type="text"
          placeholder="角色编码"
        />
        <input
          aria-label="角色描述"
          v-model="createRoleForm.description"
          data-testid="rbac-create-role-description"
          class="field-input"
          type="text"
          placeholder="角色描述"
        />
        <button data-testid="rbac-create-role-submit" class="primary-btn" type="button" @click="createRole">
          创建角色
        </button>
      </div>
    </div>

    <div class="sub-panel">
      <h3>角色详情</h3>
      <div class="form-grid">
        <input
          aria-label="角色详情名称"
          v-model="roleDetailForm.name"
          data-testid="rbac-role-detail-name"
          class="field-input"
          type="text"
          placeholder="角色名称"
        />
        <input
          aria-label="角色详情描述"
          v-model="roleDetailForm.description"
          data-testid="rbac-role-detail-description"
          class="field-input"
          type="text"
          placeholder="角色描述"
        />
        <div class="inline-actions">
          <button data-testid="rbac-role-detail-submit" class="primary-btn" type="button" @click="updateActiveRole">
            保存角色
          </button>
          <button
            data-testid="rbac-role-delete-trigger"
            class="ghost-btn"
            type="button"
            @click="requestDeleteActiveRole"
          >
            删除角色
          </button>
        </div>
      </div>
    </div>

    <div
      v-if="pendingDeleteRoleId === activeRoleId && activeRole"
      class="sub-panel danger-panel"
      data-testid="rbac-role-delete-confirmation"
    >
      <h3>删除确认</h3>
      <p>确认删除角色 {{ activeRole.name }}（{{ activeRole.code }}）？关联用户权限将同步变化。</p>
      <div class="inline-actions">
        <button
          data-testid="rbac-role-delete-confirm"
          class="primary-btn danger-btn"
          type="button"
          @click="deleteActiveRole"
        >
          确认删除
        </button>
        <button data-testid="rbac-role-delete-cancel" class="ghost-btn" type="button" @click="cancelDeleteActiveRole">
          取消
        </button>
      </div>
    </div>

    <div class="sub-panel">
      <h3>权限池</h3>
      <ul class="token-list">
        <li
          v-for="permission in permissions"
          :key="getPermissionBusinessId(permission) ?? `permission-missing-${permission.code}`"
        >
          <strong>{{ permission.name }}</strong>
          <span>{{ permission.code }}</span>
        </li>
      </ul>
    </div>

    <div class="sub-panel">
      <h3>绑定权限</h3>
      <div class="form-grid">
        <select
          aria-label="待绑定权限"
          v-model="assignPermissionForm.permissionId"
          data-testid="rbac-assign-permission-permission-id"
          class="field-input"
        >
          <option value="">请选择权限</option>
          <option
            v-for="permission in permissions"
            :key="`assignable-permission-${getPermissionBusinessId(permission)}`"
            :value="String(getPermissionBusinessId(permission) ?? '')"
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
        <li v-for="permission in rolePermissions" :key="`assigned-permission-${getPermissionBusinessId(permission)}`">
          <div class="token-item-main">
            <strong>{{ permission.name }}</strong>
            <span>{{ permission.code }}</span>
          </div>
          <button
            :data-testid="`rbac-remove-role-permission-${getPermissionBusinessId(permission)}`"
            class="ghost-btn inline-action-btn"
            type="button"
            @click="removePermissionFromActiveRole(getPermissionBusinessId(permission) ?? '')"
          >
            解绑
          </button>
        </li>
      </ul>
    </div>

    <div class="sub-panel">
      <h3>当前用户菜单预览</h3>
      <ul class="menu-list">
        <li v-for="menu in profileMenus" :key="`role-workspace-menu-${getMenuBusinessId(menu)}`">
          <strong>{{ menu.title }}</strong>
          <span>{{ menu.path }}</span>
        </li>
      </ul>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { RbacConsoleController } from '@/features/rbac/useRbacConsole'

const { controller } = defineProps<{ controller: RbacConsoleController }>()
const {
  roles,
  permissions,
  profileMenus,
  rolePermissions,
  activeRoleId,
  assignPermissionForm,
  createRoleForm,
  roleDetailForm,
  activeRole,
  pendingDeleteRoleId,
  getRoleBusinessId,
  getPermissionBusinessId,
  getMenuBusinessId,
  selectRole,
  createRole,
  updateActiveRole,
  requestDeleteActiveRole,
  cancelDeleteActiveRole,
  deleteActiveRole,
  assignPermissionToActiveRole,
  removePermissionFromActiveRole,
} = controller
</script>
