import type {
  WorkbenchRuntimeApproval,
  WorkbenchRuntimeEventSource,
  WorkbenchRuntimeToolCall,
} from '@/api/types'
import type { GenerationPhase } from '@/components/workbench/workbenchTypes'
import type { ChatRecord } from './useWorkbenchChatTimeline'

export type AgentRunStatus = 'pending' | 'running' | 'waiting_approval' | 'suspended' | 'completed' | 'failed' | 'cancelled' | 'superseded'

type StreamListener = (event: MessageEvent<string>) => void

export const normalizeRunStatus = (raw: unknown): AgentRunStatus | '' => {
  const status = String(raw || '').trim().toLowerCase()
  if (status === 'done') return 'completed'
  return (['pending', 'running', 'waiting_approval', 'suspended', 'completed', 'failed', 'cancelled', 'superseded'] as const).includes(status as AgentRunStatus)
    ? (status as AgentRunStatus)
    : ''
}

export const parseSseData = (event: MessageEvent<string>) => {
  try {
    const record = JSON.parse(event.data || '{}') as ChatRecord
    const payloadJson = typeof record.payloadJson === 'string' ? record.payloadJson : ''
    if (!payloadJson.trim()) {
      return record
    }
    const payload = JSON.parse(payloadJson) as ChatRecord
    return {
      ...payload,
      ...record,
      type: record.type ?? payload.type,
      payloadJson,
    } as ChatRecord
  } catch {
    return {} as ChatRecord
  }
}

const getErrorMessage = (error: unknown, fallback = '未知错误') => {
  if (typeof error === 'string') return error
  if (error && typeof error === 'object' && 'message' in error) {
    const message = String((error as { message?: unknown }).message || '').trim()
    if (message) return message
  }
  return fallback
}

const normalizeToolCall = (value: unknown): WorkbenchRuntimeToolCall | null => {
  if (!value || typeof value !== 'object') return null
  const record = value as Record<string, unknown>
  return {
    toolCallId: record.toolCallId == null ? null : String(record.toolCallId),
    toolCode: record.toolCode == null ? null : String(record.toolCode),
    toolName: record.toolName == null ? null : String(record.toolName),
    status: record.status == null ? null : String(record.status),
    iteration: record.iteration == null ? null : Number(record.iteration),
    argumentsPreview: record.argumentsPreview ?? null,
    output: record.output ?? null,
    errorMessage: record.errorMessage == null ? null : String(record.errorMessage),
  }
}

const normalizeApproval = (value: unknown): WorkbenchRuntimeApproval | null => {
  if (!value || typeof value !== 'object') return null
  const record = value as Record<string, unknown>
  return {
    ...record,
    approvalId: record.approvalId == null ? null : String(record.approvalId),
    approvalType: record.approvalType == null ? null : String(record.approvalType),
    toolCallId: record.toolCallId == null ? null : String(record.toolCallId),
    nextAction: record.nextAction == null ? null : String(record.nextAction),
  }
}

const structuredRecord = (value: unknown) => (!value || typeof value !== 'object' ? null : value as Record<string, unknown>)

const toRuntimeEventSource = (eventName: string, payload: Record<string, unknown>): WorkbenchRuntimeEventSource => ({
  eventName,
  sessionId: payload.sessionId == null ? null : String(payload.sessionId),
  turnId: payload.turnId == null ? null : String(payload.turnId),
  runId: payload.runId == null ? null : String(payload.runId),
  sequence: payload.sequence == null ? null : String(payload.sequence),
  phase: payload.phase == null ? null : String(payload.phase),
  message: payload.message == null ? null : String(payload.message),
  errorMsg: payload.errorMsg == null ? null : String(payload.errorMsg),
  recoverable: typeof payload.recoverable === 'boolean' ? payload.recoverable : undefined,
  nextAction: payload.nextAction == null ? null : String(payload.nextAction),
  status: payload.status == null ? null : String(payload.status),
  toolCall: normalizeToolCall(payload.toolCall ?? payload),
  approval: normalizeApproval(payload.approval ?? payload),
  todoPlan: structuredRecord(payload.todoPlan),
  storyBibleApproval: structuredRecord(payload.storyBibleApproval),
})

const failureMessage = (payload: Record<string, unknown>) => String(
  payload.errorMsg || payload.message || payload.errorCode || '运行失败',
)

