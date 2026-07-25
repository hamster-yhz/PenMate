import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ProfileAuthorPreferencesPanel from './ProfileAuthorPreferencesPanel.vue'
import type { AuthorProfile } from '@/entities/author/model'

const profile = (): AuthorProfile => ({
  defaultLanguage: 'zh-CN', collaborationMode: 'COLLABORATIVE', defaultPov: 'PROJECT_DEFAULT',
  defaultTense: 'PROJECT_DEFAULT', descriptionDensity: 'MEDIUM', dialoguePreference: '',
  bannedExpressions: '', longTermMemory: '',
})

describe('ProfileAuthorPreferencesPanel', () => {
  it('warns_about_global_impact_and_emits_the_edited_profile', async () => {
    const wrapper = mount(ProfileAuthorPreferencesPanel, {
      props: { profile: profile(), loading: false, saving: false, error: '', saved: false },
    })

    expect(wrapper.text()).toContain('长期设置会影响所有项目')
    const areas = wrapper.findAll('textarea')
    await areas[2].setValue('所有项目都避免全知旁白')
    await wrapper.get('header button').trigger('click')

    expect(wrapper.emitted<AuthorProfile[]>('save')?.[0]?.[0].longTermMemory)
      .toBe('所有项目都避免全知旁白')
  })

  it('shows_retry_without_exposing_the_form_when_loading_failed', async () => {
    const wrapper = mount(ProfileAuthorPreferencesPanel, {
      props: { profile: profile(), loading: false, saving: false, error: '加载失败', saved: false },
    })

    expect(wrapper.find('form').exists()).toBe(false)
    await wrapper.get('.state button').trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })
})
