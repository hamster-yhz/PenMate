import request from '@/utils/request'
import type { IdLike } from '@/api/types'

type AnyRecord = Record<string, unknown>
type StreamListener = (event: MessageEvent<string>) => void

const trimTrailingSlash = (value: string) => value.replace(/\/+$/, '')

const resolveApiBaseUrl = () => {
  const configuredBase = String(import.meta.env.VITE_APP_API_BASE_URL || '/api').trim()
  if (!configuredBase) return '/api'
  if (/^https?:\/\//i.test(configuredBase)) {
    return trimTrailingSlash(configuredBase)
  }
  return trimTrailingSlash(configuredBase.startsWith('/') ? configuredBase : `/${configuredBase}`)
}

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
    const apiBase = resolveApiBaseUrl()
    return `${apiBase}/v1/novels/${projectId}/agent/generations/${taskId}/stream`
  },
  openGenerationStream(projectId: IdLike, taskId: IdLike) {
    const url = this.getGenerationStreamUrl(projectId, taskId)
    return new EventSource(url)
  },
  addStreamListener(stream: EventSource, eventName: string, listener: StreamListener) {
    stream.addEventListener(eventName, listener as EventListener)
  }
}

