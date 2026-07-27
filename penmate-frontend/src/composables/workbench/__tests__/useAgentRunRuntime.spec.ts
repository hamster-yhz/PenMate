import { afterEach, describe, expect, it, vi } from 'vitest'
import { createAgentRunRuntime } from '../useAgentRunRuntime'
import type { AgentRunEventStream } from '@/api/agentRunStream'
import type { GenerationPhase } from '@/components/workbench/workbenchTypes'

type Listener = (event: MessageEvent<string>) => void

class FakeEventSource implements AgentRunEventStream {
  listeners = new Map<string, Listener>()
  closed = false

  addEventListener(eventName: string, listener: Listener) {
    this.listeners.set(eventName, listener)
  }

  close() {
    this.closed = true
  }
}

const sseEvent = (data: Record<string, unknown>) =>
  ({
    data: JSON.stringify(data),
  }) as MessageEvent<string>

const agentRunEventDto = (type: string, payload: Record<string, unknown>, sequence: string) =>
  sseEvent({
    eventId: `event-${sequence}`,
    runId: 'run-1',
    projectId: 'project-1',
    sessionId: 'session-1',
    turnId: 'turn-1',
    sequence,
    schemaVersion: 1,
    type,
    payloadJson: JSON.stringify({ schemaVersion: 1, ...payload }),
    createdAt: '2026-07-06T22:33:20.132Z',
  })

const createRuntimeHarness = (streamErrorStatus: 'completed' | 'failed' | '' = '') => {
  const stream = new FakeEventSource()
  const tokens: string[] = []
  let completedText = ''
  let runtimeEvent: Record<string, unknown> | null = null
  let runPhase: GenerationPhase = 'idle'
  let runStatus = ''
  let statusDetail = ''
  let latestSequence = '0'
  let resetCount = 0

  const runtime = createAgentRunRuntime({
    getRunStatus: () => runStatus as any,
    setRunStatus: (value) => {
      runStatus = value
    },
    setAgentStatusDetailText: (value) => {
      statusDetail = value
    },
    getRunPhase: () => runPhase,
    setRunPhase: (value) => {
      runPhase = value
    },
    getRunStream: () => stream,
    setRunStream: () => undefined,
    openRunStream: () => stream,
    addStreamListener: (_stream, eventName, listener) => {
      stream.listeners.set(eventName, listener)
    },
    scrollChat: () => undefined,
    setRuntimeEventSource: (value) => {
      runtimeEvent = value as Record<string, unknown>
    },
    setLatestSequence: (value) => {
      latestSequence = value
    },
    onToken: (token) => {
      tokens.push(token)
    },
    onMessageCompleted: (text) => {
      completedText = text
    },
    onStreamReset: async () => {
      resetCount += 1
      return 'completed' as const
    },
    onStreamError: async () => ({
      status: streamErrorStatus,
      errorMessage: streamErrorStatus === 'failed' ? 'backend failure' : undefined,
    }),
  })

  return {
    stream,
    tokens,
    get completedText() {
      return completedText
    },
    get runtimeEvent() {
      return runtimeEvent
    },
    get runPhase() {
      return runPhase
    },
    get runStatus() {
      return runStatus
    },
    get statusDetail() {
      return statusDetail
    },
    get latestSequence() {
      return latestSequence
    },
    get resetCount() {
      return resetCount
    },
    consume: () => runtime.consumeRunStream('project-1', 'run-1', '0'),
    close: runtime.closeRunStream,
  }
}

