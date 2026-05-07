import request from '@/utils/request'
import type { IdLike } from '@/api/types'

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
  delete next.modelType
  delete next.keyName
  delete next.selectedKeyId
  delete next.userKeyId
  delete next.officialKeyId
  delete next.keySourceType
  return next
}

const normalizeUserModelPreferencePayload = (payload: AnyRecord) => ({
  mainAgentModelConfigId: normalizeBusinessStringId(payload.mainAgentModelConfigId),
  dirtyWorkAgentModelConfigId: normalizeBusinessStringId(payload.dirtyWorkAgentModelConfigId),
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

export const modelApi = {
  async listProviders() {
    const providers = await request.get<AnyRecord[]>('/v1/model/providers')
    return (providers ?? [])
      .map((item) => normalizeProviderPayload(item))
      .filter((item): item is AnyRecord => item !== null)
  },
  listKeys(userId: IdLike) {
    return request.get<AnyRecord[]>(`/v1/model/keys?userId=${userId}`)
  },
  createKey(userId: IdLike, operatorId: IdLike, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/model/keys?userId=${userId}&operatorId=${operatorId}`, payload)
  },
  updateKey(keyId: IdLike, userId: IdLike, operatorId: IdLike, payload: AnyRecord) {
    return request.patch<string>(`/v1/model/keys/${keyId}?userId=${userId}&operatorId=${operatorId}`, payload)
  },
  deleteKey(keyId: IdLike, userId: IdLike, operatorId: IdLike) {
    return request.delete<string>(`/v1/model/keys/${keyId}?userId=${userId}&operatorId=${operatorId}`)
  },
  listOfficialKeys() {
    return request.get<AnyRecord[]>('/v1/model/official-keys')
  },
  createOfficialKey(operatorId: IdLike, payload: AnyRecord) {
    return request.post<string>(`/v1/model/official-keys?operatorId=${operatorId}`, payload)
  },
  updateOfficialKey(keyId: IdLike, operatorId: IdLike, payload: AnyRecord) {
    return request.patch<string>(`/v1/model/official-keys/${keyId}?operatorId=${operatorId}`, payload)
  },
  deleteOfficialKey(keyId: IdLike, operatorId: IdLike) {
    return request.delete<string>(`/v1/model/official-keys/${keyId}?operatorId=${operatorId}`)
  },
  listUserModelConfigs(userId: IdLike) {
    return request.get<AnyRecord[]>(`/v1/model/configs?userId=${userId}`)
  },
  createUserModelConfig(userId: IdLike, operatorId: IdLike, payload: AnyRecord) {
    return request.post<string>(
      `/v1/model/configs?userId=${userId}&operatorId=${operatorId}`,
      normalizeUserModelConfigPayload(payload)
    )
  },
  updateUserModelConfig(userId: IdLike, businessModelConfigId: IdLike, operatorId: IdLike, payload: AnyRecord) {
    return request.put<string>(
      `/v1/model/configs/${businessModelConfigId}?userId=${userId}&operatorId=${operatorId}`,
      normalizeUserModelConfigPayload(payload)
    )
  },
  deleteUserModelConfig(userId: IdLike, businessModelConfigId: IdLike, operatorId: IdLike) {
    return request.delete<string>(`/v1/model/configs/${businessModelConfigId}?userId=${userId}&operatorId=${operatorId}`)
  },
  getUserModelPreferences(userId: IdLike) {
    return request.get<AnyRecord>(`/v1/model/preferences?userId=${userId}`)
  },
  saveUserModelPreferences(userId: IdLike, operatorId: IdLike, payload: AnyRecord) {
    return request.post<string>(
      `/v1/model/preferences?userId=${userId}&operatorId=${operatorId}`,
      normalizeUserModelPreferencePayload(payload)
    )
  },
}
