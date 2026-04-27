import { mount } from '@vue/test-utils'
import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import LoginView from './index.vue'

const { pushMock, submitLoginMock, messageInfoMock } = vi.hoisted(() => ({
  pushMock: vi.fn(),
  submitLoginMock: vi.fn(),
  messageInfoMock: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock }),
}))

vi.mock('ant-design-vue', () => ({
  message: {
    info: messageInfoMock,
  },
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
    submitLoginMock.mockReset()
    messageInfoMock.mockReset()
  })

  it('renders the homepage-aligned showcase shell and toggles auth modes from both tabs and footer link', async () => {
    const wrapper = mount(LoginView)

    expect(wrapper.get('.login-shell')).toBeTruthy()
    expect(wrapper.get('.login-showcase.glass-panel')).toBeTruthy()
    expect(wrapper.get('.back-home.btn-ancient')).toBeTruthy()
    expect(wrapper.text()).toContain('沿袭主页的沉浸式创作气韵')
    expect(wrapper.text()).toContain('踏 入 书 阁')

    await wrapper.get('[data-testid="auth-tab-register"]').trigger('click')

    expect(wrapper.text()).toContain('开 启 创 作 之 旅')
    expect(wrapper.text()).toContain('返回登录')

    await wrapper.get('.footer-hint a').trigger('click')

    expect(wrapper.text()).toContain('踏 入 书 阁')
    expect(wrapper.text()).toContain('立即注册')
  })
})
