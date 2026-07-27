import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import {
  modelApi,
  type ModelConfigurationItem,
  type ModelConnectionTestResult,
  type ModelProviderOption,
  type ModelReasoningCapabilities,
} from '@/api/modules/model.api'

export type { ModelConfigurationItem }
export type AdminModelTypeFilter = 'ALL' | 'CHAT' | 'EMBEDDING'

const defaultForm = () => ({
  providerId: '',
  displayName: '',
  modelType: 'CHAT' as 'CHAT' | 'EMBEDDING',
  modelName: '',
  baseUrl: '',
  apiKey: '',
  distanceMetric: 'COSINE' as 'COSINE' | 'INNER_PRODUCT' | 'L2',
  embeddingDimensions: undefined as number | undefined,
  maxContextTokens: undefined as number | undefined,
  maxOutputTokens: undefined as number | undefined,
  reasoningEffort: 'AUTO' as NonNullable<ModelConfigurationItem['reasoningEffort']>,
  reasoningMode: 'AUTO' as NonNullable<ModelConfigurationItem['reasoningMode']>,
  reasoningSummary: 'AUTO' as NonNullable<ModelConfigurationItem['reasoningSummary']>,
  autoDetectCapacity: true,
  enabled: true,
})

export const useAdminModelServices = () => {
  const configurations = ref<ModelConfigurationItem[]>([])
  const providers = ref<ModelProviderOption[]>([])
  const loading = ref(false)
  const loadError = ref('')
  const query = ref('')
  const activeType = ref<AdminModelTypeFilter>('ALL')
  const testingId = ref('')
  const probing = ref(false)
  const drawerOpen = ref(false)
  const editingId = ref('')
  const saving = ref(false)
  const formError = ref('')
  const form = reactive(defaultForm())
  const reasoningCapabilities = ref<ModelReasoningCapabilities>({
    efforts: ['AUTO'], modes: ['AUTO'], summaries: ['AUTO'], source: 'UNSUPPORTED',
  })
  let reasoningRequestSequence = 0
  let reasoningResolveTimer: ReturnType<typeof setTimeout> | undefined

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
  const supportsReasoningEffort = computed(() => reasoningCapabilities.value.efforts.length > 1)
  const supportsReasoningMode = computed(() => reasoningCapabilities.value.modes.length > 1)
  const supportsReasoningSummary = computed(() => reasoningCapabilities.value.summaries.length > 1)

  const resolveReasoningCapabilities = async () => {
    const sequence = ++reasoningRequestSequence
    if (form.modelType !== 'CHAT' || !form.providerId || !form.modelName.trim()) {
      reasoningCapabilities.value = { efforts: ['AUTO'], modes: ['AUTO'], summaries: ['AUTO'], source: 'UNSUPPORTED' }
      return
    }
    try {
      const resolved = await modelApi.getReasoningCapabilities(form.providerId, form.modelName.trim())
      if (sequence !== reasoningRequestSequence) return
      reasoningCapabilities.value = resolved
      if (!resolved.efforts.includes(form.reasoningEffort)) form.reasoningEffort = 'AUTO'
      if (!resolved.modes.includes(form.reasoningMode)) form.reasoningMode = 'AUTO'
      if (!resolved.summaries.includes(form.reasoningSummary)) form.reasoningSummary = 'AUTO'
    } catch {
      if (sequence === reasoningRequestSequence) {
        reasoningCapabilities.value = { efforts: ['AUTO'], modes: ['AUTO'], summaries: ['AUTO'], source: 'UNSUPPORTED' }
      }
    }
  }

  watch([() => form.providerId, () => form.modelName, () => form.modelType], () => {
    if (reasoningResolveTimer) clearTimeout(reasoningResolveTimer)
    reasoningResolveTimer = setTimeout(resolveReasoningCapabilities, 200)
  })
  onBeforeUnmount(() => {
    if (reasoningResolveTimer) clearTimeout(reasoningResolveTimer)
  })

  const load = async () => {
    loading.value = true
    loadError.value = ''
    try {
      const [configurationRows, providerRows] = await Promise.all([
        modelApi.listSystemModelConfigs(),
        modelApi.listProviders(),
      ])
      configurations.value = configurationRows
      providers.value = providerRows as ModelProviderOption[]
    } catch (cause: unknown) {
      loadError.value = cause instanceof Error ? cause.message : '加载官方模型失败'
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
      reasoningEffort: item.reasoningEffort || 'AUTO',
      reasoningMode: item.reasoningMode || 'AUTO',
      reasoningSummary: item.reasoningSummary || 'AUTO',
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
      if (editingId.value) await modelApi.updateSystemModelConfig(editingId.value, payload)
      else await modelApi.createSystemModelConfig(payload)
      drawerOpen.value = false
      await load()
      return true
    } catch (cause: unknown) {
      formError.value = cause instanceof Error ? cause.message : '保存官方模型失败'
      return false
    } finally {
      saving.value = false
    }
  }

  const probeEmbeddingDimensions = async () => {
    if (probing.value || form.modelType !== 'EMBEDDING') return null
    probing.value = true
    formError.value = ''
    try {
      const result = await modelApi.probeEmbeddingDimensions({
        modelConfigId: editingId.value || undefined,
        providerId: form.providerId,
        modelName: form.modelName,
        baseUrl: form.baseUrl || undefined,
        embeddingDimensions: form.embeddingDimensions,
        apiKey: form.apiKey || undefined,
      }, true)
      const dimensions = Number(result.dimensions)
      if (Number.isSafeInteger(dimensions) && dimensions > 0) form.embeddingDimensions = dimensions
      return dimensions
    } catch (cause: unknown) {
      formError.value = cause instanceof Error ? cause.message : '无法探测向量维度'
      return null
    } finally {
      probing.value = false
    }
  }

  const removeConfiguration = async (item: ModelConfigurationItem) => {
    await modelApi.deleteSystemModelConfig(item.modelConfigId)
    configurations.value = configurations.value.filter((row) => row.modelConfigId !== item.modelConfigId)
  }

  const testConnection = async (item: ModelConfigurationItem): Promise<ModelConnectionTestResult> => {
    testingId.value = item.modelConfigId
    try {
      const result = await modelApi.testSystemModelConnection(item.modelConfigId)
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
    probing,
    drawerOpen,
    editingId,
    saving,
    formError,
    form,
    filteredConfigurations,
    compatibleProviders,
    reasoningCapabilities,
    supportsReasoningEffort,
    supportsReasoningMode,
    supportsReasoningSummary,
    load,
    openCreate,
    openEdit,
    closeDrawer,
    save,
    probeEmbeddingDimensions,
    removeConfiguration,
    testConnection,
  }
}
