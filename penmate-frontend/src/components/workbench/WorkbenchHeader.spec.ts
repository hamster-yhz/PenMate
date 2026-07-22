import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import WorkbenchHeader from './WorkbenchHeader.vue'

describe('WorkbenchHeader', () => {
  it('renders_a_read_only_project_title_and_switches_workspace_modes', async () => {
    const wrapper = mount(WorkbenchHeader, {
      props: {
        novelTitle: '测试小说',
        saveHint: '已保存',
        username: '测试作者',
        canAccessRbacAdmin: true,
        workbenchMode: 'writing',
        directoryCollapsed: true,
        aiCollapsed: true,
      },
    })

    expect(wrapper.text()).toContain('测试小说')
    expect(wrapper.find('[contenteditable]').exists()).toBe(false)
    expect(wrapper.text()).toContain('已保存')

    await wrapper.get('.project-title').trigger('click')
    await wrapper.get('[aria-label="展开作品目录"]').trigger('click')
    await wrapper.get('[aria-label="展开 AI 协作"]').trigger('click')
    await wrapper.findAll('.workspace-mode button')[1]!.trigger('click')

    expect(wrapper.emitted('open-project-settings')).toEqual([[]])
    expect(wrapper.emitted('restore-directory')).toEqual([[]])
    expect(wrapper.emitted('restore-ai')).toEqual([[]])
    expect(wrapper.emitted('update:workbench-mode')).toEqual([['story-bible']])
  })
})
