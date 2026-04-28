import { shallowMount, flushPromises } from '@vue/test-utils'
import { computed, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { pushMock } = vi.hoisted(() => ({
  pushMock: vi.fn(),
}))

const { listProfileMenusMock } = vi.hoisted(() => ({
  listProfileMenusMock: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock }),
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
    loading: ref(false),
    saving: ref(false),
    deleting: ref(false),
    totalWords: computed(() => 0),
    totalChapters: computed(() => 0),
    showEditorModal: ref(false),
    showDeleteDialog: ref(false),
    editingBook: ref(null),
    deletingBook: ref(null),
    bookForm: ref({}),
    particleStyles: ref([]),
    canSubmit: computed(() => false),
    genres: ref([]),
    loadBooks: vi.fn(),
    openCreateModal: vi.fn(),
    openEditModal: vi.fn(),
    closeEditor: vi.fn(),
    submitBook: vi.fn(),
    openDeleteDialog: vi.fn(),
    closeDeleteDialog: vi.fn(),
    confirmDelete: vi.fn(),
  }),
}))

import MyBooksView from './index.vue'

describe('MyBooks admin RBAC entry', () => {
  beforeEach(() => {
    pushMock.mockReset()
    listProfileMenusMock.mockReset()
  })

  it('shows the rbac admin entry when the backend menus contain the admin route', async () => {
    listProfileMenusMock.mockResolvedValue([
      { menuId: 4001, path: '/admin/rbac', title: 'RBAC 管理', visible: true },
    ])

    const wrapper = shallowMount(MyBooksView)
    await flushPromises()

    expect(listProfileMenusMock).toHaveBeenCalledWith(1001)
    expect(wrapper.text()).toContain('RBAC 管理')
  })

  it('hides the rbac admin entry when the backend menus do not grant access', async () => {
    listProfileMenusMock.mockResolvedValue([
      { menuId: 1, path: '/profile', title: '个人中心', visible: true },
    ])

    const wrapper = shallowMount(MyBooksView)
    await flushPromises()

    expect(listProfileMenusMock).toHaveBeenCalledWith(1001)
    expect(wrapper.text()).not.toContain('RBAC 管理')
  })
})
