import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import HomeIndex from './index.vue'

const pushMock = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: pushMock,
  }),
}))

describe('Home index refactor', () => {
  beforeEach(() => {
    pushMock.mockReset()
  })

  it('renders_split_home_sections_and_wires_navigation_ctas', async () => {
    const wrapper = mount(HomeIndex)

    expect(wrapper.find('[data-testid="home-hero"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="home-features"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="home-preview"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="home-cta"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="home-footer"]').exists()).toBe(true)

    await wrapper.find('[data-testid="home-hero-primary-cta"]').trigger('click')
    await wrapper.find('[data-testid="home-cta-button"]').trigger('click')

    expect(pushMock).toHaveBeenNthCalledWith(1, '/login')
    expect(pushMock).toHaveBeenNthCalledWith(2, '/login')
  })

  it('renders_top_navigation_and_only_the_current_product_capabilities', async () => {
    const wrapper = mount(HomeIndex)

    expect(wrapper.get('[data-testid="home-nav-link-features"]').attributes('href')).toBe('#features')
    expect(wrapper.get('[data-testid="home-nav-link-workspace"]').attributes('href')).toBe('#workspace')

    await wrapper.get('[data-testid="home-nav-enter"]').trigger('click')

    expect(pushMock).toHaveBeenCalledWith('/login')

    const featureCards = wrapper.findAll('[data-testid="home-feature-card"]')
    expect(featureCards).toHaveLength(3)
    expect(wrapper.text()).toContain('专注正文写作')
    expect(wrapper.text()).toContain('维护复杂设定')
    expect(wrapper.text()).toContain('让 AI 正式编辑')
    expect(wrapper.text()).not.toContain('文风管控')
    expect(wrapper.text()).not.toContain('插件工坊')
    expect(wrapper.text()).not.toContain('免费开始')
  })
})
