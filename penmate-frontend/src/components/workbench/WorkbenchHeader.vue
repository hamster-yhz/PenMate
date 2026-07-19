<template>
  <header class="wb-header">
    <div class="header-left">
      <button type="button" class="header-logo-button" aria-label="返回首页" @click="emit('go-home')">
        <img :src="logoImg" alt="" class="header-logo" />
      </button>
      <span class="header-brand">笔友</span>
      <div class="header-divider"></div>
      <span class="novel-title" contenteditable="true" @blur="emit('update-title', $event)">{{ novelTitle }}</span>
    </div>
    <div class="header-center">
      <div class="workspace-mode" role="group" aria-label="工作模式">
        <button
          type="button"
          title="写作"
          aria-label="写作"
          :class="{ active: workbenchMode === 'writing' }"
          @click="emit('update:workbench-mode', 'writing')"
        >
          <EditOutlined /> 写作
        </button>
        <button
          type="button"
          title="Story Bible"
          aria-label="Story Bible"
          :class="{ active: workbenchMode === 'story-bible' }"
          @click="emit('update:workbench-mode', 'story-bible')"
        >
          <BookOutlined /> Story Bible
        </button>
      </div>
      <span v-if="workbenchMode === 'writing'" class="word-count">
        <span class="wc-num">{{ wordCount }}</span> 字
      </span>
      <span v-if="saveHint" class="save-hint">{{ saveHint }}</span>
    </div>
    <div class="header-right">
      <button class="hdr-btn" title="文风设置" @click="emit('open-style-manager')">
        <img :src="iconStyle" alt="" class="hdr-icon" />
        <span>文风</span>
      </button>
      <button class="hdr-btn" title="插件工坊" @click="emit('open-plugin-workshop')">
        <img :src="iconPlugin" alt="" class="hdr-icon" />
        <span>插件</span>
      </button>
      <button class="hdr-btn" title="模型设置" @click="emit('open-model-settings')">
        <span class="hdr-emoji">🔑</span>
        <span>模型</span>
      </button>
      <div class="header-divider"></div>
      <div class="user-dropdown-wrap">
        <button
          type="button"
          class="user-avatar"
          aria-label="打开用户菜单"
          :aria-expanded="userMenuOpen"
          @click="emit('toggle-user-menu')"
        >
          <span>{{ username.charAt(0) }}</span>
        </button>
        <div v-if="userMenuOpen" class="user-dropdown">
          <div class="ud-header">
            <span class="ud-name">{{ username }}</span>
            <span class="ud-email">{{ userEmail }}</span>
          </div>
          <div class="ud-sep"></div>
          <button class="ud-item" @click="emit('go-profile')">👤 个人中心</button>
          <button class="ud-item" @click="emit('go-mybooks')">📚 我的书架</button>
          <template v-if="canAccessRbacAdmin">
            <div class="ud-sep"></div>
            <button class="ud-item" @click="emit('go-rbac-admin')">🛡️ RBAC 管理</button>
          </template>
          <button class="ud-item danger" @click="emit('logout')">🚪 退出登录</button>
        </div>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { BookOutlined, EditOutlined } from '@ant-design/icons-vue'
import logoImg from '@/assets/images/logo.webp'
import iconStyle from '@/assets/images/icon-style.webp'
import iconPlugin from '@/assets/images/feature-plugin.webp'

withDefaults(
  defineProps<{
    novelTitle: string
    wordCount?: number
    saveHint?: string
    username: string
    userEmail: string
    userMenuOpen: boolean
    canAccessRbacAdmin: boolean
    workbenchMode?: 'writing' | 'story-bible'
  }>(),
  {
    wordCount: 0,
    saveHint: '',
    workbenchMode: 'writing',
  },
)

const emit = defineEmits<{
  (event: 'go-home'): void
  (event: 'update-title', payload: Event): void
  (event: 'open-style-manager'): void
  (event: 'open-plugin-workshop'): void
  (event: 'open-model-settings'): void
  (event: 'toggle-user-menu'): void
  (event: 'close-user-menu'): void
  (event: 'go-profile'): void
  (event: 'go-mybooks'): void
  (event: 'go-rbac-admin'): void
  (event: 'logout'): void
  (event: 'update:workbench-mode', payload: 'writing' | 'story-bible'): void
}>()
</script>

<style lang="less" scoped>
.wb-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 48px;
  padding: 0 16px;
  background: rgba(11, 17, 32, 0.95);
  border-bottom: 1px solid var(--border-subtle);
  flex-shrink: 0;
  z-index: 50;
}

.header-left,
.header-center,
.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-logo-button {
  display: inline-flex;
  padding: 0;
  background: transparent;
  border: 0;
  cursor: pointer;
}

.header-logo {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  object-fit: cover;
  cursor: pointer;
  transition: transform 0.3s;

  &:hover {
    transform: scale(1.1);
  }
}

