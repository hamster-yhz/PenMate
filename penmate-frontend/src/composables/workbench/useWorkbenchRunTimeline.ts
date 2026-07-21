import { computed, ref } from 'vue'
import type { AgentRunAttempt, AgentTimelineEvent, RunConnectionState } from '@/components/workbench/workbenchTypes'
import { pickBusinessArray } from '@/utils/apiPayload'

type AnyRecord = Record<string, unknown>

const sensitiveKey = /(api[-_]?key|authorization|cookie|secret|token|password|system[-_]?prompt|prompt[-_]?snapshot)/i

export const redactEventPayload = (value: unknown, key = ''): unknown => {
  if (sensitiveKey.test(key)) return '[已脱敏]'
  if (Array.isArray(value)) return value.map((item) => redactEventPayload(item))
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value as AnyRecord).map(([childKey, child]) => [childKey, redactEventPayload(child, childKey)]),
    )
  }
  return value
}

const parsePayload = (value: unknown): AnyRecord => {
  if (value && typeof value === 'object' && !Array.isArray(value)) return value as AnyRecord
  if (typeof value !== 'string' || !value.trim()) return {}
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? (parsed as AnyRecord) : {}
  } catch {
    return { raw: value }
  }
}

const numericSequence = (value: unknown) => {
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) ? parsed : -1
}

const normalizeEvent = (record: AnyRecord, fallbackRunId = '', fallbackTurnId = ''): AgentTimelineEvent => ({
  eventId: record.eventId == null ? undefined : String(record.eventId),
  runId: String(record.runId ?? fallbackRunId),
  turnId: String(record.turnId ?? fallbackTurnId),
  sequence: numericSequence(record.sequence),
  type: String(record.type ?? record.eventName ?? 'unknown.event'),
  payload: redactEventPayload(parsePayload(record.payloadJson ?? record.payload ?? record)) as AnyRecord,
  createdAt: record.createdAt == null ? undefined : String(record.createdAt),
})

const normalizeAttempt = (record: AnyRecord): AgentRunAttempt => {
  const runId = String(record.runId ?? '')
  const turnId = String(record.turnId ?? '')
  const events = pickBusinessArray<AnyRecord>(record.events)
    .map((event) => normalizeEvent(event, runId, turnId))
    .sort((left, right) => left.sequence - right.sequence)
  const output = record.output && typeof record.output === 'object' && !Array.isArray(record.output)
    ? record.output as AnyRecord
    : null
  return {
    runId,
    turnId,
    predecessorRunId: record.predecessorRunId == null ? null : String(record.predecessorRunId),
    runStatus: String(record.runStatus ?? ''),
    runPhase: String(record.runPhase ?? ''),
    attemptCount: Number(record.attemptCount ?? 0),
    lastErrorCode: record.lastErrorCode == null ? null : String(record.lastErrorCode),
    lastErrorMessage: record.lastErrorMessage == null ? null : String(record.lastErrorMessage),
    latestSequence: numericSequence(record.latestSequence),
    startedAt: record.startedAt == null ? null : String(record.startedAt),
    finishedAt: record.finishedAt == null ? null : String(record.finishedAt),
    connectionState: 'idle',
    output: output
      ? {
          text: String(output.text ?? ''),
          offset: Math.max(0, Number(output.offset ?? 0)),
          sequence: output.sequence == null ? null : numericSequence(output.sequence),
          state: String(output.state ?? ''),
          updatedAt: output.updatedAt == null ? null : String(output.updatedAt),
        }
      : null,
    events,
  }
}

