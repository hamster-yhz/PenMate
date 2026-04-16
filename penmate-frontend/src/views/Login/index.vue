<template>
  <div class="login-page">
    <!-- Background -->
    <div class="login-bg">
      <img :src="loginBg" alt="" class="bg-img" />
      <div class="bg-overlay"></div>
    </div>

    <!-- Floating particles -->
    <div class="particles" aria-hidden="true">
      <span v-for="n in 15" :key="n" class="p-dot" :style="pStyle(n)"></span>
    </div>

    <!-- Login Card -->
    <div class="login-card glass-panel">
      <div class="card-glow-top"></div>

      <!-- Logo & Title -->
      <div class="login-header">
        <img :src="logoImg" alt="PenMate" class="login-logo" />
        <h1 class="login-title">笔 友</h1>
        <p class="login-subtitle">执笔问道 · AI小说Copilot</p>
      </div>

      <!-- Tab Switch -->
      <div class="tab-bar">
        <button
          class="tab-btn"
          :class="{ active: mode === 'login' }"
          @click="mode = 'login'"
        >登 录</button>
        <button
          class="tab-btn"
          :class="{ active: mode === 'register' }"
          @click="mode = 'register'"
        >注 册</button>
        <div class="tab-indicator" :style="{ left: mode === 'login' ? '0%' : '50%' }"></div>
      </div>

      <!-- Login Form -->
      <form class="login-form" @submit.prevent="handleSubmit" v-if="mode === 'login'">
        <div class="form-group">
          <label class="form-label">
            <img :src="iconAgent" alt="" class="label-icon" />
            <span>账号</span>
          </label>
          <input
            v-model="loginForm.username"
            type="text"
            class="form-input"
            placeholder="请输入用户名或邮箱"
            autocomplete="username"
          />
        </div>
        <div class="form-group">
          <label class="form-label">
            <span class="label-icon-text">🔐</span>
            <span>密码</span>
          </label>
          <input
            v-model="loginForm.password"
            type="password"
            class="form-input"
            placeholder="请输入密码"
            autocomplete="current-password"
          />
        </div>
        <div class="form-extra">
          <label class="remember-me">
            <input type="checkbox" v-model="loginForm.remember" />
            <span>记住我</span>
          </label>
          <a href="#" class="forgot-link">忘记密码？</a>
        </div>
        <button type="submit" class="btn-submit" :disabled="isLoading">
          <span v-if="!isLoading">踏 入 书 阁</span>
          <span v-else class="loading-text">
            <span class="loading-dot"></span>
            <span class="loading-dot"></span>
            <span class="loading-dot"></span>
          </span>
        </button>
      </form>

      <!-- Register Form -->
      <form class="login-form" @submit.prevent="handleSubmit" v-else>
        <div class="form-group">
          <label class="form-label">
            <img :src="iconAgent" alt="" class="label-icon" />
            <span>用户名</span>
          </label>
          <input
            v-model="registerForm.username"
            type="text"
            class="form-input"
            placeholder="取一个笔名"
            autocomplete="username"
          />
        </div>
        <div class="form-group">
          <label class="form-label">
            <span class="label-icon-text">📮</span>
            <span>邮箱</span>
          </label>
          <input
            v-model="registerForm.email"
            type="email"
            class="form-input"
            placeholder="请输入邮箱地址"
            autocomplete="email"
          />
        </div>
        <div class="form-group">
          <label class="form-label">
            <span class="label-icon-text">🔐</span>
            <span>密码</span>
          </label>
          <input
            v-model="registerForm.password"
            type="password"
            class="form-input"
            placeholder="设置密码（6位以上）"
            autocomplete="new-password"
          />
        </div>
        <div class="form-group">
          <label class="form-label">
            <span class="label-icon-text">🔐</span>
            <span>确认密码</span>
          </label>
          <input
            v-model="registerForm.confirmPassword"
            type="password"
            class="form-input"
            placeholder="再次输入密码"
            autocomplete="new-password"
          />
        </div>
        <button type="submit" class="btn-submit" :disabled="isLoading">
          <span v-if="!isLoading">开 启 创 作 之 旅</span>
          <span v-else class="loading-text">
            <span class="loading-dot"></span>
            <span class="loading-dot"></span>
            <span class="loading-dot"></span>
          </span>
        </button>
      </form>

      <!-- Footer -->
      <div class="login-footer">
        <div class="divider-line">
          <span>或</span>
        </div>
        <p class="footer-hint">
          {{ mode === 'login' ? '还没有账号？' : '已有账号？' }}
          <a href="#" @click.prevent="mode = mode === 'login' ? 'register' : 'login'">
            {{ mode === 'login' ? '立即注册' : '返回登录' }}
          </a>
        </p>
      </div>
    </div>

    <!-- Back to Home -->
    <a class="back-home" @click.prevent="$router.push('/')">
      <span>← 返回首页</span>
    </a>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { authApi } from '@/api/modules/auth.api'
