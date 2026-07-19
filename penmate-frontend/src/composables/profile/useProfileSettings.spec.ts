import { beforeEach, describe, expect, it, vi } from 'vitest'
import { clearSession, setSession } from '@/stores/session'

const {
  getUserModelPreferencesMock,
  saveUserModelPreferencesMock,
  listKeysMock,
  meMock,
  updateProfileMock,
  changePasswordMock,
} = vi.hoisted(() => ({
  getUserModelPreferencesMock: vi.fn(),
  saveUserModelPreferencesMock: vi.fn(),
  listKeysMock: vi.fn(),
  meMock: vi.fn(),
  updateProfileMock: vi.fn(),
  changePasswordMock: vi.fn(),
}))

vi.mock('@/api/modules/model.api', () => ({
  modelApi: {
    getUserModelPreferences: getUserModelPreferencesMock,
    saveUserModelPreferences: saveUserModelPreferencesMock,
    listKeys: listKeysMock,
  },
}))

vi.mock('@/api/modules/auth.api', () => ({
  authApi: { me: meMock, updateProfile: updateProfileMock, changePassword: changePasswordMock },
}))

vi.mock('@/api/modules/novel.api', () => ({
  novelApi: { listProjects: vi.fn().mockResolvedValue([]) },
}))

describe('useProfileSettings', () => {
  beforeEach(() => {
    getUserModelPreferencesMock.mockReset()
    saveUserModelPreferencesMock.mockReset()
    listKeysMock.mockReset()
    meMock.mockReset()
    updateProfileMock.mockReset()
    changePasswordMock.mockReset()
    listKeysMock.mockResolvedValue([])
    localStorage.clear()
    clearSession()
  })

  it('rejects_invalid_email_without_overwriting_existing_email', async () => {
    const { useProfileSettings } = await import('./useProfileSettings')
    const settings = useProfileSettings()
    const originalEmail = settings.profile.email

    const result = await settings.saveEmail(' @ ')

    expect(result).toEqual({ success: false, error: '请输入有效邮箱地址' })
    expect(settings.profile.email).toBe(originalEmail)
  })

  it('rejects_password_change_when_current_password_is_empty', async () => {
    const { useProfileSettings } = await import('./useProfileSettings')
    const settings = useProfileSettings()

    const result = await settings.savePassword({
      old: ' ',
      new1: 'new-password',
      new2: 'new-password',
    })

    expect(result).toEqual({ success: false, error: '请输入当前密码' })
  })

  it('persists_profile_and_password_changes_through_authenticated_apis', async () => {
    setSession({ userId: '1001' })
    updateProfileMock.mockResolvedValue({
      id: '1001',
      displayName: '新笔名',
      email: 'writer@example.com',
      bio: '新简介',
    })
    changePasswordMock.mockResolvedValue('ok')
    const { useProfileSettings } = await import('./useProfileSettings')
    const settings = useProfileSettings()
    settings.profile.email = 'writer@example.com'

    await expect(settings.saveProfile({ name: ' 新笔名 ', bio: ' 新简介 ' })).resolves.toEqual({ success: true })
    await expect(
      settings.savePassword({ old: 'old-password', new1: 'new-password', new2: 'new-password' }),
    ).resolves.toEqual({ success: true })

    expect(updateProfileMock).toHaveBeenCalledWith({
      displayName: '新笔名',
      email: 'writer@example.com',
      bio: '新简介',
    })
    expect(changePasswordMock).toHaveBeenCalledWith({ currentPassword: 'old-password', newPassword: 'new-password' })
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

  it('loads_model_candidates_from_nested_preferences_alias_when_candidate_configs_missing', async () => {
    setSession({ userId: '1001' })
    getUserModelPreferencesMock.mockResolvedValue({
      preferences: {
        mainAgentModelConfigId: 'mcfg-nested-9001',
        dirtyWorkAgentModelConfigId: 'mcfg-nested-9002',
        modelConfigs: [
          { modelConfigId: 'mcfg-nested-9001', modelName: 'gpt-4o-mini', keySourceType: 'USER_KEY' },
          { modelConfigId: 'mcfg-nested-9002', modelName: 'deepseek-chat', keySourceType: 'OFFICIAL_KEY' },
        ],
      },
    })

    const { useProfileSettings } = await import('./useProfileSettings')
    const settings = useProfileSettings()

    await settings.loadModelPreferences()

    expect(settings.modelPreferences.mainAgentModelConfigId).toBe('mcfg-nested-9001')
    expect(settings.modelPreferences.dirtyWorkAgentModelConfigId).toBe('mcfg-nested-9002')
    expect(settings.modelConfigOptions.value).toEqual([
      { modelConfigId: 'mcfg-nested-9001', modelName: 'gpt-4o-mini', keySourceType: 'USER_KEY' },
      { modelConfigId: 'mcfg-nested-9002', modelName: 'deepseek-chat', keySourceType: 'OFFICIAL_KEY' },
    ])
  })

  it('loads_model_preferences_when_backend_keeps_business_payload_under_data_field', async () => {
    setSession({ userId: '1001' })
    getUserModelPreferencesMock.mockResolvedValue({
      data: {
        mainAgentModelConfigId: 'mcfg-data-9001',
        dirtyWorkAgentModelConfigId: 'mcfg-data-9002',
        candidateConfigs: [
          { modelConfigId: 'mcfg-data-9001', modelName: 'gpt-4o-mini', keySourceType: 'USER_KEY' },
          { modelConfigId: 'mcfg-data-9002', modelName: 'deepseek-chat', keySourceType: 'OFFICIAL_KEY' },
        ],
      },
    })

    const { useProfileSettings } = await import('./useProfileSettings')
    const settings = useProfileSettings()

    await settings.loadModelPreferences()

    expect(settings.modelPreferences.mainAgentModelConfigId).toBe('mcfg-data-9001')
    expect(settings.modelPreferences.dirtyWorkAgentModelConfigId).toBe('mcfg-data-9002')
    expect(settings.modelConfigOptions.value).toEqual([
      { modelConfigId: 'mcfg-data-9001', modelName: 'gpt-4o-mini', keySourceType: 'USER_KEY' },
      { modelConfigId: 'mcfg-data-9002', modelName: 'deepseek-chat', keySourceType: 'OFFICIAL_KEY' },
    ])
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
