import { describe, expect, it, vi } from 'vitest'
import { useWorkbenchChat } from '../useWorkbenchChat'

type Listener = (event: MessageEvent<string>) => void

class FakeEventSource {
  listeners = new Map<string, Listener>()

  close() {}
}

const event = (payload: Record<string, unknown>) =>
  ({
    data: JSON.stringify(payload),
  }) as MessageEvent<string>

describe('useWorkbenchChat terminal Run retry', () => {
  it('requests_one_successor_and_consumes_its_stream_in_the_existing_message_slot', async () => {
    const stream = new FakeEventSource()
    const retryRun = vi.fn().mockResolvedValue({
      turnId: '80',
      runId: '61',
      runStatus: 'PENDING',
      runPhase: 'created',
      latestSequence: '1',
    })
    const chat = useWorkbenchChat({
      getContext: () => ({ projectId: '10', operatorId: '50' }),
      getCurrentProjectId: () => '10',
      getActiveChapterKey: () => '',
      getSelectedText: () => '',
      getActivePlugins: () => [],
      ensureModelConfigId: async () => '90',
      listSessions: async () => [],
      createSession: async () => ({}),
      getSessionRecovery: async () => ({}),
      createTurn: async () => ({}),
      cancelRun: async () => ({}),
      retryRun,
      openRunStream: () => stream as unknown as EventSource,
      addStreamListener: (_source, eventName, listener) => {
        stream.listeners.set(eventName, listener)
      },
      scrollChat: () => undefined,
      nextTick: async () => undefined,
    })
    chat.hydrateFromRecoverySnapshot({
      session: { sessionId: '30' },
      activeRun: { runId: '60', runStatus: 'FAILED', latestSequence: '9' },
      messages: [{ messageId: '900', role: 'assistant', contentMarkdown: 'old failure' }],
    })

    expect(chat.canRetryRun.value).toBe(true)
    const retrying = chat.retryCurrentRun()
    void chat.retryCurrentRun()
    await vi.waitFor(() => expect(stream.listeners.has('run.completed')).toBe(true))

    stream.listeners.get('message.completed')?.(event({ text: 'new answer', sequence: '4' }))
    stream.listeners.get('run.completed')?.(event({ phase: 'completed', sequence: '5' }))
    await retrying

    expect(retryRun).toHaveBeenCalledOnce()
    expect(retryRun).toHaveBeenCalledWith('10', '60', { operatorId: '50' })
    expect(chat.messages.value).toHaveLength(1)
    expect(chat.messages.value[0]?.text).toContain('new answer')
    expect(chat.isRetrying.value).toBe(false)
  })
})
