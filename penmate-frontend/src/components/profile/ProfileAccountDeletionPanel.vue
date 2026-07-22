<template>
  <div class="settings-surface account-deletion-panel">
    <header>
      <h2>注销账户</h2>
      <p>提交后立即退出所有设备，账户将在 30 天后永久删除。</p>
    </header>
    <div class="account-delete-row">
      <span>
        <strong>永久删除账户与数据</strong>
        <small>等待期内无法登录；管理员可以恢复。到期后作品和个人模型密钥不可找回。</small>
      </span>
      <button type="button" @click="dialogOpen = true"><DeleteOutlined />注销账户</button>
    </div>
  </div>

  <AModal
    :open="dialogOpen"
    title="确认注销账户"
    :footer="null"
    :mask-closable="false"
    @cancel="close"
  >
    <form class="account-delete-form" @submit.prevent="submit">
      <div class="risk-notice">
        <WarningOutlined />
        <p>账户会立即停用并退出所有设备。30 天后，作品正文、封面、AI 数据和个人模型密钥将被永久删除。</p>
      </div>
      <label>
        <span>当前密码</span>
        <input v-model="password" type="password" autocomplete="current-password" required />
      </label>
      <label class="confirm-check">
        <input v-model="confirmed" type="checkbox" />
        <span>我已了解等待期结束后数据无法恢复</span>
      </label>
      <p v-if="error" class="delete-error" role="alert">{{ error }}</p>
      <div class="dialog-actions">
        <button type="button" :disabled="submitting" @click="close">取消</button>
        <button class="danger" type="submit" :disabled="submitting || !password || !confirmed">
          <LoadingOutlined v-if="submitting" spin />
          <DeleteOutlined v-else />
          确认注销
        </button>
      </div>
    </form>
  </AModal>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { DeleteOutlined, LoadingOutlined, WarningOutlined } from '@ant-design/icons-vue'
import { Modal as AModal } from 'ant-design-vue'

const props = defineProps<{
  deleteAccount: (currentPassword: string) => Promise<{ success: boolean; error?: string }>
}>()

const dialogOpen = ref(false)
const password = ref('')
const confirmed = ref(false)
const submitting = ref(false)
const error = ref('')

const close = () => {
  if (submitting.value) return
  dialogOpen.value = false
  password.value = ''
  confirmed.value = false
  error.value = ''
}

const submit = async () => {
  if (!password.value || !confirmed.value || submitting.value) return
  submitting.value = true
  error.value = ''
  const result = await props.deleteAccount(password.value)
  if (!result.success) {
    error.value = result.error || '注销账户失败'
    submitting.value = false
  }
}
</script>

<style scoped>
.account-delete-row { display: grid; grid-template-columns: 1fr auto; align-items: center; gap: 20px; padding: 16px 20px; }
.account-delete-row > span { display: grid; gap: 4px; }
.account-delete-row strong { font-size: 13px; }
.account-delete-row small { max-width: 560px; color: var(--text-muted); font-size: 11px; line-height: 1.6; }
.account-delete-row button, .dialog-actions button { display: inline-flex; min-height: 36px; align-items: center; justify-content: center; gap: 7px; padding: 0 12px; border-radius: 4px; cursor: pointer; }
.account-delete-row button { color: var(--danger); background: var(--bg-surface); border: 1px solid var(--danger-border); }
.account-delete-form { display: grid; gap: 16px; padding-top: 6px; }
.risk-notice { display: grid; grid-template-columns: 20px 1fr; gap: 9px; padding: 11px 12px; color: var(--danger); background: var(--danger-soft); border: 1px solid var(--danger-border); }
.risk-notice p { margin: 0; color: inherit; font-size: 12px; line-height: 1.65; }
.account-delete-form > label:not(.confirm-check) { display: grid; gap: 7px; color: var(--text-secondary); font-size: 13px; font-weight: 600; }
.account-delete-form input[type='password'] { min-height: 38px; padding: 0 10px; color: var(--text-primary); background: var(--bg-surface); border: 1px solid var(--border-strong); border-radius: 4px; outline: 0; }
.account-delete-form input[type='password']:focus { border-color: var(--accent); box-shadow: 0 0 0 3px var(--focus-ring); }
.confirm-check { display: flex; align-items: flex-start; gap: 8px; color: var(--text-secondary); font-size: 12px; }
.confirm-check input { margin-top: 2px; accent-color: var(--danger); }
.delete-error { margin: 0; color: var(--danger); font-size: 12px; }
.dialog-actions { display: flex; justify-content: flex-end; gap: 8px; }
.dialog-actions button { color: var(--text-secondary); background: var(--bg-surface); border: 1px solid var(--border-strong); }
.dialog-actions button.danger { color: #fff; background: var(--danger); border-color: var(--danger); }
.dialog-actions button:disabled { cursor: not-allowed; opacity: .55; }
@media (max-width: 560px) { .account-delete-row { grid-template-columns: 1fr; } .account-delete-row button { justify-self: start; } }
</style>
