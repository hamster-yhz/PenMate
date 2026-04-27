<template>
  <section id="hero" class="hero-section" data-testid="home-hero">
    <div class="hero-bg-wrap">
      <img :src="heroBg" alt="" class="hero-bg-img" />
      <div class="hero-overlay"></div>
    </div>

    <div class="hero-content">
      <div class="hero-badge">
        <span class="badge-dot"></span>
        AI驱动的智能写作平台
      </div>

      <img :src="logoImg" alt="PenMate" class="hero-logo-img" />

      <h1 class="hero-title">
        <span class="title-line">执 笔 问 道</span>
        <span class="title-sub">以AI为墨，以心为笺</span>
      </h1>

      <p class="hero-desc">
        AI是主力写手，作者充当导演与编辑。<br />
        通过智能Agent深度统筹项目上下文，让创作如行云流水。
      </p>

      <div class="hero-actions">
        <button
          type="button"
          class="btn-ancient btn-primary-glow"
          data-testid="home-hero-primary-cta"
          @click="$emit('enter-workbench')"
        >
          <span>踏入工作区</span>
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M5 12h14M12 5l7 7-7 7" />
          </svg>
        </button>
        <a href="#features" class="btn-ancient btn-ghost">
          <span>一览功能</span>
        </a>
      </div>

      <div class="hero-stats">
        <div class="stat-item">
          <span class="stat-value">∞</span>
          <span class="stat-label">创意无限</span>
        </div>
        <div class="stat-divider"></div>
        <div class="stat-item">
          <span class="stat-value">AI</span>
          <span class="stat-label">智能驱动</span>
        </div>
        <div class="stat-divider"></div>
        <div class="stat-item">
          <span class="stat-value">笔</span>
          <span class="stat-label">文风如一</span>
        </div>
      </div>
    </div>

    <div class="scroll-hint">
      <div class="scroll-line"></div>
      <span>向下探索</span>
    </div>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  heroBg: string
  logoImg: string
}>()

defineEmits<{
  (event: 'enter-workbench'): void
}>()
</script>

<style scoped lang="less">
.hero-section {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 120px 48px 80px;
  overflow: hidden;
}

.hero-bg-wrap {
  position: absolute;
  inset: 0;
  z-index: 0;
}

.hero-bg-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0.45;
  filter: brightness(0.6) saturate(0.7);
}

.hero-overlay {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(180deg, rgba(11, 17, 32, 0.3) 0%, rgba(11, 17, 32, 0.5) 50%, rgba(11, 17, 32, 0.95) 100%),
    radial-gradient(ellipse at 50% 30%, rgba(201, 169, 110, 0.06) 0%, transparent 60%);
}

.hero-content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  max-width: 800px;
  animation: fadeInUp 1.2s var(--ease-silk) both;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 20px;
  font-size: 0.85rem;
  color: var(--amber-gold);
  background: rgba(201, 169, 110, 0.08);
  border: 1px solid rgba(201, 169, 110, 0.2);
  border-radius: 20px;
  margin-bottom: 32px;
  letter-spacing: 0.1em;
}

.badge-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--amber-gold);
  animation: pulse-gold 2s infinite;
}

.hero-logo-img {
  width: 200px;
  height: 200px;
  object-fit: contain;
  margin-bottom: 24px;
  animation: floatSlow 6s ease-in-out infinite;
  filter: drop-shadow(0 0 30px rgba(201, 169, 110, 0.3));
}

.hero-title {
  margin-bottom: 24px;
}

.title-line {
  display: block;
  font-family: var(--font-heading);
  font-size: clamp(3rem, 6vw, 4.5rem);
  color: var(--xuan-paper);
  letter-spacing: 0.4em;
  background: linear-gradient(135deg, var(--xuan-paper) 0%, var(--amber-gold) 50%, var(--xuan-paper) 100%);
  background-size: 200% auto;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  animation: shimmer 4s linear infinite;
}

.title-sub {
  display: block;
  font-family: var(--font-heading);
  font-size: clamp(1.2rem, 2.5vw, 1.6rem);
  color: var(--text-secondary);
  letter-spacing: 0.3em;
  margin-top: 12px;
}

.hero-desc {
  font-size: 1.05rem;
  color: var(--text-secondary);
  line-height: 2;
  margin-bottom: 40px;
  max-width: 600px;
}

.hero-actions {
  display: flex;
  gap: 20px;
  margin-bottom: 56px;
}

.btn-primary-glow {
  background: linear-gradient(135deg, rgba(201, 169, 110, 0.15), rgba(201, 169, 110, 0.05));
  animation: pulse-gold 3s infinite;
}

.btn-ghost {
  border-color: rgba(201, 169, 110, 0.25);
}

.hero-stats {
  display: flex;
  align-items: center;
  gap: 36px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.stat-value {
  font-family: var(--font-heading);
  font-size: 1.6rem;
  color: var(--amber-gold);
  letter-spacing: 0.1em;
}

.stat-label {
  font-size: 0.8rem;
  color: var(--text-muted);
  letter-spacing: 0.15em;
}

.stat-divider {
  width: 1px;
  height: 36px;
  background: linear-gradient(180deg, transparent, var(--border-gold), transparent);
}

.scroll-hint {
  position: absolute;
  bottom: 32px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: var(--text-muted);
  font-size: 0.75rem;
  letter-spacing: 0.2em;
}

.scroll-line {
  width: 1px;
  height: 40px;
  background: linear-gradient(180deg, var(--amber-gold), transparent);
  animation: floatSlow 2s ease-in-out infinite;
}

@media (max-width: 768px) {
  .hero-section {
    padding: 100px 20px 60px;
  }

  .hero-logo-img {
    width: 140px;
    height: 140px;
  }

  .hero-actions {
    flex-direction: column;
    align-items: center;
  }

  .hero-stats {
    gap: 20px;
  }
}
</style>
