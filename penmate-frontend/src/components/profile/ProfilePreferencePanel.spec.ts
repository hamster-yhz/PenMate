import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ProfilePreferencePanel from './ProfilePreferencePanel.vue'

const mountPreferencePanel = () =>
  mount(ProfilePreferencePanel, {
    props: {
      defaultStyle: '古风文言化 · 慢节奏',
      autoSaveInterval: 30,
      fontSize: 16,
    },
  })

describe('ProfilePreferencePanel', () => {
  it('emits_open_workbench_when_clicking_open_settings_button', async () => {
    const wrapper = mountPreferencePanel()
    const button = wrapper.find('button')

    expect(button.exists()).toBe(true)

    await button.trigger('click')

    expect(wrapper.emitted('open-workbench')).toEqual([[]])
  })

  it('emits_change_auto_save_interval_when_select_changes', async () => {
    const wrapper = mountPreferencePanel()
    const selects = wrapper.findAll('select')

    await selects[0].setValue('60')

    expect(wrapper.emitted('change-auto-save-interval')).toEqual([[60]])
  })

  it('emits_change_font_size_when_select_changes', async () => {
    const wrapper = mountPreferencePanel()
    const selects = wrapper.findAll('select')

    await selects[1].setValue('20')

    expect(wrapper.emitted('change-font-size')).toEqual([[20]])
  })
})
