<template>
  <section class="settings-surface security-panel">
    <header>
      <div><h2><SafetyCertificateOutlined />账号安全</h2><p>修改登录凭据后，所有设备都会退出，需要重新登录。</p></div>
    </header>

    <div class="credential-row">
      <MailOutlined class="row-icon" />
      <span><strong>登录邮箱</strong><small>{{ email }}</small></span>
      <button type="button" data-testid="profile-security-email-toggle" :disabled="emailSaving" @click="toggleEmailForm">
        {{ showEmailForm ? '收起' : '更改邮箱' }}
      </button>
    </div>
    <form v-if="showEmailForm" class="credential-form" data-testid="profile-security-email-form" @submit.prevent="handleSaveEmail">
      <label>
        <span>新邮箱地址</span>
        <input v-model="newEmail" type="email" autocomplete="email" data-testid="profile-security-email-input" />
      </label>
      <label>
        <span>当前密码</span>
        <input v-model="emailCurrentPassword" type="password" autocomplete="current-password" data-testid="profile-security-email-password" />
      </label>
      <p v-if="emailError" class="form-error" role="alert">{{ emailError }}</p>
      <footer>
        <button class="primary-button" type="submit" data-testid="profile-security-email-save" :disabled="emailSaving">
          <LoadingOutlined v-if="emailSaving" spin />
          <SaveOutlined v-else />
          {{ emailSaving ? '正在更改' : '确认更改邮箱' }}
        </button>
      </footer>
    </form>

    <div class="credential-row">
      <KeyOutlined class="row-icon" />
      <span><strong>登录密码</strong><small>建议使用至少 8 位且不与其他服务重复的密码</small></span>
      <button type="button" data-testid="profile-security-password-toggle" :disabled="passwordSaving" @click="togglePasswordForm">
        {{ showPasswordForm ? '收起' : '更改密码' }}
      </button>
    </div>
    <form v-if="showPasswordForm" class="credential-form" data-testid="profile-security-password-form" @submit.prevent="handleSavePassword">
      <label>
        <span>当前密码</span>
        <input v-model="passwords.old" type="password" autocomplete="current-password" data-testid="profile-security-current-password" />
      </label>
      <div class="field-grid">
        <label>
          <span>新密码</span>
          <input v-model="passwords.new1" type="password" autocomplete="new-password" data-testid="profile-security-new-password" />
        </label>
        <label>
          <span>确认新密码</span>
          <input v-model="passwords.new2" type="password" autocomplete="new-password" data-testid="profile-security-confirm-password" />
        </label>
      </div>
      <p v-if="passwordError" class="form-error" role="alert">{{ passwordError }}</p>
      <footer>
        <button class="primary-button" type="submit" data-testid="profile-security-password-save" :disabled="passwordSaving">
          <LoadingOutlined v-if="passwordSaving" spin />
          <SaveOutlined v-else />
          {{ passwordSaving ? '正在更改' : '确认更改密码' }}
        </button>
      </footer>
    </form>
  </section>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { KeyOutlined, LoadingOutlined, MailOutlined, SafetyCertificateOutlined, SaveOutlined } from '@ant-design/icons-vue'
import type {
  ProfileActionResult,
  ProfileEmailPayload,
  ProfilePasswordPayload,
} from '@/composables/profile/useProfileSettings'

const props = defineProps<{
  email: string
  saveEmail: (payload: ProfileEmailPayload) => Promise<ProfileActionResult>
  savePassword: (payload: ProfilePasswordPayload) => Promise<ProfileActionResult>
}>()

const emit = defineEmits<{
  credentialChanged: [kind: 'email' | 'password']
}>()

const showEmailForm = ref(false)
const showPasswordForm = ref(false)
const newEmail = ref('')
const emailCurrentPassword = ref('')
const passwords = reactive<ProfilePasswordPayload>({ old: '', new1: '', new2: '' })
const emailError = ref('')
const passwordError = ref('')
const emailSaving = ref(false)
const passwordSaving = ref(false)

