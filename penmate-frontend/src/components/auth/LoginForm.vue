<template>
  <form class="login-form" data-testid="login-form" @submit.prevent="handleSubmit">
    <label class="form-field">
      <span>邮箱</span>
      <span class="input-shell">
        <MailOutlined aria-hidden="true" />
        <input
          :value="username"
          type="email"
          data-testid="login-username"
          placeholder="name@example.com"
          autocomplete="username"
          required
          @input="emit('update:username', ($event.target as HTMLInputElement).value)"
        />
      </span>
    </label>

    <label class="form-field">
      <span>密码</span>
      <span class="input-shell">
        <LockOutlined aria-hidden="true" />
        <input
          :value="password"
          type="password"
          data-testid="login-password"
          placeholder="输入密码"
          autocomplete="current-password"
          required
          @input="emit('update:password', ($event.target as HTMLInputElement).value)"
        />
      </span>
    </label>

    <button class="submit-button" type="submit" data-testid="login-submit" :disabled="loading">
      <LoadingOutlined v-if="loading" spin />
      <span>{{ loading ? '正在登录' : '登录' }}</span>
    </button>
  </form>
</template>

<script setup lang="ts">
import { LoadingOutlined, LockOutlined, MailOutlined } from '@ant-design/icons-vue'

export interface LoginFormSubmitPayload {
  username: string
  password: string
}

const props = defineProps<{
  username: string
  password: string
  loading: boolean
}>()

const emit = defineEmits<{
  'update:username': [value: string]
  'update:password': [value: string]
  submit: [payload: LoginFormSubmitPayload]
}>()

const handleSubmit = () => {
  if (props.loading) return
  emit('submit', { username: props.username, password: props.password })
}
</script>

<style scoped>
.login-form {
  display: grid;
  gap: 18px;
}

.form-field {
  display: grid;
  gap: 7px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.input-shell {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 42px;
  padding: 0 12px;
  color: var(--text-muted);
  background: var(--bg-surface);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-md);
}

.input-shell:focus-within {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--focus-ring);
}

.input-shell input {
  width: 100%;
  min-width: 0;
  color: var(--text-primary);
  background: transparent;
  border: 0;
  outline: 0;
}

.input-shell input::placeholder {
  color: var(--text-muted);
}

.submit-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 42px;
  margin-top: 4px;
  color: var(--text-inverse);
  font-weight: 650;
  background: var(--accent);
  border: 1px solid var(--accent);
  border-radius: var(--radius-md);
  cursor: pointer;
}

.submit-button:hover:not(:disabled) {
  background: var(--accent-hover);
}

.submit-button:disabled {
  cursor: wait;
  opacity: 0.7;
}
</style>
