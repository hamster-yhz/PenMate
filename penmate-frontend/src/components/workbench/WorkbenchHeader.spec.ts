import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import WorkbenchHeader from './WorkbenchHeader.vue'

const mountWorkbenchHeader = () =>
  mount(WorkbenchHeader, {
    props: {
      novelTitle: '测试小说',
      wordCount: 1280,
      saveHint: '已保存',
      username: '测试作者',
      userEmail: 'writer@penmate.test',
      userMenuOpen: true,
      canAccessRbacAdmin: true,
    },
  })

describe('WorkbenchHeader', () => {
  it('renders_title_and_forwards_header_actions', async () => {
    const wrapper = mountWorkbenchHeader()

    expect(wrapper.text()).toContain('测试小说')
    expect(wrapper.text()).toContain('1280')
    expect(wrapper.text()).toContain('已保存')
    expect(wrapper.text()).toContain('测试作者')
    expect(wrapper.text()).toContain('writer@penmate.test')
    expect(wrapper.text()).toContain('RBAC 管理')

    await wrapper.get('.header-logo').trigger('click')
    await wrapper.get('.hdr-btn').trigger('click')
    await wrapper.get('.user-avatar').trigger('click')
    await wrapper.get('.user-dropdown').trigger('mouseleave')
    await wrapper.get('.ud-item').trigger('click')
    await wrapper.get('.ud-item:nth-of-type(3)').trigger('click')
    await wrapper.get('.ud-item.danger').trigger('click')

    expect(wrapper.emitted('go-home')).toEqual([[]])
    expect(wrapper.emitted('open-style-manager')).toEqual([[]])
    expect(wrapper.emitted('toggle-user-menu')).toEqual([[]])
    expect(wrapper.emitted('go-profile')).toEqual([[]])
    expect(wrapper.emitted('go-rbac-admin')).toEqual([[]])
    expect(wrapper.emitted('logout')).toEqual([[]])
  })

  it('hides_rbac_admin_action_when_access_denied', async () => {
    const wrapper = mount(WorkbenchHeader, {
      props: {
        novelTitle: '测试小说',
        wordCount: 1280,
        saveHint: '已保存',
        username: '测试作者',
        userEmail: 'writer@penmate.test',
        userMenuOpen: true,
        canAccessRbacAdmin: false,
      },
    })

    expect(wrapper.text()).not.toContain('RBAC 管理')
    expect(wrapper.findAll('.ud-item')).toHaveLength(3)
  })
})
