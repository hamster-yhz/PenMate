import { describe, expect, it, vi, beforeEach } from 'vitest'
import { nextTick } from 'vue'

const errorMock = vi.fn()
const successMock = vi.fn()
const listProjectsMock = vi.fn()
const createProjectMock = vi.fn()
const updateProjectMock = vi.fn()
const deleteProjectMock = vi.fn()

vi.mock('ant-design-vue', () => ({
  message: {
    error: errorMock,
    success: successMock,
  },
}))

vi.mock('@/api/modules/novel.api', () => ({
  novelApi: {
    listProjects: listProjectsMock,
    createProject: createProjectMock,
    updateProject: updateProjectMock,
    deleteProject: deleteProjectMock,
  },
}))

vi.mock('@/stores/session', () => ({
  getSession: () => ({
    userId: 7,
    userName: '墨客',
    userEmail: 'moke@penmate.test',
  }),
}))

describe('useBookshelf', () => {
  beforeEach(() => {
    errorMock.mockReset()
    successMock.mockReset()
    listProjectsMock.mockReset()
    createProjectMock.mockReset()
    updateProjectMock.mockReset()
    deleteProjectMock.mockReset()
  })

  it('shows_validation_message_and_skips_submit_when_title_is_empty', async () => {
    const { useBookshelf } = await import('./useBookshelf')
    const bookshelf = useBookshelf()

    bookshelf.openCreateModal()
    bookshelf.bookForm.title = '   '
    bookshelf.bookForm.description = 'test'

    await bookshelf.submitBook()
    await nextTick()

    expect(errorMock).toHaveBeenCalledWith('请输入书名')
    expect(createProjectMock).not.toHaveBeenCalled()
    expect(updateProjectMock).not.toHaveBeenCalled()
  })
})
