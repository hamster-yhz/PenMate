import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { rbacApi } from '@/api/modules/rbac.api'
import { useBookshelf, type BookshelfBook } from '@/composables/bookshelf/useBookshelf'
import { getSession } from '@/stores/session'
import { getErrorMessage } from '@/utils/errors'

export const useBookshelfPage = () => {
  const router = useRouter()
  const route = useRoute()
  const session = getSession()
  const bookshelf = useBookshelf()
  const adminMenuPaths = ref<string[]>([])
  const createError = ref('')
  const deleteError = ref('')
  const trashActionError = ref('')
  const collection = ref<'books' | 'trash'>(route.query.view === 'trash' ? 'trash' : 'books')
  const permanentDeleteBook = ref<BookshelfBook | null>(null)
  const showImportDialog = ref(false)
  const showPermanentDeleteDialog = computed(() => permanentDeleteBook.value != null)
  const canAccessAdmin = computed(() => adminMenuPaths.value.some((path) => path.startsWith('/admin')))

  const openBook = (book: BookshelfBook) => router.push(`/workbench?projectId=${encodeURIComponent(book.id)}`)
  const openProjectSettings = (book: BookshelfBook) => router.push(`/projects/${encodeURIComponent(book.id)}/settings`)
  const openImportDialog = () => { showImportDialog.value = true }
  const closeImportDialog = () => { showImportDialog.value = false }
  const handleImportedProject = async (projectId: string) => {
    showImportDialog.value = false
    await router.push(`/workbench?projectId=${encodeURIComponent(projectId)}`)
  }

  const handleCreateBook = async () => {
    createError.value = ''
    try {
      const projectId = await bookshelf.submitBook()
      if (projectId) await router.push(`/workbench?projectId=${encodeURIComponent(projectId)}`)
    } catch (error: unknown) {
      createError.value = getErrorMessage(error, '创建作品失败')
    }
  }

  const handleConfirmDelete = async () => {
    deleteError.value = ''
    try {
      await bookshelf.confirmDelete()
    } catch (error: unknown) {
      deleteError.value = getErrorMessage(error, '移入回收站失败')
    }
  }

  const setCollection = async (value: 'books' | 'trash') => {
    collection.value = value
    trashActionError.value = ''
    await router.replace({ query: value === 'trash' ? { ...route.query, view: 'trash' } : { ...route.query, view: undefined } })
    if (value === 'trash') await bookshelf.loadTrash()
    else if (!bookshelf.books.value.length) await bookshelf.loadBooks()
  }

  const handleRestoreBook = async (book: BookshelfBook) => {
    trashActionError.value = ''
    try {
      await bookshelf.restoreTrashBook(book)
    } catch (error: unknown) {
      trashActionError.value = getErrorMessage(error, '恢复作品失败')
    }
  }

  const openPermanentDeleteDialog = (book: BookshelfBook) => {
    trashActionError.value = ''
    permanentDeleteBook.value = book
  }

  const closePermanentDeleteDialog = () => {
    if (bookshelf.permanentlyDeletingId.value) return
    trashActionError.value = ''
    permanentDeleteBook.value = null
  }

  const handlePermanentDelete = async (confirmationTitle: string) => {
    if (!permanentDeleteBook.value) return
    trashActionError.value = ''
    try {
      await bookshelf.permanentlyDeleteTrashBook(permanentDeleteBook.value, confirmationTitle)
      permanentDeleteBook.value = null
    } catch (error: unknown) {
      trashActionError.value = getErrorMessage(error, '永久删除失败')
    }
  }

  const handleUndoDelete = async () => {
    trashActionError.value = ''
    try {
      await bookshelf.undoDelete()
    } catch (error: unknown) {
      trashActionError.value = getErrorMessage(error, '撤销失败，可前往回收站恢复')
    }
  }

  const handleEditorVisibilityChange = (visible: boolean) => {
    if (visible) return
    createError.value = ''
    bookshelf.closeEditor()
  }

  const handleDeleteVisibilityChange = (visible: boolean) => {
    if (visible) return
    deleteError.value = ''
    bookshelf.closeDeleteDialog()
  }

  const loadAdminAccess = async () => {
    if (!session.userId) return
    try {
      const menus = await rbacApi.listProfileMenus(session.userId)
      adminMenuPaths.value = (menus || []).map((menu) => String(menu.path || '')).filter(Boolean)
    } catch {
      adminMenuPaths.value = []
    }
  }

  onMounted(() => {
    if (collection.value === 'trash') void bookshelf.loadTrash()
    else void bookshelf.loadBooks()
    void loadAdminAccess()
  })

  return {
    ...bookshelf,
    createError,
    deleteError,
    trashActionError,
    collection,
    permanentDeleteBook,
    showPermanentDeleteDialog,
    showImportDialog,
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
    handleEditorVisibilityChange,
    handleDeleteVisibilityChange,
  }
}
