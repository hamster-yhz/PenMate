import { reactive, ref } from 'vue'
import { modelApi } from '@/api/modules/model.api'
import { novelApi } from '@/api/modules/novel.api'
import { authApi, type UserProfile } from '@/api/modules/auth.api'
import { clearSession, getSession } from '@/stores/session'
import { pickBusinessRecord } from '@/utils/apiPayload'
import { broadcastSessionLogout } from '@/utils/request'
import { getErrorMessage } from '@/utils/errors'

export interface ProfileModel {
  name: string
  email: string
  bio: string
  bookCount: number
  totalWords: number
  defaultStyle: string
  autoSaveInterval: number
  fontSize: number
}

export interface ProfileApiKeyItem {
  id: string
  name: string
  maskedKey: string
  status: 'active' | 'none'
}

export interface ProfilePasswordPayload {
  old: string
  new1: string
  new2: string
}

export interface ProfileEmailPayload {
  email: string
  currentPassword: string
}

export interface ProfileActionResult {
  success: boolean
  error?: string
  deletionDueAt?: string
}

export interface ProfileModelPreferences {
  creativeModelConfigId: string | null
  contextSelectorModelConfigId: string | null
  embeddingModelConfigId: string | null
}

export interface ProfileModelConfigOption {
  modelConfigId: string
  modelName: string
  displayName?: string
  modelType?: string
  providerName?: string
  keySourceType?: string
}

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const PREFERENCES_KEY = 'penmate.ui-preferences'

const readUiPreferences = () => {
  try {
    const parsed = JSON.parse(localStorage.getItem(PREFERENCES_KEY) || '{}') as Record<string, unknown>
    return {
      autoSaveInterval: [15, 30, 60, 120].includes(Number(parsed.autoSaveInterval))
        ? Number(parsed.autoSaveInterval)
        : 30,
      fontSize: [14, 16, 18, 20].includes(Number(parsed.fontSize)) ? Number(parsed.fontSize) : 16,
    }
  } catch {
    return { autoSaveInterval: 30, fontSize: 16 }
  }
}

const extractPreferenceRecord = (payload: unknown): Record<string, unknown> => {
  if (!payload || typeof payload !== 'object') {
    return {}
  }

  const record = pickBusinessRecord(payload)
  const nestedPreferences = record.preferences
  if (nestedPreferences && typeof nestedPreferences === 'object') {
    return nestedPreferences as Record<string, unknown>
  }

  const nestedConfig = record.config
  if (nestedConfig && typeof nestedConfig === 'object') {
    return nestedConfig as Record<string, unknown>
  }

  return record
}

