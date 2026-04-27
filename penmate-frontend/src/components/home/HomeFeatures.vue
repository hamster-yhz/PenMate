<template>
  <section id="features" class="features-section" data-testid="home-features">
    <HomeSectionHeader
      title="六 大 神 通"
      subtitle="汇聚天地灵气，锻造写作神兵"
      :divider-img="dividerImg"
    />

    <div class="features-grid">
      <div v-for="feature in features" :key="feature.title" class="feature-card" data-testid="home-feature-card">
        <div class="card-glow"></div>
        <div class="card-img-wrap">
          <img :src="feature.img" :alt="feature.title" class="card-img" />
        </div>
        <div class="card-content">
          <div class="card-icon-badge">{{ feature.icon }}</div>
          <h3 class="card-title">{{ feature.title }}</h3>
          <p class="card-desc">{{ feature.desc }}</p>
        </div>
        <div class="card-footer">
          <span v-for="tag in feature.tags" :key="tag" class="card-tag">{{ tag }}</span>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import HomeSectionHeader from './HomeSectionHeader.vue'

defineProps<{
  dividerImg: string
  features: Array<{
    img: string
    icon: string
    title: string
    desc: string
    tags: string[]
  }>
}>()
</script>

<style scoped lang="less">
.features-section {
  position: relative;
  z-index: 1;
  padding: var(--section-gap) 48px;
  max-width: var(--content-max);
  margin: 0 auto;
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 28px;
}

.feature-card {
  position: relative;
  background: var(--bg-card);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  overflow: hidden;
  transition: all 0.5s var(--ease-silk);

  &:hover {
    transform: translateY(-8px);
    border-color: var(--border-gold);
    background: var(--bg-card-hover);
    box-shadow: var(--shadow-gold), var(--shadow-lg);
  }
}

.card-glow {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--amber-gold), transparent);
}

.card-img-wrap {
  width: 100%;
  height: 180px;
  overflow: hidden;
}

.card-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  filter: brightness(0.85) saturate(0.9);
}

.card-content {
  padding: 20px 24px 16px;
}

.card-icon-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  font-family: var(--font-heading);
  font-size: 1.2rem;
  color: var(--amber-gold);
  border: 1px solid var(--border-gold);
  border-radius: 8px;
  background: rgba(201, 169, 110, 0.08);
  margin-bottom: 14px;
}

.card-title {
  font-family: var(--font-heading);
  font-size: 1.3rem;
  color: var(--xuan-paper);
  letter-spacing: 0.15em;
  margin-bottom: 10px;
}

.card-desc {
  font-size: 0.9rem;
  color: var(--text-secondary);
  line-height: 1.8;
}

.card-footer {
  padding: 0 24px 20px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.card-tag {
  padding: 3px 12px;
  font-size: 0.75rem;
  color: var(--amber-gold);
  background: rgba(201, 169, 110, 0.08);
  border: 1px solid rgba(201, 169, 110, 0.15);
  border-radius: 12px;
}

@media (max-width: 1024px) {
  .features-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .features-section {
    padding: 60px 20px;
  }

  .features-grid {
    grid-template-columns: 1fr;
  }
}
</style>
