<template>
  <div class="admin-rbac-page">
    <section v-if="errorMessage" class="rbac-error" data-testid="rbac-error-state" role="alert">
      <ExclamationCircleOutlined />
      <div>
        <strong>{{ loadFailed ? 'RBAC 数据加载失败' : '操作未完成' }}</strong>
        <span>{{ errorMessage }}</span>
      </div>
      <button v-if="loadFailed" type="button" data-testid="rbac-retry" @click="loadPage">
        <ReloadOutlined />重新加载
      </button>
    </section>

    <section v-if="loading" class="rbac-loading" role="status" aria-label="正在加载身份与权限数据">
      <div class="loading-toolbar"></div>
      <div class="loading-grid">
        <div></div>
        <div></div>
      </div>
    </section>

    <template v-if="!loading && !loadFailed">
      <nav v-if="!workspace" class="workspace-tabs" aria-label="身份与权限工作区切换">
        <button
          data-testid="rbac-tab-users"
          class="workspace-tab"
          :class="{ active: activeWorkspace === 'users' }"
          type="button"
          :aria-pressed="activeWorkspace === 'users'"
          @click="activeWorkspace = 'users'"
        >
          用户管理
        </button>
        <button
          data-testid="rbac-tab-roles"
          class="workspace-tab"
          :class="{ active: activeWorkspace === 'roles' }"
          type="button"
          :aria-pressed="activeWorkspace === 'roles'"
          @click="activeWorkspace = 'roles'"
        >
          角色与权限
        </button>
        <button
          data-testid="rbac-tab-menus"
          class="workspace-tab"
          :class="{ active: activeWorkspace === 'menus' }"
          type="button"
          :aria-pressed="activeWorkspace === 'menus'"
          @click="activeWorkspace = 'menus'"
        >
          访问结果
        </button>
      </nav>

      <main class="rbac-workspace">
        <RbacUsersWorkspace v-if="currentWorkspace === 'users'" :controller="rbacController" />
        <RbacRolesWorkspace v-else-if="currentWorkspace === 'roles'" :controller="rbacController" />
        <RbacMenusWorkspace v-else :controller="rbacController" />
      </main>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ExclamationCircleOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import RbacUsersWorkspace from '@/features/rbac/RbacUsersWorkspace.vue'
import RbacRolesWorkspace from '@/features/rbac/RbacRolesWorkspace.vue'
import RbacMenusWorkspace from '@/features/rbac/RbacMenusWorkspace.vue'
import { useRbacConsole } from '@/features/rbac/useRbacConsole'
import type { RbacWorkspaceKey } from '@/features/rbac/rbacModel'

const { workspace } = defineProps<{ workspace?: RbacWorkspaceKey }>()
const rbacController = useRbacConsole()
const { errorMessage, loading, loadFailed, loadPage, activeWorkspace } = rbacController
const currentWorkspace = computed(() => workspace || activeWorkspace.value)
</script>

<style scoped>
.admin-rbac-page {
  width: min(1440px, calc(100% - 48px));
  margin: 0 auto;
  padding: 20px 0 48px;
  color: var(--text-primary);
}

.rbac-error {
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding: 11px 13px;
  color: var(--danger);
  background: var(--danger-soft);
  border: 1px solid var(--danger-border);
  border-radius: 6px;
}

.rbac-error > div { display: grid; gap: 2px; }
.rbac-error span { color: var(--text-secondary); font-size: 12px; }
.rbac-error button {
  display: inline-flex;
  min-height: 32px;
  align-items: center;
  gap: 6px;
  padding: 0 10px;
  color: var(--danger);
  background: var(--bg-surface);
  border: 1px solid var(--danger-border);
  border-radius: 5px;
  cursor: pointer;
}

.rbac-loading { display: grid; gap: 12px; }
.loading-toolbar,
.loading-grid > div {
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  animation: rbac-pulse 1.4s ease-in-out infinite;
}
.loading-toolbar { height: 54px; }
.loading-grid { display: grid; grid-template-columns: minmax(420px, 0.9fr) minmax(520px, 1.1fr); gap: 12px; }
.loading-grid > div { min-height: 520px; }

.workspace-tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--border-subtle);
}
.workspace-tab {
  min-height: 38px;
  padding: 0 12px;
  color: var(--text-secondary);
  background: transparent;
  border: 0;
  border-bottom: 2px solid transparent;
  cursor: pointer;
}
.workspace-tab.active { color: var(--accent); border-bottom-color: var(--accent); }
.rbac-workspace { min-width: 0; }

@keyframes rbac-pulse { 50% { opacity: 0.48; } }

@media (max-width: 1100px) {
  .loading-grid { grid-template-columns: 1fr; }
}
</style>
