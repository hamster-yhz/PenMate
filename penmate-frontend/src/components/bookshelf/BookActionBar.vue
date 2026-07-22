<template>
  <div class="action-bar" data-testid="book-action-bar">
    <div class="heading-group">
      <div>
        <h1>{{ collection === 'trash' ? '回收站' : '书架' }}</h1>
        <p>{{ bookCount }} 部作品</p>
      </div>
      <div class="collection-switch" role="tablist" aria-label="作品集合">
        <button type="button" role="tab" :aria-selected="collection === 'books'" :class="{ active: collection === 'books' }" @click="$emit('update:collection', 'books')">
          <BookOutlined /><span>作品</span>
        </button>
        <button type="button" role="tab" :aria-selected="collection === 'trash'" :class="{ active: collection === 'trash' }" @click="$emit('update:collection', 'trash')">
          <DeleteOutlined /><span>回收站</span>
        </button>
      </div>
    </div>
    <div v-if="collection === 'books'" class="action-group">
      <label class="sort-control">
        <span class="sr-only">作品排序</span>
        <select :value="sort" @change="$emit('update:sort', ($event.target as HTMLSelectElement).value as BookshelfSort)">
          <option value="updated-desc">最近编辑</option>
          <option value="title-asc">按名称</option>
          <option value="words-desc">按字数</option>
        </select>
      </label>
      <div class="view-switch" role="group" aria-label="书架视图">
        <button type="button" title="封面视图" :class="{ active: viewMode === 'grid' }" @click="$emit('update:view-mode', 'grid')">
          <AppstoreOutlined />
        </button>
        <button type="button" title="列表视图" :class="{ active: viewMode === 'list' }" @click="$emit('update:view-mode', 'list')">
          <UnorderedListOutlined />
        </button>
      </div>
      <button type="button" class="import-button" @click="$emit('import')">
        <ImportOutlined />
        <span>导入 TXT</span>
      </button>
      <button type="button" class="create-button" data-testid="create-book-button" @click="$emit('create')">
        <PlusOutlined />
        <span>新建作品</span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { AppstoreOutlined, BookOutlined, DeleteOutlined, ImportOutlined, PlusOutlined, UnorderedListOutlined } from '@ant-design/icons-vue'
import type { BookshelfSort, BookshelfViewMode } from '@/composables/bookshelf/useBookshelf'

withDefaults(defineProps<{
  bookCount: number
  viewMode: BookshelfViewMode
  sort: BookshelfSort
  collection?: 'books' | 'trash'
}>(), { collection: 'books' })
defineEmits<{
  create: []
  import: []
  'update:collection': ['books' | 'trash']
  'update:view-mode': [BookshelfViewMode]
  'update:sort': [BookshelfSort]
}>()
</script>

<style scoped>
.action-bar,
.action-group,
.view-switch,
.heading-group,
.collection-switch {
  display: flex;
  align-items: center;
}

.action-bar {
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 22px;
}

h1 {
  font-size: 24px;
}

p {
  margin-top: 4px;
  font-size: 13px;
}

.action-group {
  gap: 10px;
}

.heading-group { gap: 18px; }
.collection-switch { overflow: hidden; border: 1px solid var(--border-strong); border-radius: var(--radius-md); }
.collection-switch button { display: inline-flex; min-height: 34px; align-items: center; gap: 6px; padding: 0 10px; color: var(--text-secondary); background: var(--bg-surface); border: 0; cursor: pointer; }
.collection-switch button + button { border-left: 1px solid var(--border-subtle); }
.collection-switch button.active { color: var(--accent); background: var(--accent-soft); font-weight: 650; }

.sort-control select,
.view-switch,
.import-button,
.create-button {
  min-height: 36px;
  border-radius: var(--radius-md);
}

.sort-control select {
  padding: 0 30px 0 10px;
  color: var(--text-primary);
  background: var(--bg-surface);
  border: 1px solid var(--border-strong);
}

.view-switch {
  overflow: hidden;
  border: 1px solid var(--border-strong);
}

.view-switch button {
  display: grid;
  width: 36px;
  height: 34px;
  place-items: center;
  color: var(--text-secondary);
  background: var(--bg-surface);
  border: 0;
  cursor: pointer;
}

.view-switch button + button {
  border-left: 1px solid var(--border-subtle);
}

.view-switch button.active {
  color: var(--accent);
  background: var(--accent-soft);
}

.create-button,
.import-button {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 0 14px;
  color: var(--text-inverse);
  font-weight: 650;
  background: var(--accent);
  border: 1px solid var(--accent);
  cursor: pointer;
}

.import-button { color: var(--text-primary); background: var(--bg-surface); border: 1px solid var(--border-strong); }
.import-button:hover { color: var(--accent); border-color: var(--accent-border); }

.create-button:hover {
  background: var(--accent-hover);
}

@media (max-width: 640px) {
  .action-bar {
    align-items: flex-start;
    flex-direction: column;
  }

  .action-group {
    width: 100%;
    flex-wrap: wrap;
  }

  .sort-control {
    flex: 1;
  }

  .sort-control select {
    width: 100%;
  }

  .heading-group { width: 100%; justify-content: space-between; }
}
</style>
