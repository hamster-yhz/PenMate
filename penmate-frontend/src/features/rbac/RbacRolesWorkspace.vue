<template>
  <section class="panel panel-stack workspace-single role-workspace" data-testid="rbac-role-workspace">
    <div class="panel-header">
      <div>
        <h2>角色权限</h2>
        <span class="muted">集中处理角色信息、权限绑定，并实时查看菜单变化</span>
      </div>
    </div>

    <div class="sub-panel role-catalog">
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

    <div class="sub-panel role-create">
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

    <div class="sub-panel role-detail">
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

    <div class="sub-panel permission-editor">
      <div class="assignment-heading">
        <div>
          <h3>权限分配</h3>
          <span v-if="rolePermissionsDirty" class="unsaved-state">有未保存变更</span>
          <span v-else class="saved-state">已保存 · r{{ rolePermissionsRevision }}</span>
        </div>
        <div class="inline-actions">
          <button
            data-testid="rbac-role-permissions-discard"
            class="ghost-btn inline-action-btn"
            type="button"
            :disabled="!rolePermissionsDirty"
            @click="discardRolePermissionChanges"
          >
            <UndoOutlined />撤销
          </button>
          <button
            data-testid="rbac-role-permissions-save"
            class="primary-btn"
            type="button"
            :disabled="!rolePermissionsDirty"
            @click="saveRolePermissions"
          >
            <SaveOutlined />保存权限
          </button>
        </div>
      </div>
      <label class="permission-search">
        <SearchOutlined />
        <input
          v-model.trim="permissionQuery"
          data-testid="rbac-permission-search"
          type="search"
          aria-label="搜索权限"
          placeholder="搜索权限名称、编码或业务域"
        />
      </label>
      <div v-if="permissionGroups.length" class="permission-groups">
        <section v-for="group in permissionGroups" :key="group.key" class="permission-group">
          <header>
            <div><strong>{{ group.label }}</strong><span>{{ group.items.length }} 项</span></div>
            <button
              type="button"
              :data-testid="`rbac-permission-group-toggle-${group.key}`"
              @click="togglePermissionGroup(group.items)"
            >{{ groupAllSelected(group.items) ? '清空' : '全选' }}</button>
          </header>
          <div class="permission-options">
            <label v-for="permission in group.items" :key="getPermissionBusinessId(permission) ?? permission.code">
              <input
                type="checkbox"
                :data-testid="`rbac-permission-toggle-${getPermissionBusinessId(permission)}`"
                :checked="permissionSelected(permission)"
                @change="togglePermission(permission, ($event.target as HTMLInputElement).checked)"
              />
              <span><strong>{{ permission.name }}</strong><small>{{ permission.code }}</small></span>
            </label>
          </div>
        </section>
      </div>
      <p v-else class="permission-empty">没有匹配的权限</p>
    </div>

    <div class="sub-panel role-menu-preview">
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
import { computed, ref } from 'vue'
import { SaveOutlined, SearchOutlined, UndoOutlined } from '@ant-design/icons-vue'
import type { RbacPermission } from '@/features/rbac/rbacModel'
import type { RbacConsoleController } from '@/features/rbac/useRbacConsole'

const { controller } = defineProps<{ controller: RbacConsoleController }>()
const {
  roles,
  permissions,
  profileMenus,
  rolePermissions,
  rolePermissionsRevision,
  rolePermissionsDirty,
  activeRoleId,
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
  saveRolePermissions,
  discardRolePermissionChanges,
} = controller

