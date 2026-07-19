<template>
  <div class="admin-rbac-page">
    <header class="rbac-header">
      <div>
        <p class="eyebrow">Admin Console</p>
        <h1>RBAC 管理</h1>
        <p class="subtitle">按用户、角色、菜单三个工作区拆分，减少长页面操作负担</p>
      </div>
      <div class="header-actions">
        <button class="ghost-btn" type="button" @click="router.push('/mybooks')">返回书架</button>
      </div>
    </header>

    <section class="summary-grid">
      <article class="summary-card current-admin">
        <span class="summary-label">当前管理员</span>
        <strong data-testid="rbac-active-user-name">{{
          activeUser?.displayName || session.userName || '未识别用户'
        }}</strong>
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

    <section class="workspace-tabs" aria-label="RBAC 工作区切换">
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

    <main class="rbac-layout">
      <RbacUsersWorkspace v-if="activeWorkspace === 'users'" :controller="rbacController" />
      <RbacRolesWorkspace v-else-if="activeWorkspace === 'roles'" :controller="rbacController" />
      <RbacMenusWorkspace v-else :controller="rbacController" />
    </main>
  </div>
</template>

<script setup lang="ts">
import RbacUsersWorkspace from '@/features/rbac/RbacUsersWorkspace.vue'
import RbacRolesWorkspace from '@/features/rbac/RbacRolesWorkspace.vue'
import RbacMenusWorkspace from '@/features/rbac/RbacMenusWorkspace.vue'
import { useRbacConsole } from '@/features/rbac/useRbacConsole'

const rbacController = useRbacConsole()
const { router, session, users, roles, permissions, menus, activeUser, errorMessage, activeWorkspace } = rbacController
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
    grid-template-columns: minmax(320px, 420px) minmax(0, 1fr);
    gap: 16px;
  }

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
    .rbac-layout {
      grid-template-columns: 1fr;
    }

    .compact-grid {
      grid-template-columns: 1fr;
    }
  }
}
</style>
