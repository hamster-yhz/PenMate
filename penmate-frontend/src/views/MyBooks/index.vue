<template>
  <div class="mybooks-page">
    <div class="particles" aria-hidden="true">
      <span v-for="(style, index) in particleStyles" :key="index" class="p-dot" :style="style"></span>
    </div>

    <nav class="page-nav">
      <div class="nav-left">
        <img :src="logoImg" alt="PenMate" class="nav-logo" @click="router.push('/')" />
        <span class="nav-brand">笔友 · 书架</span>
      </div>
      <div class="nav-right">
        <button v-if="canAccessRbacAdmin" class="nav-btn" @click="router.push('/admin/rbac')">
          <span>🛡️ RBAC 管理</span>
        </button>
        <button class="nav-btn" @click="router.push('/profile')">
          <div class="avatar-sm">{{ userInfo.name.charAt(0) }}</div>
          <span>{{ userInfo.name }}</span>
        </button>
      </div>
    </nav>

    <div class="page-body">
      <BookStatsBar :book-count="books.length" :total-words="totalWords" :total-chapters="totalChapters" />

      <BookActionBar @create="openCreateModal" />

      <div class="book-grid">
        <BookCard
          v-for="book in books"
          :key="book.id"
          :book="book"
          @open="openBook"
          @edit="openEditModal"
          @delete="openDeleteDialog"
        />

        <div v-if="!loading && books.length === 0" class="empty-state">
          <span class="empty-icon">📚</span>
          <p>你的书架空空如也</p>
          <p class="empty-sub">点击「创建新书」开始你的创作之旅</p>
        </div>
      </div>
    </div>

    <BookEditorModal
      :visible="showEditorModal"
      :editing="Boolean(editingBook)"
      :form="bookForm"
      :genres="genres"
      :can-submit="canSubmit"
      :saving="saving"
      @update:visible="handleEditorVisibilityChange"
      @submit="submitBook"
    />

    <DeleteBookDialog
      :visible="showDeleteDialog"
      :deleting="deleting"
      :book="deletingBook"
      @update:visible="handleDeleteVisibilityChange"
      @confirm="confirmDelete"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import logoImg from '@/assets/images/logo.png'
import { rbacApi } from '@/api/modules/rbac.api'
import BookActionBar from '@/components/bookshelf/BookActionBar.vue'
import BookCard from '@/components/bookshelf/BookCard.vue'
import BookEditorModal from '@/components/bookshelf/BookEditorModal.vue'
import BookStatsBar from '@/components/bookshelf/BookStatsBar.vue'
import DeleteBookDialog from '@/components/bookshelf/DeleteBookDialog.vue'
import { useBookshelf, type BookshelfBook } from '@/composables/bookshelf/useBookshelf'
import { getSession } from '@/stores/session'

const router = useRouter()
const session = getSession()
const adminMenuPaths = ref<string[]>([])
const canAccessRbacAdmin = computed(() => adminMenuPaths.value.includes('/admin/rbac'))

const userInfo = reactive({
  name: '墨客',
  email: 'moke@penmate.com',
})

const {
  books,
  loading,
  saving,
  deleting,
  totalWords,
  totalChapters,
  showEditorModal,
  showDeleteDialog,
  editingBook,
  deletingBook,
  bookForm,
  particleStyles,
  canSubmit,
  genres,
  loadBooks,
  openCreateModal,
  openEditModal,
  closeEditor,
  submitBook,
  openDeleteDialog,
  closeDeleteDialog,
  confirmDelete,
} = useBookshelf()

const openBook = (book: BookshelfBook) => {
  router.push({
    path: '/workbench',
    query: {
      projectId: book.id,
      ...(session.userId ? { operatorId: String(session.userId) } : {}),
    },
  })
}

const handleEditorVisibilityChange = (visible: boolean) => {
  if (!visible) {
    closeEditor()
  }
}

const handleDeleteVisibilityChange = (visible: boolean) => {
  if (!visible) {
    closeDeleteDialog()
  }
}

onMounted(() => {
  if (session.userName) userInfo.name = session.userName
  if (session.userEmail) userInfo.email = session.userEmail
  if (session.userId) {
    void rbacApi
      .listProfileMenus(session.userId)
      .then((menus) => {
        adminMenuPaths.value = (menus || [])
          .map((menu) => String((menu as Record<string, unknown>)?.path || ''))
          .filter(Boolean)
      })
      .catch(() => {
        adminMenuPaths.value = []
      })
  }
  loadBooks()
})
</script>

<style lang="less">
.mybooks-page {
  min-height: 100vh;
  background: var(--bg-primary);
  position: relative;
}

.particles {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}

.p-dot {
  position: absolute;
  border-radius: 50%;
  background: radial-gradient(circle, var(--amber-gold), transparent);
  animation: particleDrift linear infinite;
}

.page-nav {
  position: sticky;
  top: 0;
  z-index: 50;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 32px;
  background: rgba(11, 17, 32, 0.9);
  backdrop-filter: blur(16px);
  border-bottom: 1px solid var(--border-subtle);
}

.nav-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.nav-logo {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
  cursor: pointer;
}

.nav-brand {
  font-family: var(--font-heading);
  font-size: 1.1rem;
  color: var(--amber-gold);
  letter-spacing: 0.2em;
}

.nav-right {
  display: flex;
  align-items: center;
}

.nav-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  background: none;
  border: 1px solid var(--border-subtle);
  border-radius: 20px;
  color: var(--text-secondary);
  font-size: 0.85rem;
  cursor: pointer;
  transition: all 0.3s;

  &:hover {
    border-color: var(--border-gold);
    color: var(--amber-gold);
  }
}

.avatar-sm {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(201, 169, 110, 0.3), rgba(201, 169, 110, 0.1));
  border: 1px solid var(--border-gold);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-heading);
  font-size: 0.8rem;
  color: var(--amber-gold);
}

.page-body {
  position: relative;
  z-index: 1;
  max-width: 1100px;
  margin: 0 auto;
  padding: 32px 24px 64px;
}

.book-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
}

.empty-state {
  grid-column: 1 / -1;
  text-align: center;
  padding: 60px 20px;
  color: var(--text-muted);
}

.empty-icon {
  font-size: 3rem;
  display: block;
  margin-bottom: 16px;
}

.empty-sub {
  font-size: 0.82rem;
  margin-top: 4px;
}
</style>
