import { beforeEach, describe, expect, it, vi } from 'vitest'
import { clearSession, setSession } from '@/stores/session'

const { getUserModelPreferencesMock, saveUserModelPreferencesMock } = vi.hoisted(() => ({
  getUserModelPreferencesMock: vi.fn(),
  saveUserModelPreferencesMock: vi.fn(),
}))

vi.mock('@/api/modules/model.api', () => ({
  modelApi: {
    getUserModelPreferences: getUserModelPreferencesMock,
    saveUserModelPreferences: saveUserModelPreferencesMock,
  },
}))

describe('useProfileSettings', () => {
  beforeEach(() => {
    getUserModelPreferencesMock.mockReset()
    saveUserModelPreferencesMock.mockReset()
    localStorage.clear()
    clearSession()
  })

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

  it('loads_model_preferences_detail_when_session_user_exists', async () => {
    setSession({ userId: 1001 })
    getUserModelPreferencesMock.mockResolvedValue({
      mainAgentModelConfigId: 9001,
      dirtyWorkAgentModelConfigId: 9002,
      candidateConfigs: [
        { modelConfigId: 9001, modelName: 'gpt-4o-mini', keySourceType: 'USER_KEY' },
        { modelConfigId: 9002, modelName: 'deepseek-chat', keySourceType: 'OFFICIAL_KEY' },
      ],
    })

    const { useProfileSettings } = await import('./useProfileSettings')
    const settings = useProfileSettings()

    await settings.loadModelPreferences()

    expect(getUserModelPreferencesMock).toHaveBeenCalledWith(1001)
    expect(settings.modelPreferences.mainAgentModelConfigId).toBe(9001)
    expect(settings.modelPreferences.dirtyWorkAgentModelConfigId).toBe(9002)
    expect(settings.modelConfigOptions.value).toHaveLength(2)
  })

  it('clears_model_preference_state_when_loading_fails_after_previous_success', async () => {
    setSession({ userId: 1001 })
    getUserModelPreferencesMock
      .mockResolvedValueOnce({
        mainAgentModelConfigId: 9001,
        dirtyWorkAgentModelConfigId: 9002,
        candidateConfigs: [{ modelConfigId: 9001, modelName: 'gpt-4o-mini', keySourceType: 'USER_KEY' }],
      })
      .mockRejectedValueOnce(new Error('load failed'))

    const { useProfileSettings } = await import('./useProfileSettings')
    const settings = useProfileSettings()

    await settings.loadModelPreferences()
    await expect(settings.loadModelPreferences()).rejects.toThrow('load failed')

    expect(settings.modelPreferences.mainAgentModelConfigId).toBeNull()
    expect(settings.modelPreferences.dirtyWorkAgentModelConfigId).toBeNull()
    expect(settings.modelConfigOptions.value).toEqual([])
  })

  it('saves_model_preferences_with_session_user_and_operator', async () => {
    setSession({ userId: 1001 })
    saveUserModelPreferencesMock.mockResolvedValue('updated')

    const { useProfileSettings } = await import('./useProfileSettings')
    const settings = useProfileSettings()
    settings.modelPreferences.mainAgentModelConfigId = 9001
    settings.modelPreferences.dirtyWorkAgentModelConfigId = 9002

    await settings.saveModelPreferences()

    expect(saveUserModelPreferencesMock).toHaveBeenCalledWith(1001, 1001, {
      mainAgentModelConfigId: 9001,
      dirtyWorkAgentModelConfigId: 9002,
    })
  })
})
