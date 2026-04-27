import { describe, expect, it } from 'vitest'

describe('useProfileSettings', () => {
  it('rejects_invalid_email_without_overwriting_existing_email', async () => {
    const { useProfileSettings } = await import('./useProfileSettings')
    const settings = useProfileSettings()
    const originalEmail = settings.profile.email

    const result = settings.saveEmail(' @ ')

    expect(result).toEqual({ success: false, error: '请输入有效邮箱地址' })
    expect(settings.profile.email).toBe(originalEmail)
  })

  it('rejects_password_change_when_current_password_is_empty', async () => {
    const { useProfileSettings } = await import('./useProfileSettings')
    const settings = useProfileSettings()

    const result = settings.savePassword({
      old: ' ',
      new1: 'new-password',
      new2: 'new-password',
    })

    expect(result).toEqual({ success: false, error: '请输入当前密码' })
  })
})
