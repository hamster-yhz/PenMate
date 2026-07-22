<template>
  <main class="login-page">
    <img class="login-background" :src="loginBg" alt="安静的写作桌面" />
    <div class="login-scrim" aria-hidden="true"></div>

    <button class="back-button" type="button" @click="router.push('/')">
      <ArrowLeftOutlined />
      <span>返回首页</span>
    </button>

    <div class="login-panel">
      <AuthCardShell>
        <p v-if="errorMessage" class="login-error" role="alert">{{ errorMessage }}</p>
        <LoginForm
          v-model:username="loginForm.username"
          v-model:password="loginForm.password"
          :loading="isLoading"
          @submit="handleLoginSubmit"
        />
      </AuthCardShell>
    </div>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeftOutlined } from '@ant-design/icons-vue'
import loginBg from '@/assets/images/login-bg.webp'
import AuthCardShell from '@/components/auth/AuthCardShell.vue'
import LoginForm, { type LoginFormSubmitPayload } from '@/components/auth/LoginForm.vue'
import { useLoginSubmit } from '@/composables/auth/useLoginSubmit'

const router = useRouter()
const route = useRoute()
const { isLoading, submitLogin } = useLoginSubmit()
const errorMessage = ref('')
const loginForm = reactive({ username: '', password: '' })

const handleLoginSubmit = async (payload: LoginFormSubmitPayload) => {
  errorMessage.value = ''
  const result = await submitLogin(payload)
  if (!result.success) {
    errorMessage.value = result.error || '登录失败，请检查邮箱和密码'
    return
  }
  const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
    ? route.query.redirect
    : '/mybooks'
  await router.replace(redirect)
}
</script>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  background: #18201c;
}

.login-background,
.login-scrim {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.login-background {
  object-fit: cover;
  object-position: center;
}

.login-scrim {
  background: rgba(12, 17, 14, 0.48);
}

.back-button {
  position: absolute;
  top: 22px;
  left: 24px;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 36px;
  padding: 0 11px;
  color: #ffffff;
  background: rgba(18, 24, 21, 0.56);
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: var(--radius-md);
  cursor: pointer;
}

.login-panel {
  position: relative;
  z-index: 1;
  display: grid;
  min-height: 100vh;
  padding: 72px 7vw 48px;
  place-items: center end;
}

.login-error {
  margin-bottom: 18px;
  padding: 10px 12px;
  color: var(--danger);
  background: var(--danger-soft);
  border: 1px solid color-mix(in srgb, var(--danger) 40%, transparent);
  border-radius: var(--radius-md);
  font-size: 13px;
}

@media (max-width: 720px) {
  .login-panel {
    padding-inline: 20px;
    place-items: center;
  }
}
</style>
