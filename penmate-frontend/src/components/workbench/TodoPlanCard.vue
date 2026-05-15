<template>
  <section class="runtime-card" data-testid="todo-plan-card">
    <header class="runtime-card__header">
      <span class="runtime-card__title">{{ title }}</span>
      <span class="runtime-card__badge">{{ itemCountText }}</span>
    </header>
    <ul class="runtime-card__list">
      <li v-for="item in items" :key="`${item.title}-${item.priorityText}`" class="runtime-card__item">
        <span>{{ item.title }}</span>
        <span class="runtime-card__meta">{{ item.statusText }} / {{ item.priorityText }}</span>
      </li>
    </ul>
    <p v-if="nextActionText" class="runtime-card__text runtime-card__text--muted">{{ nextActionText }}</p>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  itemCountText: string
  nextActionText?: string
  items: Array<{
    title: string
    statusText: string
    priorityText: string
  }>
}>()
</script>

<style scoped lang="less">
.runtime-card {
  padding: 12px;
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  background: rgba(15, 23, 42, 0.72);
}

.runtime-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.runtime-card__title {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-main);
}

.runtime-card__badge {
  color: var(--amber-gold);
  font-size: 0.78rem;
}

.runtime-card__list {
  margin: 0;
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.runtime-card__item {
  color: var(--text-main);
  font-size: 0.82rem;
}

.runtime-card__meta {
  margin-left: 6px;
  color: var(--text-muted);
}

.runtime-card__text {
  margin: 8px 0 0;
  color: var(--text-main);
  font-size: 0.82rem;
}

.runtime-card__text--muted {
  color: var(--text-muted);
}
</style>
