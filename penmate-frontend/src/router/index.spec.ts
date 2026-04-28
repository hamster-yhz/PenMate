import { beforeEach, describe, expect, it, vi } from 'vitest'

const { listProfileMenusMock } = vi.hoisted(() => ({
  listProfileMenusMock: vi.fn(),
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
}))

const loadRouter = async () => {
  const mod = await import('./index')
  return mod.default
}

describe('router admin rbac guard', () => {
  beforeEach(() => {
    listProfileMenusMock.mockReset()
    getSessionMock.mockReset()
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
})
