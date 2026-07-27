import { ref } from 'vue'
import { modelApi } from '@/api/modules/model.api'
import { pluginApi } from '@/api/modules/plugin.api'
import { ragApi } from '@/api/modules/rag.api'
import { pickBusinessArray, pickBusinessRecord } from '@/utils/apiPayload'

type WorkbenchIntegrationOptions = {
  getUserId: () => string | undefined
  getProjectId: () => string
}

const modelConfigId = (item: Record<string, unknown>) => {
  if (typeof item.modelConfigId !== 'string') return null
  return item.modelConfigId.trim() || null
}

const isActiveChatModel = (item: Record<string, unknown>) =>
  String(item.modelType ?? '').trim().toUpperCase() === 'CHAT' &&
  String(item.status ?? '').trim().toUpperCase() === 'ACTIVE' &&
  item.usable !== false

const preferenceRecord = (payload: unknown): Record<string, unknown> => {
  if (!payload || typeof payload !== 'object') return {}
  const record = pickBusinessRecord(payload)
  if (record.preferences && typeof record.preferences === 'object') {
    return record.preferences as Record<string, unknown>
  }
  if (record.config && typeof record.config === 'object') return record.config as Record<string, unknown>
  return record
}

export const useWorkbenchIntegrations = ({ getUserId, getProjectId }: WorkbenchIntegrationOptions) => {
  const activePlugins = ref<string[]>([])
  const activeModelConfigId = ref<string | null>(null)
  const currentModelName = ref('')
  const currentReasoningLabel = ref('')

  const loadActivePlugins = async (projectId: string) => {
    if (!projectId) {
      activePlugins.value = []
      return
    }
    try {
      const installs = (await pluginApi.listProjectPlugins(projectId)) as Array<Record<string, unknown>>
      activePlugins.value = installs
        .filter((item) => item.enabled !== false)
        .map((item) => String(item.pluginName || item.name || item.pluginCode || '').trim() || '未命名插件')
    } catch {
      activePlugins.value = []
    }
  }

  const refreshActiveModelInfo = async () => {
    const userId = getUserId()
    if (!userId) {
      activeModelConfigId.value = null
      currentModelName.value = ''
      currentReasoningLabel.value = ''
      return null
    }
    try {
      const projectId = getProjectId()
      const [preferencesPayload, configurationsPayload, projectConfiguration] = await Promise.all([
        modelApi.getUserModelPreferences(userId),
        modelApi.listUserModelConfigs(userId),
        projectId ? ragApi.getConfiguration(projectId) : Promise.resolve({}),
      ])
      const detail = pickBusinessRecord(preferencesPayload)
      const preferences = preferenceRecord(detail)
      const configs = pickBusinessArray<Record<string, unknown>>(configurationsPayload)
      const projectPreferences = projectConfiguration as Record<string, unknown>
      const accountPreferredId =
        typeof preferences.defaultCreativeModelConfigId === 'string'
          ? preferences.defaultCreativeModelConfigId.trim()
          : ''
      const projectPreferredId = typeof projectPreferences.creativeModelConfigId === 'string'
        ? projectPreferences.creativeModelConfigId.trim()
        : ''
      const preferredId = projectPreferredId || accountPreferredId
      const preferred = preferredId
        ? configs.find((item) => modelConfigId(item) === preferredId && isActiveChatModel(item))
        : undefined
      activeModelConfigId.value = preferred ? modelConfigId(preferred) : null
      currentModelName.value = String(preferred?.modelName || '').trim()
      const effort = String(preferred?.reasoningEffort || 'AUTO').toUpperCase()
      const mode = String(preferred?.reasoningMode || 'AUTO').toUpperCase()
      const labels: Record<string, string> = { AUTO: '自动', NONE: '关闭', MINIMAL: '最小', LOW: '低', MEDIUM: '中', HIGH: '高', XHIGH: '超高', MAX: '最高' }
      const modeLabels: Record<string, string> = { AUTO: '自动', STANDARD: '标准', PRO: '专业', ADAPTIVE: '自适应', DISABLED: '关闭' }
      currentReasoningLabel.value = preferred ? `推理 ${labels[effort] || effort} · ${modeLabels[mode] || mode}` : ''
      return activeModelConfigId.value
    } catch {
      activeModelConfigId.value = null
      currentModelName.value = ''
      currentReasoningLabel.value = ''
      return null
    }
  }

  const ensureModelConfigId = async () => (await refreshActiveModelInfo()) || ''

  return {
    activePlugins,
    currentModelName,
    currentReasoningLabel,
    loadActivePlugins,
    refreshActiveModelInfo,
    ensureModelConfigId,
  }
}
