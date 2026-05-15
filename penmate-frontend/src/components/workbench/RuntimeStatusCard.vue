<template>
  <section class="runtime-card" data-testid="runtime-status-card">
    <header class="runtime-card__header">
      <span class="runtime-card__title">{{ title }}</span>
      <span class="runtime-card__badge">{{ badgeText }}</span>
    </header>
    <p v-if="description" class="runtime-card__text">{{ description }}</p>
    <p v-if="failureReasonText" class="runtime-card__text runtime-card__text--danger">{{ failureReasonText }}</p>
    <p v-if="nextActionText" class="runtime-card__text runtime-card__text--muted">{{ nextActionText }}</p>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  badgeText: string
  description?: string
  nextActionText?: string
  failureReasonText?: string
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

.runtime-card__text {
  margin: 4px 0 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--text-main);
  font-size: 0.82rem;
}

.runtime-card__text--muted {
  color: var(--text-muted);
}

.runtime-card__text--danger {
  color: #fca5a5;
}
</style>
