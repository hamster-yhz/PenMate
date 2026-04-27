<script setup lang="ts">
defineProps<{
  bookCount: number
  totalWords: number
  totalChapters: number
}>()
</script>

<template>
  <div class="stats-bar" data-testid="book-stats-bar">
    <div class="stat">
      <span class="stat-val">{{ bookCount }}</span>
      <span class="stat-lbl">部作品</span>
    </div>
    <div class="stat-sep"></div>
    <div class="stat">
      <span class="stat-val">{{ totalWords }}</span>
      <span class="stat-lbl">总字数</span>
    </div>
    <div class="stat-sep"></div>
    <div class="stat">
      <span class="stat-val">{{ totalChapters }}</span>
      <span class="stat-lbl">总章节</span>
    </div>
  </div>
</template>

<style scoped lang="less">
.stats-bar {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 16px 24px;
  background: rgba(17, 24, 39, 0.5);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  margin-bottom: 32px;
}

.stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.stat-val {
  font-family: var(--font-heading);
  font-size: 1.3rem;
  color: var(--amber-gold);
  letter-spacing: 0.1em;
}

.stat-lbl {
  font-size: 0.72rem;
  color: var(--text-muted);
}

.stat-sep {
  width: 1px;
  height: 28px;
  background: var(--border-subtle);
}
</style>
