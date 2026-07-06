import { computed, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { novelApi, type AnyRecord } from '@/api/modules/novel.api'
import { getSession } from '@/stores/session'

export interface BookshelfBook {
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

export interface BookFormState {
  title: string
  description: string
  genre: string
  tagsStr: string
}

export interface ParticleStyle {
  width: string
  height: string
  left: string
  bottom: string
  animationDuration: string
  animationDelay: string
  opacity: number
}

const coverGradients = [
  'linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%)',
  'linear-gradient(135deg, #2d1b2e 0%, #3d1f3d 50%, #1a0a2e 100%)',
  'linear-gradient(135deg, #1a2e1a 0%, #0d3b2e 50%, #0a2e1a 100%)',
  'linear-gradient(135deg, #2e2a1a 0%, #3d3520 50%, #2e1a0a 100%)',
  'linear-gradient(135deg, #1a2a2e 0%, #0d2b3b 50%, #0a1a2e 100%)',
]

const defaultGenre = '仙侠'

const buildDefaultForm = (): BookFormState => ({
  title: '',
  description: '',
  genre: defaultGenre,
  tagsStr: '',
})

const parseTags = (value: unknown) => {
  if (Array.isArray(value)) {
    return value.map((item) => String(item)).filter(Boolean)
  }

  return String(value || '')
    .split(/[,，]/)
    .map((item) => item.trim())
    .filter(Boolean)
}

const toBook = (item: AnyRecord, index: number): BookshelfBook => {
  const id = String(item.projectId ?? '')
  const title = String(item.title ?? item.name ?? `未命名作品-${index + 1}`)
  const description = String(item.description ?? item.summary ?? '')
  const genre = String(item.genre ?? item.category ?? '其他')

  return {
    id,
    title,
    description,
    genre,
    tags: parseTags(item.tags),
    wordCount: Number(item.wordCount ?? item.totalWords ?? 0),
    chapterCount: Number(item.chapterCount ?? item.totalChapters ?? 0),
    updatedAt: String(item.updatedAt ?? item.updateTime ?? '刚刚'),
    coverGradient: coverGradients[index % coverGradients.length],
  }
}

const createParticleStyle = (): ParticleStyle => ({
  width: `${Math.random() * 3 + 1}px`,
  height: `${Math.random() * 3 + 1}px`,
  left: `${Math.random() * 100}%`,
  bottom: '-5px',
  animationDuration: `${Math.random() * 12 + 12}s`,
  animationDelay: `${Math.random() * 15}s`,
  opacity: Math.random() * 0.3 + 0.1,
})

export const bookshelfGenres = ['仙侠', '玄幻', '都市', '科幻', '古风悬疑', '言情', '历史', '其他']

export const useBookshelf = () => {
  const session = getSession()
  const books = ref<BookshelfBook[]>([])
  const loading = ref(false)
  const saving = ref(false)
  const deleting = ref(false)
  const showEditorModal = ref(false)
  const showDeleteDialog = ref(false)
  const editingBook = ref<BookshelfBook | null>(null)
  const deletingBook = ref<BookshelfBook | null>(null)
  const bookForm = reactive<BookFormState>(buildDefaultForm())
  const particleStyles = ref<ParticleStyle[]>(Array.from({ length: 12 }, () => createParticleStyle()))

  const totalWords = computed(() => books.value.reduce((sum, book) => sum + book.wordCount, 0))
  const totalChapters = computed(() => books.value.reduce((sum, book) => sum + book.chapterCount, 0))
  const canSubmit = computed(() => bookForm.title.trim().length > 0 && !saving.value)

  const resetBookForm = () => {
    Object.assign(bookForm, buildDefaultForm())
  }

  const closeEditor = () => {
    showEditorModal.value = false
    editingBook.value = null
    resetBookForm()
  }

  const closeDeleteDialog = () => {
    showDeleteDialog.value = false
    deletingBook.value = null
  }

  const loadBooks = async () => {
    loading.value = true
    try {
      const list = await novelApi.listProjects()
      books.value = (list || [])
        .map((item, index) => toBook(item, index))
        .filter((item) => Number(item.id) > 0)
    } catch (error: any) {
      message.error(error?.message || '加载书架失败')
    } finally {
      loading.value = false
    }
  }

  const openCreateModal = () => {
    editingBook.value = null
    resetBookForm()
    showEditorModal.value = true
  }

  const openEditModal = (book: BookshelfBook) => {
    editingBook.value = book
    bookForm.title = book.title
    bookForm.description = book.description
    bookForm.genre = book.genre
    bookForm.tagsStr = book.tags.join(', ')
    showEditorModal.value = true
  }

  const openDeleteDialog = (book: BookshelfBook) => {
    deletingBook.value = book
    showDeleteDialog.value = true
  }

  const submitBook = async () => {
    if (!bookForm.title.trim()) {
      message.error('请输入书名')
      return
    }

    const tags = parseTags(bookForm.tagsStr)
    saving.value = true

    try {
      if (editingBook.value) {
        await novelApi.updateProject(editingBook.value.id, {
          title: bookForm.title,
          description: bookForm.description,
          genre: bookForm.genre,
          tags,
        })
        message.success('作品已更新')
      } else {
        await novelApi.createProject({
          title: bookForm.title,
          description: bookForm.description,
          genre: bookForm.genre,
          tags,
          ownerUserId: session.userId || undefined,
        })
        message.success('作品已创建')
      }

      await loadBooks()
      closeEditor()
    } catch (error: any) {
      message.error(error?.message || '保存作品失败')
    } finally {
      saving.value = false
    }
  }

  const confirmDelete = async () => {
    if (!deletingBook.value) {
      closeDeleteDialog()
      return
    }

    deleting.value = true
    try {
      await novelApi.deleteProject(deletingBook.value.id, session.userId || '')
      message.success('作品已删除')
      await loadBooks()
      closeDeleteDialog()
    } catch (error: any) {
      message.error(error?.message || '删除失败')
    } finally {
      deleting.value = false
    }
  }

  return {
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
    genres: bookshelfGenres,
    loadBooks,
    openCreateModal,
    openEditModal,
    closeEditor,
    submitBook,
    openDeleteDialog,
    closeDeleteDialog,
    confirmDelete,
  }
}
