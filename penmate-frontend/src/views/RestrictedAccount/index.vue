<template>
  <div class="restricted-page">
    <AppTopbar context-title="受限账号" />
    <main>
      <section class="restricted-panel">
        <div class="restricted-icon"><LockOutlined /></div>
        <div class="restricted-copy">
          <p>账号已登录</p>
          <h1>暂未获得工作区权限</h1>
          <span>管理员尚未为当前账号分配可进入业务工作区的角色。账号安全与登录会话仍可正常管理。</span>
        </div>
        <div class="restricted-actions">
          <button type="button" class="primary-action" @click="router.push('/profile?section=security')">
            <SafetyCertificateOutlined />账号安全
          </button>
          <button type="button" @click="router.push('/profile?section=data')">
            <UserOutlined />账户管理
          </button>
        </div>
        <footer>
          <span>需要使用 PenMate 工作区时，请联系管理员分配普通用户或其他业务角色。</span>
          <button type="button" @click="logout"><LogoutOutlined />退出登录</button>
        </footer>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { LockOutlined, LogoutOutlined, SafetyCertificateOutlined, UserOutlined } from '@ant-design/icons-vue'
import AppTopbar from '@/components/app/AppTopbar.vue'
import { logoutCurrentSession } from '@/composables/auth/useAuthSession'

const router = useRouter()
const logout = async () => {
  await logoutCurrentSession()
  await router.replace('/login')
}
</script>

<style scoped>
.restricted-page { min-height: 100vh; background: var(--bg-canvas); }
main { display: grid; min-height: calc(100vh - var(--app-header-height)); place-items: start center; padding: 72px 20px; }
.restricted-panel { width: min(620px, 100%); overflow: hidden; background: var(--bg-surface); border: 1px solid var(--border-subtle); border-radius: 8px; box-shadow: var(--shadow-sm); }
.restricted-icon { display: grid; width: 54px; height: 54px; margin: 28px 28px 0; place-items: center; color: var(--warning); background: var(--warning-soft); border-radius: 8px; font-size: 24px; }
.restricted-copy { display: grid; gap: 7px; padding: 18px 28px 24px; }
.restricted-copy p, .restricted-copy h1 { margin: 0; letter-spacing: 0; }
.restricted-copy p { color: var(--text-muted); font-size: 11px; }
.restricted-copy h1 { font-size: 22px; }
.restricted-copy span { max-width: 520px; color: var(--text-secondary); font-size: 13px; line-height: 1.7; }
.restricted-actions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; padding: 0 28px 28px; }
.restricted-actions button, footer button { display: inline-flex; min-height: 40px; align-items: center; justify-content: center; gap: 7px; color: var(--text-secondary); background: var(--bg-surface); border: 1px solid var(--border-strong); border-radius: 5px; cursor: pointer; }
.restricted-actions .primary-action { color: var(--text-inverse); background: var(--accent); border-color: var(--accent); }
footer { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 14px 28px; background: var(--bg-subtle); border-top: 1px solid var(--border-subtle); }
footer span { color: var(--text-muted); font-size: 11px; }
footer button { flex: 0 0 auto; min-height: 32px; padding: 0 9px; }
@media (max-width: 560px) { main { padding: 28px 14px; } .restricted-actions { grid-template-columns: 1fr; } footer { align-items: flex-start; flex-direction: column; } }
</style>
