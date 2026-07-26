<template>
  <header class="wb-header">
    <div class="header-leading">
      <button type="button" class="icon-button brand-button" title="返回书架" @click="$emit('go-mybooks')">
        <span class="brand-mark">P</span>
      </button>
      <span class="header-divider" aria-hidden="true"></span>
      <button type="button" class="project-title" title="打开作品设置" @click="$emit('open-project-settings')">
        <span>{{ novelTitle }}</span>
        <SettingOutlined />
      </button>
      <div class="panel-restore-actions" aria-label="恢复工作台面板">
        <button
          v-if="workbenchMode === 'writing' && directoryCollapsed"
          type="button"
          class="icon-button panel-restore"
          title="展开作品目录"
          aria-label="展开作品目录"
          @click="$emit('restore-directory')"
        >
          <UnorderedListOutlined aria-hidden="true" />
        </button>
        <button
          v-if="aiCollapsed"
          type="button"
          class="icon-button panel-restore"
          title="展开 AI 协作"
          aria-label="展开 AI 协作"
          @click="$emit('restore-ai')"
        >
          <MessageOutlined aria-hidden="true" />
        </button>
      </div>
    </div>

    <div class="workspace-mode" role="group" aria-label="工作模式">
      <button type="button" aria-label="写作" title="写作" :class="{ active: workbenchMode === 'writing' }" @click="$emit('update:workbench-mode', 'writing')">
        <EditOutlined aria-hidden="true" /><span class="mode-label">写作</span>
      </button>
      <button type="button" aria-label="Story Bible" title="Story Bible" :class="{ active: workbenchMode === 'story-bible' }" @click="$emit('update:workbench-mode', 'story-bible')">
        <BookOutlined aria-hidden="true" /><span class="mode-label">Story Bible</span>
      </button>
    </div>

    <div class="header-actions">
      <span v-if="saveHint" class="save-hint" role="status">{{ saveHint }}</span>
      <a-dropdown class="layout-menu" placement="bottomRight" :trigger="['click']">
        <button type="button" class="layout-button" aria-label="选择工作台布局" title="工作台布局">
          <LayoutOutlined aria-hidden="true" />
        </button>
        <template #overlay>
          <a-menu selectable :selected-keys="[layoutPreset]" @click="handleLayoutMenu">
            <a-menu-item key="balanced">均衡</a-menu-item>
            <a-menu-item key="focus">专注写作</a-menu-item>
            <a-menu-item key="ai">AI 协作</a-menu-item>
          </a-menu>
        </template>
      </a-dropdown>
      <ThemeToggleButton class="header-theme-toggle" />
      <a-dropdown placement="bottomRight" :trigger="['click']">
        <button type="button" class="user-button" aria-label="打开账户菜单">
          <span>{{ username.charAt(0).toUpperCase() }}</span>
          <DownOutlined />
        </button>
        <template #overlay>
          <a-menu @click="handleUserMenu">
            <a-menu-item key="profile"><UserOutlined />个人设置</a-menu-item>
            <a-menu-item key="books"><BookOutlined />书架</a-menu-item>
            <a-menu-item v-if="canAccessRbacAdmin" key="admin"><SafetyCertificateOutlined />管理员工作台</a-menu-item>
            <a-menu-divider />
            <a-menu-item key="logout" danger><LogoutOutlined />退出登录</a-menu-item>
          </a-menu>
        </template>
      </a-dropdown>
    </div>
  </header>
</template>

