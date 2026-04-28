import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, type Component } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { pushMock } = vi.hoisted(() => ({
  pushMock: vi.fn(),
}))

const { listUsersMock, listRolesMock, listPermissionsMock, listMenusMock, listProfileMenusMock } = vi.hoisted(() => ({
  listUsersMock: vi.fn(),
  listRolesMock: vi.fn(),
  listPermissionsMock: vi.fn(),
  listMenusMock: vi.fn(),
  listProfileMenusMock: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock }),
}))

vi.mock('@/api/modules/rbac.api', () => ({
  rbacApi: {
    listUsers: listUsersMock,
    listRoles: listRolesMock,
    listPermissions: listPermissionsMock,
    listMenus: listMenusMock,
    listProfileMenus: listProfileMenusMock,
  },
}))

vi.mock('@/stores/session', () => ({
  getSession: () => ({
    userId: 1001,
    userName: '管理员A',
    userEmail: 'admin@penmate.ai',
  }),
}))

const MissingAdminRbacView = defineComponent({
  name: 'MissingAdminRbacView',
  template: '<div data-testid="missing-admin-rbac-view"></div>',
})

const loadAdminRbacView = async (): Promise<Component> => {
  try {
    const componentPath = './index.vue'
    return (await import(/* @vite-ignore */ componentPath)).default
  } catch {
    return MissingAdminRbacView
  }
}

const mountAdminRbacView = async () => {
  const AdminRbacView = await loadAdminRbacView()
  return mount(AdminRbacView)
}

describe('AdminRbac view', () => {
  beforeEach(() => {
    pushMock.mockReset()
    listUsersMock.mockReset()
    listRolesMock.mockReset()
    listPermissionsMock.mockReset()
    listMenusMock.mockReset()
    listProfileMenusMock.mockReset()

    listUsersMock.mockResolvedValue([
      {
        userId: 1001,
        email: 'admin@penmate.ai',
        displayName: '管理员A',
        status: 1,
        authMethod: 'local',
      },
      {
        userId: 1002,
        email: 'editor@penmate.ai',
        displayName: '编辑B',
        status: 1,
        authMethod: 'local',
      },
    ])
    listRolesMock.mockResolvedValue([
      { roleId: 2001, id: 2001, code: 'ADMIN', name: '管理员', description: '系统管理员', isSystem: true },
    ])
    listPermissionsMock.mockResolvedValue([
      { permissionId: 3001, id: 3001, code: 'rbac.manage', name: 'RBAC 管理', module: 'rbac' },
    ])
    listMenusMock.mockResolvedValue([
      { menuId: 4001, id: 4001, title: 'RBAC 管理', path: '/admin/rbac', permissionCode: 'rbac.manage', visible: true },
    ])
    listProfileMenusMock.mockResolvedValue([
      { menuId: 4001, id: 4001, title: 'RBAC 管理', path: '/admin/rbac', permissionCode: 'rbac.manage', visible: true },
    ])
  })

  it('loads_rbac_resources_for_the_admin_console_on_mount', async () => {
    const wrapper = await mountAdminRbacView()
    await flushPromises()

    expect(wrapper.find('[data-testid="missing-admin-rbac-view"]').exists()).toBe(false)
    expect(listUsersMock).toHaveBeenCalledTimes(1)
    expect(listRolesMock).toHaveBeenCalledTimes(1)
    expect(listPermissionsMock).toHaveBeenCalledTimes(1)
    expect(listMenusMock).toHaveBeenCalledTimes(1)
    expect(listProfileMenusMock).toHaveBeenCalledWith(1001)
    expect(wrapper.text()).toContain('管理员A')
    expect(wrapper.text()).toContain('admin@penmate.ai')
    expect(wrapper.text()).toContain('RBAC 管理')
  })

  it('refreshes_profile_menus_when_selecting_another_user', async () => {
    const wrapper = await mountAdminRbacView()
    await flushPromises()

    expect(wrapper.find('[data-testid="missing-admin-rbac-view"]').exists()).toBe(false)

    await wrapper.get('[data-testid="rbac-user-select-1002"]').trigger('click')
    await flushPromises()

    expect(listProfileMenusMock).toHaveBeenLastCalledWith(1002)
    expect(wrapper.text()).toContain('编辑B')
  })

  it('renders_an_error_state_when_initial_rbac_loading_fails', async () => {
    listRolesMock.mockRejectedValueOnce(new Error('roles failed'))

    const wrapper = await mountAdminRbacView()
    await flushPromises()

    expect(wrapper.find('[data-testid="missing-admin-rbac-view"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="rbac-error-state"]').text()).toContain('RBAC 数据加载失败')
    expect(wrapper.text()).toContain('roles failed')
  })

  it('keeps_the_previous_selection_when_refreshing_profile_menus_fails', async () => {
    listProfileMenusMock
      .mockResolvedValueOnce([
        { menuId: 4001, id: 4001, title: 'RBAC 管理', path: '/admin/rbac', permissionCode: 'rbac.manage', visible: true },
      ])
      .mockRejectedValueOnce(new Error('profile menus failed'))

    const wrapper = await mountAdminRbacView()
    await flushPromises()

    await wrapper.get('[data-testid="rbac-user-select-1002"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="rbac-active-user-name"]').text()).toContain('管理员A')
    expect(wrapper.get('[data-testid="rbac-error-state"]').text()).toContain('profile menus failed')
  })
})
