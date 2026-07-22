<template>
  <div class="admin-rbac-page">
    <div class="rbac-context-bar">
      <div>
        <strong data-testid="rbac-active-user-name">{{ activeUser?.displayName || session.userName || '未选择用户' }}</strong>
        <span>{{ session.userEmail || activeUser?.email || '无邮箱信息' }}</span>
      </div>
      <dl aria-label="RBAC 数据概览">
        <div><dt>用户</dt><dd>{{ users.length }}</dd></div>
        <div><dt>角色</dt><dd>{{ roles.length }}</dd></div>
        <div><dt>权限</dt><dd>{{ permissions.length }}</dd></div>
        <div><dt>菜单</dt><dd>{{ menus.length }}</dd></div>
      </dl>
    </div>

    <section v-if="errorMessage" class="error-banner" data-testid="rbac-error-state">
      <div><strong>{{ loadFailed ? 'RBAC 数据加载失败' : '操作未完成' }}</strong><span>{{ errorMessage }}</span></div>
      <button v-if="loadFailed" type="button" data-testid="rbac-retry" @click="loadPage"><ReloadOutlined />重新加载</button>
    </section>

    <section v-if="loading" class="rbac-loading" role="status" aria-label="正在加载 RBAC 数据">
      <div v-for="index in 4" :key="index"><i></i><span></span><span></span></div>
    </section>

    <section v-if="!loading && !loadFailed" class="workspace-tabs" aria-label="RBAC 工作区切换">
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
        角色权限
      </button>
      <button
        data-testid="rbac-tab-menus"
        class="workspace-tab"
        :class="{ active: activeWorkspace === 'menus' }"
        type="button"
        :aria-pressed="activeWorkspace === 'menus'"
        @click="activeWorkspace = 'menus'"
      >
        菜单预览
      </button>
    </section>

    <main v-if="!loading && !loadFailed" class="rbac-layout" :class="{ 'rbac-layout-single': activeWorkspace !== 'users' }">
      <RbacUsersWorkspace v-if="activeWorkspace === 'users'" :controller="rbacController" />
      <RbacRolesWorkspace v-else-if="activeWorkspace === 'roles'" :controller="rbacController" />
      <RbacMenusWorkspace v-else :controller="rbacController" />
    </main>
  </div>
</template>

<script setup lang="ts">
import { ReloadOutlined } from '@ant-design/icons-vue'
import RbacUsersWorkspace from '@/features/rbac/RbacUsersWorkspace.vue'
import RbacRolesWorkspace from '@/features/rbac/RbacRolesWorkspace.vue'
import RbacMenusWorkspace from '@/features/rbac/RbacMenusWorkspace.vue'
import { useRbacConsole } from '@/features/rbac/useRbacConsole'

