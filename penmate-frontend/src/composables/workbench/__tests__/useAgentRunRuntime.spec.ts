import { describe, expect, it } from 'vitest'
import { createAgentRunRuntime } from '../useAgentRunRuntime'
import type { GenerationPhase } from '@/components/workbench/workbenchTypes'

type Listener = (event: MessageEvent<string>) => void

class FakeEventSource {
  listeners = new Map<string, Listener>()
  closed = false

  close() {
    this.closed = true
  }
}

const sseEvent = (data: Record<string, unknown>) => ({
  data: JSON.stringify(data),
}) as MessageEvent<string>

const agentRunEventDto = (
  type: string,
  payload: Record<string, unknown>,
  sequence: string,
) => sseEvent({
  eventId: `event-${sequence}`,
  runId: 'run-1',
  projectId: 'project-1',
  sessionId: 'session-1',
  turnId: 'turn-1',
  sequence,
  schemaVersion: 1,
  type,
  payloadJson: JSON.stringify({ schemaVersion: 1, ...payload }),
  createdAt: '2026-07-06T22:33:20.132',
})

const createRuntimeHarness = () => {
  const stream = new FakeEventSource()
  const tokens: string[] = []
  let completedText = ''
  let runtimeEvent: Record<string, unknown> | null = null
  let runPhase: GenerationPhase = 'idle'
  let runStatus = ''
  let statusDetail = ''
  let latestSequence = '0'

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
    getRunStream: () => stream as unknown as EventSource,
    setRunStream: () => undefined,
    openRunStream: () => stream as unknown as EventSource,
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
    consume: () => runtime.consumeRunStream('project-1', 'run-1', '0'),
  }
}

describe('createAgentRunRuntime', () => {
  it('unwraps backend AgentRunEventDto payloadJson for stream tokens status and completed text', async () => {
    const harness = createRuntimeHarness()
    const consuming = harness.consume()

    harness.stream.listeners.get('run.phase.changed')?.(agentRunEventDto(
      'run.phase.changed',
      { phase: 'executing', status: 'running', message: 'writing' },
      '6',
    ))
    harness.stream.listeners.get('message.delta')?.(agentRunEventDto(
      'message.delta',
      { llmTurnIndex: 1, text: 'Hello' },
      '-1',
    ))
    harness.stream.listeners.get('message.completed')?.(agentRunEventDto(
      'message.completed',
      { llmTurnIndex: 1, role: 'assistant', text: 'Hello final' },
      '9',
    ))
    harness.stream.listeners.get('run.completed')?.(agentRunEventDto(
      'run.completed',
      { phase: 'completed', message: 'done' },
      '11',
    ))

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
})
