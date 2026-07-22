<template>
  <article
    class="book-card"
    :class="viewMode"
    data-testid="book-card"
    role="link"
    tabindex="0"
    @click="emit('open', book)"
    @keydown.enter="emit('open', book)"
  >
    <div class="book-cover" :class="`tone-${book.coverTone}`">
      <img v-if="book.coverUrl" :src="book.coverUrl" :alt="`${book.title}封面`" />
      <template v-else>
        <span class="cover-title">{{ book.title }}</span>
        <span class="cover-genre">{{ book.genre }}</span>
      </template>
    </div>

    <div class="book-info">
      <div class="title-row">
        <h2>{{ book.title }}</h2>
        <a-dropdown :trigger="['click']" placement="bottomRight">
          <button class="more-button" type="button" title="作品操作" @click.stop>
            <EllipsisOutlined />
          </button>
          <template #overlay>
            <a-menu @click="handleMenuClick">
              <a-menu-item key="settings"><SettingOutlined />作品设置</a-menu-item>
              <a-menu-divider />
              <a-menu-item key="delete" danger><DeleteOutlined />移入回收站</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </div>
      <p class="book-description">{{ book.description || '暂无简介' }}</p>
      <div class="book-meta">
        <span>{{ book.wordCount.toLocaleString('zh-CN') }} 字</span>
        <span>{{ book.chapterCount }} 章</span>
        <time v-if="book.updatedAt">{{ formatUpdatedAt(book.updatedAt) }}</time>
      </div>
      <div v-if="book.tags.length" class="book-tags">
        <span v-for="tag in book.tags.slice(0, 4)" :key="tag">{{ tag }}</span>
      </div>
    </div>
  </article>
</template>

<script setup lang="ts">
import { Dropdown as ADropdown, Menu as AMenu, MenuDivider as AMenuDivider, MenuItem as AMenuItem } from 'ant-design-vue'
import { DeleteOutlined, EllipsisOutlined, SettingOutlined } from '@ant-design/icons-vue'
import type { BookshelfBook, BookshelfViewMode } from '@/composables/bookshelf/useBookshelf'

const props = defineProps<{ book: BookshelfBook; viewMode: BookshelfViewMode }>()
const emit = defineEmits<{
  open: [BookshelfBook]
  settings: [BookshelfBook]
  delete: [BookshelfBook]
}>()

const handleMenuClick = ({ key, domEvent }: { key: string | number; domEvent: Event }) => {
  domEvent.stopPropagation()
  if (key === 'settings') emit('settings', props.book)
  if (key === 'delete') emit('delete', props.book)
}

const formatUpdatedAt = (value: string) => {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', { month: 'short', day: 'numeric' }).format(date)
}
</script>

<style scoped>
.book-card {
  position: relative;
  min-width: 0;
  overflow: hidden;
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs);
  cursor: pointer;
  transition: border-color 160ms ease, box-shadow 160ms ease, transform 160ms ease;
}

.book-card:hover,
.book-card:focus-visible {
  border-color: var(--accent-border);
  box-shadow: var(--shadow-sm);
  transform: translateY(-2px);
}

.book-cover {
  position: relative;
  display: grid;
  aspect-ratio: 2 / 3;
  min-height: 0;
  padding: 20px 16px;
  place-items: center;
  color: #ffffff;
  background: #27332e;
}

.book-cover img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.tone-forest { background: #244c3e; }
.tone-ink { background: #293847; }
.tone-plum { background: #57404d; }
.tone-ocean { background: #31556a; }
.tone-graphite { background: #414846; }

.cover-title {
  max-width: 100%;
  font-family: var(--font-writing);
  font-size: clamp(18px, 1.7vw, 25px);
  font-weight: 650;
  line-height: 1.45;
  text-align: center;
  overflow-wrap: anywhere;
}

.cover-genre {
  position: absolute;
  right: 14px;
  bottom: 14px;
  color: rgba(255, 255, 255, 0.76);
  font-size: 11px;
}

.book-info {
  min-width: 0;
  padding: 14px;
}

.title-row,
.book-meta,
.book-tags {
  display: flex;
  align-items: center;
}

.title-row {
  gap: 8px;
}

h2 {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.more-button {
  display: grid;
  flex: 0 0 auto;
  width: 30px;
  height: 30px;
  place-items: center;
  color: var(--text-secondary);
  background: transparent;
  border: 0;
  border-radius: var(--radius-md);
  cursor: pointer;
}

.more-button:hover {
  background: var(--bg-subtle);
}

.book-description {
  display: -webkit-box;
  min-height: 40px;
  margin-top: 8px;
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.book-meta {
  flex-wrap: wrap;
  gap: 5px 12px;
  margin-top: 12px;
  color: var(--text-muted);
  font-size: 11px;
}

.book-tags {
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 10px;
}

.book-tags span {
  padding: 2px 6px;
  color: var(--text-secondary);
  background: var(--bg-subtle);
  border-radius: var(--radius-sm);
  font-size: 10px;
}

.book-card.list {
  display: grid;
  grid-template-columns: 68px minmax(0, 1fr);
  min-height: 102px;
}

.book-card.list .book-cover {
  width: 68px;
  min-height: 102px;
  padding: 8px;
}

.book-card.list .cover-title {
  font-size: 11px;
}

.book-card.list .cover-genre {
  display: none;
}

.book-card.list .book-description {
  min-height: auto;
  -webkit-line-clamp: 1;
}

@media (max-width: 560px) {
  .book-cover {
    aspect-ratio: 3 / 4;
  }
}
</style>
