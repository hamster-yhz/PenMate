<template>
  <div class="settings-section glass-panel">
    <h3 class="section-title">🔐 账号安全</h3>

    <div class="setting-row">
      <div class="sr-info">
        <span class="sr-label">登录邮箱</span>
        <span class="sr-value">{{ email }}</span>
      </div>
      <button
        class="sr-btn"
        type="button"
        data-testid="profile-security-email-toggle"
        @click="showEmailForm = !showEmailForm"
      >
        修改
      </button>
    </div>
    <div v-if="showEmailForm" class="setting-row-expand" data-testid="profile-security-email-form">
      <input v-model="newEmail" class="f-input" placeholder="新邮箱地址" type="email" aria-label="新邮箱地址" />
      <button class="btn-sm" type="button" data-testid="profile-security-email-save" @click="handleSaveEmail">
        保存
      </button>
      <p v-if="emailError" class="form-error">{{ emailError }}</p>
    </div>

    <div class="setting-row">
      <div class="sr-info">
        <span class="sr-label">登录密码</span>
        <span class="sr-value">••••••••</span>
      </div>
      <button
        class="sr-btn"
        type="button"
        data-testid="profile-security-password-toggle"
        @click="showPasswordForm = !showPasswordForm"
      >
        修改
      </button>
    </div>
    <div v-if="showPasswordForm" class="setting-row-expand" data-testid="profile-security-password-form">
      <input v-model="passwords.old" class="f-input" placeholder="当前密码" type="password" aria-label="当前密码" />
      <input v-model="passwords.new1" class="f-input" placeholder="新密码" type="password" aria-label="新密码" />
      <input
        v-model="passwords.new2"
        class="f-input"
        placeholder="确认新密码"
        type="password"
        aria-label="确认新密码"
      />
      <button class="btn-sm" type="button" data-testid="profile-security-password-save" @click="handleSavePassword">
        保存
      </button>
      <p v-if="passwordError" class="form-error">{{ passwordError }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import type { ProfileActionResult, ProfilePasswordPayload } from '@/composables/profile/useProfileSettings'

type SaveEmailHandler = (email: string) => ProfileActionResult | Promise<ProfileActionResult>
type SavePasswordHandler = (payload: ProfilePasswordPayload) => ProfileActionResult | Promise<ProfileActionResult>

const props = defineProps<{
  email: string
  saveEmail?: SaveEmailHandler
  savePassword?: SavePasswordHandler
}>()

const showEmailForm = ref(false)
const showPasswordForm = ref(false)
const newEmail = ref('')
const passwords = reactive<ProfilePasswordPayload>({ old: '', new1: '', new2: '' })
const emailError = ref('')
const passwordError = ref('')

const handleSaveEmail = async () => {
  emailError.value = ''

  const result = props.saveEmail ? await props.saveEmail(newEmail.value) : { success: true as const }

  if (!result.success) {
    emailError.value = result.error ?? '保存邮箱失败'
    return
  }

  newEmail.value = ''
  showEmailForm.value = false
}

const handleSavePassword = async () => {
  passwordError.value = ''

  if (!passwords.old.trim()) {
    passwordError.value = '请输入当前密码'
    return
  }

  const result = props.savePassword ? await props.savePassword({ ...passwords }) : { success: true as const }

  if (!result.success) {
    passwordError.value = result.error ?? '修改密码失败'
    return
  }

  passwords.old = ''
  passwords.new1 = ''
  passwords.new2 = ''
  showPasswordForm.value = false
}
</script>

<style lang="less" scoped>
.settings-section {
  padding: 20px 24px;
  background: rgba(17, 24, 39, 0.5);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
}

.section-title {
  font-family: var(--font-heading);
  font-size: 1rem;
  color: var(--xuan-paper);
  letter-spacing: 0.12em;
  margin-bottom: 16px;
}

.setting-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid rgba(201, 169, 110, 0.06);

  &:last-of-type {
    border-bottom: none;
  }
}

.sr-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sr-label {
  font-size: 0.85rem;
  color: var(--text-primary);
}

.sr-value {
  font-size: 0.75rem;
  color: var(--text-muted);
}

.sr-btn,
.btn-sm {
  padding: 5px 14px;
  font-size: 0.78rem;
  color: var(--amber-gold);
  background: rgba(201, 169, 110, 0.06);
  border: 1px solid rgba(201, 169, 110, 0.15);
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s;

  &:hover {
    background: rgba(201, 169, 110, 0.12);
    border-color: var(--border-gold);
  }
}

.setting-row-expand {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 0 16px;
}

.f-input {
  padding: 8px 12px;
  background: rgba(11, 17, 32, 0.6);
  border: 1px solid var(--border-subtle);
  border-radius: 6px;
  color: var(--text-primary);
  font-size: 0.85rem;
  outline: none;

  &:focus {
    border-color: var(--border-gold);
  }
}

.form-error {
  color: #e8a87c;
  font-size: 0.78rem;
}
</style>
