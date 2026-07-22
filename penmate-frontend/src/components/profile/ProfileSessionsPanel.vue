<script setup lang="ts">
import { computed } from 'vue'
import { DesktopOutlined, LogoutOutlined, MobileOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import type { AuthSessionItem } from '@/entities/auth/model'

const props = withDefaults(defineProps<{
  sessions?: AuthSessionItem[]
  loading?: boolean
  error?: string
  revokingSessionId?: string
  revokingOtherSessions?: boolean
  actionError?: string
}>(), {
  sessions: () => [],
  loading: false,
  error: '',
  revokingSessionId: '',
  revokingOtherSessions: false,
  actionError: '',
})

const emit = defineEmits<{
  retry: []
  revoke: [sessionId: string]
  revokeOthers: []
}>()

const hasOtherSessions = computed(() => props.sessions.some((session) => !session.current))

const formatTime = (value?: string | null) => {
  if (!value) return '未知时间'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '未知时间' : date.toLocaleString('zh-CN', { hour12: false })
}
</script>

<template>
  <div class="settings-surface sessions-panel">
    <header>
      <div><h2>已登录设备</h2><p>登录状态最长保留 7 天，使用期间会自动顺延。</p></div>
      <button
        v-if="hasOtherSessions"
        type="button"
        class="revoke-others-button"
        data-testid="profile-sessions-revoke-others"
        :disabled="revokingOtherSessions || Boolean(revokingSessionId)"
        @click="emit('revokeOthers')"
      ><LogoutOutlined />{{ revokingOtherSessions ? '正在退出' : '退出其他设备' }}</button>
    </header>
    <div v-if="loading" class="session-state">正在加载设备</div>
    <div v-else-if="error" class="session-state error" role="alert">
      <span>{{ error }}</span>
      <button type="button" @click="emit('retry')"><ReloadOutlined />重试</button>
    </div>
    <div v-else-if="!sessions.length" class="session-state">没有可用的设备会话</div>
    <div v-else class="session-list">
      <p v-if="actionError" class="session-action-error" role="alert">{{ actionError }}</p>
      <div v-for="session in sessions" :key="session.sessionId" class="session-row">
        <component :is="session.deviceName.toLowerCase().includes('mobile') ? MobileOutlined : DesktopOutlined" class="device-icon" />
        <div class="session-main">
          <div class="session-title">
            <strong>{{ session.browserName }} · {{ session.operatingSystem }}</strong>
            <span v-if="session.current">当前设备</span>
          </div>
          <small>{{ session.ipAddress || '未知 IP' }} · 最近活动 {{ formatTime(session.lastSeenAt) }}</small>
        </div>
        <button
          v-if="!session.current"
          type="button"
          class="revoke-button"
          :disabled="revokingOtherSessions || revokingSessionId === session.sessionId"
          @click="emit('revoke', session.sessionId)"
        >{{ revokingSessionId === session.sessionId ? '正在退出' : '退出设备' }}</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.sessions-panel > header { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 18px 20px; border-bottom: 1px solid var(--border-subtle); }
.sessions-panel h2 { margin: 0 0 4px; font-size: 15px; }
.sessions-panel p { margin: 0; color: var(--text-muted); font-size: 12px; }
.session-state { display: flex; min-height: 96px; align-items: center; justify-content: center; gap: 10px; color: var(--text-muted); font-size: 12px; }
.session-state.error { color: var(--danger); }
.session-state button { display: inline-flex; align-items: center; gap: 5px; color: var(--info); background: transparent; border: 0; cursor: pointer; }
.revoke-others-button { display: inline-flex; min-height: 34px; flex: 0 0 auto; align-items: center; gap: 6px; padding: 0 10px; color: var(--danger); background: var(--bg-surface); border: 1px solid var(--danger-border); border-radius: 4px; cursor: pointer; }
.revoke-others-button:hover:not(:disabled) { background: var(--danger-soft); }
.revoke-others-button:disabled { cursor: wait; opacity: .58; }
.session-list { display: grid; }
.session-action-error { margin: 10px 20px 0; padding: 9px 10px; color: var(--danger); background: var(--danger-soft); border: 1px solid var(--danger-border); font-size: 12px; }
.session-row { display: grid; grid-template-columns: 34px minmax(0, 1fr) auto; align-items: center; gap: 10px; min-height: 72px; padding: 10px 20px; border-bottom: 1px solid var(--border-subtle); }
.session-row:last-child { border-bottom: 0; }
.device-icon { color: var(--text-muted); font-size: 20px; }
.session-main { display: grid; gap: 4px; min-width: 0; }
.session-title { display: flex; align-items: center; gap: 8px; }
.session-title strong { overflow: hidden; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.session-title span { flex: 0 0 auto; padding: 2px 5px; color: var(--accent); background: var(--accent-soft); border-radius: 3px; font-size: 10px; }
.session-main small { overflow: hidden; color: var(--text-muted); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.revoke-button { min-height: 32px; padding: 0 9px; color: var(--danger); background: transparent; border: 1px solid var(--danger-border); border-radius: 4px; cursor: pointer; }
.revoke-button:disabled { cursor: wait; opacity: .6; }
@media (max-width: 560px) { .sessions-panel > header { align-items: flex-start; flex-direction: column; } .session-row { grid-template-columns: 28px minmax(0, 1fr); } .revoke-button { grid-column: 2; justify-self: start; } }
</style>
