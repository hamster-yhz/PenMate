<template>
  <div class="app-page bookshelf-page">
    <AppTopbar
      context-title="书架"
      searchable
      :search-value="searchQuery"
      search-placeholder="搜索作品、类型或标签"
      :show-admin="canAccessAdmin"
      @update:search-value="searchQuery = $event"
    />

    <main class="app-content">
      <BookActionBar
        :book-count="collection === 'trash' ? trashBooks.length : books.length"
        :view-mode="viewMode"
        :sort="sort"
        :collection="collection"
        @create="openCreateModal"
        @import="openImportDialog"
        @update:collection="setCollection"
        @update:view-mode="setViewMode"
        @update:sort="setSort"
      />

      <section v-if="collection === 'books' && loading" class="book-grid" :class="viewMode" aria-label="正在加载作品">
        <div v-for="item in 6" :key="item" class="book-skeleton surface">
          <div class="skeleton-cover"></div>
          <div class="skeleton-copy">
            <span class="skeleton-line title"></span>
            <span class="skeleton-line"></span>
            <span class="skeleton-line short"></span>
          </div>
        </div>
      </section>

      <section v-else-if="collection === 'books' && loadError" class="error-panel surface" role="alert">
        <WarningOutlined class="state-icon" />
        <p>{{ loadError }}</p>
        <button type="button" @click="loadBooks"><ReloadOutlined />重试</button>
      </section>

      <section v-else-if="collection === 'books' && visibleBooks.length" class="book-grid" :class="viewMode" aria-label="作品列表">
        <BookCard
          v-for="book in visibleBooks"
          :key="book.id"
          :book="book"
          :view-mode="viewMode"
          @open="openBook"
          @settings="openProjectSettings"
          @delete="openDeleteDialog"
        />
      </section>

      <section v-else-if="collection === 'books'" class="empty-panel surface">
        <BookOutlined class="state-icon" />
        <h2>{{ searchQuery ? '没有匹配的作品' : '开始你的第一部作品' }}</h2>
        <p>{{ searchQuery ? '尝试更换关键词。' : '创建后会自动生成第一卷和第一章。' }}</p>
        <button v-if="!searchQuery" type="button" class="empty-create" @click="openCreateModal">
          <PlusOutlined />新建作品
        </button>
      </section>

      <section v-else-if="trashLoading" class="trash-list" aria-label="正在加载回收站">
        <div v-for="item in 4" :key="item" class="trash-skeleton surface">
          <span></span><div><i></i><i></i></div>
        </div>
      </section>

      <section v-else-if="trashError" class="error-panel surface" role="alert">
        <WarningOutlined class="state-icon" />
        <p>{{ trashError }}</p>
        <button type="button" @click="loadTrash"><ReloadOutlined />重试</button>
      </section>

      <template v-else-if="visibleTrashBooks.length">
        <p v-if="trashActionError" class="trash-action-error" role="alert">{{ trashActionError }}</p>
        <section class="trash-list" aria-label="回收站作品">
          <TrashBookRow
            v-for="book in visibleTrashBooks"
            :key="book.id"
            :book="book"
            :restoring="restoringId === book.id"
            :deleting="permanentlyDeletingId === book.id"
            @restore="handleRestoreBook"
            @permanent-delete="openPermanentDeleteDialog"
          />
        </section>
      </template>

      <section v-else class="empty-panel surface">
        <DeleteOutlined class="state-icon" />
        <h2>{{ searchQuery ? '没有匹配的已删除作品' : '回收站是空的' }}</h2>
        <p>{{ searchQuery ? '尝试更换关键词。' : '移入回收站的作品会在这里保留 30 天。' }}</p>
      </section>
    </main>

    <BookEditorModal
      :visible="showEditorModal"
      :form="bookForm"
      :genres="genres"
      :can-submit="canSubmit"
      :saving="saving"
      :error="createError"
      @update:visible="handleEditorVisibilityChange"
      @submit="handleCreateBook"
    />

    <BookImportDialog
      :visible="showImportDialog"
      @close="closeImportDialog"
      @imported="handleImportedProject"
    />

    <DeleteBookDialog
      :visible="showDeleteDialog"
      :deleting="deleting"
      :book="deletingBook"
      :error="deleteError"
      @update:visible="handleDeleteVisibilityChange"
      @confirm="handleConfirmDelete"
    />

    <PermanentlyDeleteBookDialog
      :visible="showPermanentDeleteDialog"
      :book="permanentDeleteBook"
      :deleting="Boolean(permanentlyDeletingId)"
      :error="trashActionError"
      @close="closePermanentDeleteDialog"
      @confirm="handlePermanentDelete"
    />

    <div v-if="lastDeletedBook" class="undo-toast" role="status">
      <span>“{{ lastDeletedBook.title }}”已移入回收站</span>
      <button type="button" :disabled="undoDeleteBusy" @click="handleUndoDelete">
        <LoadingOutlined v-if="undoDeleteBusy" spin />
        <RollbackOutlined v-else />
        {{ undoDeleteBusy ? '正在恢复' : '撤销' }}
      </button>
      <button type="button" class="dismiss-toast" title="关闭" :disabled="undoDeleteBusy" @click="dismissDeleteUndo">
        <CloseOutlined />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { BookOutlined, CloseOutlined, DeleteOutlined, LoadingOutlined, PlusOutlined, ReloadOutlined, RollbackOutlined, WarningOutlined } from '@ant-design/icons-vue'