.header-brand {
  font-family: var(--font-heading);
  font-size: 1rem;
  color: var(--amber-gold);
  letter-spacing: 0.2em;
}

.header-divider {
  width: 1px;
  height: 20px;
  background: var(--border-subtle);
}

.novel-title {
  font-family: var(--font-heading);
  font-size: 0.95rem;
  color: var(--text-primary);
  letter-spacing: 0.1em;
  padding: 2px 8px;
  border-radius: 4px;
  outline: none;
  transition: background 0.3s;

  &:hover,
  &:focus {
    background: rgba(201, 169, 110, 0.06);
  }
}

.word-count {
  font-size: 0.78rem;
  color: var(--text-muted);
  letter-spacing: 0.05em;

  .wc-num {
    color: var(--amber-gold);
    font-weight: 500;
  }
}

.save-hint {
  font-size: 0.72rem;
  color: var(--jade-green);
  animation: fadeInUp 0.3s ease;
}

.workspace-mode {
  display: grid;
  grid-template-columns: 92px 126px;
  height: 32px;
  border: 1px solid var(--border-subtle);
  border-radius: 4px;
  overflow: hidden;

  button {
    min-width: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 5px;
    border: 0;
    border-right: 1px solid var(--border-subtle);
    color: var(--text-secondary);
    background: rgba(17, 24, 39, 0.72);
    cursor: pointer;
  }

  button:last-child {
    border-right: 0;
  }
  button.active {
    color: var(--amber-gold);
    background: rgba(201, 169, 110, 0.12);
  }
}

.hdr-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: none;
  border: 1px solid transparent;
  border-radius: 4px;
  font-size: 0.78rem;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.3s;
  letter-spacing: 0.05em;

  &:hover {
    border-color: var(--border-gold);
    color: var(--amber-gold);
    background: rgba(201, 169, 110, 0.06);
  }

  .hdr-icon {
    width: 18px;
    height: 18px;
    border-radius: 3px;
    object-fit: cover;
  }

  .hdr-emoji {
    font-size: 0.9rem;
  }
}

.user-dropdown-wrap {
  position: relative;
}

.user-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(201, 169, 110, 0.3), rgba(201, 169, 110, 0.1));
  border: 1px solid var(--border-gold);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-heading);
  font-size: 0.85rem;
  color: var(--amber-gold);
  cursor: pointer;
  transition: all 0.3s;

  &:hover {
    box-shadow: 0 0 12px rgba(201, 169, 110, 0.2);
  }
}

.user-dropdown {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 8px;
  width: 200px;
  background: rgba(17, 24, 39, 0.95);
  backdrop-filter: blur(16px);
  border: 1px solid var(--border-gold);
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
  animation: fadeInUp 0.2s ease;
  z-index: 999;
}

.ud-header {
  padding: 12px 14px;
}

.ud-name {
  display: block;
  font-family: var(--font-heading);
  font-size: 0.92rem;
  color: var(--xuan-paper);
  letter-spacing: 0.1em;
}

.ud-email {
  display: block;
  font-size: 0.72rem;
  color: var(--text-muted);
  margin-top: 2px;
}

.ud-sep {
  height: 1px;
  background: var(--border-subtle);
}

.ud-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 14px;
  background: none;
  border: none;
  font-size: 0.82rem;
  color: var(--text-secondary);
  cursor: pointer;
  text-align: left;
  transition: all 0.2s;

  &:hover {
    background: rgba(201, 169, 110, 0.06);
    color: var(--amber-gold);
  }

  &.danger {
    color: #e8a87c;

    &:hover {
      background: rgba(192, 60, 45, 0.08);
    }
  }
}

@media (max-width: 640px) {
  .wb-header {
    position: relative;
    padding: 0 8px;
    gap: 8px;
  }
  .header-left,
  .header-center,
  .header-right {
    flex: 0 0 auto;
    gap: 6px;
  }
  .header-left .header-divider,
  .novel-title,
  .word-count,
  .save-hint,
  .header-right > .hdr-btn,
  .header-right > .header-divider {
    display: none;
  }
  .header-logo {
    width: 26px;
    height: 26px;
  }
  .header-brand {
    flex: 0 0 auto;
    font-size: 0.88rem;
    white-space: nowrap;
  }
  .header-center {
    position: absolute;
    left: 50%;
    transform: translateX(-50%);
  }
  .header-right {
    position: absolute;
    right: 8px;
  }
  .workspace-mode {
    grid-template-columns: 42px 48px;
    width: 90px;
  }
  .workspace-mode button {
    gap: 0;
    font-size: 0;
  }
  .workspace-mode button :deep(.anticon) {
    font-size: 14px;
  }
  .user-avatar {
    width: 28px;
    height: 28px;
  }
}
</style>
