import { existsSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { describe, expect, it, vi } from 'vitest'

interface ProfileHeroModel {
  name: string
  email: string
  bio: string
  bookCount: number
  totalWords: number
  daysActive: number
  streak: number
}

const buildProfile = (overrides: Partial<ProfileHeroModel> = {}): ProfileHeroModel => ({
  name: '墨客',
  email: 'moke@penmate.com',
  bio: '执笔问道，以墨寄情。',
  bookCount: 3,
  totalWords: 46370,
  daysActive: 42,
  streak: 7,
  ...overrides,
})

const missingComponent = (name: string) =>
  defineComponent({
    name,
    template: `<div data-testid="missing-${name}"></div>`,
  })

const currentDir = dirname(fileURLToPath(import.meta.url))

const loadVueComponent = async (filename: string, fallbackName: string) => {
  const componentPath = resolve(currentDir, filename)

  if (!existsSync(componentPath)) {
    return missingComponent(fallbackName)
  }

  return (await import(/* @vite-ignore */ pathToFileURL(componentPath).href)).default
}

const loadProfileHeroCard = async () => loadVueComponent('ProfileHeroCard.vue', 'profile-hero-card')

const loadProfileSecurityPanel = async () =>
  loadVueComponent('ProfileSecurityPanel.vue', 'profile-security-panel')

describe('ProfileHeroCard', () => {
  it('enters_edit_mode_when_clicking_edit_button', async () => {
    const ProfileHeroCard = await loadProfileHeroCard()
    const profile = buildProfile()
    const wrapper = mount(ProfileHeroCard, {
      props: {
        profile,
      },
    })

    const editButton = wrapper.find('[data-testid="profile-hero-edit"]')

    expect(editButton.exists()).toBe(true)

    await editButton.trigger('click')

    expect(wrapper.find('[data-testid="profile-hero-name-input"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="profile-hero-bio-input"]').exists()).toBe(true)
    expect(wrapper.emitted('edit-profile')).toBeUndefined()
  })

  it('emits_save_profile_with_trimmed_fields_after_entering_edit_mode', async () => {
    const ProfileHeroCard = await loadProfileHeroCard()
    const profile = buildProfile()
    const wrapper = mount(ProfileHeroCard, {
      props: {
        profile,
      },
    })

    await wrapper.find('[data-testid="profile-hero-edit"]').trigger('click')

    const nameInput = wrapper.find('[data-testid="profile-hero-name-input"]')
    const bioInput = wrapper.find('[data-testid="profile-hero-bio-input"]')
    const saveButton = wrapper.find('[data-testid="profile-hero-save"]')

    expect(nameInput.exists()).toBe(true)
    expect(bioInput.exists()).toBe(true)
    expect(saveButton.exists()).toBe(true)

    await nameInput.setValue('  新笔名  ')
    await bioInput.setValue('  新简介  ')
    await saveButton.trigger('click')

    expect(wrapper.emitted('save-profile')).toEqual([
      [
        {
          ...profile,
          name: '新笔名',
          bio: '新简介',
        },
      ],
    ])
  })
})

describe('ProfileSecurityPanel', () => {
  it('toggles_email_and_password_forms_from_security_actions', async () => {
    const ProfileSecurityPanel = await loadProfileSecurityPanel()
    const wrapper = mount(ProfileSecurityPanel, {
      props: {
        email: 'moke@penmate.com',
      },
    })

    const emailToggle = wrapper.find('[data-testid="profile-security-email-toggle"]')
    const passwordToggle = wrapper.find('[data-testid="profile-security-password-toggle"]')

    expect(emailToggle.exists()).toBe(true)
    expect(passwordToggle.exists()).toBe(true)
    expect(wrapper.find('[data-testid="profile-security-email-form"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="profile-security-password-form"]').exists()).toBe(false)

    await emailToggle.trigger('click')
    expect(wrapper.find('[data-testid="profile-security-email-form"]').exists()).toBe(true)

    await passwordToggle.trigger('click')
    expect(wrapper.find('[data-testid="profile-security-password-form"]').exists()).toBe(true)

    await emailToggle.trigger('click')
    await passwordToggle.trigger('click')

    expect(wrapper.find('[data-testid="profile-security-email-form"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="profile-security-password-form"]').exists()).toBe(false)
  })

  it('keeps_password_form_open_and_skips_submit_when_current_password_is_missing', async () => {
    const savePassword = vi.fn().mockResolvedValue({ success: true })
    const ProfileSecurityPanel = await loadProfileSecurityPanel()
    const wrapper = mount(ProfileSecurityPanel, {
      props: {
        email: 'moke@penmate.com',
        savePassword,
      },
    })

    await wrapper.find('[data-testid="profile-security-password-toggle"]').trigger('click')
    await wrapper.find('input[placeholder="当前密码"]').setValue('')
    await wrapper.find('input[placeholder="新密码"]').setValue('new-password')
    await wrapper.find('input[placeholder="确认新密码"]').setValue('new-password')

    const saveButton = wrapper.find('[data-testid="profile-security-password-save"]')

    expect(saveButton.exists()).toBe(true)

    await saveButton.trigger('click')

    expect(savePassword).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="profile-security-password-form"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('请输入当前密码')
  })
})
