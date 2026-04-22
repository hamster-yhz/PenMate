import request from '@/utils/request'
import type { IdLike } from '@/api/types'

type AnyRecord = Record<string, unknown>

const normalizeConfigPayload = (payload: AnyRecord) => {
  const next: AnyRecord = { ...payload }
  if (typeof next.configName === 'string' && !next.policyName) {
    next.policyName = next.configName
  }
  if (typeof next.modelInput === 'string' && !next.modelName) {
    next.modelName = next.modelInput
  }
  if (typeof next.modelName === 'string') {
    const trimmed = next.modelName.trim()
    next.modelName = trimmed || undefined
  }
  if (typeof next.providerModelId === 'number' && next.providerModelId <= 0) {
    next.providerModelId = undefined
  }
  if (typeof next.baseUrl === 'string') {
    const trimmed = next.baseUrl.trim()
    next.baseUrl = trimmed || undefined
  }
  if (!next.scene) {
    next.scene = 'write'
  }
  return next
}

export const modelApi = {
  listProviders() {
    return request.get<AnyRecord[]>('/v1/model/providers')
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
  listConfigs(projectId: IdLike) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/model-configs`)
  },
  createConfig(projectId: IdLike, operatorId: IdLike, payload: AnyRecord) {
    return request.post<string>(
      `/v1/novels/${projectId}/model-configs?operatorId=${operatorId}`,
      normalizeConfigPayload(payload)
    )
  },
  updateConfig(projectId: IdLike, configId: IdLike, operatorId: IdLike, payload: AnyRecord) {
    return request.put<string>(
      `/v1/novels/${projectId}/model-configs/${configId}?operatorId=${operatorId}`,
      normalizeConfigPayload(payload)
    )
  },
  deleteConfig(projectId: IdLike, configId: IdLike, operatorId: IdLike) {
    return request.delete<string>(`/v1/novels/${projectId}/model-configs/${configId}?operatorId=${operatorId}`)
  },
  setDefaultConfig(projectId: IdLike, configId: IdLike, operatorId: IdLike) {
    return request.post<string>(`/v1/novels/${projectId}/model-configs/${configId}/set-default?operatorId=${operatorId}`)
  },
  listPolicies(projectId: IdLike) {
    return this.listConfigs(projectId)
  },
  createPolicy(projectId: IdLike, operatorId: IdLike, payload: AnyRecord) {
    return this.createConfig(projectId, operatorId, payload)
  },
  updatePolicy(projectId: IdLike, policyId: IdLike, operatorId: IdLike, payload: AnyRecord) {
    return this.updateConfig(projectId, policyId, operatorId, payload)
  },
  setDefaultPolicy(projectId: IdLike, policyId: IdLike, operatorId: IdLike) {
    return this.setDefaultConfig(projectId, policyId, operatorId)
  }
}

