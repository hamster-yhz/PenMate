<template>
  <div class="mybooks-page">
    <!-- Background particles -->
    <div class="particles" aria-hidden="true">
      <span v-for="n in 12" :key="n" class="p-dot" :style="pStyle(n)"></span>
    </div>

    <!-- Top Nav -->
    <nav class="page-nav">
      <div class="nav-left">
        <img :src="logoImg" alt="PenMate" class="nav-logo" @click="$router.push('/')" />
        <span class="nav-brand">笔友 · 书架</span>
      </div>
      <div class="nav-right">
        <button class="nav-btn" @click="$router.push('/domain-console')">
          <span>🧪 三域控台</span>
        </button>
        <button class="nav-btn" @click="$router.push('/profile')">
          <div class="avatar-sm">{{ userInfo.name.charAt(0) }}</div>
          <span>{{ userInfo.name }}</span>
        </button>
      </div>
    </nav>

    <!-- Page Content -->
    <div class="page-body">
      <!-- Stats Bar -->
      <div class="stats-bar">
        <div class="stat">
          <span class="stat-val">{{ books.length }}</span>
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

      <!-- Action Bar -->
      <div class="action-bar">
        <h2 class="page-title">我 的 书 架</h2>
        <button class="btn-new-book" @click="showCreateModal = true">
          <span>+ 创建新书</span>
        </button>
      </div>

      <!-- Book Grid -->
      <div class="book-grid">
        <div
          v-for="book in books"
          :key="book.id"
          class="book-card"
          @click="openBook(book)"
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
              <span class="b-tag" v-for="tag in book.tags" :key="tag">{{ tag }}</span>
            </div>
          </div>
          <div class="book-actions" @click.stop>
            <button class="ba-btn" @click="editBook(book)" title="编辑">✏️</button>
            <button class="ba-btn danger" @click="deleteBook(book)" title="删除">🗑️</button>
          </div>
        </div>

        <!-- Empty state -->
        <div v-if="books.length === 0" class="empty-state">
          <span class="empty-icon">📚</span>
          <p>你的书架空空如也</p>
          <p class="empty-sub">点击「创建新书」开始你的创作之旅</p>
        </div>
      </div>
    </div>

    <!-- Create / Edit Book Modal -->
    <div class="modal-overlay" v-if="showCreateModal" @click.self="showCreateModal = false">
      <div class="modal-card glass-panel">
        <div class="modal-glow"></div>
        <h3 class="modal-title">{{ editingBook ? '编辑作品' : '创建新书' }}</h3>

        <div class="modal-form">
          <div class="form-row">
            <label>书名</label>
            <input v-model="bookForm.title" type="text" class="f-input" placeholder="为你的作品取一个名字" />
          </div>
          <div class="form-row">
            <label>简介</label>
            <textarea v-model="bookForm.description" class="f-input f-textarea" placeholder="简要描述你的故事..." rows="3"></textarea>
          </div>
          <div class="form-row">
            <label>类型</label>
            <div class="genre-options">
              <button
                v-for="g in genres"
                :key="g"
                class="genre-btn"
                :class="{ active: bookForm.genre === g }"
                @click="bookForm.genre = g"
              >{{ g }}</button>
            </div>
          </div>
          <div class="form-row">
            <label>标签（逗号分隔）</label>
            <input v-model="bookForm.tagsStr" type="text" class="f-input" placeholder="修仙, 热血, 轻松" />
          </div>
        </div>

        <div class="modal-actions">
          <button class="btn-cancel" @click="showCreateModal = false; editingBook = null">取消</button>
          <button class="btn-confirm" @click="confirmBook">{{ editingBook ? '保存' : '创建' }}</button>
        </div>
      </div>
    </div>

    <!-- Delete Confirm -->
    <div class="modal-overlay" v-if="showDeleteConfirm" @click.self="showDeleteConfirm = false">
      <div class="modal-card glass-panel small">
        <h3 class="modal-title">确认删除</h3>
        <p class="delete-msg">确定要删除「{{ deletingBook?.title }}」吗？此操作不可撤销。</p>
        <div class="modal-actions">
          <button class="btn-cancel" @click="showDeleteConfirm = false">取消</button>
          <button class="btn-confirm danger" @click="confirmDelete">删除</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import logoImg from '@/assets/images/logo.png'
import { novelApi } from '@/api/modules/novel.api'
import { getSession } from '@/stores/session'

const router = useRouter()

const session = getSession()

const userInfo = reactive({
  name: '墨客',
  email: 'moke@penmate.com'
})

