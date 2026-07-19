<script setup lang="ts">
import type { BookshelfBook } from '@/composables/bookshelf/useBookshelf'

const props = defineProps<{
  book: BookshelfBook
}>()

const emit = defineEmits<{
  open: [BookshelfBook]
  edit: [BookshelfBook]
  delete: [BookshelfBook]
}>()

const openBook = () => {
  emit('open', props.book)
}

const editBook = () => {
  emit('edit', props.book)
}

const deleteBook = () => {
  emit('delete', props.book)
}
</script>

<template>
  <div
    class="book-card"
    data-testid="book-card"
    role="button"
    tabindex="0"
    @click="openBook"
    @keydown.enter="openBook"
    @keydown.space.prevent="openBook"
  >
    <div class="book-cover" :style="{ background: book.coverGradient }">
      <span class="cover-title">{{ book.title }}</span>
      <span class="cover-genre">{{ book.genre }}</span>
    </div>
    <div class="book-info">
      <h3 class="book-title">{{ book.title }}</h3>
      <p class="book-desc">{{ book.description }}</p>
      <div class="book-meta">
        <span>{{ book.wordCount }} 字</span>
        <span>{{ book.chapterCount }} 章</span>
        <span>{{ book.updatedAt }}</span>
      </div>
      <div class="book-tags">
        <span v-for="tag in book.tags" :key="tag" class="b-tag">{{ tag }}</span>
      </div>
    </div>
    <div class="book-actions" @click.stop>
      <button type="button" class="ba-btn" data-testid="book-card-edit" title="编辑" @click="editBook">✏️</button>
      <button type="button" class="ba-btn danger" data-testid="book-card-delete" title="删除" @click="deleteBook">
        🗑️
      </button>
    </div>
  </div>
</template>

<style scoped lang="less">
.book-card {
  position: relative;
  background: var(--bg-card);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.4s var(--ease-silk);

  &:hover {
    transform: translateY(-6px);
    border-color: var(--border-gold);
    box-shadow: var(--shadow-gold), var(--shadow-lg);

    .book-actions {
      opacity: 1;
    }

    .book-cover .cover-title {
      text-shadow: 0 0 16px rgba(201, 169, 110, 0.5);
    }
  }
}

.book-cover {
  height: 120px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  position: relative;

  &::after {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(180deg, transparent 50%, rgba(11, 17, 32, 0.6) 100%);
  }
}

.cover-title {
  font-family: var(--font-heading);
  font-size: 1.4rem;
  color: var(--xuan-paper);
  letter-spacing: 0.2em;
  z-index: 1;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.5);
  transition: text-shadow 0.3s;
}

.cover-genre {
  font-size: 0.72rem;
  color: rgba(245, 237, 214, 0.6);
  letter-spacing: 0.1em;
  z-index: 1;
  padding: 2px 10px;
  border: 1px solid rgba(245, 237, 214, 0.2);
  border-radius: 10px;
}

.book-info {
  padding: 16px;
}

.book-title {
  font-family: var(--font-heading);
  font-size: 1.05rem;
  color: var(--xuan-paper);
  letter-spacing: 0.1em;
  margin-bottom: 6px;
}

.book-desc {
  font-size: 0.82rem;
  color: var(--text-secondary);
  line-height: 1.6;
  margin-bottom: 10px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.book-meta {
  display: flex;
  gap: 12px;
  font-size: 0.72rem;
  color: var(--text-muted);
  margin-bottom: 10px;
}

.book-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.b-tag {
  padding: 2px 8px;
  font-size: 0.68rem;
  color: var(--amber-gold);
  background: rgba(201, 169, 110, 0.06);
  border: 1px solid rgba(201, 169, 110, 0.12);
  border-radius: 8px;
}

.book-actions {
  position: absolute;
  top: 8px;
  right: 8px;
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.3s;
  z-index: 2;
}

.ba-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(11, 17, 32, 0.8);
  backdrop-filter: blur(8px);
  border: 1px solid var(--border-subtle);
  border-radius: 6px;
  font-size: 0.75rem;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: var(--border-gold);
  }

  &.danger:hover {
    border-color: rgba(192, 60, 45, 0.5);
  }
}
</style>
