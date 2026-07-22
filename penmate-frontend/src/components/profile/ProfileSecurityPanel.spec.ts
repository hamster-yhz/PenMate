import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import ProfileSecurityPanel from './ProfileSecurityPanel.vue'

describe('ProfileSecurityPanel', () => {
  it('submits_email_with_the_current_password_and_emits_relogin_after_success', async () => {
    const saveEmail = vi.fn().mockResolvedValue({ success: true })
    const wrapper = mount(ProfileSecurityPanel, {
      props: { email: 'old@example.com', saveEmail, savePassword: vi.fn() },
    })

    await wrapper.get('[data-testid="profile-security-email-toggle"]').trigger('click')
    await wrapper.get('[data-testid="profile-security-email-input"]').setValue('new@example.com')
    await wrapper.get('[data-testid="profile-security-email-password"]').setValue('secret')
    await wrapper.get('[data-testid="profile-security-email-form"]').trigger('submit')
    await flushPromises()

    expect(saveEmail).toHaveBeenCalledWith({ email: 'new@example.com', currentPassword: 'secret' })
    expect(wrapper.emitted('credentialChanged')).toEqual([['email']])
  })

  it('keeps_password_errors_next_to_the_open_form', async () => {
    const savePassword = vi.fn().mockResolvedValue({ success: false, error: '当前密码错误' })
    const wrapper = mount(ProfileSecurityPanel, {
      props: { email: 'old@example.com', saveEmail: vi.fn(), savePassword },
    })

    await wrapper.get('[data-testid="profile-security-password-toggle"]').trigger('click')
    await wrapper.get('[data-testid="profile-security-current-password"]').setValue('wrong')
    await wrapper.get('[data-testid="profile-security-new-password"]').setValue('new-password')
    await wrapper.get('[data-testid="profile-security-confirm-password"]').setValue('new-password')
    await wrapper.get('[data-testid="profile-security-password-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('当前密码错误')
    expect(wrapper.find('[data-testid="profile-security-password-form"]').exists()).toBe(true)
    expect(wrapper.emitted('credentialChanged')).toBeUndefined()
  })
})
