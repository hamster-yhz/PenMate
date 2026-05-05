import { reactive, ref } from 'vue'
import { modelApi } from '@/api/modules/model.api'
import { getSession } from '@/stores/session'

export interface ProfileModel {
  name: string
  email: string
  bio: string
  bookCount: number
  totalWords: number
  daysActive: number
  streak: number
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

export interface ProfileActionResult {
  success: boolean
  error?: string
}

export interface ProfileModelPreferences {
  mainAgentModelConfigId: number | null
  dirtyWorkAgentModelConfigId: number | null
}

export interface ProfileModelConfigOption {
  modelConfigId: number
  modelName: string
  providerName?: string
  keySourceType?: string
}

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const buildParticleStyle = () => ({
  width: `${Math.random() * 3 + 1}px`,
  height: `${Math.random() * 3 + 1}px`,
  left: `${Math.random() * 100}%`,
  bottom: '-5px',
  animationDuration: `${Math.random() * 12 + 12}s`,
  animationDelay: `${Math.random() * 15}s`,
  opacity: Math.random() * 0.3 + 0.1,
})

export const useProfileSettings = () => {
  const profile = reactive<ProfileModel>({
    name: '墨客',
    email: 'moke@penmate.com',
    bio: '执笔问道，以墨寄情。热爱仙侠与悬疑交织的故事。',
    bookCount: 3,
    totalWords: 46370,
    daysActive: 42,
    streak: 7,
    defaultStyle: '古风文言化 · 慢节奏',
    autoSaveInterval: 30,
    fontSize: 16,
  })

  const apiKeys = ref<ProfileApiKeyItem[]>([
    { id: 'k1', name: 'DeepSeek', maskedKey: 'sk-****...7a2f', status: 'active' },
    { id: 'k2', name: 'OpenAI', maskedKey: '未配置', status: 'none' },
    { id: 'k3', name: 'Anthropic', maskedKey: '未配置', status: 'none' },
  ])

  const particleStyles = Array.from({ length: 10 }, buildParticleStyle)
  const session = getSession()
  const modelPreferences = reactive<ProfileModelPreferences>({
    mainAgentModelConfigId: null,
    dirtyWorkAgentModelConfigId: null,
  })
  const modelConfigOptions = ref<ProfileModelConfigOption[]>([])

  const saveProfile = (nextProfile: Pick<ProfileModel, 'name' | 'bio'>): ProfileActionResult => {
    const name = nextProfile.name.trim()
    const bio = nextProfile.bio.trim()

    if (!name) {
      return { success: false, error: '请输入昵称' }
    }

    profile.name = name
    profile.bio = bio

    return { success: true }
  }

  const saveEmail = (email: string): ProfileActionResult => {
    const normalizedEmail = email.trim()

    if (!emailPattern.test(normalizedEmail)) {
      return { success: false, error: '请输入有效邮箱地址' }
    }

    profile.email = normalizedEmail

    return { success: true }
  }

  const savePassword = (payload: ProfilePasswordPayload): ProfileActionResult => {
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

    return { success: true }
  }

  const updateAutoSaveInterval = (value: number) => {
    profile.autoSaveInterval = value
  }

  const updateFontSize = (value: number) => {
    profile.fontSize = value
  }

  const resetModelPreferenceState = () => {
    modelPreferences.mainAgentModelConfigId = null
    modelPreferences.dirtyWorkAgentModelConfigId = null
    modelConfigOptions.value = []
  }

  const loadModelPreferences = async () => {
    if (!session.userId) {
      resetModelPreferenceState()
      return
    }

    try {
      const detail = await modelApi.getUserModelPreferences(session.userId)
      const mainValue = Number(detail.mainAgentModelConfigId ?? 0)
      const dirtyValue = Number(detail.dirtyWorkAgentModelConfigId ?? 0)

      modelPreferences.mainAgentModelConfigId = mainValue > 0 ? mainValue : null
      modelPreferences.dirtyWorkAgentModelConfigId = dirtyValue > 0 ? dirtyValue : null
      modelConfigOptions.value = Array.isArray(detail.candidateConfigs)
        ? detail.candidateConfigs.map((item) => ({
            modelConfigId: Number(item.modelConfigId),
            modelName: String(item.modelName ?? ''),
            providerName: typeof item.providerName === 'string' ? item.providerName : undefined,
            keySourceType: typeof item.keySourceType === 'string' ? item.keySourceType : undefined,
          }))
        : []
    } catch (error) {
      resetModelPreferenceState()
      throw error
    }
  }

  const saveModelPreferences = async () => {
    if (!session.userId) {
      throw new Error('缺少用户会话')
    }

    await modelApi.saveUserModelPreferences(session.userId, session.userId, {
      mainAgentModelConfigId: modelPreferences.mainAgentModelConfigId,
      dirtyWorkAgentModelConfigId: modelPreferences.dirtyWorkAgentModelConfigId,
    })
  }

  const pStyle = (n: number) => particleStyles[n - 1] ?? particleStyles[0]

  return {
    profile,
    apiKeys,
    modelPreferences,
    modelConfigOptions,
    saveProfile,
    saveEmail,
    savePassword,
    updateAutoSaveInterval,
    updateFontSize,
    loadModelPreferences,
    saveModelPreferences,
    pStyle,
  }
}
