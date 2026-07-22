<template>
  <section class="settings-surface session-exit">
    <header><h2>当前会话</h2><p>只退出当前浏览器，其他已登录设备不受影响。</p></header>
    <div class="exit-row">
      <span><strong>退出登录</strong><small>未保存到后端的本地编辑内容仍会保留在当前设备</small></span>
      <button type="button" @click="emit('logout')"><LogoutOutlined />退出当前设备</button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { LogoutOutlined } from '@ant-design/icons-vue'

const emit = defineEmits<{
  (event: 'logout'): void
}>()
</script>

<style scoped>
.session-exit { overflow: hidden; background: var(--bg-surface); border: 1px solid var(--border-subtle); border-radius: 6px; }
.session-exit header { padding: 18px 20px; border-bottom: 1px solid var(--border-subtle); }
.session-exit h2 { margin: 0 0 4px; font-size: 15px; letter-spacing: 0; }
.session-exit p { margin: 0; color: var(--text-muted); font-size: 12px; }
.exit-row { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: 18px; padding: 16px 20px; }
.exit-row > span { display: grid; gap: 4px; }
.exit-row strong { font-size: 13px; }
.exit-row small { color: var(--text-muted); font-size: 11px; }
.exit-row button { display: inline-flex; min-height: 36px; align-items: center; gap: 7px; padding: 0 12px; color: var(--danger); background: var(--bg-surface); border: 1px solid var(--danger-border); border-radius: 4px; cursor: pointer; }
.exit-row button:hover { background: var(--danger-soft); }
@media (max-width: 560px) { .exit-row { grid-template-columns: 1fr; } .exit-row button { justify-self: start; } }
</style>
