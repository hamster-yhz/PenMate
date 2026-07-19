import { beforeEach, describe, expect, it, vi } from 'vitest'

const { listProfileMenusMock } = vi.hoisted(() => ({
  listProfileMenusMock: vi.fn(),
}))

const { refreshMock, meMock, clearSessionMock, setSessionMock } = vi.hoisted(() => ({
  refreshMock: vi.fn(),
  meMock: vi.fn(),
  clearSessionMock: vi.fn(),
  setSessionMock: vi.fn(),
}))

vi.mock('@/api/modules/auth.api', () => ({
  authApi: {
    refresh: refreshMock,
    me: meMock,
  },
}))

const { getSessionMock } = vi.hoisted(() => ({
  getSessionMock: vi.fn(),
}))

vi.mock('@/api/modules/rbac.api', () => ({
  rbacApi: {
    listProfileMenus: listProfileMenusMock,
  },
}))

vi.mock('@/stores/session', () => ({
  getSession: getSessionMock,
  clearSession: clearSessionMock,
  setSession: setSessionMock,
}))

vi.mock('@/views/Home/index.vue', () => ({
  default: { template: '<div>home</div>' },
}))

vi.mock('@/views/Login/index.vue', () => ({
  default: { template: '<div>login</div>' },
}))

vi.mock('@/views/MyBooks/index.vue', () => ({
  default: { template: '<div>mybooks</div>' },
}))

vi.mock('@/views/Profile/index.vue', () => ({
  default: { template: '<div>profile</div>' },
}))

vi.mock('@/views/Workbench/index.vue', () => ({
  default: { template: '<div>workbench</div>' },
}))

vi.mock('@/views/AdminRbac/index.vue', () => ({
  default: { template: '<div>admin-rbac</div>' },
}))

const loadRouter = async () => {
  const mod = await import('./index')
  return mod.default
}

describe('router admin rbac guard', () => {
  beforeEach(() => {
    vi.resetModules()
    listProfileMenusMock.mockReset()
    getSessionMock.mockReset()
    refreshMock.mockReset()
    meMock.mockReset()
    clearSessionMock.mockReset()
    setSessionMock.mockReset()
    refreshMock.mockRejectedValue(new Error('no refresh session'))
  })

  it('allows_non_admin_routes_without_querying_rbac_menus', async () => {
    getSessionMock.mockReturnValue({ userId: 1002, accessToken: 'atk' })

    const router = await loadRouter()
    await router.push('/profile')

    expect(listProfileMenusMock).not.toHaveBeenCalled()
    expect(router.currentRoute.value.fullPath).toBe('/profile')
  })

  it('redirects_unauthenticated_users_to_login_when_entering_admin_rbac_route', async () => {
    getSessionMock.mockReturnValue({ userId: null, accessToken: '' })

    const router = await loadRouter()
    await router.push('/admin/rbac')

    expect(listProfileMenusMock).not.toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe('/admin/rbac')
  })

  it('redirects non admin users away from the admin rbac route', async () => {
    getSessionMock.mockReturnValue({ userId: 1002, accessToken: 'atk' })
    listProfileMenusMock.mockResolvedValue([{ path: '/profile', title: '个人中心' }])

    const router = await loadRouter()
    await router.push('/admin/rbac')

    expect(listProfileMenusMock).toHaveBeenCalledWith(1002)
    expect(router.currentRoute.value.fullPath).toBe('/mybooks')
  })

  it('allows admin users into the admin rbac route when the backend menu grants access', async () => {
    getSessionMock.mockReturnValue({ userId: 1001, accessToken: 'atk' })
    listProfileMenusMock.mockResolvedValue([{ path: '/admin/rbac', title: 'RBAC 管理' }])

    const router = await loadRouter()
    await router.push('/admin/rbac')

    expect(listProfileMenusMock).toHaveBeenCalledWith(1001)
    expect(router.currentRoute.value.fullPath).toBe('/admin/rbac')
  })

  it('redirects_to_mybooks_when_rbac_menu_query_fails', async () => {
    getSessionMock.mockReturnValue({ userId: 1001, accessToken: 'atk' })
    listProfileMenusMock.mockRejectedValue(new Error('menus failed'))

    const router = await loadRouter()
    await router.push('/admin/rbac')

    expect(listProfileMenusMock).toHaveBeenCalledWith(1001)
    expect(router.currentRoute.value.fullPath).toBe('/mybooks')
  })
})
