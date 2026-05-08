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

const buildTaskStreamUrl = (projectId: IdLike, taskId: IdLike) => {
  const apiBase = resolveApiBaseUrl()
  return `${apiBase}/v1/novels/${projectId}/agent/tasks/${taskId}/stream`
}

export const agentApi = {
  listSessions(projectId: IdLike) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/agent/sessions`)
  },
  createSession(projectId: IdLike, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/agent/sessions`, payload)
  },
  getSessionRecovery(projectId: IdLike, sessionId: IdLike) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}/agent/sessions/${sessionId}/recovery`)
  },
  resumeSession(projectId: IdLike, sessionId: IdLike, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/agent/sessions/${sessionId}/resume`, payload)
  },
  createTurn(projectId: IdLike, sessionId: IdLike, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/agent/sessions/${sessionId}/turns`, payload)
  },
  getTask(projectId: IdLike, taskId: IdLike) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}/agent/tasks/${taskId}`)
  },
  getTaskStreamUrl(projectId: IdLike, taskId: IdLike) {
    return buildTaskStreamUrl(projectId, taskId)
  },
  openTaskStream(projectId: IdLike, taskId: IdLike) {
    const url = buildTaskStreamUrl(projectId, taskId)
    return new EventSource(url)
  },
  addStreamListener(stream: EventSource, eventName: string, listener: StreamListener) {
    stream.addEventListener(eventName, listener as EventListener)
  }
}
