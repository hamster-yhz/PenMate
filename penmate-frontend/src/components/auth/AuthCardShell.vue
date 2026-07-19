<template>
  <div class="login-card glass-panel">
    <div class="card-glow-top"></div>
    <div class="login-header">
      <img :src="logoImg" alt="PenMate" class="login-logo" />
      <h1 class="login-title">笔 友</h1>
      <p class="login-subtitle">执笔问道 · AI小说Copilot</p>
    </div>
    <slot />
  </div>
</template>

<script setup lang="ts">
import logoImg from '@/assets/images/logo.webp'
</script>

<style scoped lang="less">
.login-card {
  position: relative;
  z-index: 10;
  width: 440px;
  max-width: min(92vw, 100%);
  padding: 40px 36px 32px;
  background: rgba(17, 24, 39, 0.78);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border: 1px solid rgba(201, 169, 110, 0.18);
  border-radius: 24px;
  box-shadow:
    0 24px 80px rgba(0, 0, 0, 0.42),
    0 0 60px rgba(201, 169, 110, 0.08);
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
  filter: drop-shadow(0 0 18px rgba(201, 169, 110, 0.32));
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
  letter-spacing: 0.18em;
}

@media (max-width: 480px) {
  .login-card {
    padding: 28px 20px 24px;
    border-radius: 18px;
  }
}
</style>