const rbacController = useRbacConsole()
const { session, users, roles, permissions, menus, activeUser, errorMessage, loading, loadFailed, loadPage, activeWorkspace } = rbacController
</script>
<style lang="less">
.admin-rbac-page {
  min-height: 100vh;
  padding: 24px;
  background: #0b1120;
  color: #e5e7eb;

  .rbac-header,
  .summary-grid,
  .workspace-tabs,
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

  .danger-btn {
    background: #f87171;
    color: #fff7ed;
  }

  .pending-deletion-notice {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 14px;
    padding: 12px 14px;
    color: #fecaca;
    background: #3f1d24;
    border: 1px solid #7f1d1d;
    border-radius: 6px;

    div { display: grid; gap: 4px; }
    span { color: #fca5a5; font-size: 12px; }
  }

  .summary-grid {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 12px;
    margin-bottom: 20px;
  }

  .workspace-tabs {
    display: flex;
    gap: 10px;
    margin-bottom: 20px;
    flex-wrap: wrap;
  }

  .rbac-context-bar {
    display: flex;
    max-width: 1320px;
    min-height: 52px;
    align-items: center;
    justify-content: space-between;
    gap: 20px;
    margin: 0 auto 14px;
    padding: 8px 12px;
    background: var(--bg-surface);
    border: 1px solid var(--border-subtle);
    border-radius: 5px;
  }

  .rbac-context-bar > div { display: grid; gap: 2px; min-width: 0; }
  .rbac-context-bar > div span { overflow: hidden; color: var(--text-muted); font-size: 11px; text-overflow: ellipsis; }
  .rbac-context-bar dl { display: flex; margin: 0; }
  .rbac-context-bar dl div { display: grid; grid-template-columns: auto auto; gap: 5px; padding: 0 10px; border-left: 1px solid var(--border-subtle); }
  .rbac-context-bar dt { color: var(--text-muted); font-size: 11px; }
  .rbac-context-bar dd { margin: 0; font-size: 12px; font-weight: 650; }

  .workspace-tab {
    border: 1px solid #334155;
    background: rgba(15, 23, 42, 0.88);
    color: #cbd5e1;
    border-radius: 999px;
    padding: 10px 16px;
    cursor: pointer;
  }

  .workspace-tab.active {
    background: rgba(242, 213, 139, 0.18);
    border-color: #f2d58b;
    color: #fef3c7;
  }

  .error-banner {
    max-width: 1320px;
    margin: 0 auto 20px;
    padding: 14px 16px;
    border-radius: 5px;
    border: 1px solid rgba(248, 113, 113, 0.35);
    background: rgba(127, 29, 29, 0.22);
    color: #fecaca;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
  }

  .error-banner > div { display: grid; gap: 4px; }
  .error-banner button { display: inline-flex; min-height: 34px; align-items: center; gap: 6px; padding: 0 10px; color: var(--danger); background: var(--bg-surface); border: 1px solid var(--danger-border); border-radius: 4px; cursor: pointer; }

  .rbac-loading { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); max-width: 1320px; gap: 8px; margin: 0 auto 14px; }
  .rbac-loading > div { display: grid; gap: 8px; min-height: 92px; padding: 14px; background: var(--bg-surface); border: 1px solid var(--border-subtle); }
  .rbac-loading i, .rbac-loading span { display: block; height: 10px; background: var(--bg-muted); animation: rbac-pulse 1.4s ease-in-out infinite; }
  .rbac-loading i { width: 46%; height: 18px; }.rbac-loading span:nth-child(2) { width: 72%; }.rbac-loading span:nth-child(3) { width: 58%; }
  @keyframes rbac-pulse { 50% { opacity: .48; } }

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
    grid-template-columns: minmax(320px, 420px) minmax(0, 1fr);
    gap: 16px;
  }

  .rbac-layout-single { grid-template-columns: minmax(0, 1fr); }

  .panel {
    padding: 18px;
  }

  .workspace-main,
  .workspace-single {
    min-width: 0;
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

  .inline-actions {
    display: flex;
    gap: 10px;
    flex-wrap: wrap;
  }

  .assignment-heading {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 10px;
  }

  .assignment-heading > div:first-child { display: grid; gap: 3px; }
  .unsaved-state { color: var(--warning); font-size: 11px; }
  .saved-state { color: var(--text-muted); font-size: 11px; }
  .icon-btn {
    display: grid;
    width: 30px;
    height: 30px;
    place-items: center;
    padding: 0;
    color: var(--text-muted);
    background: transparent;
    border: 0;
    border-radius: 4px;
    cursor: pointer;
  }
  .icon-btn:hover { color: var(--danger); background: var(--danger-soft); }
  button:disabled { cursor: not-allowed; opacity: 0.45; }

  .form-grid {
    display: grid;
    gap: 10px;
  }

  .compact-grid {
    grid-template-columns: minmax(0, 1fr) 180px;
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

  .sub-panel-header {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    align-items: flex-start;
    margin-bottom: 10px;
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

  .toolbar-panel {
    margin-bottom: 14px;
  }

  .pagination-bar {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    align-items: center;
    margin-top: 14px;
    flex-wrap: wrap;
  }

  .pagination-actions {
    display: flex;
    gap: 10px;
  }

  .empty-state {
    padding: 20px 14px;
    border: 1px dashed #334155;
    border-radius: 12px;
    color: #94a3b8;
    text-align: center;
  }

  .danger-panel {
    border-color: rgba(248, 113, 113, 0.35);
    background: rgba(127, 29, 29, 0.16);
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
    .rbac-layout,
    .rbac-loading {
      grid-template-columns: 1fr;
    }

    .compact-grid {
      grid-template-columns: 1fr;
    }

    .error-banner { align-items: flex-start; flex-direction: column; }
  }
}
</style>