export const createAgentRunRuntime = (deps: {
  getRunStatus: () => AgentRunStatus | ''
  setRunStatus: (value: AgentRunStatus | '') => void
  setAgentStatusDetailText: (value: string) => void
  getRunPhase: () => GenerationPhase
  setRunPhase: (value: GenerationPhase) => void
  getRunStream: () => EventSource | null
  setRunStream: (stream: EventSource | null) => void
  openRunStream: (projectId: string, runId: string, after?: string) => EventSource
  addStreamListener: (stream: EventSource, eventName: string, listener: StreamListener) => void
  closeRunStream?: (stream: EventSource | null) => void
  scrollChat: () => void
  setRuntimeEventSource?: (value: WorkbenchRuntimeEventSource | null) => void
  onToken?: (token: string) => void
  onMessageCompleted?: (text: string) => void
  onToolCall?: (payload: Record<string, unknown>) => void
  onWaitingApproval?: (payload: Record<string, unknown>) => void
  onStreamReset?: (payload: Record<string, unknown>) => AgentRunStatus | '' | Promise<AgentRunStatus | ''>
  setLatestSequence?: (value: string) => void
}) => {
  const closeRunStream = () => {
    const stream = deps.getRunStream()
    if (stream) {
      deps.closeRunStream?.(stream)
      stream.close()
      deps.setRunStream(null)
    }
  }

  const publish = (eventName: string, payload: Record<string, unknown>) => {
    const sequence = payload.sequence == null ? null : String(payload.sequence)
    if (sequence && /^\d+$/.test(sequence)) deps.setLatestSequence?.(sequence)
    deps.setRuntimeEventSource?.(toRuntimeEventSource(eventName, payload))
  }

  const consumeRunStream = (projectId: string, runId: string, after = '0') => new Promise<AgentRunStatus | ''>((resolve, reject) => {
    closeRunStream()
    const stream = deps.openRunStream(projectId, runId, after)
    deps.setRunStream(stream)
    let settled = false

    const settleResolve = (status: AgentRunStatus | '') => {
      if (settled) return
      settled = true
      closeRunStream()
      resolve(status)
    }

    const settleReject = (error: Error) => {
      if (settled) return
      settled = true
      closeRunStream()
      reject(error)
    }

    deps.addStreamListener(stream, 'stream.reset', async (event) => {
      const payload = parseSseData(event) as Record<string, unknown>
      const latestSequence = String(payload.latestSequence ?? after)
      publish('stream.reset', { ...payload, sequence: latestSequence })
      try {
        const status = await deps.onStreamReset?.(payload) || ''
        if (status === 'completed' || status === 'cancelled' || status === 'superseded') {
          settleResolve(status)
        } else if (status === 'failed') {
          settleReject(new Error(failureMessage(payload)))
        }
      } catch (error) {
        settleReject(error instanceof Error ? error : new Error(String(error)))
      }
    })

    deps.addStreamListener(stream, 'run.started', (event) => {
      const payload = parseSseData(event) as Record<string, unknown>
      publish('run.started', payload)
      deps.setRunPhase('streaming')
      deps.setRunStatus('running')
      deps.setAgentStatusDetailText(String(payload.message || ''))
    })
    deps.addStreamListener(stream, 'run.phase.changed', (event) => {
      const payload = parseSseData(event) as Record<string, unknown>
      publish('run.phase.changed', payload)
      const status = normalizeRunStatus(payload.status)
      if (status) deps.setRunStatus(status)
      deps.setRunPhase(String(payload.phase || '').toLowerCase() === 'waiting_approval' ? 'waiting_approval' : 'streaming')
      deps.setAgentStatusDetailText(String(payload.message || ''))
    })
    deps.addStreamListener(stream, 'message.delta', (event) => {
      const payload = parseSseData(event)
      publish('message.delta', payload as Record<string, unknown>)
      const token = String(payload.text ?? payload.token ?? payload.content ?? '')
      if (!token) return
      deps.onToken?.(token)
      deps.scrollChat()
    })
    deps.addStreamListener(stream, 'tool.call.started', (event) => {
      const payload = parseSseData(event) as Record<string, unknown>
      publish('tool.call.started', payload)
      deps.onToolCall?.(payload)
      deps.setRunPhase('streaming')
      deps.scrollChat()
    })
    deps.addStreamListener(stream, 'tool.call.completed', (event) => {
      const payload = parseSseData(event) as Record<string, unknown>
      publish('tool.call.completed', payload)
      deps.onToolCall?.(payload)
      deps.scrollChat()
    })
    deps.addStreamListener(stream, 'tool.call.waiting_approval', (event) => {
      const payload = parseSseData(event) as Record<string, unknown>
      publish('tool.call.waiting_approval', payload)
      deps.onWaitingApproval?.(payload)
      deps.setRunPhase('waiting_approval')
      deps.setRunStatus('waiting_approval')
      deps.scrollChat()
    })
    deps.addStreamListener(stream, 'approval.requested', (event) => {
      const payload = parseSseData(event) as Record<string, unknown>
      publish('approval.requested', payload)
      deps.onWaitingApproval?.(payload)
      deps.setRunPhase('waiting_approval')
      deps.setRunStatus('waiting_approval')
      deps.scrollChat()
    })
    deps.addStreamListener(stream, 'message.completed', (event) => {
      const payload = parseSseData(event) as Record<string, unknown>
      publish('message.completed', payload)
      const text = String(payload.text ?? payload.content ?? payload.message ?? '')
      if (text) {
        deps.onMessageCompleted?.(text)
        deps.scrollChat()
      }
    })
    deps.addStreamListener(stream, 'run.completed', (event) => {
      const payload = parseSseData(event) as Record<string, unknown>
      publish('run.completed', payload)
      deps.setRunStatus('completed')
      deps.setAgentStatusDetailText(String(payload.message || ''))
      settleResolve('completed')
    })
    deps.addStreamListener(stream, 'run.failed', (event) => {
      const payload = parseSseData(event) as Record<string, unknown>
      publish('run.failed', payload)
      deps.setRunPhase('failed')
      deps.setRunStatus('failed')
      const message = failureMessage(payload)
      deps.setAgentStatusDetailText(message)
      settleReject(new Error(message))
    })
    deps.addStreamListener(stream, 'run.cancelled', (event) => {
      const payload = parseSseData(event) as Record<string, unknown>
      publish('run.cancelled', payload)
      deps.setRunStatus('cancelled')
      settleResolve('cancelled')
    })
  })

  return {
    closeRunStream,
    consumeRunStream,
    getErrorMessage,
  }
}
