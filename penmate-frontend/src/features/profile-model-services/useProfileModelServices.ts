import { computed, onMounted, reactive, ref } from 'vue'
import {
  modelApi,
  type ModelConfigurationItem,
  type ModelConnectionTestResult,
  type ModelProviderOption,
} from '@/api/modules/model.api'
import { getSession } from '@/stores/session'

export type { ModelConfigurationItem }
export type ModelTypeFilter = 'ALL' | 'CHAT' | 'EMBEDDING'

const defaultForm = () => ({
  providerId: '',
  displayName: '',
  modelType: 'CHAT' as 'CHAT' | 'EMBEDDING',
  modelName: '',
  baseUrl: '',
  apiKey: '',
  distanceMetric: 'COSINE',
  embeddingDimensions: undefined as number | undefined,
  maxContextTokens: undefined as number | undefined,
  maxOutputTokens: undefined as number | undefined,
  autoDetectCapacity: true,
  enabled: true,
})

export const useProfileModelServices = () => {
  const session = getSession()
  const configurations = ref<ModelConfigurationItem[]>([])
  const providers = ref<ModelProviderOption[]>([])
  const loading = ref(false)
  const loadError = ref('')
  const query = ref('')
  const activeType = ref<ModelTypeFilter>('ALL')
  const testingId = ref('')
  const drawerOpen = ref(false)
  const editingId = ref('')
  const saving = ref(false)
  const formError = ref('')
  const form = reactive(defaultForm())

  const filteredConfigurations = computed(() => {
    const term = query.value.trim().toLowerCase()
    return configurations.value.filter((item) =>
      (activeType.value === 'ALL' || item.modelType === activeType.value)
      && (!term || [item.displayName, item.modelName, item.providerName, item.providerCode]
        .some((value) => String(value || '').toLowerCase().includes(term))))
  })

  const compatibleProviders = computed(() => providers.value.filter((provider) =>
    !provider.capabilities?.length
    || provider.capabilities.some((capability) => capability.capabilityCode === form.modelType)))

  const load = async () => {
    loading.value = true
    loadError.value = ''
    try {
      const [configurationRows, providerRows] = await Promise.all([
        modelApi.listUserModelConfigs(session.userId || ''),
        modelApi.listProviders(),
      ])
      configurations.value = configurationRows.filter((item) => item.scopeType === 'USER')
      providers.value = providerRows as ModelProviderOption[]
    } catch (cause: unknown) {
      loadError.value = cause instanceof Error ? cause.message : '加载个人模型失败'
    } finally {
      loading.value = false
    }
  }

  const resetForm = () => Object.assign(form, defaultForm())

  const openCreate = () => {
    editingId.value = ''
    resetForm()
    formError.value = ''
    drawerOpen.value = true
  }

  const openEdit = (item: ModelConfigurationItem) => {
    editingId.value = item.modelConfigId
    Object.assign(form, {
      providerId: item.providerId,
      displayName: item.displayName || item.modelName,
      modelType: item.modelType,
      modelName: item.modelName,
      baseUrl: item.baseUrl || '',
      apiKey: '',
      distanceMetric: item.distanceMetric || 'COSINE',
      embeddingDimensions: item.embeddingDimensions ?? undefined,
      maxContextTokens: item.maxContextTokens || 128000,
      maxOutputTokens: item.maxOutputTokens || 8192,
      autoDetectCapacity: item.contextCapacitySource !== 'MANUAL',
      enabled: item.status !== 'DISABLED',
    })
    formError.value = ''
    drawerOpen.value = true
  }

  const closeDrawer = () => {
    if (!saving.value) drawerOpen.value = false
  }

  const save = async () => {
    if (saving.value) return false
    saving.value = true
    formError.value = ''
    const payload = { ...form, status: form.enabled ? 'ACTIVE' : 'DISABLED' }
    try {
      if (editingId.value) {
        await modelApi.updateUserModelConfig(
          session.userId || '', editingId.value, session.userId || '', payload)
      } else {
        await modelApi.createUserModelConfig(session.userId || '', session.userId || '', payload)
      }
      drawerOpen.value = false
      await load()
      return true
    } catch (cause: unknown) {
      formError.value = cause instanceof Error ? cause.message : '保存模型失败'
      return false
    } finally {
      saving.value = false
    }
  }

  const removeConfiguration = async (item: ModelConfigurationItem) => {
    await modelApi.deleteUserModelConfig(session.userId || '', item.modelConfigId, session.userId || '')
    configurations.value = configurations.value.filter((row) => row.modelConfigId !== item.modelConfigId)
  }

  const testConnection = async (item: ModelConfigurationItem): Promise<ModelConnectionTestResult> => {
    testingId.value = item.modelConfigId
    try {
      const result = await modelApi.testUserModelConnection(item.modelConfigId)
      Object.assign(item, {
        lastTestStatus: result.success ? 'SUCCESS' : 'FAILED',
        lastTestLatencyMs: result.latencyMs,
        lastTestError: result.error,
        lastTestedAt: result.testedAt,
        ...(result.dimensions ? { embeddingDimensions: result.dimensions } : {}),
      })
      return result
    } finally {
      testingId.value = ''
    }
  }

  onMounted(load)

  return {
    configurations,
    loading,
    loadError,
    query,
    activeType,
    testingId,
    drawerOpen,
    editingId,
    saving,
    formError,
    form,
    filteredConfigurations,
    compatibleProviders,
    load,
    openCreate,
    openEdit,
    closeDrawer,
    save,
    removeConfiguration,
    testConnection,
  }
}
