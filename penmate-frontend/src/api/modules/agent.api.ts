import request from '@/utils/request'

type AnyRecord = Record<string, unknown>
type StreamListener = (event: MessageEvent<string>) => void

type AgentBusinessId = string

type AgentSessionRecord = AnyRecord & {
  sessionId?: AgentBusinessId
}

type AgentRunRecord = AnyRecord & {
  turnId?: AgentBusinessId
  runId?: AgentBusinessId
  runStatus?: string
  runPhase?: string
  latestSequence?: string
}

type AgentSessionSnapshot = AnyRecord & {
  session?: AgentSessionRecord
  activeRun?: AgentRunRecord
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

const buildRunStreamUrl = (projectId: string, runId: string, after = '0') => {
  const apiBase = resolveApiBaseUrl()
  return `${apiBase}/v1/novels/${projectId}/agent/runs/${runId}/stream?after=${encodeURIComponent(after)}`
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
  cancelRun(projectId: string, runId: string, payload: AnyRecord) {
    return request.post<AgentRunRecord>(`/v1/novels/${projectId}/agent/runs/${runId}/cancel`, payload)
  },
  retryRun(projectId: string, runId: string, payload: AnyRecord) {
    return request.post<AgentRunRecord>(`/v1/novels/${projectId}/agent/runs/${runId}/retry`, payload)
  },
  getRunStreamUrl(projectId: string, runId: string, after = '0') {
    return buildRunStreamUrl(projectId, runId, after)
  },
  openRunStream(projectId: string, runId: string, after = '0') {
    const url = buildRunStreamUrl(projectId, runId, after)
    return new EventSource(url)
  },
  addStreamListener(stream: EventSource, eventName: string, listener: StreamListener) {
    stream.addEventListener(eventName, listener as EventListener)
  },
}
