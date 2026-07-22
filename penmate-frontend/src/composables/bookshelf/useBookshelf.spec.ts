import { beforeEach, describe, expect, it, vi } from 'vitest'

const listProjectsMock = vi.fn()
const createProjectMock = vi.fn()
const deleteProjectMock = vi.fn()
const listDeletedProjectsMock = vi.fn()
const restoreProjectMock = vi.fn()
const permanentlyDeleteProjectMock = vi.fn()

vi.mock('@/api/modules/novel.api', () => ({
  novelApi: {
    listProjects: listProjectsMock,
    createProject: createProjectMock,
    deleteProject: deleteProjectMock,
    listDeletedProjects: listDeletedProjectsMock,
    restoreProject: restoreProjectMock,
    permanentlyDeleteProject: permanentlyDeleteProjectMock,
  },
}))

vi.mock('@/stores/session', () => ({
  getSession: () => ({ userId: '7', userName: '墨客', userEmail: 'moke@penmate.test' }),
}))

describe('useBookshelf', () => {
  beforeEach(() => {
    localStorage.clear()
    listProjectsMock.mockReset()
    createProjectMock.mockReset()
    deleteProjectMock.mockReset()
    listDeletedProjectsMock.mockReset()
    restoreProjectMock.mockReset()
    permanentlyDeleteProjectMock.mockReset()
  })

  it('skips_create_when_title_is_empty', async () => {
    const { useBookshelf } = await import('./useBookshelf')
    const bookshelf = useBookshelf()
    bookshelf.bookForm.title = '   '

    expect(await bookshelf.submitBook()).toBeNull()
    expect(createProjectMock).not.toHaveBeenCalled()
  })

  it('filters_and_sorts_books_without_mutating_the_server_order', async () => {
    listProjectsMock.mockResolvedValue([
      { projectId: '1', title: '北城', summary: '现实故事', updatedAt: '2026-01-01', totalWords: 10 },
      { projectId: '2', title: '长夜', summary: '悬疑故事', updatedAt: '2026-02-01', totalWords: 20 },
    ])
    const { useBookshelf } = await import('./useBookshelf')
    const bookshelf = useBookshelf()
    await bookshelf.loadBooks()
    bookshelf.searchQuery.value = '悬疑'

    expect(bookshelf.visibleBooks.value.map((book) => book.id)).toEqual(['2'])
  })

  it('restores_a_book_and_refreshes_both_collections', async () => {
    listProjectsMock.mockResolvedValue([])
    listDeletedProjectsMock
      .mockResolvedValueOnce([{ projectId: '2', title: '长夜', deletedAt: '2026-07-20T00:00:00Z' }])
      .mockResolvedValueOnce([])
    restoreProjectMock.mockResolvedValue({ projectId: '2' })
    const { useBookshelf } = await import('./useBookshelf')
    const bookshelf = useBookshelf()
    await bookshelf.loadTrash()

    await bookshelf.restoreTrashBook(bookshelf.trashBooks.value[0])

    expect(restoreProjectMock).toHaveBeenCalledWith('2')
    expect(listProjectsMock).toHaveBeenCalledOnce()
    expect(bookshelf.trashBooks.value).toEqual([])
  })

  it('sends_the_exact_title_for_permanent_deletion', async () => {
    listDeletedProjectsMock.mockResolvedValue([])
    permanentlyDeleteProjectMock.mockResolvedValue('deleted')
    const { useBookshelf } = await import('./useBookshelf')
    const bookshelf = useBookshelf()
    const book = {
      id: '2', title: '长夜', description: '', genre: '悬疑', tags: [], wordCount: 0,
      chapterCount: 0, updatedAt: '', updatedAtValue: 0, coverTone: 'ink',
    }

    await bookshelf.permanentlyDeleteTrashBook(book, '长夜')

    expect(permanentlyDeleteProjectMock).toHaveBeenCalledWith('2', '长夜')
  })
})
