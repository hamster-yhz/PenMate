import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ProjectDataSection from './ProjectDataSection.vue'

describe('ProjectDataSection', () => {
  it('emits all manuscript formats and disables commands while exporting', async () => {
    const wrapper = mount(ProjectDataSection, {
      props: { exportingFormat: null, error: '', success: '' },
    })

    await wrapper.findAll('button')[2].trigger('click')
    await wrapper.findAll('button')[3].trigger('click')
    expect(wrapper.emitted('export')).toEqual([['docx'], ['epub']])

    await wrapper.setProps({ exportingFormat: 'docx' })
    expect(wrapper.findAll('button').every((button) => button.attributes('disabled') !== undefined)).toBe(true)
    expect(wrapper.text()).toContain('正在导出')
  })

  it('renders persistent export feedback in the section', () => {
    const wrapper = mount(ProjectDataSection, {
      props: { exportingFormat: null, error: '导出失败', success: '' },
    })

    expect(wrapper.get('[role="alert"]').text()).toBe('导出失败')
  })
})
