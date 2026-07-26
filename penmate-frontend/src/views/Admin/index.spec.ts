import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { routeState, pushMock, useRbacConsoleMock } = vi.hoisted(() => ({
  routeState: { meta: { adminSection: 'overview' } as Record<string, unknown> },
  pushMock: vi.fn(),
  useRbacConsoleMock: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({ push: pushMock }),
}))

vi.mock('@/features/rbac/useRbacConsole', () => ({
  useRbacConsole: useRbacConsoleMock,
}))

vi.mock('@/components/admin/AdminOfficialModels.vue', () => ({
  default: { template: '<section data-testid="official-models">official models</section>' },
}))

vi.mock('@/views/AdminRbac/index.vue', () => ({
  default: {
    props: ['workspace'],
    template: '<section data-testid="admin-rbac-workspace" :data-workspace="workspace"></section>',
  },
}))

import AdminView from './index.vue'

describe('Admin sections', () => {
  beforeEach(() => {
    useRbacConsoleMock.mockReset()
    pushMock.mockReset()
  })

  it.each(['overview', 'tasks', 'audit'])(
    'does not initialize RBAC requests while rendering the %s placeholder',
    (section) => {
      routeState.meta = { adminSection: section }

      const wrapper = mount(AdminView)

      expect(useRbacConsoleMock).not.toHaveBeenCalled()
      expect(wrapper.find('.integration-state.connected').exists()).toBe(false)
      wrapper.unmount()
    },
  )

  it.each(['users', 'rbac'])('renders the connected %s identity workspace', (section) => {
    routeState.meta = { adminSection: section }

    const wrapper = mount(AdminView)

    expect(wrapper.find('.integration-state.connected').exists()).toBe(true)
    expect(wrapper.get('[data-testid="admin-rbac-workspace"]').attributes('data-workspace')).toBe(
      section === 'users' ? 'users' : 'roles',
    )
    wrapper.unmount()
  })

  it('marks official models as integrated without initializing RBAC requests', () => {
    routeState.meta = { adminSection: 'models' }

    const wrapper = mount(AdminView)

    expect(useRbacConsoleMock).not.toHaveBeenCalled()
    expect(wrapper.find('.integration-state.connected').exists()).toBe(true)
    expect(wrapper.find('[data-testid="official-models"]').exists()).toBe(true)
    wrapper.unmount()
  })
})
