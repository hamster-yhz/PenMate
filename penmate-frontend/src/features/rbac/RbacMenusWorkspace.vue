<template>
  <section class="panel panel-stack workspace-single" data-testid="rbac-menu-workspace">
    <div class="panel-header">
      <div>
        <h2>菜单可见性</h2>
        <span class="muted">当前选中用户：{{ activeUser?.displayName || '未选择' }}</span>
      </div>
    </div>

    <div class="sub-panel">
      <h3>系统菜单</h3>
      <ul class="menu-list">
        <li v-for="menu in menus" :key="getMenuBusinessId(menu) ?? `menu-missing-${menu.path}`">
          <strong>{{ menu.title }}</strong>
          <span>{{ menu.path }}</span>
        </li>
      </ul>
    </div>

    <div class="sub-panel">
      <h3>用户可见菜单</h3>
      <ul class="menu-list">
        <li v-for="menu in profileMenus" :key="getMenuBusinessId(menu) ?? `profile-menu-missing-${menu.path}`">
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
const { activeUser, menus, profileMenus, getMenuBusinessId } = controller
</script>
