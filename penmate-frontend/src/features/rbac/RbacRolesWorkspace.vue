<template>
  <div class="role-workspace" data-testid="rbac-role-workspace">
    <aside class="role-directory">
      <header class="directory-header">
        <div><h2>角色目录</h2><span>{{ roles.length }} 个角色</span></div>
        <button data-testid="rbac-toggle-create-role" type="button" :title="createRoleExpanded ? '收起创建角色' : '创建角色'" @click="createRoleExpanded = !createRoleExpanded">
          <CloseOutlined v-if="createRoleExpanded" />
          <PlusOutlined v-else />
        </button>
      </header>
      <label class="role-search">
        <SearchOutlined />
        <input v-model.trim="roleQuery" type="search" aria-label="搜索角色" placeholder="搜索角色名称或编码" />
      </label>

      <div class="role-list">
        <button
          v-for="role in filteredRoles"
          :key="getRoleBusinessId(role) ?? `role-missing-${role.code}`"
          :data-testid="`rbac-role-select-${getRoleBusinessId(role)}`"
          class="role-select-btn"
          :class="{ active: getRoleBusinessId(role) === activeRoleId }"
          type="button"
          @click="selectRole(getRoleBusinessId(role) ?? '')"
        >
          <span class="role-mark"><SafetyCertificateOutlined /></span>
          <span><strong>{{ role.name }}</strong><small>{{ role.code }}</small></span>
          <span v-if="role.isSystem" class="system-badge">系统</span>
          <RightOutlined />
        </button>
        <div v-if="!filteredRoles.length" class="empty-list">没有匹配的角色</div>
      </div>

      <section v-if="createRoleExpanded" class="create-role-section">
        <div><h3>创建角色</h3><p>角色编码创建后保持稳定，用于策略识别。</p></div>
        <label><span>名称</span><input v-model="createRoleForm.name" data-testid="rbac-create-role-name" class="field-input" type="text" placeholder="例如：内容运营" /></label>
        <label><span>编码</span><input v-model="createRoleForm.code" data-testid="rbac-create-role-code" class="field-input" type="text" placeholder="CONTENT_OPERATOR" /></label>
        <label><span>描述</span><input v-model="createRoleForm.description" data-testid="rbac-create-role-description" class="field-input" type="text" placeholder="说明这个角色的职责边界" /></label>
        <button data-testid="rbac-create-role-submit" class="primary-btn" type="button" @click="handleCreateRole">
          <PlusOutlined />创建角色
        </button>
      </section>
    </aside>

    <section v-if="activeRole" class="role-detail">
      <header class="role-detail-header">
        <div class="role-icon"><SafetyCertificateOutlined /></div>
        <div>
          <p>角色详情</p>
          <h2>{{ activeRole.name }}</h2>
          <span>{{ activeRole.code }}<template v-if="activeRole.isSystem"> · 系统内置角色</template></span>
        </div>
        <span :class="rolePermissionsDirty ? 'unsaved-state' : 'saved-state'">
          {{ rolePermissionsDirty ? '有未保存变更' : `已保存 · r${rolePermissionsRevision}` }}
        </span>
      </header>

      <section class="role-definition">
        <div v-if="activeRole.isSystem" class="system-role-notice">
          <SafetyCertificateOutlined />
          <span><strong>系统内置角色</strong><small>名称、职责和权限由应用基线维护，管理员只能查看。</small></span>
        </div>
        <div class="section-heading">
          <div><h3>角色定义</h3><p>名称和职责说明帮助管理员在分配前判断适用范围。</p></div>
        </div>
        <div class="role-definition-fields">
          <label>
            <span>角色名称</span>
            <input v-model="roleDetailForm.name" data-testid="rbac-role-detail-name" class="field-input" type="text" :disabled="activeRole.isSystem" />
          </label>
          <label>
            <span>职责说明</span>
            <input v-model="roleDetailForm.description" data-testid="rbac-role-detail-description" class="field-input" type="text" :disabled="activeRole.isSystem" placeholder="说明该角色应当授予谁" />
          </label>
          <button data-testid="rbac-role-detail-submit" class="secondary-btn" type="button" :disabled="activeRole.isSystem" @click="updateActiveRole">
            <SaveOutlined />保存定义
          </button>
        </div>
      </section>

      <section class="permission-editor">
        <div class="section-heading permission-heading">
          <div>
            <h3>权限分配</h3>
            <p>按业务域审阅最小权限；删除和授权类操作会标记为高风险。</p>
          </div>
          <span>{{ selectedPermissionIds.size }} / {{ permissions.length }} 已授权</span>
        </div>
        <label class="permission-search">
          <SearchOutlined />
          <input
            v-model.trim="permissionQuery"
            data-testid="rbac-permission-search"
            type="search"
            aria-label="搜索权限"
            placeholder="搜索权限名称、编码、说明或业务域"
          />
        </label>
        <div class="permission-filters" aria-label="权限筛选">
          <button type="button" :class="{ active: activeDomain === 'all' }" @click="activeDomain = 'all'">全部域</button>
          <button v-for="domain in permissionDomains" :key="domain.key" type="button" :class="{ active: activeDomain === domain.key }" @click="activeDomain = domain.key">
            {{ domain.label }} <span>{{ domain.count }}</span>
          </button>
          <label class="assigned-only"><input v-model="assignedOnly" type="checkbox" />仅看已授权</label>
        </div>

        <div v-if="permissionGroups.length" class="permission-groups">
          <section v-for="group in permissionGroups" :key="group.key" class="permission-group">
            <header>
              <div><strong>{{ group.label }}</strong><span>{{ selectedCount(group.items) }} / {{ group.items.length }} 已授权</span></div>
              <button
                type="button"
                :data-testid="`rbac-permission-group-toggle-${group.key}`"
                :disabled="activeRole.isSystem"
                @click="togglePermissionGroup(group.items)"
              >
                {{ groupAllSelected(group.items) ? '清空本组' : '授权本组' }}
              </button>
            </header>
            <div class="permission-options">
              <label v-for="permission in group.items" :key="getPermissionBusinessId(permission) ?? permission.code" :class="{ selected: permissionSelected(permission) }">
                <input
                  type="checkbox"
                  :data-testid="`rbac-permission-toggle-${getPermissionBusinessId(permission)}`"
                  :checked="permissionSelected(permission)"
                  :disabled="activeRole.isSystem"
                  @change="togglePermission(permission, ($event.target as HTMLInputElement).checked)"
                />
                <span class="permission-copy">
                  <span><strong>{{ permission.name }}</strong><em :class="permissionRiskClass(permission.code)">{{ permissionActionLabel(permission.code) }}</em></span>
                  <small>{{ permission.description || '暂无说明' }}</small>
                </span>
              </label>
            </div>
          </section>
        </div>
        <p v-else class="permission-empty">没有匹配的权限</p>

        <footer class="permission-actions">
          <span>{{ rolePermissionsDirty ? '修改尚未生效' : '当前权限已与服务端一致' }}</span>
          <div>
            <button
              data-testid="rbac-role-permissions-discard"
              class="ghost-btn"
              type="button"
              :disabled="!rolePermissionsDirty || activeRole.isSystem"
              @click="discardRolePermissionChanges"
            >
              <UndoOutlined />撤销
            </button>
            <button
              data-testid="rbac-role-permissions-save"
              class="primary-btn"
              type="button"
              :disabled="!rolePermissionsDirty || activeRole.isSystem"
              @click="saveRolePermissions"
            >
              <SaveOutlined />保存权限
            </button>
          </div>
        </footer>
      </section>

      <section class="access-impact">
        <div class="section-heading">
          <div><h3>访问影响</h3><p>根据当前暂存权限推导出的菜单入口；保存后才会对用户生效。</p></div>
          <span>{{ impactedMenus.length }} 项</span>
        </div>
        <ul>
          <li v-for="menu in impactedMenus" :key="getMenuBusinessId(menu) ?? menu.path">
            <CheckCircleOutlined />
            <span><strong>{{ menu.title }}</strong><small>{{ menu.path }}</small></span>
            <span class="impact-state">可访问</span>
          </li>
          <li v-if="!impactedMenus.length" class="empty-impact">当前角色不会解锁任何受控菜单</li>
        </ul>
      </section>

      <section
        v-if="pendingDeleteRoleId === activeRoleId"
        class="danger-confirmation"
        data-testid="rbac-role-delete-confirmation"
      >
        <div><strong>删除角色 {{ activeRole.name }}？</strong><span>关联用户将立即失去该角色带来的访问权限。</span></div>
        <button data-testid="rbac-role-delete-cancel" class="ghost-btn" type="button" @click="cancelDeleteActiveRole">取消</button>
        <button data-testid="rbac-role-delete-confirm" class="danger-btn" type="button" @click="deleteActiveRole">确认删除</button>
      </section>
      <footer v-else class="role-danger-footer">
        <span>{{ activeRole.isSystem ? '系统角色不可删除' : '不再使用的自定义角色可以删除' }}</span>
        <button data-testid="rbac-role-delete-trigger" class="danger-link" type="button" :disabled="activeRole.isSystem" @click="requestDeleteActiveRole">
          <DeleteOutlined />删除角色
        </button>
      </footer>
    </section>

    <section v-else class="role-detail empty-detail">
      <SafetyCertificateOutlined />
      <strong>选择一个角色</strong>
      <span>在此维护角色定义、最小权限和访问影响。</span>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  CheckCircleOutlined,
  CloseOutlined,
  DeleteOutlined,
  PlusOutlined,
  RightOutlined,
  SafetyCertificateOutlined,
  SaveOutlined,
  SearchOutlined,
  UndoOutlined,
} from '@ant-design/icons-vue'
import type { RbacPermission } from '@/features/rbac/rbacModel'
import type { RbacConsoleController } from '@/features/rbac/useRbacConsole'

