import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, type Component } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { pushMock } = vi.hoisted(() => ({
  pushMock: vi.fn(),
}))

const {
  listUsersMock,
  listRolesMock,
  listPermissionsMock,
  listMenusMock,
  listProfileMenusMock,
  listUserRolesMock,
  listRolePermissionsMock,
  createUserMock,
  updateUserMock,
  deleteUserMock,
  assignUserRoleMock,
  removeUserRoleMock,
  assignRolePermissionMock,
  removeRolePermissionMock,
  createRoleMock,
  updateRoleMock,
  deleteRoleMock,
} = vi.hoisted(() => ({
  listUsersMock: vi.fn(),
  listRolesMock: vi.fn(),
  listPermissionsMock: vi.fn(),
  listMenusMock: vi.fn(),
  listProfileMenusMock: vi.fn(),
  listUserRolesMock: vi.fn(),
  listRolePermissionsMock: vi.fn(),
  createUserMock: vi.fn(),
  updateUserMock: vi.fn(),
  deleteUserMock: vi.fn(),
  assignUserRoleMock: vi.fn(),
  removeUserRoleMock: vi.fn(),
  assignRolePermissionMock: vi.fn(),
  removeRolePermissionMock: vi.fn(),
  createRoleMock: vi.fn(),
  updateRoleMock: vi.fn(),
  deleteRoleMock: vi.fn(),
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
    listUserRoles: listUserRolesMock,
    listRolePermissions: listRolePermissionsMock,
    createUser: createUserMock,
    updateUser: updateUserMock,
    deleteUser: deleteUserMock,
    assignUserRole: assignUserRoleMock,
    removeUserRole: removeUserRoleMock,
    assignRolePermission: assignRolePermissionMock,
    removeRolePermission: removeRolePermissionMock,
    createRole: createRoleMock,
    updateRole: updateRoleMock,
    deleteRole: deleteRoleMock,
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
    listUserRolesMock.mockReset()
    listRolePermissionsMock.mockReset()
    createUserMock.mockReset()
    updateUserMock.mockReset()
    deleteUserMock.mockReset()
    assignUserRoleMock.mockReset()
    removeUserRoleMock.mockReset()
    assignRolePermissionMock.mockReset()
    removeRolePermissionMock.mockReset()
    createRoleMock.mockReset()
    updateRoleMock.mockReset()
    deleteRoleMock.mockReset()

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
    listUserRolesMock.mockResolvedValue([
      { roleId: 2001, id: 2001, code: 'ADMIN', name: '管理员', description: '系统管理员', isSystem: true },
    ])
    listRolePermissionsMock.mockResolvedValue([
      { permissionId: 3001, id: 3001, code: 'rbac.manage', name: 'RBAC 管理', module: 'rbac' },
    ])
    createUserMock.mockResolvedValue({
      userId: 1003,
      email: 'new@penmate.ai',
      displayName: '新管理员',
      status: 1,
      authMethod: 'local',
    })
    updateUserMock.mockResolvedValue({
      userId: 1001,
      email: 'admin@penmate.ai',
      displayName: '管理员A-更新',
      status: 0,
      authMethod: 'local',
    })
    deleteUserMock.mockResolvedValue({ deleted: true })
    assignUserRoleMock.mockResolvedValue({ bound: true })
    removeUserRoleMock.mockResolvedValue({ unbound: true })
    assignRolePermissionMock.mockResolvedValue({ bound: true })
    removeRolePermissionMock.mockResolvedValue({ unbound: true })
    createRoleMock.mockResolvedValue({
      roleId: 2002,
      id: 2002,
      code: 'EDITOR',
      name: '编辑',
      description: '内容编辑',
      isSystem: false,
    })
    updateRoleMock.mockResolvedValue({
      roleId: 2001,
      id: 2001,
      code: 'ADMIN',
      name: '管理员-更新',
      description: '系统管理员-更新',
      isSystem: true,
    })
    deleteRoleMock.mockResolvedValue({ deleted: true })
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

  it('loads_assigned_roles_for_active_user_and_permissions_for_active_role_on_mount', async () => {
    const wrapper = await mountAdminRbacView()
    await flushPromises()

    expect(listUserRolesMock).toHaveBeenCalledWith(1001)
    expect(listRolePermissionsMock).toHaveBeenCalledWith(2001)
    expect(wrapper.text()).toContain('已绑定角色')
    expect(wrapper.text()).toContain('已绑定权限')
    expect(wrapper.text()).toContain('管理员')
    expect(wrapper.text()).toContain('rbac.manage')
  })

  it('creates_user_and_refreshes_the_user_list_when_submitting_new_user', async () => {
    listUsersMock
      .mockResolvedValueOnce([
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
      .mockResolvedValueOnce([
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
        {
          userId: 1003,
          email: 'new@penmate.ai',
          displayName: '新管理员',
          status: 1,
          authMethod: 'local',
        },
      ])

    const wrapper = await mountAdminRbacView()
    await flushPromises()

    await wrapper.get('[data-testid="rbac-create-user-email"]').setValue('new@penmate.ai')
    await wrapper.get('[data-testid="rbac-create-user-display-name"]').setValue('新管理员')
    await wrapper.get('[data-testid="rbac-create-user-auth-method"]').setValue('local')
    await wrapper.get('[data-testid="rbac-create-user-submit"]').trigger('click')
    await flushPromises()

    expect(createUserMock).toHaveBeenCalledWith({
      email: 'new@penmate.ai',
      displayName: '新管理员',
      status: 1,
      authMethod: 'local',
    })
    expect(listUsersMock).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('新管理员')
  })

  it('updates_the_selected_user_and_refreshes_the_user_list_when_submitting_user_details', async () => {
    listUsersMock
      .mockResolvedValueOnce([
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
      .mockResolvedValueOnce([
        {
          userId: 1001,
          email: 'admin@penmate.ai',
          displayName: '管理员A-更新',
          status: 0,
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

    const wrapper = await mountAdminRbacView()
    await flushPromises()

    await wrapper.get('[data-testid="rbac-user-detail-display-name"]').setValue('管理员A-更新')
    await wrapper.get('[data-testid="rbac-user-detail-status"]').setValue('0')
    await wrapper.get('[data-testid="rbac-user-detail-submit"]').trigger('click')
    await flushPromises()

    expect(updateUserMock).toHaveBeenCalledWith(1001, {
      displayName: '管理员A-更新',
      status: 0,
    })
    expect(listUsersMock).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('管理员A-更新')
    expect(wrapper.text()).toContain('停用')
  })

  it('deletes_the_selected_user_and_refreshes_the_user_list_when_confirming_delete', async () => {
    listUsersMock
      .mockResolvedValueOnce([
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
      .mockResolvedValueOnce([
        {
          userId: 1002,
          email: 'editor@penmate.ai',
          displayName: '编辑B',
          status: 1,
          authMethod: 'local',
        },
      ])

    const wrapper = await mountAdminRbacView()
    await flushPromises()

    await wrapper.get('[data-testid="rbac-user-delete-submit"]').trigger('click')
    await flushPromises()

    expect(deleteUserMock).toHaveBeenCalledWith(1001)
    expect(listUsersMock).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="rbac-user-select-1001"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="rbac-active-user-name"]').text()).toContain('编辑B')
    expect(wrapper.text()).toContain('编辑B')
  })

  it('assigns_a_role_to_the_selected_user_and_refreshes_assigned_roles', async () => {
    listRolesMock.mockResolvedValue([
      { roleId: 2001, id: 2001, code: 'ADMIN', name: '管理员', description: '系统管理员', isSystem: true },
      { roleId: 2002, id: 2002, code: 'EDITOR', name: '编辑', description: '内容编辑', isSystem: false },
    ])
    listProfileMenusMock
      .mockResolvedValueOnce([
        { menuId: 4001, id: 4001, title: 'RBAC 管理', path: '/admin/rbac', permissionCode: 'rbac.manage', visible: true },
      ])
      .mockResolvedValueOnce([
        { menuId: 4001, id: 4001, title: 'RBAC 管理', path: '/admin/rbac', permissionCode: 'rbac.manage', visible: true },
        { menuId: 4004, id: 4004, title: '编辑台', path: '/editor', permissionCode: 'editor.access', visible: true },
      ])
    listUserRolesMock
      .mockResolvedValueOnce([
        { roleId: 2001, id: 2001, code: 'ADMIN', name: '管理员', description: '系统管理员', isSystem: true },
      ])
      .mockResolvedValueOnce([
        { roleId: 2001, id: 2001, code: 'ADMIN', name: '管理员', description: '系统管理员', isSystem: true },
        { roleId: 2002, id: 2002, code: 'EDITOR', name: '编辑', description: '内容编辑', isSystem: false },
      ])

    const wrapper = await mountAdminRbacView()
    await flushPromises()

    await wrapper.get('[data-testid="rbac-assign-role-role-id"]').setValue('2002')
    await wrapper.get('[data-testid="rbac-assign-role-submit"]').trigger('click')
    await flushPromises()

    expect(assignUserRoleMock).toHaveBeenCalledWith(1001, 2002)
    expect(listUserRolesMock).toHaveBeenCalledTimes(2)
    expect(listProfileMenusMock).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('编辑')
    expect(wrapper.text()).toContain('EDITOR')
    expect(wrapper.text()).toContain('/editor')
  })

  it('removes_a_role_from_the_selected_user_and_refreshes_assigned_roles', async () => {
    listProfileMenusMock
      .mockResolvedValueOnce([
        { menuId: 4001, id: 4001, title: 'RBAC 管理', path: '/admin/rbac', permissionCode: 'rbac.manage', visible: true },
        { menuId: 4004, id: 4004, title: '编辑台', path: '/editor', permissionCode: 'editor.access', visible: true },
      ])
      .mockResolvedValueOnce([
        { menuId: 4004, id: 4004, title: '编辑台', path: '/editor', permissionCode: 'editor.access', visible: true },
      ])
    listUserRolesMock
      .mockResolvedValueOnce([
        { roleId: 2001, id: 2001, code: 'ADMIN', name: '管理员', description: '系统管理员', isSystem: true },
        { roleId: 2002, id: 2002, code: 'EDITOR', name: '编辑', description: '内容编辑', isSystem: false },
      ])
      .mockResolvedValueOnce([
        { roleId: 2002, id: 2002, code: 'EDITOR', name: '编辑', description: '内容编辑', isSystem: false },
      ])

    const wrapper = await mountAdminRbacView()
    await flushPromises()

    await wrapper.get('[data-testid="rbac-remove-user-role-2001"]').trigger('click')
    await flushPromises()

    expect(removeUserRoleMock).toHaveBeenCalledWith(1001, 2001)
    expect(listUserRolesMock).toHaveBeenCalledTimes(2)
    expect(listProfileMenusMock).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="rbac-remove-user-role-2001"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('EDITOR')
    expect(wrapper.text()).toContain('/editor')
  })

  it('assigns_a_permission_to_the_active_role_and_refreshes_bound_permissions', async () => {
    listPermissionsMock.mockResolvedValue([
      { permissionId: 3001, id: 3001, code: 'rbac.manage', name: 'RBAC 管理', module: 'rbac' },
      { permissionId: 3002, id: 3002, code: 'content.publish', name: '内容发布', module: 'content' },
    ])
    listProfileMenusMock
      .mockResolvedValueOnce([
        { menuId: 4001, id: 4001, title: 'RBAC 管理', path: '/admin/rbac', permissionCode: 'rbac.manage', visible: true },
      ])
      .mockResolvedValueOnce([
        { menuId: 4001, id: 4001, title: 'RBAC 管理', path: '/admin/rbac', permissionCode: 'rbac.manage', visible: true },
        { menuId: 4003, id: 4003, title: '发布中心', path: '/publish', permissionCode: 'content.publish', visible: true },
      ])
    listRolePermissionsMock
      .mockResolvedValueOnce([
        { permissionId: 3001, id: 3001, code: 'rbac.manage', name: 'RBAC 管理', module: 'rbac' },
      ])
      .mockResolvedValueOnce([
        { permissionId: 3001, id: 3001, code: 'rbac.manage', name: 'RBAC 管理', module: 'rbac' },
        { permissionId: 3002, id: 3002, code: 'content.publish', name: '内容发布', module: 'content' },
      ])

    const wrapper = await mountAdminRbacView()
    await flushPromises()

    await wrapper.get('[data-testid="rbac-assign-permission-permission-id"]').setValue('3002')
    await wrapper.get('[data-testid="rbac-assign-permission-submit"]').trigger('click')
    await flushPromises()

    expect(assignRolePermissionMock).toHaveBeenCalledWith(2001, 3002)
    expect(listRolePermissionsMock).toHaveBeenCalledTimes(2)
    expect(listProfileMenusMock).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('content.publish')
    expect(wrapper.text()).toContain('/publish')
  })

  it('removes_a_permission_from_the_active_role_and_refreshes_bound_permissions', async () => {
    listProfileMenusMock
      .mockResolvedValueOnce([
        { menuId: 4001, id: 4001, title: 'RBAC 管理', path: '/admin/rbac', permissionCode: 'rbac.manage', visible: true },
        { menuId: 4003, id: 4003, title: '发布中心', path: '/publish', permissionCode: 'content.publish', visible: true },
      ])
      .mockResolvedValueOnce([
        { menuId: 4003, id: 4003, title: '发布中心', path: '/publish', permissionCode: 'content.publish', visible: true },
      ])
    listRolePermissionsMock
      .mockResolvedValueOnce([
        { permissionId: 3001, id: 3001, code: 'rbac.manage', name: 'RBAC 管理', module: 'rbac' },
        { permissionId: 3002, id: 3002, code: 'content.publish', name: '内容发布', module: 'content' },
      ])
      .mockResolvedValueOnce([
        { permissionId: 3002, id: 3002, code: 'content.publish', name: '内容发布', module: 'content' },
      ])

    const wrapper = await mountAdminRbacView()
    await flushPromises()

    await wrapper.get('[data-testid="rbac-remove-role-permission-3001"]').trigger('click')
    await flushPromises()

    expect(removeRolePermissionMock).toHaveBeenCalledWith(2001, 3001)
    expect(listRolePermissionsMock).toHaveBeenCalledTimes(2)
    expect(listProfileMenusMock).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="rbac-remove-role-permission-3001"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('content.publish')
    expect(wrapper.text()).toContain('/publish')
  })

  it('creates_a_role_and_refreshes_the_role_list', async () => {
    listRolesMock
      .mockResolvedValueOnce([
        { roleId: 2001, id: 2001, code: 'ADMIN', name: '管理员', description: '系统管理员', isSystem: true },
      ])
      .mockResolvedValueOnce([
        { roleId: 2001, id: 2001, code: 'ADMIN', name: '管理员', description: '系统管理员', isSystem: true },
        { roleId: 2002, id: 2002, code: 'EDITOR', name: '编辑', description: '内容编辑', isSystem: false },
      ])

    const wrapper = await mountAdminRbacView()
    await flushPromises()

    await wrapper.get('[data-testid="rbac-create-role-name"]').setValue('编辑')
    await wrapper.get('[data-testid="rbac-create-role-code"]').setValue('EDITOR')
    await wrapper.get('[data-testid="rbac-create-role-description"]').setValue('内容编辑')
    await wrapper.get('[data-testid="rbac-create-role-submit"]').trigger('click')
    await flushPromises()

    expect(createRoleMock).toHaveBeenCalledWith({
      name: '编辑',
      code: 'EDITOR',
      description: '内容编辑',
      isSystem: false,
    })
    expect(listRolesMock).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('EDITOR')
  })

  it('updates_the_active_role_and_refreshes_the_role_list', async () => {
    listRolesMock
      .mockResolvedValueOnce([
        { roleId: 2001, id: 2001, code: 'ADMIN', name: '管理员', description: '系统管理员', isSystem: true },
      ])
      .mockResolvedValueOnce([
        { roleId: 2001, id: 2001, code: 'ADMIN', name: '管理员-更新', description: '系统管理员-更新', isSystem: true },
      ])

    const wrapper = await mountAdminRbacView()
    await flushPromises()

    await wrapper.get('[data-testid="rbac-role-detail-name"]').setValue('管理员-更新')
    await wrapper.get('[data-testid="rbac-role-detail-description"]').setValue('系统管理员-更新')
    await wrapper.get('[data-testid="rbac-role-detail-submit"]').trigger('click')
    await flushPromises()

    expect(updateRoleMock).toHaveBeenCalledWith(2001, {
      name: '管理员-更新',
      description: '系统管理员-更新',
    })
    expect(listRolesMock).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('管理员-更新')
  })

  it('deletes_the_active_role_and_refreshes_the_role_list', async () => {
    listRolesMock
      .mockResolvedValueOnce([
        { roleId: 2001, id: 2001, code: 'ADMIN', name: '管理员', description: '系统管理员', isSystem: true },
        { roleId: 2002, id: 2002, code: 'EDITOR', name: '编辑', description: '内容编辑', isSystem: false },
      ])
      .mockResolvedValueOnce([
        { roleId: 2002, id: 2002, code: 'EDITOR', name: '编辑', description: '内容编辑', isSystem: false },
      ])

    const wrapper = await mountAdminRbacView()
    await flushPromises()

    await wrapper.get('[data-testid="rbac-role-delete-submit"]').trigger('click')
    await flushPromises()

    expect(deleteRoleMock).toHaveBeenCalledWith(2001)
    expect(listRolesMock).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="rbac-role-select-2001"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('EDITOR')
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

  it('restores_previous_profile_menus_when_user_role_refresh_fails_after_profile_menu_refresh_succeeds', async () => {
    listProfileMenusMock
      .mockResolvedValueOnce([
        { menuId: 4001, id: 4001, title: 'RBAC 管理', path: '/admin/rbac', permissionCode: 'rbac.manage', visible: true },
      ])
      .mockResolvedValueOnce([
        { menuId: 4002, id: 4002, title: '个人中心', path: '/profile', permissionCode: 'profile.read', visible: true },
      ])
    listUserRolesMock
      .mockResolvedValueOnce([
        { roleId: 2001, id: 2001, code: 'ADMIN', name: '管理员', description: '系统管理员', isSystem: true },
      ])
      .mockRejectedValueOnce(new Error('user roles failed'))

    const wrapper = await mountAdminRbacView()
    await flushPromises()

    await wrapper.get('[data-testid="rbac-user-select-1002"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="rbac-active-user-name"]').text()).toContain('管理员A')
    expect(wrapper.text()).toContain('/admin/rbac')
    expect(wrapper.text()).not.toContain('/profile')
    expect(wrapper.get('[data-testid="rbac-error-state"]').text()).toContain('user roles failed')
  })

  it('refreshes_current_user_roles_and_profile_menus_after_deleting_the_active_role', async () => {
    listRolesMock
      .mockResolvedValueOnce([
        { roleId: 2001, id: 2001, code: 'ADMIN', name: '管理员', description: '系统管理员', isSystem: false },
        { roleId: 2002, id: 2002, code: 'EDITOR', name: '编辑', description: '内容编辑', isSystem: false },
      ])
      .mockResolvedValueOnce([
        { roleId: 2002, id: 2002, code: 'EDITOR', name: '编辑', description: '内容编辑', isSystem: false },
      ])
    listUserRolesMock
      .mockResolvedValueOnce([
        { roleId: 2001, id: 2001, code: 'ADMIN', name: '管理员', description: '系统管理员', isSystem: false },
      ])
      .mockResolvedValueOnce([])
    listProfileMenusMock
      .mockResolvedValueOnce([
        { menuId: 4001, id: 4001, title: 'RBAC 管理', path: '/admin/rbac', permissionCode: 'rbac.manage', visible: true },
      ])
      .mockResolvedValueOnce([])

    const wrapper = await mountAdminRbacView()
    await flushPromises()

    await wrapper.get('[data-testid="rbac-role-delete-submit"]').trigger('click')
    await flushPromises()

    expect(deleteRoleMock).toHaveBeenCalledWith(2001)
    expect(listUserRolesMock).toHaveBeenCalledTimes(2)
    expect(listProfileMenusMock).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="rbac-remove-user-role-2001"]').exists()).toBe(false)
  })
})