interface Book {
  id: string
  title: string
  description: string
  genre: string
  tags: string[]
  wordCount: number
  chapterCount: number
  updatedAt: string
  coverGradient: string
}

const coverGradients = [
  'linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%)',
  'linear-gradient(135deg, #2d1b2e 0%, #3d1f3d 50%, #1a0a2e 100%)',
  'linear-gradient(135deg, #1a2e1a 0%, #0d3b2e 50%, #0a2e1a 100%)',
  'linear-gradient(135deg, #2e2a1a 0%, #3d3520 50%, #2e1a0a 100%)',
  'linear-gradient(135deg, #1a2a2e 0%, #0d2b3b 50%, #0a1a2e 100%)'
]

const books = ref<Book[]>([])

const toBook = (item: Record<string, any>, idx: number): Book => {
  const id = String(item.projectId ?? item.id ?? `book-${idx}`)
  const title = String(item.title ?? item.name ?? `未命名作品-${idx + 1}`)
  const description = String(item.description ?? item.summary ?? '')
  const genre = String(item.genre ?? item.category ?? '其他')
  const tagsRaw = item.tags
  const tags = Array.isArray(tagsRaw)
    ? tagsRaw.map((v: unknown) => String(v))
    : String(tagsRaw || '')
      .split(/[,，]/)
      .map((v) => v.trim())
      .filter(Boolean)
  return {
    id,
    title,
    description,
    genre,
    tags,
    wordCount: Number(item.wordCount ?? item.totalWords ?? 0),
    chapterCount: Number(item.chapterCount ?? item.totalChapters ?? 0),
    updatedAt: String(item.updatedAt ?? item.updateTime ?? '刚刚'),
    coverGradient: coverGradients[idx % coverGradients.length]
  }
}

const loadBooks = async () => {
  try {
    const list = await novelApi.listProjects()
    books.value = (list || []).map((item, idx) => toBook(item as Record<string, any>, idx))
  } catch (error: any) {
    message.error(error?.message || '加载书架失败')
  }
}

const totalWords = computed(() => books.value.reduce((s, b) => s + b.wordCount, 0))
const totalChapters = computed(() => books.value.reduce((s, b) => s + b.chapterCount, 0))

const genres = ['仙侠', '玄幻', '都市', '科幻', '古风悬疑', '言情', '历史', '其他']

const showCreateModal = ref(false)
const editingBook = ref<Book | null>(null)
const bookForm = reactive({
  title: '',
  description: '',
  genre: '仙侠',
  tagsStr: ''
})

const showDeleteConfirm = ref(false)
const deletingBook = ref<Book | null>(null)

const openBook = (book: Book) => {
  router.push({
    path: '/workbench',
    query: {
      bookId: book.id,
      ...(session.userId ? { operatorId: String(session.userId) } : {})
    }
  })
}

const editBook = (book: Book) => {
  editingBook.value = book
  bookForm.title = book.title
  bookForm.description = book.description
  bookForm.genre = book.genre
  bookForm.tagsStr = book.tags.join(', ')
  showCreateModal.value = true
}

const confirmBook = async () => {
  if (!bookForm.title.trim()) return
  const tags = bookForm.tagsStr.split(/[,，]/).map(t => t.trim()).filter(Boolean)

  try {
    if (editingBook.value) {
      await novelApi.updateProject(editingBook.value.id, {
        title: bookForm.title,
        description: bookForm.description,
        genre: bookForm.genre,
        tags
      })
      message.success('作品已更新')
    } else {
      await novelApi.createProject({
        title: bookForm.title,
        description: bookForm.description,
        genre: bookForm.genre,
        tags,
        ownerId: session.userId
      })
      message.success('作品已创建')
    }
    await loadBooks()
  } catch (error: any) {
    message.error(error?.message || '保存作品失败')
    return
  }

  showCreateModal.value = false
  editingBook.value = null
  bookForm.title = ''
  bookForm.description = ''
  bookForm.genre = '仙侠'
  bookForm.tagsStr = ''
}

const deleteBook = (book: Book) => {
  deletingBook.value = book
  showDeleteConfirm.value = true
}

const confirmDelete = async () => {
  if (deletingBook.value) {
    try {
      await novelApi.deleteProject(deletingBook.value.id, session.userId || 0)
      message.success('作品已删除')
      await loadBooks()
    } catch (error: any) {
      message.error(error?.message || '删除失败')
      return
    }
  }
  showDeleteConfirm.value = false
  deletingBook.value = null
}

