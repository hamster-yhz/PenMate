import { computed, getCurrentScope, onScopeDispose, reactive, ref } from 'vue'
import { novelApi, type AnyRecord } from '@/api/modules/novel.api'
import { getSession } from '@/stores/session'
import { getErrorMessage } from '@/utils/errors'

export type BookshelfViewMode = 'grid' | 'list'
export type BookshelfSort = 'updated-desc' | 'title-asc' | 'words-desc'

export interface BookshelfBook {
  id: string
  title: string
  description: string
  genre: string
  tags: string[]
  wordCount: number
  chapterCount: number
  updatedAt: string
  updatedAtValue: number
  coverUrl?: string
  coverTone: string
  deletedAt?: string
  remainingDays?: number
}

export interface BookFormState {
  title: string
  description: string
  genre: string
  tagsStr: string
}

const VIEW_KEY = 'penmate.bookshelf.view'
const SORT_KEY = 'penmate.bookshelf.sort'
const coverTones = ['forest', 'ink', 'plum', 'ocean', 'graphite'] as const
const defaultGenre = '玄幻'

const buildDefaultForm = (): BookFormState => ({
  title: '',
  description: '',
  genre: defaultGenre,
  tagsStr: '',
})

const parseTags = (value: unknown) => {
  const source = Array.isArray(value) ? value.map(String) : String(value || '').split(/[,，]/)
  return [...new Set(source.map((item) => item.trim()).filter(Boolean))].slice(0, 10)
}

const stableTone = (title: string) => {
  const total = [...title].reduce((sum, character) => sum + (character.codePointAt(0) || 0), 0)
  return coverTones[total % coverTones.length]
}

const toTimestamp = (value: unknown) => {
  const timestamp = Date.parse(String(value || ''))
  return Number.isFinite(timestamp) ? timestamp : 0
}

const toBook = (item: AnyRecord, index: number): BookshelfBook => {
  const id = String(item.projectId ?? '')
  const title = String(item.title ?? item.name ?? `未命名作品-${index + 1}`)
  const updatedAt = String(item.updatedAt ?? item.updateTime ?? '')
  return {
    id,
    title,
    description: String(item.summary ?? item.description ?? ''),
    genre: String(item.genre ?? item.category ?? '其他'),
    tags: parseTags(item.tags),
    wordCount: Number(item.totalWords ?? item.wordCount ?? 0),
    chapterCount: Number(item.totalChapters ?? item.chapterCount ?? 0),
    updatedAt,
    updatedAtValue: toTimestamp(updatedAt),
    coverUrl: typeof item.coverUrl === 'string' && item.coverUrl.trim() ? item.coverUrl : undefined,
    coverTone: stableTone(title),
    deletedAt: typeof item.deletedAt === 'string' ? item.deletedAt : undefined,
    remainingDays: remainingTrashDays(item.deletedAt),
  }
}

const remainingTrashDays = (deletedAt: unknown) => {
  const deletedAtValue = toTimestamp(deletedAt)
  if (!deletedAtValue) return undefined
  const expiresAt = deletedAtValue + 30 * 24 * 60 * 60 * 1000
  return Math.max(0, Math.ceil((expiresAt - Date.now()) / (24 * 60 * 60 * 1000)))
}

const initialViewMode = (): BookshelfViewMode =>
  localStorage.getItem(VIEW_KEY) === 'list' ? 'list' : 'grid'

const initialSort = (): BookshelfSort => {
  const value = localStorage.getItem(SORT_KEY)
  return value === 'title-asc' || value === 'words-desc' ? value : 'updated-desc'
}

export const bookshelfGenres = [
  '玄幻',
  '奇幻',
  '武侠',
  '仙侠',
  '都市',
  '历史',
  '科幻',
  '悬疑',
  '言情',
  '现实',
  '轻小说',
  '其他',
]

