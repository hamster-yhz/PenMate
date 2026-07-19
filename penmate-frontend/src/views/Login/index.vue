<template>
  <div class="login-page">
    <div class="login-bg">
      <img :src="loginBg" alt="" class="bg-img" />
      <div class="bg-overlay"></div>
    </div>

    <div class="particles" aria-hidden="true">
      <span v-for="n in 15" :key="n" class="p-dot" :style="pStyle(n)"></span>
    </div>

    <div class="login-shell">
      <section class="login-showcase glass-panel">
        <div class="showcase-badge">
          <span class="badge-dot"></span>
          沿袭主页的沉浸式创作气韵
        </div>

        <h1 class="showcase-title">执 笔 入 阁</h1>
        <p class="showcase-desc">
          从主页的古风暗金视觉延展到登录入口，保持统一的磨砂浮层、鎏金描边与夜幕氛围。<br />
          入阁之后，继续以同一套视觉语言衔接你的创作工作流。
        </p>

        <div class="showcase-metrics">
          <div class="metric-item">
            <span class="metric-value">AI</span>
            <span class="metric-label">协同执笔</span>
          </div>
          <div class="metric-divider"></div>
          <div class="metric-item">
            <span class="metric-value">阁</span>
            <span class="metric-label">统一工作区</span>
          </div>
          <div class="metric-divider"></div>
          <div class="metric-item">
            <span class="metric-value">境</span>
            <span class="metric-label">沉浸式体验</span>
          </div>
        </div>

        <div class="showcase-actions">
          <span class="showcase-chip">粒子夜幕</span>
          <span class="showcase-chip">宣纸金纹</span>
          <span class="showcase-chip">磨砂浮层</span>
        </div>
      </section>

      <div class="login-auth-panel">
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
      </div>
    </div>

    <button type="button" class="back-home btn-ancient" @click="router.push('/')">
      <span>← 返回首页</span>
    </button>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import loginBg from '@/assets/images/login-bg.webp'
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

const pStyle = (index: number) => {
  void index
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
  padding: 32px;
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
    radial-gradient(ellipse at 20% 20%, rgba(201, 169, 110, 0.14) 0%, transparent 45%),
    radial-gradient(ellipse at 80% 18%, rgba(201, 169, 110, 0.08) 0%, transparent 42%),
    linear-gradient(180deg, rgba(11, 17, 32, 0.36) 0%, rgba(11, 17, 32, 0.82) 100%);
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

.login-shell {
  position: relative;
  z-index: 10;
  width: min(1180px, 100%);
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(420px, 460px);
  align-items: center;
  gap: 32px;
}

.login-showcase {
  padding: 44px;
  border-radius: 28px;
  background:
    linear-gradient(180deg, rgba(17, 24, 39, 0.62), rgba(11, 17, 32, 0.76)),
    radial-gradient(circle at top left, rgba(201, 169, 110, 0.08), transparent 45%);
  box-shadow:
    0 24px 80px rgba(0, 0, 0, 0.28),
    0 0 50px rgba(201, 169, 110, 0.06);
}

.showcase-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 20px;
  margin-bottom: 28px;
  font-size: 0.85rem;
  color: var(--amber-gold);
  background: rgba(201, 169, 110, 0.08);
  border: 1px solid rgba(201, 169, 110, 0.2);
  border-radius: 999px;
  letter-spacing: 0.1em;
}

.badge-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--amber-gold);
  animation: pulse-gold 2s infinite;
}

.showcase-title {
  margin-bottom: 16px;
  font-family: var(--font-heading);
  font-size: clamp(2.8rem, 5vw, 4.2rem);
  color: var(--xuan-paper);
  letter-spacing: 0.24em;
  background: linear-gradient(135deg, var(--xuan-paper) 0%, var(--amber-gold) 50%, var(--xuan-paper) 100%);
  background-size: 200% auto;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  animation: shimmer 5s linear infinite;
}

.showcase-desc {
  max-width: 540px;
  margin-bottom: 34px;
  font-size: 1rem;
  color: var(--text-secondary);
  line-height: 2;
}

.showcase-metrics {
  display: flex;
  align-items: center;
  gap: 24px;
  margin-bottom: 26px;
}

.metric-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.metric-value {
  font-family: var(--font-heading);
  font-size: 1.45rem;
  color: var(--amber-gold);
  letter-spacing: 0.16em;
}

.metric-label {
  font-size: 0.82rem;
  color: var(--text-muted);
  letter-spacing: 0.14em;
}

.metric-divider {
  width: 1px;
  height: 36px;
  background: linear-gradient(180deg, transparent, var(--border-gold), transparent);
}

.showcase-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.showcase-chip {
  padding: 8px 16px;
  font-size: 0.82rem;
  color: var(--text-secondary);
  letter-spacing: 0.12em;
  background: rgba(11, 17, 32, 0.35);
  border: 1px solid var(--border-subtle);
  border-radius: 999px;
}

.login-auth-panel {
  display: flex;
  justify-content: center;
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
  padding: 10px 18px;
  font-size: 0.9rem;
  letter-spacing: 0.1em;
}

@media (max-width: 1080px) {
  .login-shell {
    grid-template-columns: 1fr;
  }

  .login-showcase {
    order: 2;
    padding: 32px 28px;
  }

  .login-auth-panel {
    order: 1;
  }
}

@media (max-width: 768px) {
  .login-page {
    padding: 88px 16px 24px;
  }

  .login-showcase {
    padding: 28px 22px;
    border-radius: 22px;
  }

  .showcase-metrics {
    flex-wrap: wrap;
    gap: 16px;
  }

  .metric-divider {
    display: none;
  }

  .back-home {
    top: 18px;
    left: 16px;
    padding: 9px 14px;
    font-size: 0.82rem;
  }
}
</style>