const pStyle = (_n: number) => ({
  width: `${Math.random() * 3 + 1}px`,
  height: `${Math.random() * 3 + 1}px`,
  left: `${Math.random() * 100}%`,
  bottom: '-5px',
  animationDuration: `${Math.random() * 12 + 12}s`,
  animationDelay: `${Math.random() * 15}s`,
  opacity: Math.random() * 0.3 + 0.1
})

onMounted(() => {
  if (session.userName) userInfo.name = session.userName
  if (session.userEmail) userInfo.email = session.userEmail
  loadBooks()
})
</script>

<style lang="less" scoped>
.mybooks-page {
  min-height: 100vh;
  background: var(--bg-primary);
  position: relative;
}

.particles { position: fixed; inset: 0; pointer-events: none; z-index: 0; }
.p-dot {
  position: absolute;
  border-radius: 50%;
  background: radial-gradient(circle, var(--amber-gold), transparent);
  animation: particleDrift linear infinite;
}

/* Nav */
.page-nav {
  position: sticky;
  top: 0;
  z-index: 50;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 32px;
  background: rgba(11,17,32,0.9);
  backdrop-filter: blur(16px);
  border-bottom: 1px solid var(--border-subtle);
}

.nav-left { display: flex; align-items: center; gap: 12px; }
.nav-logo { width: 32px; height: 32px; border-radius: 50%; object-fit: cover; cursor: pointer; }
.nav-brand {
  font-family: var(--font-heading);
  font-size: 1.1rem;
  color: var(--amber-gold);
  letter-spacing: 0.2em;
}

.nav-right { display: flex; align-items: center; }
.nav-btn {
  display: flex; align-items: center; gap: 8px;
  padding: 6px 14px;
  background: none; border: 1px solid var(--border-subtle);
  border-radius: 20px; color: var(--text-secondary);
  font-size: 0.85rem; cursor: pointer;
  transition: all 0.3s;
  &:hover { border-color: var(--border-gold); color: var(--amber-gold); }
}

.avatar-sm {
  width: 26px; height: 26px; border-radius: 50%;
  background: linear-gradient(135deg, rgba(201,169,110,0.3), rgba(201,169,110,0.1));
  border: 1px solid var(--border-gold);
  display: flex; align-items: center; justify-content: center;
  font-family: var(--font-heading); font-size: 0.8rem; color: var(--amber-gold);
}

/* Body */
.page-body {
  position: relative; z-index: 1;
  max-width: 1100px;
  margin: 0 auto;
  padding: 32px 24px 64px;
}

.stats-bar {
  display: flex; align-items: center; gap: 24px;
  padding: 16px 24px;
  background: rgba(17,24,39,0.5);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  margin-bottom: 32px;
}

.stat { display: flex; flex-direction: column; align-items: center; gap: 2px; }
.stat-val { font-family: var(--font-heading); font-size: 1.3rem; color: var(--amber-gold); letter-spacing: 0.1em; }
.stat-lbl { font-size: 0.72rem; color: var(--text-muted); }
.stat-sep { width: 1px; height: 28px; background: var(--border-subtle); }

.action-bar {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 24px;
}

.page-title {
  font-family: var(--font-heading);
  font-size: 1.5rem;
  color: var(--xuan-paper);
  letter-spacing: 0.25em;
}

.btn-new-book {
  padding: 10px 24px;
  font-family: var(--font-heading);
  font-size: 0.95rem;
  letter-spacing: 0.15em;
  color: var(--amber-gold);
  background: linear-gradient(135deg, rgba(201,169,110,0.15), rgba(201,169,110,0.05));
  border: 1px solid var(--border-gold);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
  &:hover { box-shadow: var(--shadow-gold); color: var(--xuan-paper); transform: translateY(-2px); }
}

/* Book Grid */
.book-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
}

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
    .book-actions { opacity: 1; }
    .book-cover .cover-title { text-shadow: 0 0 16px rgba(201,169,110,0.5); }
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
    content: ''; position: absolute; inset: 0;
    background: linear-gradient(180deg, transparent 50%, rgba(11,17,32,0.6) 100%);
  }
}

.cover-title {
  font-family: var(--font-heading);
  font-size: 1.4rem;
  color: var(--xuan-paper);
  letter-spacing: 0.2em;
  z-index: 1;
  text-shadow: 0 2px 8px rgba(0,0,0,0.5);
  transition: text-shadow 0.3s;
}

.cover-genre {
  font-size: 0.72rem;
  color: rgba(245,237,214,0.6);
  letter-spacing: 0.1em;
  z-index: 1;
  padding: 2px 10px;
  border: 1px solid rgba(245,237,214,0.2);
  border-radius: 10px;
}