export const useWorkbenchRunTimeline = () => {
  const attempts = ref<AgentRunAttempt[]>([])

  const replaceHistory = (payload: unknown) => {
    attempts.value = pickBusinessArray<AnyRecord>(payload)
      .map(normalizeAttempt)
      .filter((attempt) => attempt.runId && attempt.turnId)
  }

  const mergeHistory = (payload: unknown) => {
    for (const incoming of pickBusinessArray<AnyRecord>(payload).map(normalizeAttempt)) {
      if (!incoming.runId || !incoming.turnId) continue
      const current = attempts.value.find((attempt) => attempt.runId === incoming.runId)
      if (!current) {
        attempts.value.push(incoming)
        continue
      }
      const events = new Map<string, AgentTimelineEvent>()
      for (const event of [...current.events, ...incoming.events]) {
        const key = event.eventId || (event.sequence >= 0 ? `sequence:${event.sequence}` : `${event.type}:${event.createdAt ?? ''}`)
        events.set(key, event)
      }
      current.turnId = incoming.turnId || current.turnId
      current.predecessorRunId = incoming.predecessorRunId ?? current.predecessorRunId
      current.runStatus = incoming.runStatus || current.runStatus
      current.runPhase = incoming.runPhase || current.runPhase
      current.attemptCount = incoming.attemptCount || current.attemptCount
      current.lastErrorCode = incoming.lastErrorCode ?? current.lastErrorCode
      current.lastErrorMessage = incoming.lastErrorMessage ?? current.lastErrorMessage
      current.latestSequence = Math.max(current.latestSequence, incoming.latestSequence)
      current.startedAt = incoming.startedAt ?? current.startedAt
      current.finishedAt = incoming.finishedAt ?? current.finishedAt
      current.output = incoming.output ?? current.output
      current.events = [...events.values()].sort((left, right) => left.sequence - right.sequence)
    }
  }

  const ensureAttempt = (runId: string, turnId = '') => {
    let attempt = attempts.value.find((item) => item.runId === runId)
    if (!attempt) {
      attempt = normalizeAttempt({ runId, turnId, runStatus: 'PENDING', runPhase: 'created', events: [] })
      attempts.value.push(attempt)
    } else if (!attempt.turnId && turnId) {
      attempt.turnId = turnId
    }
    return attempt
  }

  const appendEvent = (eventName: string, payload: AnyRecord, fallbackRunId = '', fallbackTurnId = '') => {
    const source = { ...payload, type: payload.type ?? eventName }
    const event = normalizeEvent(source, fallbackRunId, fallbackTurnId)
    if (!event.runId) return
    const attempt = ensureAttempt(event.runId, event.turnId)
    const duplicate = attempt.events.some(
      (item) => (event.eventId && item.eventId === event.eventId) || (event.sequence >= 0 && item.sequence === event.sequence),
    )
    if (!duplicate && event.type !== 'message.delta') {
      attempt.events.push(event)
      attempt.events.sort((left, right) => left.sequence - right.sequence)
    }
    if (event.sequence >= 0) attempt.latestSequence = Math.max(attempt.latestSequence, event.sequence)
    if (event.type === 'run.started' && !attempt.startedAt) {
      attempt.startedAt = event.createdAt ?? new Date().toISOString()
    }
    const normalizedStatus = String(event.payload.status ?? '').toUpperCase()
    if (normalizedStatus) attempt.runStatus = normalizedStatus
    const phase = String(event.payload.phase ?? '')
    if (phase) attempt.runPhase = phase
    if (event.type.startsWith('run.')) {
      const suffix = event.type.slice(4).toUpperCase()
      if (['COMPLETED', 'FAILED', 'CANCELLED', 'SUPERSEDED', 'SUSPENDED', 'WAITING_APPROVAL'].includes(suffix)) {
        attempt.runStatus = suffix === 'COMPLETED' ? 'DONE' : suffix
      }
      if (['COMPLETED', 'FAILED', 'CANCELLED', 'SUPERSEDED'].includes(suffix) && !attempt.finishedAt) {
        attempt.finishedAt = event.createdAt ?? new Date().toISOString()
      }
      if (suffix === 'FAILED') {
        attempt.lastErrorCode = String(event.payload.errorCode ?? '').trim() || attempt.lastErrorCode
        attempt.lastErrorMessage = String(
          event.payload.errorMessage ?? event.payload.errorMsg ?? event.payload.message ?? '',
        ).trim() || attempt.lastErrorMessage
      }
    }
  }

  const setConnectionState = (runId: string, state: RunConnectionState) => {
    if (!runId) return
    ensureAttempt(runId).connectionState = state
  }

  const attemptsByTurn = computed(() => {
    const grouped = new Map<string, AgentRunAttempt[]>()
    for (const attempt of attempts.value) {
      const group = grouped.get(attempt.turnId) ?? []
      group.push(attempt)
      grouped.set(attempt.turnId, group)
    }
    for (const group of grouped.values()) {
      group.sort((left, right) => left.attemptCount - right.attemptCount || left.runId.localeCompare(right.runId))
    }
    return grouped
  })

  return { attempts, attemptsByTurn, replaceHistory, mergeHistory, ensureAttempt, appendEvent, setConnectionState }
}
