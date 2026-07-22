import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import WorkbenchLeftPanel from './WorkbenchLeftPanel.vue'

const props = { collapsed: false, panelWidth: 220, outlineData: [], activeChapter: '', outlineOpBusy: false }

describe('WorkbenchLeftPanel', () => {
  it('uses_the_compact_work_catalog_label_and_respects_the_width', () => {
    const wrapper = mount(WorkbenchLeftPanel, { props, global: { stubs: { OutlineTree: true } } })
    expect(wrapper.text()).toContain('作品目录')
    expect(wrapper.attributes('style')).toContain('220px')
  })

  it('emits_collapse_requests', async () => {
    const wrapper = mount(WorkbenchLeftPanel, { props, global: { stubs: { OutlineTree: true } } })
    await wrapper.get('.panel-toggle').trigger('click')
    expect(wrapper.emitted('toggle-collapse')).toHaveLength(1)
  })
})
