<template>
  <header class="app-topbar" :class="{ 'has-search': searchable }">
    <div class="topbar-leading">
      <button class="brand-button" type="button" aria-label="进入书架" @click="router.push('/mybooks')">
        <span class="brand-mark" aria-hidden="true">P</span>
        <span class="brand-name">PenMate</span>
      </button>
      <button v-if="backTo" class="back-button" type="button" :aria-label="backLabel" @click="router.push(backTo)">
        <ArrowLeftOutlined aria-hidden="true" />
        <span>{{ backLabel }}</span>
      </button>
      <span v-if="contextTitle" class="context-divider" aria-hidden="true"></span>
      <span v-if="contextTitle" class="context-title">{{ contextTitle }}</span>
    </div>

    <div v-if="searchable" class="topbar-search">
      <SearchOutlined aria-hidden="true" />
      <input
        :value="searchValue"
        type="search"
        :placeholder="searchPlaceholder"
        aria-label="搜索"
        @input="$emit('update:search-value', ($event.target as HTMLInputElement).value)"
      />
    </div>

    <div class="topbar-actions">
      <slot name="actions"></slot>
      <button class="icon-button" type="button" :title="isDark ? '切换到浅色主题' : '切换到深色主题'" @click="toggleTheme">
        <BulbOutlined />
      </button>
      <a-dropdown placement="bottomRight" :trigger="['click']">
        <button class="account-button" type="button" aria-label="打开账户菜单">
          <span class="account-avatar">{{ accountInitial }}</span>
          <span class="account-name">{{ accountName }}</span>
          <DownOutlined class="account-chevron" />
        </button>
        <template #overlay>
          <a-menu class="account-menu" @click="handleMenuClick">
            <a-menu-item key="books"><BookOutlined />书架</a-menu-item>
            <a-menu-item key="settings"><SettingOutlined />个人设置</a-menu-item>
            <a-menu-item v-if="showAdmin" key="admin"><SafetyCertificateOutlined />管理员工作台</a-menu-item>
            <a-menu-divider />
            <a-menu-item key="logout" danger><LogoutOutlined />退出登录</a-menu-item>
          </a-menu>
        </template>
      </a-dropdown>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { Dropdown as ADropdown, Menu as AMenu, MenuDivider as AMenuDivider, MenuItem as AMenuItem } from 'ant-design-vue'
import {
  BookOutlined,
  ArrowLeftOutlined,
  BulbOutlined,
  DownOutlined,
  LogoutOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  SettingOutlined,
} from '@ant-design/icons-vue'
import { getSession } from '@/stores/session'
import { logoutCurrentSession } from '@/composables/auth/useAuthSession'
import { useAppearance } from '@/composables/useAppearance'

withDefaults(defineProps<{
  contextTitle?: string
  searchable?: boolean
  searchValue?: string
  searchPlaceholder?: string
  showAdmin?: boolean
  backTo?: string
  backLabel?: string
}>(), {
  contextTitle: '',
  searchable: false,
  searchValue: '',
  searchPlaceholder: '',
  showAdmin: false,
  backTo: '',
  backLabel: '返回',
})

defineEmits<{
  (event: 'update:search-value', value: string): void
}>()

const router = useRouter()
const session = getSession()
const { isDark, toggleTheme } = useAppearance()
const accountName = computed(() => session.userName || session.userEmail || '账户')
const accountInitial = computed(() => accountName.value.trim().charAt(0).toUpperCase() || 'P')

const handleMenuClick = async ({ key }: { key: string | number }) => {
  if (key === 'books') await router.push('/mybooks')
  if (key === 'settings') await router.push('/profile')
  if (key === 'admin') await router.push('/admin')
  if (key === 'logout') {
    await logoutCurrentSession()
    await router.replace('/login')
  }
}
</script>

<style scoped>
.app-topbar {
  position: sticky;
  top: 0;
  z-index: 100;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  min-height: var(--app-header-height);
  padding: 0 24px;
  background: color-mix(in srgb, var(--bg-surface) 94%, transparent);
  border-bottom: 1px solid var(--border-subtle);
  backdrop-filter: blur(12px);
}

.app-topbar.has-search {
  grid-template-columns: minmax(220px, 1fr) minmax(240px, 520px) minmax(220px, 1fr);
}

.topbar-leading,
.topbar-actions,
.brand-button,
.back-button,
.account-button {
  display: flex;
  align-items: center;
}

.topbar-leading {
  min-width: 0;
  gap: 12px;
}

.brand-button,
.back-button,
.account-button,
.icon-button {
  color: var(--text-primary);
  background: transparent;
  border: 0;
  cursor: pointer;
}

.back-button {
  gap: 6px;
  min-height: 32px;
  padding: 0 8px;
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  font-size: 13px;
}

.back-button:hover {
  color: var(--accent);
  background: var(--accent-soft);
}

.brand-button {
  gap: 9px;
  padding: 4px 0;
}

.brand-mark,
.account-avatar {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  color: var(--text-inverse);
  background: var(--accent);
}

.brand-mark {
  width: 30px;
  height: 30px;
  border-radius: var(--radius-md);
  font-weight: 750;
}

.brand-name {
  font-size: 16px;
  font-weight: 700;
}

.context-divider {
  width: 1px;
  height: 20px;
  background: var(--border-subtle);
}

.context-title {
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.topbar-search {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  padding: 0 11px;
  color: var(--text-muted);
  background: var(--bg-subtle);
  border: 1px solid transparent;
  border-radius: var(--radius-md);
}

.topbar-search:focus-within {
  background: var(--bg-surface);
  border-color: var(--accent-border);
  box-shadow: 0 0 0 3px var(--focus-ring);
}

.topbar-search input {
  width: 100%;
  min-width: 0;
  color: var(--text-primary);
  background: transparent;
  border: 0;
  outline: 0;
}

.topbar-actions {
  justify-content: flex-end;
  min-width: 0;
  gap: 8px;
}

.icon-button {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  border-radius: var(--radius-md);
}

.icon-button:hover,
.account-button:hover {
  background: var(--bg-subtle);
}

.account-button {
  min-width: 0;
  gap: 8px;
  padding: 4px 8px;
  border-radius: var(--radius-md);
}

.account-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  font-size: 12px;
  font-weight: 700;
}

.account-name {
  max-width: 130px;
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-chevron {
  color: var(--text-muted);
  font-size: 10px;
}

@media (max-width: 820px) {
  .app-topbar {
    grid-template-columns: 1fr auto;
    padding: 0 12px;
  }

  .app-topbar.has-search {
    grid-template-columns: 1fr auto;
  }

  .topbar-search {
    display: none;
  }

  .back-button {
    width: 32px;
    justify-content: center;
    padding: 0;
  }

  .context-divider,
  .context-title,
  .back-button span,
  .account-name,
  .account-chevron {
    display: none;
  }
}
</style>
