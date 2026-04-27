<template>
  <section class="cta-section" data-testid="home-cta">
    <div class="cta-bg-pattern"></div>
    <div class="cta-content">
      <h2 class="cta-title">提 笔 即 道</h2>
      <p class="cta-desc">加入PenMate，让AI成为你的创作伙伴</p>
      <button
        type="button"
        class="btn-ancient btn-cta"
        data-testid="home-cta-button"
        @click="$emit('enter-workbench')"
      >
        <span>开 启 创 作 之 旅</span>
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M5 12h14M12 5l7 7-7 7" />
        </svg>
      </button>
    </div>
  </section>
</template>

<script setup lang="ts">
defineEmits<{
  (event: 'enter-workbench'): void
}>()
</script>

<style scoped lang="less">
.cta-section {
  position: relative;
  z-index: 1;
  padding: var(--section-gap) 48px;
  text-align: center;
  overflow: hidden;
}

.cta-bg-pattern {
  position: absolute;
  inset: 0;
  background: radial-gradient(ellipse at 50% 50%, rgba(201, 169, 110, 0.06) 0%, transparent 60%);
}

.cta-content {
  position: relative;
  z-index: 1;
}

.cta-title {
  font-family: var(--font-heading);
  font-size: clamp(2.2rem, 4.5vw, 3.5rem);
  color: var(--xuan-paper);
  letter-spacing: 0.4em;
  margin-bottom: 16px;
}

.cta-desc {
  font-size: 1.1rem;
  color: var(--text-secondary);
  letter-spacing: 0.1em;
  margin-bottom: 40px;
}

.btn-cta {
  font-size: 1.2rem;
  padding: 16px 48px;
  background: linear-gradient(135deg, rgba(201, 169, 110, 0.2), rgba(201, 169, 110, 0.08));
  animation: pulse-gold 3s infinite;
}

@media (max-width: 768px) {
  .cta-section {
    padding: 60px 20px;
  }
}
</style>
