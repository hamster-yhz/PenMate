import { shallowMount, flushPromises } from '@vue/test-utils'
import { computed, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { pushMock, replaceMock } = vi.hoisted(() => ({
  pushMock: vi.fn(),
  replaceMock: vi.fn(),
}))

const { listProfileMenusMock } = vi.hoisted(() => ({
  listProfileMenusMock: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock, replace: replaceMock }),
  useRoute: () => ({ query: {} }),
}))

vi.mock('@/stores/session', () => ({
  getSession: () => ({
    userId: 1001,
    userName: '管理员A',
    userEmail: 'admin@penmate.ai',
  }),
}))

vi.mock('@/api/modules/rbac.api', () => ({
  rbacApi: {
    listProfileMenus: listProfileMenusMock,
  },
}))

vi.mock('@/composables/bookshelf/useBookshelf', () => ({
  useBookshelf: () => ({
    books: ref([]),
    visibleBooks: computed(() => []),
    trashBooks: ref([]),
    visibleTrashBooks: computed(() => []),
    loading: ref(false),
    loadError: ref(''),
    saving: ref(false),
    deleting: ref(false),
    trashLoading: ref(false),
    trashError: ref(''),
    restoringId: ref(''),
    permanentlyDeletingId: ref(''),
    lastDeletedBook: ref(null),
    undoDeleteBusy: ref(false),
    searchQuery: ref(''),
    viewMode: ref('grid'),
    sort: ref('updated-desc'),
    showEditorModal: ref(false),
    showDeleteDialog: ref(false),
    deletingBook: ref(null),
    bookForm: ref({}),
    canSubmit: computed(() => false),
    genres: ref([]),
    loadBooks: vi.fn(),
    loadTrash: vi.fn(),
    openCreateModal: vi.fn(),
    setViewMode: vi.fn(),
    setSort: vi.fn(),
    closeEditor: vi.fn(),
    submitBook: vi.fn(),
    openDeleteDialog: vi.fn(),
    closeDeleteDialog: vi.fn(),
    confirmDelete: vi.fn(),
    restoreTrashBook: vi.fn(),
    permanentlyDeleteTrashBook: vi.fn(),
    undoDelete: vi.fn(),
    dismissDeleteUndo: vi.fn(),
  }),
}))

import MyBooksView from './index.vue'
import AppTopbar from '@/components/app/AppTopbar.vue'

describe('MyBooks admin RBAC entry', () => {
  beforeEach(() => {
    pushMock.mockReset()
    replaceMock.mockReset()
    listProfileMenusMock.mockReset()
  })

  it('shows the rbac admin entry when the backend menus contain the admin route', async () => {
    listProfileMenusMock.mockResolvedValue([{ menuId: 4001, path: '/admin/rbac', title: 'RBAC 管理', visible: true }])

    const wrapper = shallowMount(MyBooksView)
    await flushPromises()

    expect(listProfileMenusMock).toHaveBeenCalledWith(1001)
    expect(wrapper.findComponent(AppTopbar).props('showAdmin')).toBe(true)
  })

  it('hides the rbac admin entry when the backend menus do not grant access', async () => {
    listProfileMenusMock.mockResolvedValue([{ menuId: 1, path: '/profile', title: '个人中心', visible: true }])

    const wrapper = shallowMount(MyBooksView)
    await flushPromises()

    expect(listProfileMenusMock).toHaveBeenCalledWith(1001)
    expect(wrapper.findComponent(AppTopbar).props('showAdmin')).toBe(false)
  })
})
