import { mount } from '@vue/test-utils'
import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import LoginView from './index.vue'

const { pushMock, replaceMock, submitLoginMock } = vi.hoisted(() => ({
  pushMock: vi.fn(),
  replaceMock: vi.fn(),
  submitLoginMock: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock, replace: replaceMock }),
  useRoute: () => ({ query: {} }),
}))

vi.mock('@/composables/auth/useLoginSubmit', () => ({
  useLoginSubmit: () => ({
    isLoading: ref(false),
    submitLogin: submitLoginMock,
  }),
}))

describe('Login page shell', () => {
  beforeEach(() => {
    pushMock.mockReset()
    replaceMock.mockReset()
    submitLoginMock.mockReset()
  })

  it('renders_only_the_real_email_password_login_flow', () => {
    const wrapper = mount(LoginView)

    expect(wrapper.get('.login-panel')).toBeTruthy()
    expect(wrapper.get('[data-testid="login-form"]')).toBeTruthy()
    expect(wrapper.find('[data-testid="auth-tab-register"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('忘记密码')
    expect(wrapper.text()).not.toContain('记住我')
  })
})
