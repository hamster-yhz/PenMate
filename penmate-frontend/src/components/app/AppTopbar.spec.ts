import { shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AppTopbar from './AppTopbar.vue'

const { pushMock } = vi.hoisted(() => ({ pushMock: vi.fn() }))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock, replace: vi.fn() }),
}))

describe('AppTopbar', () => {
  beforeEach(() => pushMock.mockReset())

  it('keeps account actions in the trailing column when search is absent', async () => {
    const wrapper = shallowMount(AppTopbar, {
      props: { contextTitle: '个人设置', backTo: '/mybooks', backLabel: '返回书架' },
    })

    expect(wrapper.classes()).not.toContain('has-search')
    expect(wrapper.get('.back-button').text()).toContain('返回书架')

    await wrapper.get('.back-button').trigger('click')
    expect(pushMock).toHaveBeenCalledWith('/mybooks')
  })

  it('uses the three-column layout only when search is visible', () => {
    const wrapper = shallowMount(AppTopbar, { props: { searchable: true } })

    expect(wrapper.classes()).toContain('has-search')
    expect(wrapper.find('.topbar-search').exists()).toBe(true)
  })
})
