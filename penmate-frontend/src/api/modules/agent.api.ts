import request from '@/utils/request'
import type { IdLike } from '@/api/types'

type AnyRecord = Record<string, unknown>

export const agentApi = {
  listConversations(projectId: IdLike) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/agent/conversations`)
  },
  createConversation(projectId: IdLike, operatorId: IdLike, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/agent/conversations?operatorId=${operatorId}`, payload)
  },
  listMessages(projectId: IdLike, conversationId: IdLike) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/agent/conversations/${conversationId}/messages`)
  },
  createMessage(projectId: IdLike, conversationId: IdLike, operatorId: IdLike, payload: AnyRecord) {
    return request.post<AnyRecord>(
      `/v1/novels/${projectId}/agent/conversations/${conversationId}/messages?operatorId=${operatorId}`,
      payload
    )
  },
  createGeneration(projectId: IdLike, operatorId: IdLike, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/agent/generations?operatorId=${operatorId}`, payload)
  },
  getGeneration(projectId: IdLike, taskId: IdLike) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}/agent/generations/${taskId}`)
  },
  applyGeneration(projectId: IdLike, taskId: IdLike, operatorId: IdLike, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/agent/generations/${taskId}/apply?operatorId=${operatorId}`, payload)
  },
  getGenerationStreamUrl(projectId: IdLike, taskId: IdLike) {
    return `/api/v1/novels/${projectId}/agent/generations/${taskId}/stream`
  }
}