import AppTopbar from '@/components/app/AppTopbar.vue'
import BookActionBar from '@/components/bookshelf/BookActionBar.vue'
import BookCard from '@/components/bookshelf/BookCard.vue'
import BookEditorModal from '@/components/bookshelf/BookEditorModal.vue'
import BookImportDialog from '@/components/bookshelf/BookImportDialog.vue'
import DeleteBookDialog from '@/components/bookshelf/DeleteBookDialog.vue'
import PermanentlyDeleteBookDialog from '@/components/bookshelf/PermanentlyDeleteBookDialog.vue'
import TrashBookRow from '@/components/bookshelf/TrashBookRow.vue'
import { useBookshelfPage } from '@/features/bookshelf/useBookshelfPage'

const {
  books,
  trashBooks,
  visibleBooks,
  visibleTrashBooks,
  loading,
  loadError,
  saving,
  deleting,
  trashLoading,
  trashError,
  restoringId,
  permanentlyDeletingId,
  lastDeletedBook,
  undoDeleteBusy,
  searchQuery,
  viewMode,
  sort,
  showEditorModal,
  showDeleteDialog,
  showImportDialog,
  deletingBook,
  bookForm,
  canSubmit,
  genres,
  loadBooks,
  loadTrash,
  setViewMode,
  setSort,
  openCreateModal,
  openDeleteDialog,
  createError,
  deleteError,
  trashActionError,
  collection,
  permanentDeleteBook,
  showPermanentDeleteDialog,
  canAccessAdmin,
  openBook,
  openProjectSettings,
  openImportDialog,
  closeImportDialog,
  handleImportedProject,
  handleCreateBook,
  handleConfirmDelete,
  setCollection,
  handleRestoreBook,
  openPermanentDeleteDialog,
  closePermanentDeleteDialog,
  handlePermanentDelete,
  handleUndoDelete,
  dismissDeleteUndo,
  handleEditorVisibilityChange,
  handleDeleteVisibilityChange,
} = useBookshelfPage()
</script>

<style scoped>
.book-grid.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(174px, 1fr));
  gap: 18px;
}

.book-grid.list {
  display: grid;
  gap: 10px;
}

.trash-list { display: grid; gap: 9px; }
.trash-action-error { margin-bottom: 10px; padding: 9px 11px; color: var(--danger); background: var(--danger-soft); border: 1px solid var(--danger-border); font-size: 12px; }
.trash-skeleton { display: grid; grid-template-columns: 58px 1fr; gap: 14px; padding: 12px; }
.trash-skeleton > span { aspect-ratio: 2 / 3; background: var(--bg-muted); animation: skeleton-pulse 1.4s ease-in-out infinite; }
.trash-skeleton > div { display: grid; align-content: center; gap: 10px; }
.trash-skeleton i { display: block; width: min(320px, 70%); height: 12px; background: var(--bg-muted); animation: skeleton-pulse 1.4s ease-in-out infinite; }
.trash-skeleton i + i { width: min(220px, 45%); }
.undo-toast { position: fixed; right: 22px; bottom: 22px; z-index: 500; display: flex; max-width: min(460px, calc(100vw - 28px)); align-items: center; gap: 12px; padding: 10px 10px 10px 14px; color: var(--text-primary); background: var(--bg-elevated); border: 1px solid var(--border-strong); border-radius: var(--radius-md); box-shadow: var(--shadow-lg); font-size: 12px; }
.undo-toast > span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.undo-toast button { display: inline-flex; flex: 0 0 auto; min-height: 30px; align-items: center; gap: 5px; padding: 0 8px; color: var(--accent); background: transparent; border: 0; border-radius: var(--radius-sm); cursor: pointer; }
.undo-toast button:hover { background: var(--accent-soft); }
.undo-toast .dismiss-toast { width: 30px; padding: 0; justify-content: center; color: var(--text-muted); }

.book-skeleton {
  overflow: hidden;
}

.grid .book-skeleton .skeleton-cover {
  aspect-ratio: 2 / 3;
  background: var(--bg-muted);
  animation: skeleton-pulse 1.4s ease-in-out infinite;
}

.list .book-skeleton {
  display: grid;
  grid-template-columns: 68px 1fr;
  min-height: 102px;
}

.list .book-skeleton .skeleton-cover {
  background: var(--bg-muted);
}

.skeleton-copy {
  display: grid;
  align-content: start;
  gap: 10px;
  padding: 15px;
}

.skeleton-line.title { width: 68%; }
.skeleton-line.short { width: 44%; }

.state-icon {
  color: var(--text-muted);
  font-size: 28px;
}

.empty-panel {
  gap: 9px;
}

.empty-panel h2 {
  font-size: 17px;
}

.empty-panel p {
  font-size: 13px;
}

.empty-create,
.error-panel button {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 36px;
  margin-top: 8px;
  padding: 0 13px;
  color: var(--text-inverse);
  background: var(--accent);
  border: 1px solid var(--accent);
  border-radius: var(--radius-md);
  cursor: pointer;
}

@media (max-width: 560px) {
  .book-grid.grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }
  .undo-toast { right: 14px; bottom: 14px; left: 14px; }
}
</style>
