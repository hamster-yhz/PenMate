import { beforeEach, describe, expect, it, vi } from 'vitest'
import { clearSession, setSession } from '@/stores/session'

const {
  getUserModelPreferencesMock,
  saveUserModelPreferencesMock,
  listKeysMock,
  listUserModelConfigsMock,
  meMock,
  updateProfileMock,
  changeEmailMock,
  changePasswordMock,
} = vi.hoisted(() => ({
  getUserModelPreferencesMock: vi.fn(),
  saveUserModelPreferencesMock: vi.fn(),
  listKeysMock: vi.fn(),
  listUserModelConfigsMock: vi.fn(),
  meMock: vi.fn(),
  updateProfileMock: vi.fn(),
  changeEmailMock: vi.fn(),
  changePasswordMock: vi.fn(),
}))

vi.mock('@/api/modules/model.api', () => ({
  modelApi: {
    getUserModelPreferences: getUserModelPreferencesMock,
    saveUserModelPreferences: saveUserModelPreferencesMock,
    listKeys: listKeysMock,
    listUserModelConfigs: listUserModelConfigsMock,
  },
}))

vi.mock('@/api/modules/auth.api', () => ({
  authApi: {
    me: meMock,
    updateProfile: updateProfileMock,
    changeEmail: changeEmailMock,
    changePassword: changePasswordMock,
  },
}))

vi.mock('@/api/modules/novel.api', () => ({
  novelApi: { listProjects: vi.fn().mockResolvedValue([]) },
}))

describe('useProfileSettings', () => {
  beforeEach(() => {
    getUserModelPreferencesMock.mockReset()
    saveUserModelPreferencesMock.mockReset()
    listKeysMock.mockReset()
    listUserModelConfigsMock.mockReset()
    meMock.mockReset()
    updateProfileMock.mockReset()
    changeEmailMock.mockReset()
    changePasswordMock.mockReset()
    listKeysMock.mockResolvedValue([])
    listUserModelConfigsMock.mockResolvedValue([])
    localStorage.clear()
    clearSession()
  })

  it('rejects_invalid_email_without_overwriting_existing_email', async () => {
    const { useProfileSettings } = await import('./useProfileSettings')
    const settings = useProfileSettings()
    const originalEmail = settings.profile.email

    const result = await settings.saveEmail({ email: ' @ ', currentPassword: 'secret' })

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
    changeEmailMock.mockResolvedValue('ok')
    const { useProfileSettings } = await import('./useProfileSettings')
    const settings = useProfileSettings()
    settings.profile.email = 'writer@example.com'

    await expect(settings.saveProfile({ name: ' 新笔名 ', bio: ' 新简介 ' })).resolves.toEqual({ success: true })
    await expect(
      settings.savePassword({ old: 'old-password', new1: 'new-password', new2: 'new-password' }),
    ).resolves.toEqual({ success: true })

    expect(updateProfileMock).toHaveBeenCalledWith({
      displayName: '新笔名',
      bio: '新简介',
    })
    expect(changePasswordMock).toHaveBeenCalledWith({ currentPassword: 'old-password', newPassword: 'new-password' })
  })

  it('changes_email_with_the_current_password_and_clears_the_session', async () => {
    setSession({ userId: '1001', accessToken: 'atk_1' })
    changeEmailMock.mockResolvedValue('ok')
    const { useProfileSettings } = await import('./useProfileSettings')
    const settings = useProfileSettings()

    await expect(settings.saveEmail({
      email: ' NEW@example.com ',
      currentPassword: 'secret',
    })).resolves.toEqual({ success: true })

    expect(changeEmailMock).toHaveBeenCalledWith({ currentPassword: 'secret', newEmail: 'NEW@example.com' })
    expect(localStorage.getItem('penmate.session')).toBeNull()
  })

  it('loads the three user-facing default model roles', async () => {
    setSession({ userId: '1001' })
    getUserModelPreferencesMock.mockResolvedValue({
      defaultCreativeModelConfigId: '9001',
      defaultContextSelectorModelConfigId: '9002',
      defaultEmbeddingModelConfigId: '9003',
    })
    listUserModelConfigsMock.mockResolvedValue([
      { modelConfigId: '9001', displayName: '创作模型', modelName: 'gpt-5', modelType: 'CHAT' },
      { modelConfigId: '9002', displayName: '筛选模型', modelName: 'gpt-5-mini', modelType: 'CHAT' },
      { modelConfigId: '9003', displayName: '向量模型', modelName: 'text-embedding-3-large', modelType: 'EMBEDDING' },
    ])

    const { useProfileSettings } = await import('./useProfileSettings')
    const settings = useProfileSettings()
    await settings.loadModelPreferences()

    expect(settings.modelPreferences).toEqual({
      creativeModelConfigId: '9001',
      contextSelectorModelConfigId: '9002',
      embeddingModelConfigId: '9003',
    })
    expect(settings.modelConfigOptions.value).toHaveLength(3)
  })

  it('saves the three model roles without Worker fields', async () => {
    setSession({ userId: '1001' })
    getUserModelPreferencesMock.mockResolvedValue({
      defaultCreativeModelConfigId: '9001',
      defaultContextSelectorModelConfigId: '9002',
      defaultEmbeddingModelConfigId: '9003',
    })
    listUserModelConfigsMock.mockResolvedValue([
      { modelConfigId: '9001', modelName: 'gpt-5', modelType: 'CHAT' },
      { modelConfigId: '9002', modelName: 'gpt-5-mini', modelType: 'CHAT' },
      { modelConfigId: '9003', modelName: 'embedding', modelType: 'EMBEDDING' },
    ])
    const { useProfileSettings } = await import('./useProfileSettings')
    const settings = useProfileSettings()
    await settings.loadModelPreferences()
    await settings.saveModelPreferences()

    expect(saveUserModelPreferencesMock).toHaveBeenCalledWith('1001', '1001', {
      creativeModelConfigId: '9001',
      contextSelectorModelConfigId: '9002',
      defaultEmbeddingModelConfigId: '9003',
    })
  })

  it('clears model state after a loading failure', async () => {
    setSession({ userId: '1001' })
    getUserModelPreferencesMock.mockRejectedValue(new Error('load failed'))
    const { useProfileSettings } = await import('./useProfileSettings')
    const settings = useProfileSettings()

    await expect(settings.loadModelPreferences()).rejects.toThrow('load failed')

    expect(settings.modelPreferences.creativeModelConfigId).toBeNull()
    expect(settings.modelPreferences.contextSelectorModelConfigId).toBeNull()
    expect(settings.modelPreferences.embeddingModelConfigId).toBeNull()
    expect(settings.modelConfigOptions.value).toEqual([])
  })
})