.book-info { padding: 16px; }
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

.book-tags { display: flex; gap: 6px; flex-wrap: wrap; }
.b-tag {
  padding: 2px 8px;
  font-size: 0.68rem;
  color: var(--amber-gold);
  background: rgba(201,169,110,0.06);
  border: 1px solid rgba(201,169,110,0.12);
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
  width: 28px; height: 28px;
  display: flex; align-items: center; justify-content: center;
  background: rgba(11,17,32,0.8);
  backdrop-filter: blur(8px);
  border: 1px solid var(--border-subtle);
  border-radius: 6px;
  font-size: 0.75rem;
  cursor: pointer;
  transition: all 0.2s;
  &:hover { border-color: var(--border-gold); }
  &.danger:hover { border-color: rgba(192,60,45,0.5); }
}

.empty-state {
  grid-column: 1 / -1;
  text-align: center;
  padding: 60px 20px;
  color: var(--text-muted);
}
.empty-icon { font-size: 3rem; display: block; margin-bottom: 16px; }
.empty-sub { font-size: 0.82rem; margin-top: 4px; }

/* Modal */
.modal-overlay {
  position: fixed; inset: 0; z-index: 1000;
  display: flex; align-items: center; justify-content: center;
  background: rgba(0,0,0,0.6);
  backdrop-filter: blur(6px);
}

.modal-card {
  position: relative;
  width: 520px; max-width: 92vw;
  padding: 28px 32px;
  background: rgba(17,24,39,0.92);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(201,169,110,0.2);
  border-radius: 16px;
  animation: fadeInUp 0.3s ease;
  &.small { width: 380px; }
}

.modal-glow {
  position: absolute; top: 0; left: 15%; right: 15%; height: 1px;
  background: linear-gradient(90deg, transparent, var(--amber-gold), transparent);
  opacity: 0.4;
}

.modal-title {
  font-family: var(--font-heading);
  font-size: 1.2rem; color: var(--xuan-paper);
  letter-spacing: 0.15em; margin-bottom: 20px;
}

.modal-form { display: flex; flex-direction: column; gap: 16px; }

.form-row {
  display: flex; flex-direction: column; gap: 6px;
  label {
    font-size: 0.82rem; color: var(--text-secondary); letter-spacing: 0.08em;
  }
}

.f-input {
  padding: 10px 14px;
  background: rgba(11,17,32,0.6);
  border: 1px solid var(--border-subtle);
  border-radius: 8px;
  color: var(--text-primary);
  font-family: var(--font-body);
  font-size: 0.9rem;
  outline: none;
  transition: border-color 0.3s;
  &:focus { border-color: var(--border-gold); }
  &::placeholder { color: var(--text-muted); }
}

.f-textarea { resize: vertical; }

.genre-options { display: flex; flex-wrap: wrap; gap: 8px; }
.genre-btn {
  padding: 5px 14px; font-size: 0.78rem;
  color: var(--text-secondary);
  background: rgba(11,17,32,0.5);
  border: 1px solid var(--border-subtle);
  border-radius: 14px; cursor: pointer;
  transition: all 0.3s;
  &:hover { border-color: var(--border-gold); color: var(--amber-gold); }
  &.active {
    color: var(--amber-gold);
    background: rgba(201,169,110,0.12);
    border-color: var(--border-gold);
  }
}

.modal-actions {
  display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px;
}

.btn-cancel, .btn-confirm {
  padding: 8px 22px; font-size: 0.88rem;
  border-radius: 6px; cursor: pointer;
  transition: all 0.3s; letter-spacing: 0.1em;
}

.btn-cancel {
  background: none; border: 1px solid var(--border-subtle);
  color: var(--text-muted);
  &:hover { border-color: var(--border-gold); color: var(--text-secondary); }
}

.btn-confirm {
  color: var(--amber-gold);
  background: linear-gradient(135deg, rgba(201,169,110,0.15), rgba(201,169,110,0.05));
  border: 1px solid var(--border-gold);
  &:hover { box-shadow: var(--shadow-gold); color: var(--xuan-paper); }
  &.danger {
    color: #e8a87c;
    background: rgba(192,60,45,0.15);
    border-color: rgba(192,60,45,0.4);
    &:hover { background: rgba(192,60,45,0.25); }
  }
}

.delete-msg {
  font-size: 0.9rem; color: var(--text-secondary);
  line-height: 1.6; margin-bottom: 8px;
}
</style>