const toggleEmailForm = () => {
  if (emailSaving.value) return
  showEmailForm.value = !showEmailForm.value
  emailError.value = ''
}

const togglePasswordForm = () => {
  if (passwordSaving.value) return
  showPasswordForm.value = !showPasswordForm.value
  passwordError.value = ''
}

const handleSaveEmail = async () => {
  if (emailSaving.value) return
  emailSaving.value = true
  emailError.value = ''
  const result = await props.saveEmail({ email: newEmail.value, currentPassword: emailCurrentPassword.value })
  emailSaving.value = false
  if (!result.success) {
    emailError.value = result.error || '修改邮箱失败，请重试'
    return
  }
  emit('credentialChanged', 'email')
}

const handleSavePassword = async () => {
  if (passwordSaving.value) return
  passwordSaving.value = true
  passwordError.value = ''
  const result = await props.savePassword({ ...passwords })
  passwordSaving.value = false
  if (!result.success) {
    passwordError.value = result.error || '修改密码失败，请重试'
    return
  }
  emit('credentialChanged', 'password')
}
</script>

<style scoped>
.security-panel { overflow: hidden; background: var(--bg-surface); border: 1px solid var(--border-subtle); border-radius: 6px; }
.security-panel > header { padding: 18px 20px; border-bottom: 1px solid var(--border-subtle); }
.security-panel h2 { display: flex; align-items: center; gap: 8px; margin: 0 0 4px; font-size: 15px; letter-spacing: 0; }
.security-panel header p { margin: 0; color: var(--text-muted); font-size: 12px; }
.credential-row { display: grid; grid-template-columns: 28px minmax(0, 1fr) auto; align-items: center; gap: 10px; min-height: 74px; padding: 12px 20px; border-bottom: 1px solid var(--border-subtle); }
.row-icon { color: var(--text-muted); font-size: 17px; }
.credential-row > span { display: grid; gap: 4px; min-width: 0; }
.credential-row strong { font-size: 13px; }
.credential-row small { overflow: hidden; color: var(--text-muted); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.credential-row button, .primary-button { display: inline-flex; min-height: 34px; align-items: center; justify-content: center; gap: 7px; padding: 0 11px; border-radius: 4px; cursor: pointer; font-size: 12px; }
.credential-row button { color: var(--text-secondary); background: var(--bg-surface); border: 1px solid var(--border-strong); }
.credential-row button:hover:not(:disabled) { color: var(--accent); background: var(--accent-soft); border-color: var(--accent-border); }
.credential-form { display: grid; gap: 14px; padding: 16px 20px 18px 58px; background: var(--bg-subtle); border-bottom: 1px solid var(--border-subtle); }
.credential-form label { display: grid; gap: 6px; color: var(--text-secondary); font-size: 12px; font-weight: 650; }
.credential-form input { width: 100%; min-height: 38px; padding: 0 10px; color: var(--text-primary); background: var(--bg-surface); border: 1px solid var(--border-strong); border-radius: 4px; outline: 0; font: inherit; font-weight: 400; }
.credential-form input:focus { border-color: var(--accent); box-shadow: 0 0 0 3px var(--focus-ring); }
.field-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.form-error { margin: 0; padding: 9px 10px; color: var(--danger); background: var(--danger-soft); border: 1px solid var(--danger-border); font-size: 12px; }
.credential-form footer { display: flex; justify-content: flex-end; }
.primary-button { color: var(--text-inverse); background: var(--accent); border: 1px solid var(--accent); font-weight: 650; }
button:disabled { cursor: wait; opacity: .58; }
@media (max-width: 560px) { .credential-row { grid-template-columns: 24px minmax(0, 1fr); padding: 12px 16px; } .credential-row button { grid-column: 2; justify-self: start; } .credential-form { padding: 16px; } .field-grid { grid-template-columns: 1fr; } }
</style>
