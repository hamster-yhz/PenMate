import request from '@/utils/request'
import type { IdLike } from '@/api/types'

type AnyRecord = Record<string, unknown>

export const agentApi = {
  listConversations(projectId: IdLike) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/agent/conversations`)
  },
  listMessages(projectId: IdLike, conversationId: IdLike) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/agent/conversations/${conversationId}/messages`)
  },
  createGeneration(projectId: IdLike, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/agent/generations`, payload)
  },
  getGeneration(projectId: IdLike, taskId: IdLike) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}/agent/generations/${taskId}`)
  },
  applyGeneration(projectId: IdLike, taskId: IdLike, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/agent/generations/${taskId}/apply`, payload)
  }
}

