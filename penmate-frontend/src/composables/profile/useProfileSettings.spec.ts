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
    setSession({ userId: '1001' })
    getUserModelPreferencesMock.mockResolvedValue({
      mainAgentModelConfigId: 'mcfg-9001',
      dirtyWorkAgentModelConfigId: 'mcfg-9002',
      candidateConfigs: [
        { modelConfigId: 'mcfg-9001', modelName: 'gpt-4o-mini', keySourceType: 'USER_KEY' },
        { modelConfigId: 'mcfg-9002', modelName: 'deepseek-chat', keySourceType: 'OFFICIAL_KEY' },
      ],
    })

    const { useProfileSettings } = await import('./useProfileSettings')
    const settings = useProfileSettings()

    await settings.loadModelPreferences()

    expect(getUserModelPreferencesMock).toHaveBeenCalledWith('1001')
    expect(settings.modelPreferences.mainAgentModelConfigId).toBe('mcfg-9001')
    expect(settings.modelPreferences.dirtyWorkAgentModelConfigId).toBe('mcfg-9002')
    expect(settings.modelConfigOptions.value).toHaveLength(2)
  })

  it('loads_nested_model_preferences_detail_when_session_user_exists', async () => {
    setSession({ userId: '1001' })
    getUserModelPreferencesMock.mockResolvedValue({
      preferences: {
        mainAgentModelConfigId: 'mcfg-nested-9001',
        dirtyWorkAgentModelConfigId: 'mcfg-nested-9002',
      },
      candidateConfigs: [
        { modelConfigId: 'mcfg-nested-9001', modelName: 'gpt-4o-mini', keySourceType: 'USER_KEY' },
        { modelConfigId: 'mcfg-nested-9002', modelName: 'deepseek-chat', keySourceType: 'OFFICIAL_KEY' },
      ],
    })

    const { useProfileSettings } = await import('./useProfileSettings')
    const settings = useProfileSettings()

    await settings.loadModelPreferences()

    expect(settings.modelPreferences.mainAgentModelConfigId).toBe('mcfg-nested-9001')
    expect(settings.modelPreferences.dirtyWorkAgentModelConfigId).toBe('mcfg-nested-9002')
  })

  it('clears_model_preference_state_when_loading_fails_after_previous_success', async () => {
    setSession({ userId: '1001' })
    getUserModelPreferencesMock
      .mockResolvedValueOnce({
        mainAgentModelConfigId: 'mcfg-9001',
        dirtyWorkAgentModelConfigId: 'mcfg-9002',
        candidateConfigs: [{ modelConfigId: 'mcfg-9001', modelName: 'gpt-4o-mini', keySourceType: 'USER_KEY' }],
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
    setSession({ userId: '1001' })
    getUserModelPreferencesMock.mockResolvedValue({
      mainAgentModelConfigId: 'mcfg-9001',
      dirtyWorkAgentModelConfigId: 'mcfg-9002',
      candidateConfigs: [
        { modelConfigId: 'mcfg-9001', modelName: 'gpt-4o-mini', keySourceType: 'USER_KEY' },
        { modelConfigId: 'mcfg-9002', modelName: 'deepseek-chat', keySourceType: 'OFFICIAL_KEY' },
      ],
    })
    saveUserModelPreferencesMock.mockResolvedValue('updated')

    const { useProfileSettings } = await import('./useProfileSettings')
    const settings = useProfileSettings()
    await settings.loadModelPreferences()

    await settings.saveModelPreferences()

    expect(saveUserModelPreferencesMock).toHaveBeenCalledWith('1001', '1001', {
      mainAgentModelConfigId: 'mcfg-9001',
      dirtyWorkAgentModelConfigId: 'mcfg-9002',
    })
  })

  it('clears_stale_model_preference_ids_when_loaded_preferences_are_not_in_candidate_configs', async () => {
    setSession({ userId: '1001' })
    getUserModelPreferencesMock.mockResolvedValue({
      mainAgentModelConfigId: 'mcfg-2051723276547498000000',
      dirtyWorkAgentModelConfigId: 'mcfg-2051723276547498000000',
      candidateConfigs: [
        { modelConfigId: 'mcfg-9001', modelName: 'gpt-4o-mini', keySourceType: 'USER_KEY' },
        { modelConfigId: 'mcfg-9002', modelName: 'deepseek-chat', keySourceType: 'OFFICIAL_KEY' },
      ],
    })
    saveUserModelPreferencesMock.mockResolvedValue('updated')

    const { useProfileSettings } = await import('./useProfileSettings')
    const settings = useProfileSettings()

    await settings.loadModelPreferences()

    expect(settings.modelPreferences.mainAgentModelConfigId).toBeNull()
    expect(settings.modelPreferences.dirtyWorkAgentModelConfigId).toBeNull()

    await settings.saveModelPreferences()

    expect(saveUserModelPreferencesMock).toHaveBeenCalledWith('1001', '1001', {
      mainAgentModelConfigId: null,
      dirtyWorkAgentModelConfigId: null,
    })
  })
})