const permissionQuery = ref('')
const permissionDomainLabels: Record<string, string> = {
  rbac: '权限与角色',
  content: '内容管理',
  novel: '作品管理',
  agent: 'Agent 运行',
  model: '模型管理',
  system: '系统管理',
}
const permissionGroups = computed(() => {
  const query = permissionQuery.value.toLowerCase()
  const groups = new Map<string, RbacPermission[]>()
  permissions.value
    .filter((permission) => !query || [permission.name, permission.code, permission.module]
      .some((value) => String(value || '').toLowerCase().includes(query)))
    .forEach((permission) => {
      const key = String(permission.module || permission.code.split('.')[0] || 'other').toLowerCase()
      groups.set(key, [...(groups.get(key) || []), permission])
    })
  return [...groups.entries()]
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([key, items]) => ({ key, label: permissionDomainLabels[key] || key, items }))
})
const selectedPermissionIds = computed(() => new Set(
  rolePermissions.value.map((permission) => getPermissionBusinessId(permission)).filter(Boolean),
))
const permissionSelected = (permission: RbacPermission) => {
  const permissionId = getPermissionBusinessId(permission)
  return permissionId != null && selectedPermissionIds.value.has(permissionId)
}
const togglePermission = (permission: RbacPermission, selected: boolean) => {
  const permissionId = getPermissionBusinessId(permission)
  if (!permissionId) return
  if (selected && !selectedPermissionIds.value.has(permissionId)) {
    rolePermissions.value = [...rolePermissions.value, permission]
  } else if (!selected) {
    rolePermissions.value = rolePermissions.value.filter(
      (item) => getPermissionBusinessId(item) !== permissionId,
    )
  }
}
const groupAllSelected = (items: RbacPermission[]) => items.length > 0 && items.every(permissionSelected)
const togglePermissionGroup = (items: RbacPermission[]) => {
  const shouldSelect = !groupAllSelected(items)
  const groupIds = new Set(items.map((item) => getPermissionBusinessId(item)).filter(Boolean))
  rolePermissions.value = shouldSelect
    ? [...rolePermissions.value, ...items.filter((item) => !permissionSelected(item))]
    : rolePermissions.value.filter((item) => !groupIds.has(getPermissionBusinessId(item)))
}
</script>

<style scoped>
.role-workspace { grid-template-columns: repeat(3, minmax(0, 1fr)); column-gap: 20px; }
.role-workspace > .panel-header,
.role-workspace > .danger-panel,
.role-workspace > .permission-editor,
.role-workspace > .role-menu-preview { grid-column: 1 / -1; }
.permission-editor { display: grid; gap: 12px; }
.permission-search { display: flex; align-items: center; gap: 8px; min-height: 38px; padding: 0 10px; background: var(--bg-subtle); border: 1px solid var(--border-subtle); }
.permission-search svg { color: var(--text-muted); }
.permission-search input { width: 100%; min-width: 0; color: var(--text-primary); background: transparent; border: 0; outline: 0; }
.permission-groups { display: grid; border: 1px solid var(--border-subtle); }
.permission-group + .permission-group { border-top: 1px solid var(--border-subtle); }
.permission-group > header { display: flex; min-height: 40px; align-items: center; justify-content: space-between; gap: 12px; padding: 7px 10px; background: var(--bg-subtle); }
.permission-group > header div { display: flex; align-items: baseline; gap: 7px; }
.permission-group > header strong { font-size: 12px; }
.permission-group > header span { color: var(--text-muted); font-size: 10px; }
.permission-group > header button { min-height: 28px; padding: 0 8px; color: var(--accent); background: transparent; border: 1px solid var(--accent-border); border-radius: 4px; cursor: pointer; font-size: 11px; }
.permission-options { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
.permission-options > label { display: grid; grid-template-columns: 18px minmax(0, 1fr); align-items: center; gap: 8px; min-height: 48px; padding: 7px 10px; border-top: 1px solid var(--border-subtle); cursor: pointer; }
.permission-options > label:nth-child(odd) { border-right: 1px solid var(--border-subtle); }
.permission-options input { width: 15px; height: 15px; accent-color: var(--accent); }
.permission-options span { display: grid; min-width: 0; gap: 2px; }
.permission-options strong, .permission-options small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.permission-options strong { font-size: 12px; }
.permission-options small { color: var(--text-muted); font-size: 10px; }
.permission-empty { margin: 0; padding: 24px; color: var(--text-muted); border: 1px dashed var(--border-strong); text-align: center; font-size: 12px; }
@media (max-width: 1100px) { .role-workspace { grid-template-columns: 1fr; } .role-workspace > * { grid-column: 1; } .permission-options { grid-template-columns: 1fr; } .permission-options > label:nth-child(odd) { border-right: 0; } }
</style>
