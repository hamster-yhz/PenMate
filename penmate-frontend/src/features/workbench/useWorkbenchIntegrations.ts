import { ref } from 'vue'
import { modelApi } from '@/api/modules/model.api'
import { pluginApi } from '@/api/modules/plugin.api'
import { pickBusinessRecord } from '@/utils/apiPayload'

type WorkbenchIntegrationOptions = {
  getUserId: () => string | undefined
}

const modelConfigId = (item: Record<string, unknown>) => {
  if (typeof item.modelConfigId !== 'string') return null
  return item.modelConfigId.trim() || null
}

const preferenceRecord = (payload: unknown): Record<string, unknown> => {
  if (!payload || typeof payload !== 'object') return {}
  const record = pickBusinessRecord(payload)
  if (record.preferences && typeof record.preferences === 'object') {
    return record.preferences as Record<string, unknown>
  }
  if (record.config && typeof record.config === 'object') return record.config as Record<string, unknown>
  return record
}

export const useWorkbenchIntegrations = ({ getUserId }: WorkbenchIntegrationOptions) => {
  const activePlugins = ref<string[]>([])
  const activeModelConfigId = ref<string | null>(null)
  const currentModelName = ref('')

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
      return null
    }
    try {
      const detail = pickBusinessRecord(await modelApi.getUserModelPreferences(userId))
      const preferences = preferenceRecord(detail)
      const configs = Array.isArray(detail.candidateConfigs)
        ? (detail.candidateConfigs as Array<Record<string, unknown>>)
        : Array.isArray(preferences.candidateConfigs)
          ? (preferences.candidateConfigs as Array<Record<string, unknown>>)
          : Array.isArray(detail.modelConfigs)
            ? (detail.modelConfigs as Array<Record<string, unknown>>)
            : Array.isArray(preferences.modelConfigs)
              ? (preferences.modelConfigs as Array<Record<string, unknown>>)
              : []
      const preferredId =
        typeof preferences.mainAgentModelConfigId === 'string' ? preferences.mainAgentModelConfigId.trim() : ''
      const preferred = configs.find((item) => modelConfigId(item) === preferredId) || configs[0]
      activeModelConfigId.value = preferred ? modelConfigId(preferred) : null
      currentModelName.value = String(preferred?.modelName || '').trim()
      return activeModelConfigId.value
    } catch {
      activeModelConfigId.value = null
      currentModelName.value = ''
      return null
    }
  }

  const ensureModelConfigId = async () => (await refreshActiveModelInfo()) || ''

  return {
    activePlugins,
    currentModelName,
    loadActivePlugins,
    refreshActiveModelInfo,
    ensureModelConfigId,
  }
}
