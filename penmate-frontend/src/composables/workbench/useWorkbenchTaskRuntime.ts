import type { ChatMessage, GenerationPhase } from '@/components/workbench/workbenchTypes'
import type { ChatRecord } from './useWorkbenchChatTimeline'
import { applyAssistantEventMetadata, escapeHtml } from './useWorkbenchChatTimeline'

export type GenerationTaskStatus = 'pending' | 'running' | 'waiting_approval' | 'done' | 'applied' | 'failed' | 'cancelled'

type StreamListener = (event: MessageEvent<string>) => void

const TERMINAL_GENERATION_STATUSES: GenerationTaskStatus[] = ['done', 'applied', 'failed', 'cancelled']
const DEFAULT_POLLING_INTERVAL_MS = 200

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
  getGeneration: (projectId: string, taskId: string) => Promise<unknown>
  waitForPolling?: () => Promise<void>
  scrollChat: () => void
}) => {
  const closeGenerationStream = () => {
    const generationStream = deps.getGenerationStream()
    if (generationStream) {
      deps.closeGenerationStream?.(generationStream)
      generationStream.close()
      deps.setGenerationStream(null)
    }
  }

  const waitForPolling = () => deps.waitForPolling?.() ?? new Promise<void>((resolve) => {
    setTimeout(resolve, DEFAULT_POLLING_INTERVAL_MS)
  })

  const pollGenerationAsFallback = async (projectId: string, taskId: string, assistantMsg?: ChatMessage) => {
    let status: GenerationTaskStatus | '' = ''
    for (let i = 0; i < 12; i += 1) {
      const latest = (await deps.getGeneration(projectId, taskId)) as ChatRecord
      status = normalizeGenerationStatus(latest?.status)
      if (status) deps.setGenerationTaskStatus(status)
      if (status === 'waiting_approval') {
        if (assistantMsg) applyAssistantEventMetadata(assistantMsg, latest)
        deps.setGenerationPhase('waiting_approval')
        return status
      }
      if (status && TERMINAL_GENERATION_STATUSES.includes(status)) {
        return status
      }
      if (i < 11) {
        await waitForPolling()
      }
    }
    throw new Error(`生成任务轮询超时，状态：${status || 'unknown'}`)
  }

  const consumeGenerationStream = (projectId: string, sessionId: string, turnId: string, assistantMsg: ChatMessage) => new Promise<GenerationTaskStatus | ''>((resolve, reject) => {
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

    deps.addStreamListener(generationStream, 'generation.started', () => {
      deps.setGenerationPhase('streaming')
      deps.setGenerationTaskStatus('running')
      deps.setAgentStatusDetailText('')
    })
    deps.addStreamListener(generationStream, 'generation.status', (event) => {
      const payload = parseSseData(event)
      const status = normalizeGenerationStatus(payload.status)
      if (status) {
        deps.setGenerationTaskStatus(status)
      }
      deps.setAgentStatusDetailText(String(payload.message || ''))
    })
    deps.addStreamListener(generationStream, 'generation.token', (event) => {
      const payload = parseSseData(event)
      const token = String(payload.token || '')
      if (!token) return
      assistantMsg.text += escapeHtml(token)
      deps.scrollChat()
    })
    deps.addStreamListener(generationStream, 'generation.tool_call', (event) => {
      const payload = parseSseData(event)
      applyAssistantEventMetadata(assistantMsg, payload)
      if (normalizeGenerationStatus(payload.status) === 'waiting_approval') {
        deps.setGenerationPhase('waiting_approval')
        deps.setGenerationTaskStatus('waiting_approval')
      }
      deps.scrollChat()
    })
    deps.addStreamListener(generationStream, 'generation.waiting_approval', (event) => {
      const payload = parseSseData(event)
      applyAssistantEventMetadata(assistantMsg, payload)
      deps.setGenerationPhase('waiting_approval')
      deps.setGenerationTaskStatus('waiting_approval')
      deps.setAgentStatusDetailText(String(payload.message || ''))
      deps.scrollChat()
    })
    deps.addStreamListener(generationStream, 'generation.done', (event) => {
      const payload = parseSseData(event)
      const status = normalizeGenerationStatus(payload.status) || 'done'
      deps.setGenerationTaskStatus(status)
      settleResolve(status)
    })
    deps.addStreamListener(generationStream, 'generation.failed', (event) => {
      const payload = parseSseData(event)
      deps.setGenerationTaskStatus('failed')
      deps.setAgentStatusDetailText(String(payload.errorMsg || payload.errorCode || ''))
      settleReject(new Error(String(payload.errorMsg || payload.errorCode || '生成失败')))
    })
  })

  return {
    closeGenerationStream,
    pollGenerationAsFallback,
    consumeGenerationStream,
    getErrorMessage,
  }
}
