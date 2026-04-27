<template>
  <div class="section-header" data-testid="home-section-header">
    <img :src="dividerImg" alt="" class="section-divider" data-testid="home-section-divider" />
    <h2 class="section-title">{{ title }}</h2>
    <p class="section-subtitle">{{ subtitle }}</p>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  subtitle: string
  dividerImg: string
}>()
</script>

<style scoped lang="less">
.section-header {
  text-align: center;
  margin-bottom: 64px;
}

.section-divider {
  width: 180px;
  margin: 0 auto 24px;
  display: block;
  opacity: 0.5;
  filter: brightness(1.3) saturate(0.6);
}

.section-title {
  font-family: var(--font-heading);
  font-size: clamp(2rem, 4vw, 3rem);
  color: var(--xuan-paper);
  letter-spacing: 0.3em;
  margin-bottom: 12px;
}

.section-subtitle {
  font-size: 1rem;
  color: var(--text-muted);
  letter-spacing: 0.15em;
}
</style>
