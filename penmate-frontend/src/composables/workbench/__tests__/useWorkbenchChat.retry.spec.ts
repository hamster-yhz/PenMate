import { describe, expect, it, vi } from 'vitest'
import { nextTick, watchEffect } from 'vue'
import { useWorkbenchChat } from '../useWorkbenchChat'
import type { AgentRunEventStream } from '@/api/agentRunStream'

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

const event = (payload: Record<string, unknown>) =>
  ({
    data: JSON.stringify(payload),
  }) as MessageEvent<string>

describe('useWorkbenchChat terminal Run retry', () => {
  it('requests_one_successor_and_consumes_its_stream_in_the_existing_message_slot', async () => {
    const stream = new FakeEventSource()
    const openRunStream = vi.fn(() => stream)
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
      listSessionRuns: async () => [],
      createTurn: async () => ({}),
      cancelRun: async () => ({}),
      retryRun,
      openRunStream,
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

    stream.listeners.get('message.completed')?.(event({ channel: 'final', text: 'new answer', sequence: '4' }))
    stream.listeners.get('run.completed')?.(event({ phase: 'completed', sequence: '5' }))
    await retrying

    expect(retryRun).toHaveBeenCalledOnce()
    expect(retryRun).toHaveBeenCalledWith('10', '60', { operatorId: '50', activeSkills: [] })
    expect(openRunStream).toHaveBeenCalledWith('10', '61', '0')
    expect(chat.messages.value).toHaveLength(2)
    expect(chat.messages.value[0]?.text).toContain('old failure')
    expect(chat.messages.value[1]?.text).toContain('new answer')
    expect(chat.isRetrying.value).toBe(false)
  })

  it('restores the final assistant message when the stream disconnects after backend completion', async () => {
    const stream = new FakeEventSource()
    const chat = useWorkbenchChat({
      getContext: () => ({ projectId: '10', operatorId: '50' }),
      getCurrentProjectId: () => '10',
      getActiveChapterKey: () => '',
      getSelectedText: () => '',
      getActivePlugins: () => [],
      ensureModelConfigId: async () => '90',
      listSessions: async () => [],
      createSession: async () => ({}),
      getSessionRecovery: async () => ({
        session: { sessionId: '30' },
        activeRun: { turnId: '80', runId: '61', runStatus: 'DONE', latestSequence: '16' },
        messages: [
          { messageId: '901', turnId: '80', role: 'user', contentMarkdown: 'question' },
          { messageId: '902', turnId: '80', role: 'assistant', contentMarkdown: 'final answer' },
        ],
      }),
      listSessionRuns: async () => [
        {
          turnId: '80', runId: '61', runStatus: 'DONE', latestSequence: '16',
          output: { text: 'final answer', offset: 12, sequence: '15', state: 'final' }, events: [],
        },
      ],
      createTurn: async () => ({}),
      cancelRun: async () => ({}),
      retryRun: async () => ({}),
      openRunStream: () => stream,
      addStreamListener: (_source, eventName, listener) => {
        stream.listeners.set(eventName, listener)
      },
      scrollChat: () => undefined,
      nextTick: async () => undefined,
    })
    chat.hydrateFromRecoverySnapshot({
      session: { sessionId: '30' },
      activeRun: { turnId: '80', runId: '61', runStatus: 'RUNNING', latestSequence: '10' },
      messages: [{ messageId: '901', turnId: '80', role: 'user', contentMarkdown: 'question' }],
    })

    const consuming = chat.resumeRunningRun('10', '61', '10')
    await vi.waitFor(() => expect(stream.listeners.has('error')).toBe(true))
    stream.listeners.get('message.snapshot')?.(event({ channel: 'final', text: 'partial', offset: 7 }))
    stream.listeners.get('message.delta')?.(event({ channel: 'final', text: ' answer', offset: 7 }))
    stream.listeners.get('message.delta')?.(event({ channel: 'final', text: ' answer', offset: 7 }))
    expect(chat.messages.value.at(-1)?.text).toBe('partial answer')
    stream.listeners.get('error')?.(event({ message: 'network disconnected', fatal: false }))
    await consuming

    expect(chat.messages.value.at(-1)).toMatchObject({ role: 'assistant', text: 'final answer', turnId: '80' })
    expect(chat.generationPhase.value).toBe('idle')
    expect(chat.isGenerating.value).toBe(false)
    expect(chat.canCancelRun.value).toBe(false)
    expect(chat.canRetryRun.value).toBe(false)
  })

  it('restores the exact run output when run.completed arrives without message.completed', async () => {
    const stream = new FakeEventSource()
    const getSessionRecovery = vi.fn(async () => ({
      messages: [
        { messageId: 'old', turnId: '80', role: 'assistant', contentMarkdown: 'previous attempt answer' },
      ],
    }))
    const chat = useWorkbenchChat({
      getContext: () => ({ projectId: '10', operatorId: '50' }),
      getCurrentProjectId: () => '10',
      getActiveChapterKey: () => '',
      getSelectedText: () => '',
      getActivePlugins: () => [],
      ensureModelConfigId: async () => '90',
      listSessions: async () => [],
      createSession: async () => ({}),
      getSessionRecovery,
      listSessionRuns: async () => [
        {
          turnId: '80', runId: '61', runStatus: 'DONE', latestSequence: '5',
          output: { text: 'exact final answer', offset: 18, sequence: '4', state: 'final' }, events: [],
        },
      ],
      createTurn: async () => ({}),
      cancelRun: async () => ({}),
      retryRun: async () => ({}),
      openRunStream: () => stream,
      addStreamListener: (_source, eventName, listener) => {
        stream.listeners.set(eventName, listener)
      },
      scrollChat: () => undefined,
      nextTick: async () => undefined,
    })
    chat.hydrateFromRecoverySnapshot({
      session: { sessionId: '30' },
      activeRun: { turnId: '80', runId: '61', runStatus: 'RUNNING', latestSequence: '2' },
      messages: [],
    })

    const consuming = chat.resumeRunningRun('10', '61', '2')
    await vi.waitFor(() => expect(stream.listeners.has('run.completed')).toBe(true))
    stream.listeners.get('run.completed')?.(event({ sequence: '5' }))
    await consuming

    expect(chat.messages.value.at(-1)).toMatchObject({
      role: 'assistant',
      text: 'exact final answer',
      turnId: '80',
      runId: '61',
    })
    expect(getSessionRecovery).not.toHaveBeenCalled()
  })

  it('does not rebind a previous retry output to the active run during hydration', () => {
    const stream = new FakeEventSource()
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
      listSessionRuns: async () => [],
      createTurn: async () => ({}),
      cancelRun: async () => ({}),
      retryRun: async () => ({}),
      openRunStream: () => stream,
      addStreamListener: () => undefined,
      scrollChat: () => undefined,
      nextTick: async () => undefined,
    })

    chat.hydrateFromRecoverySnapshot({
      session: { sessionId: '30' },
      activeRun: { turnId: '80', runId: '61', runStatus: 'RUNNING', latestSequence: '2' },
      messages: [
        { messageId: 'old', turnId: '80', runId: '60', role: 'assistant', contentMarkdown: 'previous attempt' },
      ],
    })

    expect(chat.messages.value[0]).toMatchObject({ runId: '60', text: 'previous attempt' })
  })

  it('detaches an active Run when a new independent session is activated', async () => {
    const stream = new FakeEventSource()
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
      listSessionRuns: async () => [],
      createTurn: async () => ({}),
      cancelRun: async () => ({}),
      retryRun: async () => ({}),
      openRunStream: () => stream,
      addStreamListener: (_source, eventName, listener) => {
        stream.listeners.set(eventName, listener)
      },
      scrollChat: () => undefined,
      nextTick: async () => undefined,
    })
    chat.hydrateFromRecoverySnapshot({
      session: { sessionId: '30' },
      activeRun: { turnId: '80', runId: '60', runStatus: 'RUNNING', latestSequence: '2' },
      messages: [{ messageId: 'old-user', turnId: '80', role: 'user', contentMarkdown: 'old question' }],
    })

    const consuming = chat.resumeRunningRun('10', '60', '2')
    await vi.waitFor(() => expect(stream.listeners.has('run.completed')).toBe(true))
    chat.activateEmptySession('31')
    await consuming

    expect(stream.closed).toBe(true)
    expect(chat.currentConversationId.value).toBe('31')
    expect(chat.messages.value).toEqual([])
    expect(chat.runAttempts.value).toEqual([])
    expect(chat.isGenerating.value).toBe(false)
    expect(chat.canCancelRun.value).toBe(false)

    stream.listeners.get('run.completed')?.(event({ sequence: '9' }))
    expect(chat.currentConversationId.value).toBe('31')
    expect(chat.messages.value).toEqual([])
    expect(chat.isGenerating.value).toBe(false)
  })

  it('ignores an old createTurn response that arrives after activating a new session', async () => {
    const stream = new FakeEventSource()
    const openRunStream = vi.fn(() => stream)
    let resolveCreateTurn: ((value: unknown) => void) | undefined
    const createTurn = vi.fn(() => new Promise<unknown>((resolve) => { resolveCreateTurn = resolve }))
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
      listSessionRuns: async () => [],
      createTurn,
      cancelRun: async () => ({}),
      retryRun: async () => ({}),
      openRunStream,
      addStreamListener: () => undefined,
      scrollChat: () => undefined,
      nextTick: async () => undefined,
    })
    chat.hydrateFromRecoverySnapshot({ session: { sessionId: '30' }, messages: [] })
    chat.chatInput.value = 'old session question'

    const sending = chat.sendMessage()
    await vi.waitFor(() => expect(createTurn).toHaveBeenCalledOnce())
    chat.activateEmptySession('31')
    resolveCreateTurn?.({ activeRun: { turnId: '80', runId: '60', runStatus: 'PENDING' } })
    await sending

    expect(chat.currentConversationId.value).toBe('31')
    expect(chat.messages.value).toEqual([])
    expect(chat.runAttempts.value).toEqual([])
    expect(chat.isGenerating.value).toBe(false)
    expect(openRunStream).not.toHaveBeenCalled()
  })

  it('never reuses an assistant message from the previous turn', async () => {
    const stream = new FakeEventSource()
    const listSessionRuns = vi.fn(async () => [
      { turnId: '81', runId: '62', runStatus: 'RUNNING', latestSequence: '2', output: null, events: [] },
    ])
    let resolveCreateTurn: ((value: unknown) => void) | undefined
    const createTurn = vi.fn(() => new Promise<unknown>((resolve) => { resolveCreateTurn = resolve }))
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
      listSessionRuns,
      createTurn,
      cancelRun: async () => ({}),
      retryRun: async () => ({}),
      openRunStream: () => stream,
      addStreamListener: (_source, eventName, listener) => {
        stream.listeners.set(eventName, listener)
      },
      scrollChat: () => undefined,
      nextTick: async () => undefined,
    })
    chat.hydrateFromRecoverySnapshot({
      session: { sessionId: '30' },
      activeRun: { turnId: '80', runId: '61', runStatus: 'DONE' },
      messages: [
        { messageId: '900', turnId: '80', role: 'user', contentMarkdown: 'old question' },
        { messageId: '901', turnId: '80', role: 'assistant', contentMarkdown: 'old answer' },
      ],
    })
    chat.chatInput.value = 'new question'
    const observedNewTurnIds: string[] = []
    const stopObserving = watchEffect(() => {
      const message = chat.messages.value.find((item) => item.text === 'new question')
      observedNewTurnIds.push(message?.turnId ?? '')
    })

    const sending = chat.sendMessage()
    await vi.waitFor(() => expect(createTurn).toHaveBeenCalledOnce())

    expect(chat.messages.value.at(-1)).toMatchObject({ role: 'assistant', text: '' })
    expect(chat.messages.value.at(-1)?.turnId).toBeUndefined()
    expect(chat.messages.value.at(-1)?.runId).toBeUndefined()
    expect(chat.streamingAssistantMsgId.value).toBe(chat.messages.value.at(-1)?.id)
    expect(chat.isGenerating.value).toBe(true)
    expect(chat.messages.value.find((message) => message.id === '901')?.text).toBe('old answer')

    resolveCreateTurn?.({ activeRun: { turnId: '81', runId: '62' } })
    await vi.waitFor(() => expect(stream.listeners.has('run.completed')).toBe(true))
    await nextTick()

    expect(chat.messages.value.at(-1)).toMatchObject({
      role: 'assistant',
      text: '',
      turnId: '81',
      runId: '62',
    })
    expect(chat.runAttempts.value).toHaveLength(1)
    expect(chat.runAttempts.value[0]).toMatchObject({
      runId: '62',
      turnId: '81',
      runStatus: 'PENDING',
      runPhase: 'created',
      startedAt: expect.any(String),
    })
    expect(observedNewTurnIds).toContain('81')
    stream.listeners.get('error')?.(event({ message: 'disconnected', fatal: false }))
    await vi.waitFor(() => expect(listSessionRuns).toHaveBeenCalled())
    expect(chat.messages.value.at(-1)?.text).toBe('')

    stream.listeners.get('message.completed')?.(event({ channel: 'final', text: 'new answer', sequence: '4' }))
    stream.listeners.get('run.completed')?.(event({ sequence: '5' }))
    await sending
    expect(chat.messages.value.at(-1)?.text).toBe('new answer')
    stopObserving()
  })

  it('scrolls streamed content only after Vue has updated the DOM', async () => {
    const stream = new FakeEventSource()
    let releaseTick: (() => void) | undefined
    const nextTick = vi.fn(() => new Promise<void>((resolve) => { releaseTick = resolve }))
    const scrollChat = vi.fn()
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
      listSessionRuns: async () => [],
      createTurn: async () => ({}),
      cancelRun: async () => ({}),
      retryRun: async () => ({}),
      openRunStream: () => stream,
      addStreamListener: (_source, eventName, listener) => {
        stream.listeners.set(eventName, listener)
      },
      scrollChat,
      nextTick,
    })

    const consuming = chat.consumeRunStream('10', '61', '0')
    await vi.waitFor(() => expect(stream.listeners.has('message.delta')).toBe(true))
    stream.listeners.get('message.delta')?.(event({ channel: 'final', text: 'token', sequence: '1' }))

    expect(nextTick).toHaveBeenCalledOnce()
    expect(scrollChat).not.toHaveBeenCalled()
    releaseTick?.()
    await vi.waitFor(() => expect(scrollChat).toHaveBeenCalledOnce())

    stream.listeners.get('run.completed')?.(event({ sequence: '2' }))
    await consuming
  })
})
