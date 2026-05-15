<template>
  <section class="runtime-card" data-testid="story-bible-approval-card">
    <header class="runtime-card__header">
      <span class="runtime-card__title">{{ title }}</span>
    </header>
    <p v-if="proposalSummary" class="runtime-card__text">{{ proposalSummary }}</p>
    <ul v-if="entryKeys.length" class="runtime-card__list">
      <li v-for="entryKey in entryKeys" :key="entryKey" class="runtime-card__item">{{ entryKey }}</li>
    </ul>
    <p v-if="nextActionText" class="runtime-card__text runtime-card__text--muted">{{ nextActionText }}</p>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  proposalSummary?: string
  entryKeys: string[]
  nextActionText?: string
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

.runtime-card__list {
  margin: 8px 0 0;
  padding-left: 18px;
}

.runtime-card__item {
  color: var(--text-main);
  font-size: 0.82rem;
}
</style>
