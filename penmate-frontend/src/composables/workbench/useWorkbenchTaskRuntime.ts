import type {
  WorkbenchRuntimeApproval,
  WorkbenchRuntimeEventSource,
  WorkbenchRuntimeToolCall,
} from '@/api/types'
import type { GenerationPhase } from '@/components/workbench/workbenchTypes'
import type { ChatRecord } from './useWorkbenchChatTimeline'

export type GenerationTaskStatus = 'pending' | 'running' | 'waiting_approval' | 'done' | 'applied' | 'failed' | 'cancelled'

type StreamListener = (event: MessageEvent<string>) => void

export const normalizeGenerationStatus = (raw: unknown): GenerationTaskStatus | '' => {
  const status = String(raw || '').trim().toLowerCase()
  return (['pending', 'running', 'waiting_approval', 'done', 'applied', 'failed', 'cancelled'] as const).includes(status as GenerationTaskStatus)
    ? (status as GenerationTaskStatus)
    : ''
}

export const parseSseData = (event: MessageEvent<string>) => {
  try {
    return JSON.parse(event.data || '{}') as ChatRecord
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
  if (!value || typeof value !== 'object') {
    return null
  }
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
  if (!value || typeof value !== 'object') {
    return null
  }
  const record = value as Record<string, unknown>
  return {
    ...record,
    approvalId: record.approvalId == null ? null : String(record.approvalId),
    approvalType: record.approvalType == null ? null : String(record.approvalType),
    toolCallId: record.toolCallId == null ? null : String(record.toolCallId),
    nextAction: record.nextAction == null ? null : String(record.nextAction),
  }
}

const normalizeStructuredRuntimeRecord = (value: unknown) => {
  if (!value || typeof value !== 'object') {
    return null
  }
  return value as Record<string, unknown>
}

const toRuntimeEventSource = (eventName: string, payload: Record<string, unknown>): WorkbenchRuntimeEventSource => ({
  eventName,
  sessionId: payload.sessionId == null ? null : String(payload.sessionId),
  turnId: payload.turnId == null ? null : String(payload.turnId),
  taskId: payload.taskId == null ? null : String(payload.taskId),
  phase: payload.phase == null ? null : String(payload.phase),
  message: payload.message == null ? null : String(payload.message),
  errorMsg: payload.errorMsg == null ? null : String(payload.errorMsg),
  recoverable: typeof payload.recoverable === 'boolean' ? payload.recoverable : undefined,
  nextAction: payload.nextAction == null ? null : String(payload.nextAction),
  status: payload.status == null ? null : String(payload.status),
  toolCall: normalizeToolCall(payload.toolCall),
  approval: normalizeApproval(payload.approval),
  todoPlan: normalizeStructuredRuntimeRecord(payload.todoPlan),
  storyBibleApproval: normalizeStructuredRuntimeRecord(payload.storyBibleApproval),
})

const resolveRuntimeFailureMessage = (payload: Record<string, unknown>) => String(
  payload.errorMsg
  || payload.message
  || payload.errorCode
  || '生成失败',
)

export const createTaskRuntime = (deps: {
  getGenerationTaskStatus: () => GenerationTaskStatus | ''
  setGenerationTaskStatus: (value: GenerationTaskStatus | '') => void
  setAgentStatusDetailText: (value: string) => void
  getGenerationPhase: () => GenerationPhase
  setGenerationPhase: (value: GenerationPhase) => void
  getGenerationStream: () => EventSource | null
  setGenerationStream: (stream: EventSource | null) => void
  openGenerationStream: (projectId: string, sessionId: string, turnId: string) => EventSource
  addStreamListener: (stream: EventSource, eventName: string, listener: StreamListener) => void
  closeGenerationStream?: (stream: EventSource | null) => void
  scrollChat: () => void
  setRuntimeEventSource?: (value: WorkbenchRuntimeEventSource | null) => void
  onToken?: (token: string) => void
  onToolCall?: (payload: Record<string, unknown>) => void
  onWaitingApproval?: (payload: Record<string, unknown>) => void
}) => {
  const closeGenerationStream = () => {
    const generationStream = deps.getGenerationStream()
    if (generationStream) {
      deps.closeGenerationStream?.(generationStream)
      generationStream.close()
      deps.setGenerationStream(null)
    }
  }

  const publishRuntimeEventSource = (eventName: string, payload: Record<string, unknown>) => {
    deps.setRuntimeEventSource?.(toRuntimeEventSource(eventName, payload))
  }

  const consumeGenerationStream = (projectId: string, sessionId: string, turnId: string) => new Promise<GenerationTaskStatus | ''>((resolve, reject) => {
    closeGenerationStream()
    const generationStream = deps.openGenerationStream(projectId, sessionId, turnId)
    deps.setGenerationStream(generationStream)
    let settled = false

    const settleResolve = (status: GenerationTaskStatus | '') => {
      if (settled) return
      settled = true
      closeGenerationStream()
      resolve(status)
    }

    const settleReject = (error: Error) => {
      if (settled) return
      settled = true
      closeGenerationStream()
      reject(error)
    }

    deps.addStreamListener(generationStream, 'generation.started', (event) => {
      const payload = parseSseData(event) as Record<string, unknown>
      publishRuntimeEventSource('generation.started', payload)
      deps.setGenerationPhase('streaming')
      deps.setGenerationTaskStatus('running')
      deps.setAgentStatusDetailText(String(payload.message || ''))
    })
    deps.addStreamListener(generationStream, 'generation.status', (event) => {
      const payload = parseSseData(event) as Record<string, unknown>
      publishRuntimeEventSource('generation.status', payload)
      const status = normalizeGenerationStatus(payload.status)
      if (status) {
        deps.setGenerationTaskStatus(status)
      }
      if (String(payload.phase || '').trim().toLowerCase() === 'waiting_approval') {
        deps.setGenerationPhase('waiting_approval')
      } else {
        deps.setGenerationPhase('streaming')
      }
      deps.setAgentStatusDetailText(String(payload.message || ''))
    })
    deps.addStreamListener(generationStream, 'generation.token', (event) => {
      const payload = parseSseData(event)
      const token = String(payload.token || '')
      if (!token) return
      deps.onToken?.(token)
      deps.scrollChat()
    })
    deps.addStreamListener(generationStream, 'generation.tool_call', (event) => {
      const payload = parseSseData(event) as Record<string, unknown>
      publishRuntimeEventSource('generation.tool_call', payload)
      deps.onToolCall?.(payload)
      if (normalizeGenerationStatus(payload.status) === 'waiting_approval') {
        deps.setGenerationPhase('waiting_approval')
        deps.setGenerationTaskStatus('waiting_approval')
      } else {
        deps.setGenerationPhase('streaming')
      }
      deps.setAgentStatusDetailText(String(payload.message || ''))
      deps.scrollChat()
    })
    deps.addStreamListener(generationStream, 'generation.waiting_approval', (event) => {
      const payload = parseSseData(event) as Record<string, unknown>
      publishRuntimeEventSource('generation.waiting_approval', payload)
      deps.onWaitingApproval?.(payload)
      deps.setGenerationPhase('waiting_approval')
      deps.setGenerationTaskStatus('waiting_approval')
      deps.setAgentStatusDetailText(String(payload.message || ''))
      deps.scrollChat()
    })
    deps.addStreamListener(generationStream, 'generation.done', (event) => {
      const payload = parseSseData(event) as Record<string, unknown>
      publishRuntimeEventSource('generation.done', payload)
      const status = normalizeGenerationStatus(payload.status) || 'done'
      deps.setGenerationTaskStatus(status)
      deps.setAgentStatusDetailText(String(payload.message || ''))
      settleResolve(status)
    })
    deps.addStreamListener(generationStream, 'generation.failed', (event) => {
      const payload = parseSseData(event) as Record<string, unknown>
      publishRuntimeEventSource('generation.failed', payload)
      deps.setGenerationPhase('failed')
      deps.setGenerationTaskStatus('failed')
      const failureMessage = resolveRuntimeFailureMessage(payload)
      deps.setAgentStatusDetailText(failureMessage)
      settleReject(new Error(failureMessage))
    })
  })

  return {
    closeGenerationStream,
    consumeGenerationStream,
    getErrorMessage,
  }
}