export const useBookshelf = () => {
  const session = getSession()
  const books = ref<BookshelfBook[]>([])
  const trashBooks = ref<BookshelfBook[]>([])
  const loading = ref(false)
  const loadError = ref('')
  const saving = ref(false)
  const deleting = ref(false)
  const trashLoading = ref(false)
  const trashError = ref('')
  const restoringId = ref('')
  const permanentlyDeletingId = ref('')
  const lastDeletedBook = ref<BookshelfBook | null>(null)
  const undoDeleteBusy = ref(false)
  const searchQuery = ref('')
  const viewMode = ref<BookshelfViewMode>(initialViewMode())
  const sort = ref<BookshelfSort>(initialSort())
  const showEditorModal = ref(false)
  const showDeleteDialog = ref(false)
  const deletingBook = ref<BookshelfBook | null>(null)
  const bookForm = reactive<BookFormState>(buildDefaultForm())

  const visibleBooks = computed(() => {
    const query = searchQuery.value.trim().toLocaleLowerCase('zh-CN')
    const result = books.value.filter((book) => {
      if (!query) return true
      return [book.title, book.description, book.genre, ...book.tags]
        .join(' ')
        .toLocaleLowerCase('zh-CN')
        .includes(query)
    })
    return [...result].sort((left, right) => {
      if (sort.value === 'title-asc') return left.title.localeCompare(right.title, 'zh-CN')
      if (sort.value === 'words-desc') return right.wordCount - left.wordCount
      return right.updatedAtValue - left.updatedAtValue
    })
  })

  const visibleTrashBooks = computed(() => {
    const query = searchQuery.value.trim().toLocaleLowerCase('zh-CN')
    if (!query) return trashBooks.value
    return trashBooks.value.filter((book) =>
      [book.title, book.description, book.genre, ...book.tags]
        .join(' ')
        .toLocaleLowerCase('zh-CN')
        .includes(query),
    )
  })

  const canSubmit = computed(() => bookForm.title.trim().length > 0 && !saving.value)

  const resetBookForm = () => Object.assign(bookForm, buildDefaultForm())

  const setViewMode = (value: BookshelfViewMode) => {
    viewMode.value = value
    localStorage.setItem(VIEW_KEY, value)
  }

  const setSort = (value: BookshelfSort) => {
    sort.value = value
    localStorage.setItem(SORT_KEY, value)
  }

  const closeEditor = () => {
    showEditorModal.value = false
    resetBookForm()
  }

  const closeDeleteDialog = () => {
    showDeleteDialog.value = false
    deletingBook.value = null
  }

  const loadBooks = async () => {
    loading.value = true
    loadError.value = ''
    try {
      const list = await novelApi.listProjects()
      books.value = (list || []).map(toBook).filter((item) => Number(item.id) > 0)
    } catch (error: unknown) {
      loadError.value = getErrorMessage(error, '加载书架失败')
    } finally {
      loading.value = false
    }
  }

  const loadTrash = async () => {
    trashLoading.value = true
    trashError.value = ''
    try {
      const list = await novelApi.listDeletedProjects()
      trashBooks.value = (list || []).map(toBook).filter((item) => Number(item.id) > 0)
    } catch (error: unknown) {
      trashError.value = getErrorMessage(error, '加载回收站失败')
    } finally {
      trashLoading.value = false
    }
  }

  const openCreateModal = () => {
    resetBookForm()
    showEditorModal.value = true
  }

  const openDeleteDialog = (book: BookshelfBook) => {
    deletingBook.value = book
    showDeleteDialog.value = true
  }

  const submitBook = async () => {
    if (!bookForm.title.trim()) return null
    const tags = parseTags(bookForm.tagsStr)
    saving.value = true
    try {
      const created = await novelApi.createProject({
        title: bookForm.title.trim(),
        summary: bookForm.description.trim(),
        genre: bookForm.genre,
        tags,
        ownerUserId: session.userId || undefined,
      })
      const projectId = String(created?.projectId ?? created?.id ?? '').trim()
      await loadBooks()
      closeEditor()
      return projectId || null
    } finally {
      saving.value = false
    }
  }

  const confirmDelete = async () => {
    if (!deletingBook.value) return
    const deletedBook = deletingBook.value
    deleting.value = true
    try {
      await novelApi.deleteProject(deletedBook.id, session.userId || '')
      await loadBooks()
      closeDeleteDialog()
      lastDeletedBook.value = deletedBook
      scheduleUndoDismissal()
    } finally {
      deleting.value = false
    }
  }

  let undoDismissTimer: ReturnType<typeof setTimeout> | undefined
  const dismissDeleteUndo = () => {
    if (undoDismissTimer) clearTimeout(undoDismissTimer)
    undoDismissTimer = undefined
    lastDeletedBook.value = null
  }
  const scheduleUndoDismissal = () => {
    if (undoDismissTimer) clearTimeout(undoDismissTimer)
    undoDismissTimer = setTimeout(dismissDeleteUndo, 8000)
  }
  const undoDelete = async () => {
    if (!lastDeletedBook.value || undoDeleteBusy.value) return
    undoDeleteBusy.value = true
    try {
      await novelApi.restoreProject(lastDeletedBook.value.id)
      dismissDeleteUndo()
      await Promise.all([loadBooks(), loadTrash()])
    } finally {
      undoDeleteBusy.value = false
    }
  }
  const restoreTrashBook = async (book: BookshelfBook) => {
    restoringId.value = book.id
    try {
      await novelApi.restoreProject(book.id)
      await Promise.all([loadBooks(), loadTrash()])
    } finally {
      restoringId.value = ''
    }
  }
  const permanentlyDeleteTrashBook = async (book: BookshelfBook, confirmationTitle: string) => {
    permanentlyDeletingId.value = book.id
    try {
      await novelApi.permanentlyDeleteProject(book.id, confirmationTitle)
      await loadTrash()
    } finally {
      permanentlyDeletingId.value = ''
    }
  }

  if (getCurrentScope()) {
    onScopeDispose(() => {
      if (undoDismissTimer) clearTimeout(undoDismissTimer)
    })
  }

  return {
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
    deletingBook,
    bookForm,
    canSubmit,
    genres: bookshelfGenres,
    loadBooks,
    loadTrash,
    setViewMode,
    setSort,
    openCreateModal,
    closeEditor,
    submitBook,
    openDeleteDialog,
    closeDeleteDialog,
    confirmDelete,
    dismissDeleteUndo,
    undoDelete,
    restoreTrashBook,
    permanentlyDeleteTrashBook,
  }
}
