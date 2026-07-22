<template>
  <article class="trash-row" data-testid="trash-book-row">
    <div class="mini-cover" :class="`tone-${book.coverTone}`" aria-hidden="true">
      <img v-if="book.coverUrl" :src="book.coverUrl" alt="" />
      <span v-else>{{ book.title.slice(0, 4) }}</span>
    </div>
    <div class="trash-copy">
      <h2>{{ book.title }}</h2>
      <p>{{ book.description || '暂无简介' }}</p>
      <div class="trash-meta">
        <span>{{ book.wordCount.toLocaleString('zh-CN') }} 字</span>
        <span>{{ book.chapterCount }} 章</span>
        <span class="retention"><ClockCircleOutlined />{{ retentionLabel }}</span>
      </div>
    </div>
    <div class="trash-actions">
      <button type="button" class="restore-button" :disabled="busy" @click="emit('restore', book)">
        <LoadingOutlined v-if="restoring" spin />
        <RollbackOutlined v-else />
        <span>{{ restoring ? '正在恢复' : '恢复' }}</span>
      </button>
      <button type="button" class="delete-button" title="永久删除" :disabled="busy" @click="emit('permanent-delete', book)">
        <DeleteOutlined />
      </button>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ClockCircleOutlined, DeleteOutlined, LoadingOutlined, RollbackOutlined } from '@ant-design/icons-vue'
import type { BookshelfBook } from '@/composables/bookshelf/useBookshelf'

const props = defineProps<{
  book: BookshelfBook
  restoring: boolean
  deleting: boolean
}>()
const emit = defineEmits<{
  restore: [BookshelfBook]
  'permanent-delete': [BookshelfBook]
}>()

const busy = computed(() => props.restoring || props.deleting)
const retentionLabel = computed(() => {
  if (props.book.remainingDays == null) return '将在 30 天后清理'
  if (props.book.remainingDays <= 0) return '等待自动清理'
  return `还剩 ${props.book.remainingDays} 天`
})
</script>

<style scoped>
.trash-row {
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  min-width: 0;
  padding: 12px;
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
}
.mini-cover {
  position: relative;
  display: grid;
  width: 58px;
  aspect-ratio: 2 / 3;
  overflow: hidden;
  place-items: center;
  padding: 6px;
  color: #fff;
  background: #414846;
  font-family: var(--font-writing);
  font-size: 11px;
  text-align: center;
}
.mini-cover img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.tone-forest { background: #244c3e; }
.tone-ink { background: #293847; }
.tone-plum { background: #57404d; }
.tone-ocean { background: #31556a; }
.tone-graphite { background: #414846; }
.trash-copy { min-width: 0; }
.trash-copy h2 { overflow: hidden; margin: 0; font-size: 15px; text-overflow: ellipsis; white-space: nowrap; }
.trash-copy > p { overflow: hidden; margin: 5px 0 0; color: var(--text-secondary); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.trash-meta { display: flex; flex-wrap: wrap; gap: 6px 13px; margin-top: 9px; color: var(--text-muted); font-size: 11px; }
.retention { display: inline-flex; align-items: center; gap: 5px; color: var(--warning); }
.trash-actions { display: flex; align-items: center; gap: 7px; }
.trash-actions button { display: inline-flex; height: 34px; align-items: center; justify-content: center; gap: 6px; border-radius: var(--radius-md); cursor: pointer; }
.trash-actions button:disabled { cursor: wait; opacity: .58; }
.restore-button { padding: 0 11px; color: var(--accent); background: var(--accent-soft); border: 1px solid var(--accent-border); }
.delete-button { width: 34px; color: var(--danger); background: transparent; border: 1px solid var(--danger-border); }
@media (max-width: 560px) {
  .trash-row { grid-template-columns: 48px minmax(0, 1fr); gap: 10px; }
  .mini-cover { width: 48px; }
  .trash-actions { grid-column: 1 / -1; justify-content: flex-end; padding-top: 2px; }
}
</style>
