import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import ProfileHeroCard from './ProfileHeroCard.vue'

const profile = {
  name: '原笔名', email: 'writer@example.com', bio: '原简介', bookCount: 2, totalWords: 12345,
  defaultStyle: '按项目设置', autoSaveInterval: 30, fontSize: 16,
}

describe('ProfileHeroCard', () => {
  it('keeps_the_form_open_with_a_persistent_error_when_save_fails', async () => {
    const saveProfile = vi.fn().mockResolvedValue({ success: false, error: '网络暂时不可用' })
    const wrapper = mount(ProfileHeroCard, { props: { profile, saveProfile } })

    await wrapper.get('[data-testid="profile-hero-edit"]').trigger('click')
    await wrapper.get('[data-testid="profile-hero-name-input"]').setValue('新笔名')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(saveProfile).toHaveBeenCalledWith({ name: '新笔名', bio: '原简介' })
    expect(wrapper.find('form').exists()).toBe(true)
    expect(wrapper.get('[role="alert"]').text()).toContain('网络暂时不可用')
  })

  it('closes_editing_only_after_a_successful_async_save', async () => {
    const saveProfile = vi.fn().mockResolvedValue({ success: true })
    const wrapper = mount(ProfileHeroCard, { props: { profile, saveProfile } })

    await wrapper.get('[data-testid="profile-hero-edit"]').trigger('click')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('form').exists()).toBe(false)
  })
})
