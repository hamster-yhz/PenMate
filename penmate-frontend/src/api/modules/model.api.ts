import request from '@/utils/request'
import type { IdLike } from '@/api/types'

type AnyRecord = Record<string, unknown>

export const modelApi = {
  listProviders() {
    return request.get<AnyRecord[]>('/v1/model/providers')
  },
  listProviderModels(providerCode: string) {
    return request.get<AnyRecord[]>(`/v1/model/providers/${providerCode}/models`)
  },
  listKeys(userId: IdLike) {
    return request.get<AnyRecord[]>(`/v1/model/keys?userId=${userId}`)
  },
  createKey(payload: AnyRecord) {
    return request.post<AnyRecord>('/v1/model/keys', payload)
  },
  updateKey(keyId: IdLike, userId: IdLike, operatorId: IdLike, payload: AnyRecord) {
    return request.patch<string>(`/v1/model/keys/${keyId}?userId=${userId}&operatorId=${operatorId}`, payload)
  },
  deleteKey(keyId: IdLike, userId: IdLike, operatorId: IdLike) {
    return request.delete<string>(`/v1/model/keys/${keyId}?userId=${userId}&operatorId=${operatorId}`)
  },
  listPolicies(projectId: IdLike) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/model-policies`)
  }
}

