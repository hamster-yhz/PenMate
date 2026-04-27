<template>
  <div class="login-page">
    <div class="login-bg">
      <img :src="loginBg" alt="" class="bg-img" />
      <div class="bg-overlay"></div>
    </div>

    <div class="particles" aria-hidden="true">
      <span v-for="n in 15" :key="n" class="p-dot" :style="pStyle(n)"></span>
    </div>

    <AuthCardShell>
      <AuthModeTabs v-model="mode" />

      <LoginForm
        v-if="mode === 'login'"
        v-model:username="loginForm.username"
        v-model:password="loginForm.password"
        v-model:remember="loginForm.remember"
        :loading="isLoading"
        @submit="handleLoginSubmit"
      />

      <RegisterForm
        v-else
        v-model:username="registerForm.username"
        v-model:email="registerForm.email"
        v-model:password="registerForm.password"
        v-model:confirmPassword="registerForm.confirmPassword"
        :loading="isLoading"
        @submit="handleRegisterSubmit"
      />

      <div class="login-footer">
        <div class="divider-line">
          <span>或</span>
        </div>
        <p class="footer-hint">
          {{ mode === 'login' ? '还没有账号？' : '已有账号？' }}
          <a href="#" @click.prevent="toggleMode">
            {{ mode === 'login' ? '立即注册' : '返回登录' }}
          </a>
        </p>
      </div>
    </AuthCardShell>

    <a class="back-home" @click.prevent="router.push('/')">
      <span>← 返回首页</span>
    </a>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import loginBg from '@/assets/images/login-bg.png'
import AuthCardShell from '@/components/auth/AuthCardShell.vue'
import AuthModeTabs from '@/components/auth/AuthModeTabs.vue'
import LoginForm, { type LoginFormSubmitPayload } from '@/components/auth/LoginForm.vue'
import RegisterForm from '@/components/auth/RegisterForm.vue'
import { useLoginSubmit } from '@/composables/auth/useLoginSubmit'

const router = useRouter()
const mode = ref<'login' | 'register'>('login')
const { isLoading, submitLogin } = useLoginSubmit()

const loginForm = reactive({
  username: '',
  password: '',
  remember: false,
})

const registerForm = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
})

const handleLoginSubmit = async (payload: LoginFormSubmitPayload) => {
  const success = await submitLogin(payload)
  if (success) {
    router.push('/mybooks')
  }
}

const handleRegisterSubmit = () => {
  message.info('当前版本暂未接入注册接口，请先使用已有账号登录')
}

const toggleMode = () => {
  mode.value = mode.value === 'login' ? 'register' : 'login'
}

const pStyle = (_n: number) => {
  const size = Math.random() * 3 + 1
  const left = Math.random() * 100
  const dur = Math.random() * 12 + 12
  const delay = Math.random() * 15
  return {
    width: `${size}px`,
    height: `${size}px`,
    left: `${left}%`,
    bottom: '-5px',
    animationDuration: `${dur}s`,
    animationDelay: `${delay}s`,
    opacity: Math.random() * 0.4 + 0.1,
  }
}
</script>

<style lang="less" scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.login-bg {
  position: absolute;
  inset: 0;
  z-index: 0;
}

.bg-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  filter: brightness(0.35) saturate(0.6);
}

.bg-overlay {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse at 50% 40%, rgba(201, 169, 110, 0.05) 0%, transparent 55%),
    linear-gradient(180deg, rgba(11, 17, 32, 0.4) 0%, rgba(11, 17, 32, 0.7) 100%);
}

.particles {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 1;
}

.p-dot {
  position: absolute;
  border-radius: 50%;
  background: radial-gradient(circle, var(--amber-gold), transparent);
  animation: particleDrift linear infinite;
}

.login-card {
  position: relative;
  z-index: 10;
  width: 440px;
  max-width: 92vw;
  padding: 40px 36px 32px;
  background: rgba(17, 24, 39, 0.75);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border: 1px solid rgba(201, 169, 110, 0.15);
  border-radius: 16px;
  box-shadow: 0 8px 48px rgba(0, 0, 0, 0.5), 0 0 60px rgba(201, 169, 110, 0.06);
}

.card-glow-top {
  position: absolute;
  top: 0;
  left: 10%;
  right: 10%;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--amber-gold), transparent);
  opacity: 0.5;
}

.login-header {
  text-align: center;
  margin-bottom: 28px;
}

.login-logo {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  object-fit: cover;
  margin-bottom: 12px;
  filter: drop-shadow(0 0 16px rgba(201, 169, 110, 0.3));
  animation: floatSlow 5s ease-in-out infinite;
}

