<template>
  <section id="workflow" class="workflow-section" data-testid="home-workflow">
    <HomeSectionHeader
      title="创 作 之 道"
      subtitle="从落笔到成书，行云流水般的工作流"
      :divider-img="dividerImg"
    />

    <div class="workflow-timeline">
      <div class="timeline-line"></div>
      <div
        v-for="(step, index) in steps"
        :key="step.num"
        class="timeline-step"
        :class="{ 'step-right': index % 2 !== 0 }"
        data-testid="home-workflow-step"
      >
        <div class="step-dot">
          <span class="dot-number">{{ step.num }}</span>
        </div>
        <div class="step-card glass-panel">
          <div class="step-icon">{{ step.icon }}</div>
          <h3 class="step-title">{{ step.title }}</h3>
          <p class="step-desc">{{ step.desc }}</p>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import HomeSectionHeader from './HomeSectionHeader.vue'

defineProps<{
  dividerImg: string
  steps: Array<{
    num: string
    icon: string
    title: string
    desc: string
  }>
}>()
</script>

<style scoped lang="less">
.workflow-section {
  position: relative;
  z-index: 1;
  padding: var(--section-gap) 48px;
  max-width: 900px;
  margin: 0 auto;
}

.workflow-timeline {
  position: relative;
  padding: 20px 0;
}

.timeline-line {
  position: absolute;
  left: 50%;
  top: 0;
  bottom: 0;
  width: 1px;
  background: linear-gradient(180deg, transparent, var(--border-gold), var(--border-gold), transparent);
  transform: translateX(-50%);
}

.timeline-step {
  display: flex;
  align-items: flex-start;
  gap: 32px;
  margin-bottom: 48px;
  position: relative;
}

.step-right {
  flex-direction: row-reverse;
}

.step-right .step-card {
  text-align: right;
}

.step-dot {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  width: 44px;
  height: 44px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-primary);
  border: 2px solid var(--border-gold);
  z-index: 2;
}

.dot-number {
  font-family: var(--font-heading);
  font-size: 1.1rem;
  color: var(--amber-gold);
}

.step-card {
  width: calc(50% - 54px);
  padding: 24px 28px;
  transition: all 0.4s var(--ease-silk);
}

.step-icon {
  font-size: 1.8rem;
  margin-bottom: 12px;
}

.step-title {
  font-family: var(--font-heading);
  font-size: 1.2rem;
  color: var(--xuan-paper);
  letter-spacing: 0.15em;
  margin-bottom: 8px;
}

.step-desc {
  font-size: 0.9rem;
  color: var(--text-secondary);
  line-height: 1.8;
}

@media (max-width: 768px) {
  .workflow-section {
    padding: 60px 20px;
  }

  .timeline-line,
  .step-dot {
    left: 22px;
  }

  .timeline-step {
    flex-direction: column !important;
    padding-left: 64px;
  }

  .step-card {
    width: 100% !important;
    text-align: left !important;
  }
}
</style>
