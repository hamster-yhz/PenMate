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
    expect(wrapper.find('[data-testid="home-workflow"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="home-preview"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="home-cta"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="home-footer"]').exists()).toBe(true)

    await wrapper.find('[data-testid="home-hero-primary-cta"]').trigger('click')
    await wrapper.find('[data-testid="home-cta-button"]').trigger('click')

    expect(pushMock).toHaveBeenNthCalledWith(1, '/login')
    expect(pushMock).toHaveBeenNthCalledWith(2, '/login')
  })

  it('renders_top_navigation_links_and_split_section_data_from_the_page_container', async () => {
    const wrapper = mount(HomeIndex)

    expect(wrapper.get('[data-testid="home-nav-link-features"]').attributes('href')).toBe('#features')
    expect(wrapper.get('[data-testid="home-nav-link-workflow"]').attributes('href')).toBe('#workflow')
    expect(wrapper.get('[data-testid="home-nav-link-about"]').attributes('href')).toBe('#about')

    await wrapper.get('[data-testid="home-nav-enter"]').trigger('click')

    expect(pushMock).toHaveBeenCalledWith('/login')

    const featureCards = wrapper.findAll('[data-testid="home-feature-card"]')
    const workflowSteps = wrapper.findAll('[data-testid="home-workflow-step"]')

    expect(featureCards).toHaveLength(6)
    expect(workflowSteps).toHaveLength(5)
    expect(wrapper.text()).toContain('AI智能写作')
    expect(wrapper.text()).toContain('BYOK模型管理')
    expect(wrapper.text()).toContain('配置基础设施')
    expect(wrapper.text()).toContain('审批设定落库')
  })
})