import { setSession } from '@/stores/session'

import loginBg from '@/assets/images/login-bg.png'
import logoImg from '@/assets/images/logo.png'
import iconAgent from '@/assets/images/icon-agent.png'

const router = useRouter()
const mode = ref<'login' | 'register'>('login')
const isLoading = ref(false)

const loginForm = reactive({
  username: '',
  password: '',
  remember: false
})

const registerForm = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: ''
})

const handleSubmit = async () => {
  if (mode.value !== 'login') {
    message.info('当前版本暂未接入注册接口，请先使用已有账号登录')
    return
  }
  if (!loginForm.username.trim() || !loginForm.password.trim()) {
    message.warning('请输入账号与密码')
    return
  }
  isLoading.value = true
  try {
    const tokenData = await authApi.login({
      email: loginForm.username.trim(),
      password: loginForm.password
    })
    setSession({
      accessToken: String(tokenData?.accessToken || ''),
      refreshToken: String(tokenData?.refreshToken || '')
    })

    const profile = await authApi.me()
    const uid = Number(profile.userId ?? profile.id ?? profile.uid ?? 0)
    const email = String(profile.email ?? profile.userEmail ?? loginForm.username.trim())
    const name = String(profile.displayName ?? profile.username ?? profile.name ?? '创作者')
    setSession({
      userId: Number.isFinite(uid) && uid > 0 ? uid : undefined,
      userEmail: email,
      userName: name
    })

    message.success('登录成功')
    router.push('/mybooks')
  } catch (error: any) {
    message.error(error?.message || '登录失败')
  } finally {
    isLoading.value = false
  }
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
    opacity: Math.random() * 0.4 + 0.1
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

/* Background */
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
    linear-gradient(180deg, rgba(11,17,32,0.4) 0%, rgba(11,17,32,0.7) 100%);
}

/* Particles */
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

/* Login Card */
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
  box-shadow: 0 8px 48px rgba(0,0,0,0.5), 0 0 60px rgba(201,169,110,0.06);
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

/* Header */
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
  filter: drop-shadow(0 0 16px rgba(201,169,110,0.3));
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

/* Tabs */
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

/* Form */
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
    box-shadow: 0 0 0 3px rgba(201,169,110,0.08), var(--shadow-gold);
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

  input[type="checkbox"] {
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

/* Submit Button */
.btn-submit {
  width: 100%;
  margin-top: 8px;
  padding: 14px;
  font-family: var(--font-heading);
  font-size: 1.1rem;
  letter-spacing: 0.25em;
  color: var(--amber-gold);
  background: linear-gradient(135deg, rgba(201,169,110,0.15), rgba(201,169,110,0.05));
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
    background: linear-gradient(135deg, rgba(201,169,110,0.2), rgba(201,169,110,0.08));
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

  &:nth-child(2) { animation-delay: 0.2s; }
  &:nth-child(3) { animation-delay: 0.4s; }
}

@keyframes typingPulse {
  0%, 60%, 100% { opacity: 0.3; transform: scale(0.8); }
  30% { opacity: 1; transform: scale(1.3); }
}

/* Footer */
.login-footer {
  margin-top: 24px;
}

.divider-line {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;

  &::before, &::after {
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

/* Back link */
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
  background: rgba(11,17,32,0.5);
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