<script setup lang="ts">
import { Dropdown as ADropdown, Menu as AMenu, MenuDivider as AMenuDivider, MenuItem as AMenuItem } from 'ant-design-vue'
import {
  BookOutlined,
  DownOutlined,
  EditOutlined,
  LogoutOutlined,
  LayoutOutlined,
  MessageOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  UnorderedListOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import type { WorkbenchLayoutPreset } from '@/features/workbench/workbenchLayout'
import ThemeToggleButton from '@/components/app/ThemeToggleButton.vue'

withDefaults(defineProps<{
  novelTitle: string
  saveHint?: string
  username: string
  canAccessRbacAdmin: boolean
  workbenchMode?: 'writing' | 'story-bible'
  layoutPreset?: WorkbenchLayoutPreset
  directoryCollapsed?: boolean
  aiCollapsed?: boolean
}>(), {
  saveHint: '', workbenchMode: 'writing', layoutPreset: 'balanced', directoryCollapsed: false, aiCollapsed: false,
})

const emit = defineEmits<{
  'go-mybooks': []
  'go-profile': []
  'go-rbac-admin': []
  logout: []
  'open-project-settings': []
  'update:workbench-mode': ['writing' | 'story-bible']
  'update:layout-preset': [WorkbenchLayoutPreset]
  'restore-directory': []
  'restore-ai': []
}>()

const handleLayoutMenu = ({ key }: { key: string | number }) => {
  if (key === 'balanced' || key === 'focus' || key === 'ai') emit('update:layout-preset', key)
}

const handleUserMenu = ({ key }: { key: string | number }) => {
  if (key === 'profile') emit('go-profile')
  if (key === 'books') emit('go-mybooks')
  if (key === 'admin') emit('go-rbac-admin')
  if (key === 'logout') emit('logout')
}
</script>

<style scoped>
.wb-header {
  position: relative;
  z-index: 250;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  flex: 0 0 auto;
  height: 48px;
  padding: 0 12px;
  background: var(--bg-surface);
  border-bottom: 1px solid var(--border-subtle);
}
.header-leading, .header-actions, .workspace-mode, .project-title, .user-button, .panel-restore-actions { display: flex; align-items: center; }
.header-leading { min-width: 0; gap: 8px; }
.brand-button, .project-title, .user-button { color: var(--text-primary); background: transparent; border: 0; cursor: pointer; }
.brand-mark { display: grid; width: 28px; height: 28px; place-items: center; color: var(--text-inverse); background: var(--accent); border-radius: var(--radius-md); font-weight: 750; }
.header-divider { width: 1px; height: 20px; background: var(--border-subtle); }
.project-title { min-width: 0; gap: 6px; padding: 5px 7px; border-radius: var(--radius-md); }
.project-title:hover { background: var(--bg-subtle); }
.project-title span { max-width: 260px; overflow: hidden; font-size: 13px; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.project-title :deep(.anticon) { color: var(--text-muted); font-size: 12px; }
.panel-restore-actions { flex: 0 0 auto; gap: 2px; }
.panel-restore { display: grid; width: 28px; height: 28px; place-items: center; padding: 0; color: var(--text-secondary); background: transparent; border: 1px solid transparent; border-radius: var(--radius-md); cursor: pointer; }
.panel-restore:hover, .panel-restore:focus-visible { color: var(--accent); background: var(--accent-soft); border-color: var(--accent-border); outline: 0; }
.workspace-mode { overflow: hidden; border: 1px solid var(--border-strong); border-radius: var(--radius-md); }
.workspace-mode button { display: inline-flex; align-items: center; gap: 6px; min-height: 32px; padding: 0 12px; color: var(--text-secondary); background: var(--bg-surface); border: 0; cursor: pointer; }
.workspace-mode button + button { border-left: 1px solid var(--border-subtle); }
.workspace-mode button.active { color: var(--accent); background: var(--accent-soft); font-weight: 650; }
.header-actions { justify-content: flex-end; min-width: 0; gap: 9px; }
.header-theme-toggle { width: 30px; height: 30px; }
.layout-button { display: grid; width: 30px; height: 30px; place-items: center; padding: 0; color: var(--text-secondary); background: transparent; border: 1px solid transparent; border-radius: var(--radius-md); cursor: pointer; }
.layout-button:hover, .layout-button:focus-visible { color: var(--accent); background: var(--accent-soft); border-color: var(--accent-border); outline: 0; }
.save-hint { color: var(--text-muted); font-size: 11px; }
.user-button { gap: 5px; padding: 3px 6px 3px 3px; border-radius: var(--radius-md); }
.user-button > span:first-child { display: grid; width: 28px; height: 28px; place-items: center; color: var(--text-inverse); background: var(--accent); border-radius: 50%; font-size: 11px; font-weight: 700; }
.user-button :deep(.anticon) { color: var(--text-muted); font-size: 9px; }
.user-button:hover { background: var(--bg-subtle); }
@media (max-width: 700px) {
  .wb-header { grid-template-columns: minmax(0, 1fr) auto auto; gap: 4px; padding: 0 6px; }
  .workspace-mode { position: static; box-shadow: none; }
  .workspace-mode button { width: 34px; justify-content: center; padding: 0; }
  .workspace-mode .mode-label { display: none; }
  .project-title span { max-width: 96px; }
  .header-divider { display: none; }
  .save-hint { display: none; }
  .layout-menu { display: none; }
  .panel-restore-actions { display: none; }
}
</style>