const { controller } = defineProps<{ controller: RbacConsoleController }>()
const {
  roles,
  permissions,
  menus,
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

const roleQuery = ref('')
const permissionQuery = ref('')
const activeDomain = ref('all')
const assignedOnly = ref(false)
const createRoleExpanded = ref(false)

const permissionDomainLabels: Record<string, string> = {
  rbac: '权限与角色',
  content: '内容管理',
  novel: '作品管理',
  agent: 'Agent 运行',
  model: '模型管理',
  ops: '运行与任务',
  system: '系统管理',
}

const filteredRoles = computed(() => {
  const query = roleQuery.value.toLowerCase()
  return roles.value.filter((role) => !query || [role.name, role.code, role.description]
    .some((value) => String(value || '').toLowerCase().includes(query)))
})
const permissionDomainKey = (permission: RbacPermission) => String(permission.module || permission.code.split(':')[0] || 'other').toLowerCase()
const selectedPermissionIds = computed(() => new Set(
  rolePermissions.value.map((permission) => getPermissionBusinessId(permission)).filter((id): id is string => Boolean(id)),
))
const permissionSelected = (permission: RbacPermission) => {
  const permissionId = getPermissionBusinessId(permission)
  return permissionId != null && selectedPermissionIds.value.has(permissionId)
}
const permissionDomains = computed(() => {
  const counts = new Map<string, number>()
  permissions.value.forEach((permission) => {
    const key = permissionDomainKey(permission)
    counts.set(key, (counts.get(key) || 0) + 1)
  })
  return [...counts.entries()].sort(([left], [right]) => left.localeCompare(right)).map(([key, count]) => ({
    key,
    count,
    label: permissionDomainLabels[key] || key,
  }))
})
const permissionGroups = computed(() => {
  const query = permissionQuery.value.toLowerCase()
  const groups = new Map<string, RbacPermission[]>()
  permissions.value
    .filter((permission) => activeDomain.value === 'all' || permissionDomainKey(permission) === activeDomain.value)
    .filter((permission) => !assignedOnly.value || permissionSelected(permission))
    .filter((permission) => !query || [permission.name, permission.code, permission.module, permission.description]
      .some((value) => String(value || '').toLowerCase().includes(query)))
    .forEach((permission) => {
      const key = permissionDomainKey(permission)
      groups.set(key, [...(groups.get(key) || []), permission])
    })
  return [...groups.entries()].sort(([left], [right]) => left.localeCompare(right)).map(([key, items]) => ({
    key,
    label: permissionDomainLabels[key] || key,
    items,
  }))
})
const selectedPermissionCodes = computed(() => new Set(rolePermissions.value.map((permission) => permission.code)))
const impactedMenus = computed(() => {
  const candidates = [...menus.value, ...profileMenus.value]
  const uniqueMenus = new Map(candidates.map((menu) => [getMenuBusinessId(menu) || menu.path, menu]))
  return [...uniqueMenus.values()].filter(
    (menu) => menu.visible !== false && menu.permissionCode && selectedPermissionCodes.value.has(menu.permissionCode),
  )
})

const togglePermission = (permission: RbacPermission, selected: boolean) => {
  const permissionId = getPermissionBusinessId(permission)
  if (!permissionId) return
  if (selected && !selectedPermissionIds.value.has(permissionId)) rolePermissions.value = [...rolePermissions.value, permission]
  else if (!selected) rolePermissions.value = rolePermissions.value.filter((item) => getPermissionBusinessId(item) !== permissionId)
}
const groupAllSelected = (items: RbacPermission[]) => items.length > 0 && items.every(permissionSelected)
const selectedCount = (items: RbacPermission[]) => items.filter(permissionSelected).length
const togglePermissionGroup = (items: RbacPermission[]) => {
  const shouldSelect = !groupAllSelected(items)
  const groupIds = new Set(items.map((item) => getPermissionBusinessId(item)).filter(Boolean))
  rolePermissions.value = shouldSelect
    ? [...rolePermissions.value, ...items.filter((item) => !permissionSelected(item))]
    : rolePermissions.value.filter((item) => !groupIds.has(getPermissionBusinessId(item)))
}
const permissionActionLabel = (code: string) => {
  const action = code.toLowerCase().split(':').at(-1) || code
  if (action.includes('delete')) return '删除'
  if (action.includes('bind')) return '授权'
  if (action.includes('write') || action.includes('manage')) return '管理'
  if (action.includes('read') || action.includes('view')) return '查看'
  if (action.includes('access')) return '访问'
  return '操作'
}
const permissionRiskClass = (code: string) => {
  const action = permissionActionLabel(code)
  return action === '删除' || action === '授权' ? 'risk-high' : action === '管理' ? 'risk-medium' : 'risk-low'
}
const handleCreateRole = async () => {
  await createRole()
  if (!createRoleForm.value.name && !createRoleForm.value.code) createRoleExpanded.value = false
}
</script>

<style scoped>
.role-workspace { display: grid; grid-template-columns: 310px minmax(0, 1fr); align-items: start; gap: 12px; }
.role-directory,
.role-detail { min-width: 0; background: var(--bg-surface); border: 1px solid var(--border-subtle); border-radius: 6px; }
.role-directory { position: sticky; top: 0; overflow: hidden; }
.directory-header { display: flex; min-height: 60px; align-items: center; justify-content: space-between; gap: 10px; padding: 10px 12px; border-bottom: 1px solid var(--border-subtle); }
.directory-header > div { display: grid; gap: 2px; }
h2, h3, p { margin: 0; letter-spacing: 0; }
.directory-header h2,
.role-detail-header h2 { font-size: 15px; }
.directory-header span,
.role-detail-header p,
.role-detail-header span,
.section-heading p { color: var(--text-muted); font-size: 11px; }
.directory-header button { display: grid; width: 30px; height: 30px; place-items: center; color: var(--accent); background: var(--accent-soft); border: 1px solid var(--accent-border); border-radius: 4px; cursor: pointer; }
.role-search { display: flex; min-height: 34px; align-items: center; gap: 7px; margin: 10px; padding: 0 9px; background: var(--bg-subtle); border: 1px solid var(--border-subtle); border-radius: 5px; }
.role-search svg { color: var(--text-muted); }
.role-search input { width: 100%; min-width: 0; color: var(--text-primary); background: transparent; border: 0; outline: 0; }
.role-list { max-height: 420px; overflow-y: auto; border-top: 1px solid var(--border-subtle); }
.role-select-btn { display: grid; grid-template-columns: 32px minmax(0, 1fr) auto 16px; width: 100%; min-height: 58px; align-items: center; gap: 8px; padding: 7px 10px; color: var(--text-primary); background: transparent; border: 0; border-bottom: 1px solid var(--border-subtle); cursor: pointer; text-align: left; }
.role-select-btn:hover { background: var(--bg-subtle); }
.role-select-btn.active { background: var(--accent-soft); box-shadow: inset 3px 0 var(--accent); }
.role-mark { display: grid; width: 30px; height: 30px; place-items: center; color: var(--info); background: var(--info-soft); border-radius: 5px; }
.role-select-btn > span:nth-child(2) { display: grid; min-width: 0; gap: 2px; }
.role-select-btn strong,
.role-select-btn small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.role-select-btn strong { font-size: 12px; }
.role-select-btn small { color: var(--text-muted); font-size: 10px; }
.role-select-btn > svg { color: var(--text-muted); }
.system-badge { padding: 2px 5px; color: var(--info); background: var(--info-soft); border-radius: 3px; font-size: 9px; }
.empty-list,
.empty-detail { display: grid; min-height: 180px; place-items: center; align-content: center; gap: 7px; color: var(--text-muted); font-size: 11px; }
.empty-detail > svg { font-size: 26px; }
.empty-detail strong { color: var(--text-secondary); font-size: 13px; }
.create-role-section { display: grid; gap: 9px; padding: 12px; border-top: 1px solid var(--border-subtle); }
.create-role-section > div { display: grid; gap: 2px; }
.create-role-section h3 { font-size: 12px; }
.create-role-section p { color: var(--text-muted); font-size: 10px; }
.create-role-section label,
.role-definition-fields label { display: grid; gap: 4px; color: var(--text-secondary); font-size: 10px; }
.field-input { width: 100%; min-height: 34px; padding: 0 9px; color: var(--text-primary); background: var(--bg-surface); border: 1px solid var(--border-strong); border-radius: 5px; box-sizing: border-box; }
.primary-btn,
.secondary-btn,
.ghost-btn,
.danger-btn,
.danger-link { display: inline-flex; min-height: 34px; align-items: center; justify-content: center; gap: 6px; padding: 0 11px; border-radius: 5px; cursor: pointer; }
.primary-btn { color: var(--text-inverse); background: var(--accent); border: 1px solid var(--accent); }
.secondary-btn { color: var(--accent); background: var(--accent-soft); border: 1px solid var(--accent-border); }
.ghost-btn { color: var(--text-secondary); background: var(--bg-surface); border: 1px solid var(--border-strong); }
.danger-btn { color: white; background: var(--danger); border: 1px solid var(--danger); }
button:disabled { cursor: not-allowed; opacity: 0.45; }
.role-detail-header { display: grid; grid-template-columns: 44px minmax(0, 1fr) auto; min-height: 76px; align-items: center; gap: 11px; padding: 12px 16px; border-bottom: 1px solid var(--border-subtle); }
.role-detail-header > div:nth-child(2) { display: grid; min-width: 0; gap: 2px; }
.role-icon { display: grid; width: 44px; height: 44px; place-items: center; color: var(--info); background: var(--info-soft); border-radius: 6px; font-size: 19px; }
.unsaved-state { color: var(--warning) !important; }
.saved-state { color: var(--text-muted); }
.role-definition,
.permission-editor,
.access-impact { padding: 16px; }
.system-role-notice { display: flex; align-items: flex-start; gap: 9px; margin-bottom: 14px; padding: 10px; color: var(--info); background: var(--info-soft); border: 1px solid color-mix(in srgb, var(--info) 28%, transparent); border-radius: 5px; }
.system-role-notice > span { display: grid; gap: 2px; }
.system-role-notice small { color: var(--text-secondary); font-size: 10px; }
.permission-editor,
.access-impact { border-top: 1px solid var(--border-subtle); }
.section-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; margin-bottom: 12px; }
.section-heading > div { display: grid; gap: 3px; }
.section-heading h3 { font-size: 13px; }
.section-heading > span { color: var(--text-muted); font-size: 11px; }
.role-definition-fields { display: grid; grid-template-columns: minmax(160px, 0.45fr) minmax(260px, 1fr) auto; align-items: end; gap: 9px; }
.permission-search { display: flex; min-height: 38px; align-items: center; gap: 8px; padding: 0 10px; background: var(--bg-subtle); border: 1px solid var(--border-subtle); border-radius: 5px; }
.permission-search svg { color: var(--text-muted); }
.permission-search input { width: 100%; min-width: 0; color: var(--text-primary); background: transparent; border: 0; outline: 0; }
.permission-filters { display: flex; align-items: center; gap: 4px; margin: 10px 0; overflow-x: auto; }
.permission-filters > button { flex: 0 0 auto; min-height: 30px; padding: 0 8px; color: var(--text-secondary); background: transparent; border: 1px solid var(--border-subtle); border-radius: 4px; cursor: pointer; font-size: 10px; }
.permission-filters > button.active { color: var(--accent); background: var(--accent-soft); border-color: var(--accent-border); }
.permission-filters button span { color: var(--text-muted); }
.assigned-only { display: flex; flex: 0 0 auto; align-items: center; gap: 5px; margin-left: auto; color: var(--text-secondary); font-size: 10px; }
.assigned-only input,
.permission-options input { accent-color: var(--accent); }
.permission-groups { display: grid; gap: 8px; }
.permission-group { overflow: hidden; border: 1px solid var(--border-subtle); border-radius: 5px; }
.permission-group > header { display: flex; min-height: 40px; align-items: center; justify-content: space-between; gap: 12px; padding: 7px 10px; background: var(--bg-subtle); }
.permission-group > header div { display: flex; align-items: baseline; gap: 7px; }
.permission-group > header strong { font-size: 12px; }
.permission-group > header span { color: var(--text-muted); font-size: 10px; }
.permission-group > header button { min-height: 28px; padding: 0 8px; color: var(--accent); background: transparent; border: 1px solid var(--accent-border); border-radius: 4px; cursor: pointer; font-size: 10px; }
.permission-options { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
.permission-options > label { display: grid; grid-template-columns: 18px minmax(0, 1fr); min-height: 72px; align-items: start; gap: 8px; padding: 10px; border-top: 1px solid var(--border-subtle); cursor: pointer; }
.permission-options > label:nth-child(odd) { border-right: 1px solid var(--border-subtle); }
.permission-options > label.selected { background: color-mix(in srgb, var(--accent-soft) 55%, transparent); }
.permission-options input { width: 15px; height: 15px; margin-top: 2px; }
.permission-copy { display: grid; min-width: 0; gap: 3px; }
.permission-copy > span { display: flex; align-items: center; justify-content: space-between; gap: 7px; }
.permission-copy strong,
.permission-copy small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.permission-copy strong { font-size: 11px; }
.permission-copy small { color: var(--text-secondary); font-size: 10px; }
.permission-copy em { flex: 0 0 auto; padding: 2px 5px; border-radius: 3px; font-size: 9px; font-style: normal; }
.risk-low { color: var(--info); background: var(--info-soft); }
.risk-medium { color: var(--warning); background: var(--warning-soft); }
.risk-high { color: var(--danger); background: var(--danger-soft); }
.permission-empty { margin: 0; padding: 32px; color: var(--text-muted); border: 1px dashed var(--border-strong); text-align: center; font-size: 11px; }
.permission-actions { position: sticky; z-index: 2; bottom: 0; display: flex; min-height: 58px; align-items: center; justify-content: space-between; gap: 12px; margin: 12px -16px -16px; padding: 10px 16px; background: color-mix(in srgb, var(--bg-surface) 94%, transparent); border-top: 1px solid var(--border-subtle); backdrop-filter: blur(8px); }
.permission-actions > span { color: var(--text-muted); font-size: 11px; }
.permission-actions > div { display: flex; gap: 8px; }
.access-impact ul { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0; margin: 0; padding: 0; overflow: hidden; border: 1px solid var(--border-subtle); border-radius: 5px; list-style: none; }
.access-impact li { display: grid; grid-template-columns: 18px minmax(0, 1fr) auto; min-height: 48px; align-items: center; gap: 8px; padding: 7px 10px; border-bottom: 1px solid var(--border-subtle); }
.access-impact li:nth-child(odd) { border-right: 1px solid var(--border-subtle); }
.access-impact svg { color: var(--accent); }
.access-impact li > span { display: grid; min-width: 0; gap: 2px; }
.access-impact strong { font-size: 11px; }
.access-impact small,
.access-impact code { color: var(--text-muted); font-size: 9px; }
.impact-state { color: var(--accent); font-size: 10px; }
.empty-impact { display: block !important; grid-column: 1 / -1; padding: 18px !important; color: var(--text-muted); text-align: center; font-size: 11px; }
.role-danger-footer,
.danger-confirmation { display: flex; min-height: 54px; align-items: center; justify-content: space-between; gap: 9px; padding: 10px 16px; border-top: 1px solid var(--border-subtle); }
.role-danger-footer > span { color: var(--text-muted); font-size: 11px; }
.danger-link { min-height: 30px; color: var(--danger); background: transparent; border: 0; }
.danger-confirmation { background: var(--danger-soft); border-top-color: color-mix(in srgb, var(--danger) 35%, transparent); }
.danger-confirmation > div { display: grid; min-width: 0; gap: 2px; margin-right: auto; }
.danger-confirmation span { color: var(--text-muted); font-size: 11px; }

@media (max-width: 1180px) {
  .role-workspace { grid-template-columns: 270px minmax(0, 1fr); }
  .permission-options { grid-template-columns: 1fr; }
  .permission-options > label:nth-child(odd) { border-right: 0; }
  .access-impact ul { grid-template-columns: 1fr; }
  .access-impact li:nth-child(odd) { border-right: 0; }
}
</style>
