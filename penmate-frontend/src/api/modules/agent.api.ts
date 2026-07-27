import request from '@/utils/request'
import {
  createAgentRunEventStream,
  type AgentRunEventStream,
  type AgentRunStreamListener,
} from '@/api/agentRunStream'
import type { AgentSkillCatalogItem } from '@/api/types'
import type { AgentQueuedRequest, AgentSafetyMode, AgentSessionContextUsage } from '@/entities/agent/model'

type AnyRecord = Record<string, unknown>

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

const withoutActorFields = (payload: AnyRecord) => {
  const sanitized = { ...payload }
  delete sanitized.operatorId
  delete sanitized.userId
  delete sanitized.ownerUserId
  return sanitized
}

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
  getSafetyMode() {
    return request.get<{ mode: AgentSafetyMode; maximumAutomaticRisk: number }>(
      '/agent/preferences/safety',
    )
  },
  saveSafetyMode(mode: AgentSafetyMode) {
    return request.put<{ mode: AgentSafetyMode; maximumAutomaticRisk: number }>(
      '/agent/preferences/safety', { mode },
    )
  },
  listSkills(projectId: string) {
    return request.get<AgentSkillCatalogItem[]>(`/v1/novels/${projectId}/agent/skills`)
  },
  listSessions(projectId: string, deleted = false) {
    return request.get<AgentSessionRecord[]>(`/v1/novels/${projectId}/agent/sessions`, { params: { deleted } })
  },
  createSession(projectId: string, payload: AnyRecord) {
    return request.post<AgentSessionRecord>(`/v1/novels/${projectId}/agent/sessions`, withoutActorFields(payload))
  },
  getSessionRecovery(projectId: string, sessionId: string) {
    return request.get<AgentSessionSnapshot>(`/v1/novels/${projectId}/agent/sessions/${sessionId}/recovery`)
  },
  listSessionRuns(projectId: string, sessionId: string) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/agent/sessions/${sessionId}/runs`)
  },
  renameSession(projectId: string, sessionId: string, title: string) {
    return request.patch<AgentSessionRecord>(`/v1/novels/${projectId}/agent/sessions/${sessionId}`, { title })
  },
  deleteSession(projectId: string, sessionId: string) {
    return request.delete<string>(`/v1/novels/${projectId}/agent/sessions/${sessionId}`)
  },
  restoreDeletedSession(projectId: string, sessionId: string) {
    return request.post<AgentSessionRecord>(`/v1/novels/${projectId}/agent/sessions/${sessionId}/restore`)
  },
  resumeSession(projectId: string, sessionId: string, payload: AnyRecord) {
    return request.post<AgentSessionSnapshot>(
      `/v1/novels/${projectId}/agent/sessions/${sessionId}/resume`,
      withoutActorFields(payload),
    )
  },
  createTurn(projectId: string, sessionId: string, payload: AnyRecord) {
    return request.post<AgentSessionSnapshot>(
      `/v1/novels/${projectId}/agent/sessions/${sessionId}/turns`,
      withoutActorFields(payload),
    )
  },
  getQueuedRequest(projectId: string, sessionId: string) {
    return request.get<AgentQueuedRequest | null>(`/v1/novels/${projectId}/agent/sessions/${sessionId}/queued-request`)
  },
  registerQueuedRequest(projectId: string, sessionId: string, payload: AnyRecord) {
    return request.post<AgentQueuedRequest>(
      `/v1/novels/${projectId}/agent/sessions/${sessionId}/queued-request`, payload,
    )
  },
  withdrawQueuedRequest(projectId: string, sessionId: string, requestId: string) {
    return request.delete<string>(
      `/v1/novels/${projectId}/agent/sessions/${sessionId}/queued-request/${requestId}`,
    )
  },
  getSessionTokenUsage(projectId: string, sessionId: string) {
    return request.get<AgentSessionContextUsage>(`/v1/novels/${projectId}/agent/sessions/${sessionId}/token-usage`)
  },
  cancelRun(projectId: string, runId: string, payload: AnyRecord) {
    return request.post<AgentRunRecord>(`/v1/novels/${projectId}/agent/runs/${runId}/cancel`, withoutActorFields(payload))
  },
  retryRun(projectId: string, runId: string, payload: AnyRecord) {
    return request.post<AgentRunRecord>(
      `/v1/novels/${projectId}/agent/runs/${runId}/retry`,
      withoutActorFields(payload),
    )
  },
  getRunStreamUrl(projectId: string, runId: string, after = '0') {
    return buildRunStreamUrl(projectId, runId, after)
  },
  openRunStream(projectId: string, runId: string, after = '0') {
    const url = buildRunStreamUrl(projectId, runId, after)
    return createAgentRunEventStream(url)
  },
  addStreamListener(stream: AgentRunEventStream, eventName: string, listener: AgentRunStreamListener) {
    stream.addEventListener(eventName, listener)
  },
}