describe('createAgentRunRuntime', () => {
  afterEach(() => vi.useRealTimers())

  it('unwraps backend AgentRunEventDto payloadJson for stream tokens status and completed text', async () => {
    const harness = createRuntimeHarness()
    const consuming = harness.consume()

    harness.stream.listeners.get('run.phase.changed')?.(
      agentRunEventDto('run.phase.changed', { phase: 'executing', status: 'running', message: 'writing' }, '6'),
    )
    harness.stream.listeners.get('message.delta')?.(
      agentRunEventDto('message.delta', { channel: 'final', llmTurnIndex: 1, text: 'Hello' }, '-1'),
    )
    expect(harness.latestSequence).toBe('6')
    harness.stream.listeners.get('message.completed')?.(
      agentRunEventDto('message.completed',
        { channel: 'final', llmTurnIndex: 1, role: 'assistant', text: 'Hello final' }, '9'),
    )
    harness.stream.listeners.get('run.completed')?.(
      agentRunEventDto('run.completed', { phase: 'completed', message: 'done' }, '11'),
    )

    await expect(consuming).resolves.toBe('completed')
    expect(harness.tokens).toEqual(['Hello'])
    expect(harness.completedText).toBe('Hello final')
    expect(harness.runtimeEvent).toMatchObject({
      eventName: 'run.completed',
      runId: 'run-1',
      sequence: '11',
      phase: 'completed',
      message: 'done',
    })
    expect(harness.runPhase).toBe('streaming')
    expect(harness.runStatus).toBe('completed')
    expect(harness.statusDetail).toBe('done')
    expect(harness.latestSequence).toBe('11')
  })

  it('never writes untyped or process-channel message events into the final answer', async () => {
    const harness = createRuntimeHarness()
    const consuming = harness.consume()

    harness.stream.listeners.get('message.delta')?.(
      agentRunEventDto('message.delta', { text: 'untyped process text' }, '-1'),
    )
    harness.stream.listeners.get('message.delta')?.(
      agentRunEventDto('message.delta', { channel: 'commentary', text: 'commentary text' }, '-1'),
    )
    harness.stream.listeners.get('message.completed')?.(
      agentRunEventDto('message.completed', { channel: 'commentary', text: 'process completed' }, '8'),
    )

    expect(harness.tokens).toEqual([])
    expect(harness.completedText).toBe('')

    harness.stream.listeners.get('run.cancelled')?.(
      agentRunEventDto('run.cancelled', { status: 'cancelled' }, '9'),
    )
    await expect(consuming).resolves.toBe('cancelled')
  })

  it('reloads recovery state and settles when the server resets an expired cursor', async () => {
    const harness = createRuntimeHarness()
    const consuming = harness.consume()

    harness.stream.listeners.get('stream.reset')?.(
      sseEvent({
        runId: 'run-1',
        requestedAfter: '2',
        oldestAvailableSequence: '51',
        latestSequence: '80',
        reason: 'CURSOR_EXPIRED',
      }),
    )

    await expect(consuming).resolves.toBe('completed')
    expect(harness.resetCount).toBe(1)
    expect(harness.latestSequence).toBe('80')
    expect(harness.runtimeEvent).toMatchObject({
      eventName: 'stream.reset',
      runId: 'run-1',
      sequence: '80',
    })
  })

  it('publishes unknown event types from the generic agent event envelope', async () => {
    const harness = createRuntimeHarness()
    const consuming = harness.consume()

    harness.stream.listeners.get('agent.event')?.(
      agentRunEventDto('planner.future.signal', { phase: 'planning', detail: 'kept' }, '7'),
    )

    expect(harness.runtimeEvent).toMatchObject({
      eventName: 'planner.future.signal',
      runId: 'run-1',
      sequence: '7',
      phase: 'planning',
    })
    expect(harness.latestSequence).toBe('7')

    harness.stream.listeners.get('run.cancelled')?.(
      agentRunEventDto('run.cancelled', { status: 'cancelled' }, '8'),
    )
    await expect(consuming).resolves.toBe('cancelled')
  })

  it('publishes chapter edit preview events with their raw payload', async () => {
    const harness = createRuntimeHarness()
    const consuming = harness.consume()

    harness.stream.listeners.get('chapter.edit.started')?.(
      agentRunEventDto('chapter.edit.started', { chapterId: 'chapter-8', contentRevision: 3 }, '7'),
    )
    expect(harness.runtimeEvent).toMatchObject({
      eventName: 'chapter.edit.started',
      payload: { chapterId: 'chapter-8', contentRevision: 3 },
    })

    harness.stream.listeners.get('chapter.edit.delta')?.(
      agentRunEventDto('chapter.edit.delta', { chapterId: 'chapter-8', text: '新正文', offset: 0 }, '-1'),
    )
    expect(harness.runtimeEvent).toMatchObject({
      eventName: 'chapter.edit.delta',
      payload: { chapterId: 'chapter-8', text: '新正文', offset: 0 },
    })

    harness.stream.listeners.get('run.cancelled')?.(
      agentRunEventDto('run.cancelled', { status: 'cancelled' }, '8'),
    )
    await expect(consuming).resolves.toBe('cancelled')
  })

  it('keeps the Run active while publishing recoverable tool and approval failures', async () => {
    const harness = createRuntimeHarness()
    const consuming = harness.consume()

    harness.stream.listeners.get('tool.call.failed')?.(
      agentRunEventDto('tool.call.failed', { toolCallId: 'call-1', errorMessage: 'retry with less text' }, '7'),
    )
    expect(harness.runtimeEvent).toMatchObject({
      eventName: 'tool.call.failed',
      payload: { toolCallId: 'call-1', errorMessage: 'retry with less text' },
    })
    expect(harness.runPhase).toBe('streaming')

    harness.stream.listeners.get('approval.rejected')?.(
      agentRunEventDto('approval.rejected', { approvalId: '88001' }, '8'),
    )
    expect(harness.runStatus).toBe('running')
    expect(harness.runPhase).toBe('streaming')

    harness.stream.listeners.get('run.cancelled')?.(
      agentRunEventDto('run.cancelled', { status: 'cancelled' }, '9'),
    )
    await expect(consuming).resolves.toBe('cancelled')
  })

  it('settles successfully when reconciliation finds a completed Run after a stream error', async () => {
    const harness = createRuntimeHarness('completed')
    const consuming = harness.consume()

    harness.stream.listeners.get('error')?.(sseEvent({ message: 'network disconnected', fatal: false }))

    await expect(consuming).resolves.toBe('completed')
    expect(harness.runtimeEvent).toMatchObject({
      eventName: 'stream.error',
      runId: 'run-1',
      message: 'network disconnected',
    })
  })

  it('reconciles a silently stalled stream without waiting for a browser error', async () => {
    vi.useFakeTimers()
    const harness = createRuntimeHarness('completed')
    const consuming = harness.consume()

    await vi.advanceTimersByTimeAsync(5_000)

    await expect(consuming).resolves.toBe('completed')
    expect(harness.runStatus).toBe('completed')
  })

  it('settles the active consumer when the UI detaches without cancelling the backend Run', async () => {
    const harness = createRuntimeHarness()
    const consuming = harness.consume()

    harness.close()

    await expect(consuming).resolves.toBe('')
    expect(harness.stream.closed).toBe(true)
    harness.stream.listeners.get('run.completed')?.(
      agentRunEventDto('run.completed', { phase: 'completed' }, '12'),
    )
    expect(harness.runStatus).toBe('')
  })
})
