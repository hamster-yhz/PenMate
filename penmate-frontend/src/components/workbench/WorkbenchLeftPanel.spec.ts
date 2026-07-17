import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import WorkbenchLeftPanel from './WorkbenchLeftPanel.vue'

const props = {
  collapsed: false,
  leftTabs: [{ key: 'outline', label: '大纲', icon: '/outline.png' }],
  activeLeftTab: 'outline',
  outlineData: [],
  activeChapter: '',
  outlineOpBusy: false,
}

describe('WorkbenchLeftPanel', () => {
  it('keeps writing navigation focused on the outline', () => {
    const wrapper = mount(WorkbenchLeftPanel, { props, global: { stubs: { OutlineTree: true } } })
    expect(wrapper.text()).toContain('大纲')
    expect(wrapper.text()).not.toContain('角色')
    expect(wrapper.text()).not.toContain('世界')
  })

  it('emits collapse requests', async () => {
    const wrapper = mount(WorkbenchLeftPanel, { props, global: { stubs: { OutlineTree: true } } })
    await wrapper.get('.panel-toggle').trigger('click')
    expect(wrapper.emitted('toggle-collapse')).toHaveLength(1)
  })
})
