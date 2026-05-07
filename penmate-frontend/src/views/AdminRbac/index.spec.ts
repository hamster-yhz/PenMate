import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, type Component } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const createDeferred = <T>() => {
  let resolve!: (value: T | PromiseLike<T>) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })

  return { promise, resolve, reject }
}

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
      { roleId: 2001, id: 920001, code: 'ADMIN', name: '管理员', description: '系统管理员', isSystem: true },
    ])
    listPermissionsMock.mockResolvedValue([
      { permissionId: 3001, id: 930001, code: 'rbac.manage', name: 'RBAC 管理', module: 'rbac' },
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

    await wrapper.get('[data-testid="rbac-tab-roles"]').trigger('click')

    expect(wrapper.text()).toContain('已绑定权限')
    expect(wrapper.text()).toContain('管理员')
    expect(wrapper.text()).toContain('rbac.manage')
  })

  it('organizes_the_console_into_tabbed_workspaces_for_users_roles_and_menus', async () => {
    const wrapper = await mountAdminRbacView()
    await flushPromises()

    expect(wrapper.get('[data-testid="rbac-tab-users"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.find('[data-testid="rbac-user-workspace"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="rbac-role-workspace"]').exists()).toBe(false)

    await wrapper.get('[data-testid="rbac-tab-roles"]').trigger('click')

    expect(wrapper.get('[data-testid="rbac-tab-roles"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.find('[data-testid="rbac-user-workspace"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="rbac-role-workspace"]').exists()).toBe(true)

    await wrapper.get('[data-testid="rbac-tab-menus"]').trigger('click')

    expect(wrapper.get('[data-testid="rbac-tab-menus"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.find('[data-testid="rbac-menu-workspace"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="rbac-role-workspace"]').exists()).toBe(false)
  })

  it('filters_and_paginates_the_user_list_for_admin_browsing', async () => {
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
        authMethod: 'oauth',
      },
      {
        userId: 1003,
        email: 'reviewer@penmate.ai',
        displayName: '审核C',
        status: 1,
        authMethod: 'local',
      },
      {
        userId: 1004,
        email: 'guest@penmate.ai',
        displayName: '访客D',
        status: 0,
        authMethod: 'sso',
      },
      {
        userId: 1005,
        email: 'operator@penmate.ai',
        displayName: '运维E',
        status: 1,
        authMethod: 'local',
      },
    ])

    const wrapper = await mountAdminRbacView()
    await flushPromises()

    expect(wrapper.find('[data-testid="rbac-user-select-1001"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="rbac-user-select-1002"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="rbac-user-select-1003"]').exists()).toBe(false)

    await wrapper.get('[data-testid="rbac-user-search-input"]').setValue('访客')
    await flushPromises()

    expect(wrapper.find('[data-testid="rbac-user-select-1004"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="rbac-user-select-1001"]').exists()).toBe(false)

    await wrapper.get('[data-testid="rbac-user-search-input"]').setValue('')
    await wrapper.get('[data-testid="rbac-user-status-filter"]').setValue('0')
    await flushPromises()

    expect(wrapper.find('[data-testid="rbac-user-select-1004"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="rbac-user-select-1002"]').exists()).toBe(false)

    await wrapper.get('[data-testid="rbac-user-status-filter"]').setValue('all')
    await wrapper.get('[data-testid="rbac-user-page-next"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="rbac-user-select-1003"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="rbac-user-select-1005"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="rbac-user-select-1001"]').exists()).toBe(false)
  })

  it('keeps_the_create_user_form_collapsed_until_the_admin_expands_it', async () => {
    const wrapper = await mountAdminRbacView()
    await flushPromises()

    expect(wrapper.find('[data-testid="rbac-create-user-email"]').exists()).toBe(false)

    await wrapper.get('[data-testid="rbac-toggle-create-user"]').trigger('click')

    expect(wrapper.get('[data-testid="rbac-create-user-email"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="rbac-create-user-display-name"]').exists()).toBe(true)

    await wrapper.get('[data-testid="rbac-toggle-create-user"]').trigger('click')

    expect(wrapper.find('[data-testid="rbac-create-user-email"]').exists()).toBe(false)
  })

  it('requires_an_explicit_confirmation_step_before_deleting_the_selected_user', async () => {
    const wrapper = await mountAdminRbacView()
    await flushPromises()

    await wrapper.get('[data-testid="rbac-user-delete-trigger"]').trigger('click')

    expect(deleteUserMock).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="rbac-user-delete-confirmation"]').text()).toContain('管理员A')

    await wrapper.get('[data-testid="rbac-user-delete-confirm"]').trigger('click')
    await flushPromises()

    expect(deleteUserMock).toHaveBeenCalledWith(1001)
  })

  it('requires_an_explicit_confirmation_step_before_deleting_the_active_role', async () => {
    const wrapper = await mountAdminRbacView()
    await flushPromises()

    await wrapper.get('[data-testid="rbac-tab-roles"]').trigger('click')
    await wrapper.get('[data-testid="rbac-role-delete-trigger"]').trigger('click')

    expect(deleteRoleMock).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="rbac-role-delete-confirmation"]').text()).toContain('管理员')

    await wrapper.get('[data-testid="rbac-role-delete-confirm"]').trigger('click')
    await flushPromises()

    expect(deleteRoleMock).toHaveBeenCalledWith(2001)
  })

  it('keeps_the_latest_selected_user_data_when_an_older_user_request_resolves_later', async () => {
    const user1002Menus = createDeferred<Array<{ menuId: number, id: number, title: string, path: string, permissionCode: string, visible: boolean }>>()
    const user1003Menus = createDeferred<Array<{ menuId: number, id: number, title: string, path: string, permissionCode: string, visible: boolean }>>()
    const user1002Roles = createDeferred<Array<{ roleId: number, id: number, code: string, name: string, description: string, isSystem: boolean }>>()
    const user1003Roles = createDeferred<Array<{ roleId: number, id: number, code: string, name: string, description: string, isSystem: boolean }>>()

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
      {
        userId: 1003,
        email: 'reviewer@penmate.ai',
        displayName: '审核C',
        status: 1,
        authMethod: 'local',
      },
    ])
    listProfileMenusMock.mockImplementation(async (userId: number) => {
      if (userId === 1001) {
        return [
          { menuId: 4001, id: 4001, title: 'RBAC 管理', path: '/admin/rbac', permissionCode: 'rbac.manage', visible: true },
        ]
      }

      if (userId === 1002) {
        return user1002Menus.promise
      }

      if (userId === 1003) {
        return user1003Menus.promise
      }

      return []
    })
    listUserRolesMock.mockImplementation(async (userId: number) => {
      if (userId === 1001) {
        return [
          { roleId: 2001, id: 2001, code: 'ADMIN', name: '管理员', description: '系统管理员', isSystem: true },
        ]
      }

      if (userId === 1002) {
        return user1002Roles.promise
      }

      if (userId === 1003) {
        return user1003Roles.promise
      }

      return []
    })
    listRolePermissionsMock
      .mockResolvedValueOnce([
        { permissionId: 3001, id: 3001, code: 'rbac.manage', name: 'RBAC 管理', module: 'rbac' },
      ])
      .mockResolvedValue([
        { permissionId: 3002, id: 3002, code: 'review.approve', name: '审核通过', module: 'review' },
      ])

    const wrapper = await mountAdminRbacView()
    await flushPromises()

    await wrapper.get('[data-testid="rbac-user-search-input"]').setValue('编辑')
    await wrapper.get('[data-testid="rbac-user-select-1002"]').trigger('click')
    await wrapper.get('[data-testid="rbac-user-search-input"]').setValue('审核')
    await wrapper.get('[data-testid="rbac-user-select-1003"]').trigger('click')

    user1003Menus.resolve([
      { menuId: 4010, id: 4010, title: '审核台', path: '/review', permissionCode: 'review.approve', visible: true },
    ])
    user1003Roles.resolve([
      { roleId: 2003, id: 2003, code: 'REVIEWER', name: '审核员', description: '内容审核', isSystem: false },
    ])
    await flushPromises()

    user1002Menus.resolve([
      { menuId: 4004, id: 4004, title: '编辑台', path: '/editor', permissionCode: 'editor.access', visible: true },
    ])
    user1002Roles.resolve([
      { roleId: 2002, id: 2002, code: 'EDITOR', name: '编辑', description: '内容编辑', isSystem: false },
    ])
    await flushPromises()

    expect(wrapper.get('[data-testid="rbac-active-user-name"]').text()).toContain('审核C')
    expect(wrapper.text()).toContain('/review')
    expect(wrapper.text()).toContain('REVIEWER')
    expect(wrapper.text()).not.toContain('/editor')
    expect(wrapper.text()).not.toContain('EDITOR')
  })

  it('keeps_the_latest_selected_role_permissions_when_an_older_role_request_resolves_later', async () => {
    const role2001Permissions = createDeferred<Array<{ permissionId: number, id: number, code: string, name: string, module: string }>>()
    const role2002Permissions = createDeferred<Array<{ permissionId: number, id: number, code: string, name: string, module: string }>>()

    listRolesMock.mockResolvedValue([
      { roleId: 2001, id: 2001, code: 'ADMIN', name: '管理员', description: '系统管理员', isSystem: true },
      { roleId: 2002, id: 2002, code: 'EDITOR', name: '编辑', description: '内容编辑', isSystem: false },
    ])
    listRolePermissionsMock
      .mockResolvedValueOnce([
        { permissionId: 3001, id: 3001, code: 'rbac.manage', name: 'RBAC 管理', module: 'rbac' },
      ])
      .mockImplementationOnce(() => role2002Permissions.promise)
      .mockImplementationOnce(() => role2001Permissions.promise)

    const wrapper = await mountAdminRbacView()
    await flushPromises()

    await wrapper.get('[data-testid="rbac-tab-roles"]').trigger('click')
    await wrapper.get('[data-testid="rbac-role-select-2002"]').trigger('click')
    await wrapper.get('[data-testid="rbac-role-select-2001"]').trigger('click')

    role2001Permissions.resolve([
      { permissionId: 3001, id: 3001, code: 'rbac.manage', name: 'RBAC 管理', module: 'rbac' },
    ])
    await flushPromises()

    role2002Permissions.resolve([
      { permissionId: 3002, id: 3002, code: 'content.publish', name: '内容发布', module: 'content' },
    ])
    await flushPromises()

    expect(wrapper.find('[data-testid="rbac-role-select-2001"]').classes()).toContain('active')
    expect(wrapper.text()).toContain('rbac.manage')
    expect(wrapper.text()).not.toContain('content.publish')
  })

  it('does_not_let_an_inflight_user_context_override_a_newer_manual_role_selection', async () => {
    const user1002RolePermissions = createDeferred<Array<{ permissionId: number, id: number, code: string, name: string, module: string }>>()

    listRolesMock.mockResolvedValue([
      { roleId: 2001, id: 2001, code: 'ADMIN', name: '管理员', description: '系统管理员', isSystem: true },
      { roleId: 2002, id: 2002, code: 'EDITOR', name: '编辑', description: '内容编辑', isSystem: false },
    ])
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
    listProfileMenusMock.mockImplementation(async (userId: number) => {
      if (userId === 1001) {
        return [
          { menuId: 4001, id: 4001, title: 'RBAC 管理', path: '/admin/rbac', permissionCode: 'rbac.manage', visible: true },
        ]
      }

      return [
        { menuId: 4004, id: 4004, title: '编辑台', path: '/editor', permissionCode: 'editor.access', visible: true },
      ]
    })
    listUserRolesMock.mockImplementation(async (userId: number) => {
      if (userId === 1001) {
        return [
          { roleId: 2001, id: 2001, code: 'ADMIN', name: '管理员', description: '系统管理员', isSystem: true },
        ]
      }

      return [
        { roleId: 2001, id: 2001, code: 'ADMIN', name: '管理员', description: '系统管理员', isSystem: true },
        { roleId: 2002, id: 2002, code: 'EDITOR', name: '编辑', description: '内容编辑', isSystem: false },
      ]
    })
    listRolePermissionsMock.mockImplementation(async (roleId: number) => {
      if (roleId === 2001) {
        return user1002RolePermissions.promise
      }

      return [
        { permissionId: 3002, id: 3002, code: 'content.publish', name: '内容发布', module: 'content' },
      ]
    })

    const wrapper = await mountAdminRbacView()
    await flushPromises()

    user1002RolePermissions.resolve([
      { permissionId: 3001, id: 3001, code: 'rbac.manage', name: 'RBAC 管理', module: 'rbac' },
    ])
    await flushPromises()

    const delayedUser1002RolePermissions = createDeferred<Array<{ permissionId: number, id: number, code: string, name: string, module: string }>>()
    listRolePermissionsMock.mockImplementation(async (roleId: number) => {
      if (roleId === 2001) {
        return delayedUser1002RolePermissions.promise
      }

      return [
        { permissionId: 3002, id: 3002, code: 'content.publish', name: '内容发布', module: 'content' },
      ]
    })

    await wrapper.get('[data-testid="rbac-user-search-input"]').setValue('编辑')
    await wrapper.get('[data-testid="rbac-user-select-1002"]').trigger('click')
    await wrapper.get('[data-testid="rbac-tab-roles"]').trigger('click')
    await wrapper.get('[data-testid="rbac-role-select-2002"]').trigger('click')
    await flushPromises()

    delayedUser1002RolePermissions.resolve([
      { permissionId: 3001, id: 3001, code: 'rbac.manage', name: 'RBAC 管理', module: 'rbac' },
    ])
    await flushPromises()

    expect(wrapper.find('[data-testid="rbac-role-select-2002"]').classes()).toContain('active')
    expect(wrapper.find('[data-testid="rbac-remove-role-permission-3002"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="rbac-remove-role-permission-3001"]').exists()).toBe(false)
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

    await wrapper.get('[data-testid="rbac-toggle-create-user"]').trigger('click')
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

    await wrapper.get('[data-testid="rbac-user-delete-trigger"]').trigger('click')
    await wrapper.get('[data-testid="rbac-user-delete-confirm"]').trigger('click')
    await flushPromises()

    expect(deleteUserMock).toHaveBeenCalledWith(1001)
    expect(listUsersMock).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="rbac-user-select-1001"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="rbac-active-user-name"]').text()).toContain('编辑B')
    expect(wrapper.text()).toContain('编辑B')
  })

  it('assigns_a_role_to_the_selected_user_and_refreshes_assigned_roles', async () => {
    listRolesMock.mockResolvedValue([
      { roleId: 2001, id: 920001, code: 'ADMIN', name: '管理员', description: '系统管理员', isSystem: true },
      { roleId: 2002, id: 920002, code: 'EDITOR', name: '编辑', description: '内容编辑', isSystem: false },
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
        { roleId: 2001, id: 920001, code: 'ADMIN', name: '管理员', description: '系统管理员', isSystem: true },
      ])
      .mockResolvedValueOnce([
        { roleId: 2001, id: 920001, code: 'ADMIN', name: '管理员', description: '系统管理员', isSystem: true },
        { roleId: 2002, id: 920002, code: 'EDITOR', name: '编辑', description: '内容编辑', isSystem: false },
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
        { roleId: 2001, id: 920001, code: 'ADMIN', name: '管理员', description: '系统管理员', isSystem: true },
        { roleId: 2002, id: 920002, code: 'EDITOR', name: '编辑', description: '内容编辑', isSystem: false },
      ])
      .mockResolvedValueOnce([
        { roleId: 2002, id: 920002, code: 'EDITOR', name: '编辑', description: '内容编辑', isSystem: false },
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
      { permissionId: 3001, id: 930001, code: 'rbac.manage', name: 'RBAC 管理', module: 'rbac' },
      { permissionId: 3002, id: 930002, code: 'content.publish', name: '内容发布', module: 'content' },
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
        { permissionId: 3001, id: 930001, code: 'rbac.manage', name: 'RBAC 管理', module: 'rbac' },
      ])
      .mockResolvedValueOnce([
        { permissionId: 3001, id: 930001, code: 'rbac.manage', name: 'RBAC 管理', module: 'rbac' },
        { permissionId: 3002, id: 930002, code: 'content.publish', name: '内容发布', module: 'content' },
      ])

    const wrapper = await mountAdminRbacView()
    await flushPromises()

    await wrapper.get('[data-testid="rbac-tab-roles"]').trigger('click')
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

    await wrapper.get('[data-testid="rbac-tab-roles"]').trigger('click')
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

    await wrapper.get('[data-testid="rbac-tab-roles"]').trigger('click')
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

    await wrapper.get('[data-testid="rbac-tab-roles"]').trigger('click')
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

    await wrapper.get('[data-testid="rbac-tab-roles"]').trigger('click')
    await wrapper.get('[data-testid="rbac-role-delete-trigger"]').trigger('click')
    await wrapper.get('[data-testid="rbac-role-delete-confirm"]').trigger('click')
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

    await wrapper.get('[data-testid="rbac-tab-roles"]').trigger('click')
    await wrapper.get('[data-testid="rbac-role-delete-trigger"]').trigger('click')
    await wrapper.get('[data-testid="rbac-role-delete-confirm"]').trigger('click')
    await flushPromises()

    expect(deleteRoleMock).toHaveBeenCalledWith(2001)
    expect(listUserRolesMock).toHaveBeenCalledTimes(2)
    expect(listProfileMenusMock).toHaveBeenCalledWith(1001)
    expect(listProfileMenusMock.mock.calls.length).toBeGreaterThanOrEqual(1)
    expect(wrapper.find('[data-testid="rbac-remove-user-role-2001"]').exists()).toBe(false)
  })
})
