import { computed, ref, toValue, watch, type MaybeRefOrGetter } from 'vue'
import { modelApi } from '@/api/modules/model.api'

export interface ModelDiscoverySource {
  providerId: MaybeRefOrGetter<string>
  modelType: MaybeRefOrGetter<'CHAT' | 'EMBEDDING'>
  baseUrl: MaybeRefOrGetter<string>
  apiKey: MaybeRefOrGetter<string>
  modelConfigId?: MaybeRefOrGetter<string | undefined>
  systemScope?: MaybeRefOrGetter<boolean | undefined>
}

export const useModelDiscovery = (
  source: ModelDiscoverySource,
  onSelect: (model: string) => void,
) => {
  const discovering = ref(false)
  const discoveryError = ref('')
  const models = ref<string[]>([])
  const query = ref('')
  const resultsOpen = ref(false)
  const selectedFromDiscovery = ref('')

  const connectionFingerprint = computed(() => [
    toValue(source.providerId),
    toValue(source.modelType),
    toValue(source.baseUrl).trim(),
    toValue(source.apiKey).trim(),
    toValue(source.modelConfigId) || '',
    String(toValue(source.systemScope) === true),
  ].join('\u0000'))

  const filteredModels = computed(() => {
    const term = query.value.trim().toLowerCase()
    return term ? models.value.filter((model) => model.toLowerCase().includes(term)) : models.value
  })

  const reset = () => {
    models.value = []
    query.value = ''
    resultsOpen.value = false
    discoveryError.value = ''
    selectedFromDiscovery.value = ''
  }

  watch(connectionFingerprint, reset)

  const discover = async () => {
    const providerId = toValue(source.providerId)
    if (discovering.value || !providerId) return
    discovering.value = true
    discoveryError.value = ''
    selectedFromDiscovery.value = ''
    try {
      const result = await modelApi.discoverModels({
        modelConfigId: toValue(source.modelConfigId) || undefined,
        providerId,
        modelType: toValue(source.modelType),
        baseUrl: toValue(source.baseUrl) || undefined,
        apiKey: toValue(source.apiKey) || undefined,
      }, toValue(source.systemScope))
      models.value = Array.isArray(result.models) ? result.models : []
      query.value = ''
      resultsOpen.value = true
    } catch (cause: unknown) {
      models.value = []
      resultsOpen.value = false
      discoveryError.value = cause instanceof Error ? cause.message : '无法获取站点模型'
    } finally {
      discovering.value = false
    }
  }

  const selectModel = (model: string) => {
    onSelect(model)
    selectedFromDiscovery.value = model
    resultsOpen.value = false
  }

  const closeResults = () => {
    resultsOpen.value = false
  }

  return {
    discovering,
    discoveryError,
    models,
    query,
    resultsOpen,
    selectedFromDiscovery,
    filteredModels,
    discover,
    selectModel,
    closeResults,
  }
}
