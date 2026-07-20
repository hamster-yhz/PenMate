import request from '@/utils/request'

type AnyRecord = Record<string, unknown>

const normalizeBusinessStringId = (value: unknown) => {
  if (typeof value !== 'string') {
    return null
  }
  const trimmed = value.trim()
  return trimmed || null
}

const normalizeUserModelConfigPayload = (payload: AnyRecord) => {
  const next: AnyRecord = { ...payload }
  if (typeof next.modelCategory !== 'string' && typeof next.keySourceType === 'string') {
    next.modelCategory = next.keySourceType === 'OFFICIAL_KEY' ? 'OFFICIAL_MODEL' : 'USER_MODEL'
  }
  if (typeof next.modelName === 'string') {
    const trimmed = next.modelName.trim()
    next.modelName = trimmed || undefined
  }
  if (typeof next.baseUrl === 'string') {
    const trimmed = next.baseUrl.trim()
    next.baseUrl = trimmed || undefined
  }
  if (typeof next.modelCategory === 'string') {
    const trimmed = next.modelCategory.trim()
    next.modelCategory = trimmed || undefined
  }
  if (typeof next.apiKey === 'string') {
    const trimmed = next.apiKey.trim()
    next.apiKey = trimmed || undefined
  }
  if (typeof next.status === 'string') {
    const trimmed = next.status.trim()
    next.status = trimmed || undefined
  }
  delete next.id
  delete next.modelConfigId
  next.displayName = typeof next.displayName === 'string' && next.displayName.trim() ? next.displayName.trim() : next.modelName
  next.modelType = typeof next.modelType === 'string' ? next.modelType : 'CHAT'
  delete next.keyName
  delete next.selectedKeyId
  delete next.userKeyId
  delete next.officialKeyId
  delete next.keySourceType
  return next
}

const normalizeUserModelPreferencePayload = (payload: AnyRecord) => ({
  defaultMainChatModelConfigId: normalizeBusinessStringId(payload.mainAgentModelConfigId),
  defaultWorkerChatModelConfigId: normalizeBusinessStringId(payload.dirtyWorkAgentModelConfigId),
  defaultEmbeddingModelConfigId: normalizeBusinessStringId(payload.defaultEmbeddingModelConfigId),
  defaultRouterModelConfigId: normalizeBusinessStringId(payload.defaultRouterModelConfigId),
  defaultStoryBibleRoutingMode: payload.defaultStoryBibleRoutingMode ?? 'LLM_SELECTOR',
  defaultChunkTargetCharacters: payload.defaultChunkTargetCharacters ?? 800,
  defaultChunkOverlapCharacters: payload.defaultChunkOverlapCharacters ?? 120,
  defaultChunkMaxCharacters: payload.defaultChunkMaxCharacters ?? 1200,
})

const normalizeProviderPayload = (payload: AnyRecord): AnyRecord | null => {
  const next: AnyRecord = { ...payload }
  const providerId = normalizeBusinessStringId(next.providerId)
  if (providerId === null) {
    return null
  }
  delete next.id
  next.providerId = providerId
  return next
}

const assertNoLegacyOnlyProviderEntries = (providers: AnyRecord[]) => {
  const hasLegacyOnlyEntry = providers.some(
    (item) =>
      item != null &&
      typeof item === 'object' &&
      'id' in item &&
      normalizeBusinessStringId((item as AnyRecord).providerId) === null,
  )
  if (hasLegacyOnlyEntry) {
    throw new Error('Invalid provider contract')
  }
}

export const modelApi = {
  async listProviders() {
    const providers = await request.get<AnyRecord[]>('/v1/model/providers')
    const normalizedProviders = Array.isArray(providers) ? providers : []
    assertNoLegacyOnlyProviderEntries(normalizedProviders)
    return normalizedProviders
      .map((item) => normalizeProviderPayload(item))
      .filter((item): item is AnyRecord => item !== null)
  },
  listKeys(_userId: string) {
    void _userId
    return request.get<AnyRecord[]>('/v1/model/configurations')
  },
  listUserModelConfigs(_userId: string) {
    void _userId
    return request.get<AnyRecord[]>('/v1/model/configurations')
  },
  createUserModelConfig(_userId: string, _operatorId: string, payload: AnyRecord) {
    return request.post<AnyRecord>('/v1/model/configurations', normalizeUserModelConfigPayload(payload))
  },
  updateUserModelConfig(_userId: string, businessModelConfigId: string, _operatorId: string, payload: AnyRecord) {
    return request.put<AnyRecord>(
      `/v1/model/configurations/${businessModelConfigId}`,
      normalizeUserModelConfigPayload(payload),
    )
  },
  deleteUserModelConfig(_userId: string, businessModelConfigId: string, _operatorId: string) {
    void _userId
    void _operatorId
    return request.delete<string>(`/v1/model/configurations/${businessModelConfigId}`)
  },
  async getUserModelPreferences(_userId: string) {
    void _userId
    const result = await request.get<AnyRecord>('/v1/model/preferences')
    return {
      ...result,
      mainAgentModelConfigId: result.defaultMainChatModelConfigId,
      dirtyWorkAgentModelConfigId: result.defaultWorkerChatModelConfigId,
    }
  },
  saveUserModelPreferences(_userId: string, _operatorId: string, payload: AnyRecord) {
    return request.put<AnyRecord>(
      '/v1/model/preferences',
      normalizeUserModelPreferencePayload(payload),
    )
  },
}