.login-title {
  font-family: var(--font-heading);
  font-size: 2rem;
  color: var(--xuan-paper);
  letter-spacing: 0.4em;
  margin-bottom: 4px;
}

.login-subtitle {
  font-size: 0.85rem;
  color: var(--text-muted);
  letter-spacing: 0.15em;
}

.tab-bar {
  position: relative;
  display: flex;
  margin-bottom: 28px;
  border-bottom: 1px solid var(--border-subtle);
}

.tab-btn {
  flex: 1;
  padding: 10px 0;
  background: none;
  border: none;
  font-family: var(--font-heading);
  font-size: 1.05rem;
  color: var(--text-muted);
  letter-spacing: 0.2em;
  cursor: pointer;
  transition: color 0.3s;

  &.active {
    color: var(--amber-gold);
  }
}

.tab-indicator {
  position: absolute;
  bottom: -1px;
  width: 50%;
  height: 2px;
  background: var(--amber-gold);
  transition: left 0.35s var(--ease-silk);
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.85rem;
  color: var(--text-secondary);
  letter-spacing: 0.1em;
}

.label-icon {
  width: 16px;
  height: 16px;
  border-radius: 3px;
  object-fit: cover;
}

.label-icon-text {
  font-size: 0.85rem;
}

.form-input {
  padding: 12px 16px;
  background: rgba(11, 17, 32, 0.6);
  border: 1px solid var(--border-subtle);
  border-radius: 8px;
  color: var(--text-primary);
  font-family: var(--font-body);
  font-size: 0.95rem;
  outline: none;
  transition: all 0.3s var(--ease-silk);

  &::placeholder {
    color: var(--text-muted);
  }

  &:focus {
    border-color: var(--border-gold);
    box-shadow: 0 0 0 3px rgba(201, 169, 110, 0.08), var(--shadow-gold);
  }
}

.form-extra {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.82rem;
}

.remember-me {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-secondary);
  cursor: pointer;

  input[type='checkbox'] {
    accent-color: var(--amber-gold);
  }
}

.forgot-link {
  color: var(--text-muted);
  font-size: 0.82rem;

  &:hover {
    color: var(--amber-gold);
  }
}

.btn-submit {
  width: 100%;
  margin-top: 8px;
  padding: 14px;
  font-family: var(--font-heading);
  font-size: 1.1rem;
  letter-spacing: 0.25em;
  color: var(--amber-gold);
  background: linear-gradient(135deg, rgba(201, 169, 110, 0.15), rgba(201, 169, 110, 0.05));
  border: 1px solid var(--border-gold);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.4s var(--ease-silk);
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(135deg, rgba(201, 169, 110, 0.2), rgba(201, 169, 110, 0.08));
    opacity: 0;
    transition: opacity 0.4s;
  }

  &:hover:not(:disabled) {
    color: var(--xuan-paper);
    border-color: var(--border-glow);
    box-shadow: var(--shadow-gold);
    transform: translateY(-2px);

    &::before {
      opacity: 1;
    }
  }

  &:disabled {
    opacity: 0.7;
    cursor: not-allowed;
  }
}

.loading-text {
  display: flex;
  justify-content: center;
  gap: 6px;
}

.loading-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--amber-gold);
  animation: typingPulse 1.2s ease-in-out infinite;

  &:nth-child(2) {
    animation-delay: 0.2s;
  }

  &:nth-child(3) {
    animation-delay: 0.4s;
  }
}

@keyframes typingPulse {
  0%,
  60%,
  100% {
    opacity: 0.3;
    transform: scale(0.8);
  }

  30% {
    opacity: 1;
    transform: scale(1.3);
  }
}

.login-footer {
  margin-top: 24px;
}

.divider-line {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;

  &::before,
  &::after {
    content: '';
    flex: 1;
    height: 1px;
    background: var(--border-subtle);
  }

  span {
    font-size: 0.78rem;
    color: var(--text-muted);
  }
}

.footer-hint {
  text-align: center;
  font-size: 0.85rem;
  color: var(--text-muted);

  a {
    color: var(--amber-gold);
    margin-left: 4px;

    &:hover {
      color: var(--xuan-paper);
    }
  }
}

.back-home {
  position: fixed;
  top: 28px;
  left: 32px;
  z-index: 20;
  padding: 8px 16px;
  font-family: var(--font-heading);
  font-size: 0.9rem;
  color: var(--text-muted);
  letter-spacing: 0.1em;
  background: rgba(11, 17, 32, 0.5);
  backdrop-filter: blur(8px);
  border: 1px solid var(--border-subtle);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.3s;

  &:hover {
    color: var(--amber-gold);
    border-color: var(--border-gold);
  }
}

@media (max-width: 480px) {
  .login-card {
    padding: 28px 20px 24px;
  }
}
</style>
