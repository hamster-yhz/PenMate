import request from '@/utils/request'

type AnyRecord = Record<string, unknown>
type StreamListener = (event: MessageEvent<string>) => void

type AgentBusinessId = string

type AgentSessionRecord = AnyRecord & {
  sessionId?: AgentBusinessId
}

type AgentTaskRecord = AnyRecord & {
  turnId?: AgentBusinessId
  taskId?: AgentBusinessId
  taskStatus?: string
}

type AgentSessionSnapshot = AnyRecord & {
  session?: AgentSessionRecord
  activeTask?: AgentTaskRecord
}

const trimTrailingSlash = (value: string) => value.replace(/\/+$/, '')

const resolveApiBaseUrl = () => {
  const configuredBase = String(import.meta.env.VITE_APP_API_BASE_URL || '/api').trim()
  if (!configuredBase) return '/api'
  if (/^https?:\/\//i.test(configuredBase)) {
    return trimTrailingSlash(configuredBase)
  }
  return trimTrailingSlash(configuredBase.startsWith('/') ? configuredBase : `/${configuredBase}`)
}

const buildTurnStreamUrl = (projectId: string, sessionId: string, turnId: string) => {
  const apiBase = resolveApiBaseUrl()
  return `${apiBase}/v1/novels/${projectId}/agent/sessions/${sessionId}/turns/${turnId}/stream`
}

export const agentApi = {
  listSessions(projectId: string) {
    return request.get<AgentSessionRecord[]>(`/v1/novels/${projectId}/agent/sessions`)
  },
  createSession(projectId: string, payload: AnyRecord) {
    return request.post<AgentSessionRecord>(`/v1/novels/${projectId}/agent/sessions`, payload)
  },
  getSessionRecovery(projectId: string, sessionId: string) {
    return request.get<AgentSessionSnapshot>(`/v1/novels/${projectId}/agent/sessions/${sessionId}/recovery`)
  },
  resumeSession(projectId: string, sessionId: string, payload: AnyRecord) {
    return request.post<AgentSessionSnapshot>(`/v1/novels/${projectId}/agent/sessions/${sessionId}/resume`, payload)
  },
  createTurn(projectId: string, sessionId: string, payload: AnyRecord) {
    return request.post<AgentSessionSnapshot>(`/v1/novels/${projectId}/agent/sessions/${sessionId}/turns`, payload)
  },
  getTask(projectId: string, taskId: string) {
    return request.get<AgentTaskRecord>(`/v1/novels/${projectId}/agent/tasks/${taskId}`)
  },
  getTurnStreamUrl(projectId: string, sessionId: string, turnId: string) {
    return buildTurnStreamUrl(projectId, sessionId, turnId)
  },
  openTurnStream(projectId: string, sessionId: string, turnId: string) {
    const url = buildTurnStreamUrl(projectId, sessionId, turnId)
    return new EventSource(url)
  },
  addStreamListener(stream: EventSource, eventName: string, listener: StreamListener) {
    stream.addEventListener(eventName, listener as EventListener)
  }
}