export const useProfileSettings = () => {
  const uiPreferences = readUiPreferences()
  const profile = reactive<ProfileModel>({
    name: '',
    email: '',
    bio: '',
    bookCount: 0,
    totalWords: 0,
    defaultStyle: '按项目设置',
    autoSaveInterval: uiPreferences.autoSaveInterval,
    fontSize: uiPreferences.fontSize,
  })

  const apiKeys = ref<ProfileApiKeyItem[]>([])

  const session = getSession()
  const modelPreferences = reactive<ProfileModelPreferences>({
    creativeModelConfigId: null,
    contextSelectorModelConfigId: null,
    embeddingModelConfigId: null,
  })
  const modelConfigOptions = ref<ProfileModelConfigOption[]>([])

  const applyProfile = (value: UserProfile) => {
    profile.name = String(value.displayName ?? value.username ?? value.name ?? '').trim()
    profile.email = String(value.email ?? '').trim()
    profile.bio = String(value.bio ?? '').trim()
  }

  const loadProfile = async () => {
    const current = await authApi.me()
    applyProfile(current)

    const [projectsResult, keysResult] = await Promise.allSettled([
      novelApi.listProjects(),
      session.userId ? modelApi.listKeys(session.userId) : Promise.resolve([]),
    ])
    if (projectsResult.status === 'fulfilled') {
      const projects = Array.isArray(projectsResult.value) ? projectsResult.value : []
      profile.bookCount = projects.length
      profile.totalWords = projects.reduce((sum, item) => sum + Number(item.totalWords ?? item.wordCount ?? 0), 0)
    }
    if (keysResult.status === 'fulfilled') {
      const keys = Array.isArray(keysResult.value) ? keysResult.value : []
      apiKeys.value = keys.map((item, index) => ({
        id: String(item.keyId ?? item.id ?? index),
        name: String(item.providerName ?? item.providerCode ?? item.name ?? '模型服务'),
        maskedKey: String(item.maskedKey ?? item.keyMask ?? '已配置'),
        status: 'active' as const,
      }))
    }
  }

  const saveProfile = async (nextProfile: Pick<ProfileModel, 'name' | 'bio'>): Promise<ProfileActionResult> => {
    const name = nextProfile.name.trim()
    const bio = nextProfile.bio.trim()

    if (!name) {
      return { success: false, error: '请输入昵称' }
    }

    try {
      applyProfile(await authApi.updateProfile({ displayName: name, bio }))
      return { success: true }
    } catch (error: unknown) {
      return { success: false, error: getErrorMessage(error, '保存资料失败') }
    }
  }

  const clearCredentialSession = () => {
    clearSession()
    broadcastSessionLogout()
  }

  const saveEmail = async (payload: ProfileEmailPayload): Promise<ProfileActionResult> => {
    const normalizedEmail = payload.email.trim()
    const currentPassword = payload.currentPassword.trim()

    if (!emailPattern.test(normalizedEmail)) {
      return { success: false, error: '请输入有效邮箱地址' }
    }
    if (!currentPassword) {
      return { success: false, error: '请输入当前密码' }
    }

    try {
      await authApi.changeEmail({ currentPassword, newEmail: normalizedEmail })
      clearCredentialSession()
      return { success: true }
    } catch (error: unknown) {
      return { success: false, error: getErrorMessage(error, '修改邮箱失败') }
    }
  }

  const savePassword = async (payload: ProfilePasswordPayload): Promise<ProfileActionResult> => {
    const oldPassword = payload.old.trim()
    const nextPassword = payload.new1.trim()
    const confirmPassword = payload.new2.trim()

    if (!oldPassword) {
      return { success: false, error: '请输入当前密码' }
    }

    if (!nextPassword || !confirmPassword) {
      return { success: false, error: '请输入新密码' }
    }

    if (nextPassword !== confirmPassword) {
      return { success: false, error: '两次输入的新密码不一致' }
    }
    if (nextPassword.length < 8) return { success: false, error: '新密码至少需要8位' }

    try {
      await authApi.changePassword({ currentPassword: oldPassword, newPassword: nextPassword })
      clearCredentialSession()
      return { success: true }
    } catch (error: unknown) {
      return { success: false, error: getErrorMessage(error, '修改密码失败') }
    }
  }

  const updateAutoSaveInterval = (value: number) => {
    profile.autoSaveInterval = value
    localStorage.setItem(PREFERENCES_KEY, JSON.stringify({ autoSaveInterval: value, fontSize: profile.fontSize }))
  }

  const updateFontSize = (value: number) => {
    profile.fontSize = value
    localStorage.setItem(
      PREFERENCES_KEY,
      JSON.stringify({ autoSaveInterval: profile.autoSaveInterval, fontSize: value }),
    )
  }

  const resetModelPreferenceState = () => {
    modelPreferences.creativeModelConfigId = null
    modelPreferences.contextSelectorModelConfigId = null
    modelPreferences.embeddingModelConfigId = null
    modelConfigOptions.value = []
  }

  const normalizeModelConfigId = (value: string | null) => {
    if (typeof value !== 'string') {
      return null
    }
    const trimmed = value.trim()
    if (!trimmed) {
      return null
    }
    return modelConfigOptions.value.some((item) => item.modelConfigId === trimmed) ? trimmed : null
  }

  const loadModelPreferences = async () => {
    if (!session.userId) {
      resetModelPreferenceState()
      return
    }

    try {
      const [preferencePayload, configurations] = await Promise.all([
        modelApi.getUserModelPreferences(session.userId),
        modelApi.listUserModelConfigs(session.userId),
      ])
      const detail = pickBusinessRecord(preferencePayload)
      const preferenceRecord = extractPreferenceRecord(detail)
      const creativeValue = typeof preferenceRecord.defaultCreativeModelConfigId === 'string'
        ? preferenceRecord.defaultCreativeModelConfigId : null
      const selectorValue = typeof preferenceRecord.defaultContextSelectorModelConfigId === 'string'
        ? preferenceRecord.defaultContextSelectorModelConfigId : null
      const embeddingValue = typeof preferenceRecord.defaultEmbeddingModelConfigId === 'string'
        ? preferenceRecord.defaultEmbeddingModelConfigId : null

      const candidateConfigs = Array.isArray(configurations) && configurations.length
        ? configurations
        : Array.isArray(detail.candidateConfigs)
        ? detail.candidateConfigs
        : Array.isArray(preferenceRecord.candidateConfigs)
          ? preferenceRecord.candidateConfigs
          : Array.isArray((detail as Record<string, unknown>).modelConfigs)
            ? ((detail as Record<string, unknown>).modelConfigs as unknown[])
            : Array.isArray(preferenceRecord.modelConfigs)
              ? (preferenceRecord.modelConfigs as unknown[])
              : []
      const normalizedOptions: ProfileModelConfigOption[] = []
      for (const item of candidateConfigs) {
        const modelConfigId = typeof item.modelConfigId === 'string' ? item.modelConfigId.trim() : ''
        if (!modelConfigId) {
          continue
        }
        normalizedOptions.push({
          modelConfigId,
          modelName: String(item.modelName ?? ''),
          displayName: typeof item.displayName === 'string' ? item.displayName : undefined,
          modelType: typeof item.modelType === 'string' ? item.modelType : undefined,
          providerName: typeof item.providerName === 'string' ? item.providerName : undefined,
          keySourceType: typeof item.keySourceType === 'string' ? item.keySourceType : undefined,
        })
      }
      modelConfigOptions.value = normalizedOptions
      modelPreferences.creativeModelConfigId = normalizeModelConfigId(creativeValue)
      modelPreferences.contextSelectorModelConfigId = normalizeModelConfigId(selectorValue)
      modelPreferences.embeddingModelConfigId = normalizeModelConfigId(embeddingValue)
    } catch (error) {
      resetModelPreferenceState()
      throw error
    }
  }

  const saveModelPreferences = async () => {
    if (!session.userId) {
      throw new Error('缺少用户会话')
    }

    modelPreferences.creativeModelConfigId = normalizeModelConfigId(modelPreferences.creativeModelConfigId)
    modelPreferences.contextSelectorModelConfigId = normalizeModelConfigId(modelPreferences.contextSelectorModelConfigId)
    modelPreferences.embeddingModelConfigId = normalizeModelConfigId(modelPreferences.embeddingModelConfigId)

    await modelApi.saveUserModelPreferences(session.userId, session.userId, {
      creativeModelConfigId: modelPreferences.creativeModelConfigId,
      contextSelectorModelConfigId: modelPreferences.contextSelectorModelConfigId,
      defaultEmbeddingModelConfigId: modelPreferences.embeddingModelConfigId,
    })
  }

  const deleteAccount = async (currentPassword: string): Promise<ProfileActionResult> => {
    try {
      const receipt = await authApi.deleteAccount(currentPassword)
      clearSession()
      broadcastSessionLogout()
      return { success: true, deletionDueAt: receipt.deletionDueAt }
    } catch (error: unknown) {
      return { success: false, error: getErrorMessage(error, '注销账户失败') }
    }
  }

  return {
    profile,
    apiKeys,
    modelPreferences,
    modelConfigOptions,
    loadProfile,
    saveProfile,
    saveEmail,
    savePassword,
    updateAutoSaveInterval,
    updateFontSize,
    loadModelPreferences,
    saveModelPreferences,
    deleteAccount,
  }
}
