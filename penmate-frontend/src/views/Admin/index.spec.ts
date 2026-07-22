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

import AdminView from './index.vue'

describe('Admin sections', () => {
  beforeEach(() => {
    useRbacConsoleMock.mockReset()
    pushMock.mockReset()
  })

  it.each(['overview', 'users', 'tasks', 'audit'])(
    'does not initialize RBAC requests while rendering the %s placeholder',
    (section) => {
      routeState.meta = { adminSection: section }

      const wrapper = mount(AdminView)

      expect(useRbacConsoleMock).not.toHaveBeenCalled()
      expect(wrapper.find('.integration-state.connected').exists()).toBe(false)
      wrapper.unmount()
    },
  )

  it('marks official models as integrated without initializing RBAC requests', () => {
    routeState.meta = { adminSection: 'models' }

    const wrapper = mount(AdminView)

    expect(useRbacConsoleMock).not.toHaveBeenCalled()
    expect(wrapper.find('.integration-state.connected').exists()).toBe(true)
    expect(wrapper.find('[data-testid="official-models"]').exists()).toBe(true)
    wrapper.unmount()
  })
})
