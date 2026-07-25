import { describe, expect, it } from 'vitest'
import { redactEventPayload, useWorkbenchRunTimeline } from '../useWorkbenchRunTimeline'

describe('useWorkbenchRunTimeline', () => {
  it('groups history by turn and keeps retry attempts in order', () => {
    const timeline = useWorkbenchRunTimeline()
    timeline.replaceHistory([
      { runId: '2', turnId: '10', predecessorRunId: '1', attemptCount: 2, events: [] },
      { runId: '1', turnId: '10', attemptCount: 1, events: [] },
    ])

    expect(timeline.attemptsByTurn.value.get('10')?.map((item) => item.runId)).toEqual(['1', '2'])
  })

  it('deduplicates live envelopes and retains unknown events', () => {
    const timeline = useWorkbenchRunTimeline()
    const payload = { runId: '1', turnId: '10', sequence: '3', type: 'future.event', payloadJson: '{"value":1}' }

    timeline.appendEvent('agent.event', payload)
    timeline.appendEvent('agent.event', payload)

    expect(timeline.attempts.value[0]?.events).toHaveLength(1)
    expect(timeline.attempts.value[0]?.events[0]?.type).toBe('future.event')
  })

  it('does not add message deltas to the visible event timeline', () => {
    const timeline = useWorkbenchRunTimeline()
    timeline.appendEvent('message.delta', { runId: '1', turnId: '10', sequence: '1', text: 'token' })
    expect(timeline.attempts.value[0]?.events).toHaveLength(0)
  })

  it('replaces model process snapshots with the durable completed block', () => {
    const timeline = useWorkbenchRunTimeline()
    timeline.appendEvent('model.reasoning_summary.snapshot', {
      runId: '1', turnId: '10', sequence: '-1', type: 'model.reasoning_summary.snapshot',
      payloadJson: '{"llmTurnIndex":1,"text":"先检查"}',
    })
    timeline.appendEvent('model.reasoning_summary.snapshot', {
      runId: '1', turnId: '10', sequence: '-1', type: 'model.reasoning_summary.snapshot',
      payloadJson: '{"llmTurnIndex":1,"text":"先检查设定"}',
    })
    timeline.appendEvent('model.reasoning_summary.completed', {
      runId: '1', turnId: '10', sequence: '4', type: 'model.reasoning_summary.completed',
      payloadJson: '{"llmTurnIndex":1,"text":"先检查设定"}',
    })

    expect(timeline.attempts.value[0]?.events).toHaveLength(1)
    expect(timeline.attempts.value[0]?.events[0]).toMatchObject({
      type: 'model.reasoning_summary.completed',
      sequence: 4,
      payload: { llmTurnIndex: 1, text: '先检查设定' },
    })
  })

  it('keeps live process snapshots at the timeline tail without swapping their arrival order', () => {
    const timeline = useWorkbenchRunTimeline()
    timeline.appendEvent('llm.turn.started', {
      runId: '1', turnId: '10', sequence: '12', type: 'llm.turn.started',
    })
    timeline.appendEvent('model.commentary.snapshot', {
      runId: '1', turnId: '10', sequence: '-1', type: 'model.commentary.snapshot',
      payloadJson: '{"llmTurnIndex":1,"text":"正在读取"}',
    })
    timeline.appendEvent('model.reasoning_summary.snapshot', {
      runId: '1', turnId: '10', sequence: '-1', type: 'model.reasoning_summary.snapshot',
      payloadJson: '{"llmTurnIndex":1,"text":"先检查设定"}',
    })
    timeline.appendEvent('model.commentary.snapshot', {
      runId: '1', turnId: '10', sequence: '-1', type: 'model.commentary.snapshot',
      payloadJson: '{"llmTurnIndex":1,"text":"正在读取章节"}',
    })

    expect(timeline.attempts.value[0]?.events.map((event) => event.type)).toEqual([
      'llm.turn.started',
      'model.commentary.snapshot',
      'model.reasoning_summary.snapshot',
    ])

    timeline.appendEvent('model.commentary.completed', {
      runId: '1', turnId: '10', sequence: '13', type: 'model.commentary.completed',
      payloadJson: '{"llmTurnIndex":1,"text":"正在读取章节"}',
    })

    expect(timeline.attempts.value[0]?.events.map((event) => event.type)).toEqual([
      'llm.turn.started',
      'model.commentary.completed',
      'model.reasoning_summary.snapshot',
    ])
  })

  it('does not swap two process blocks while one snapshot becomes durable', () => {
    const timeline = useWorkbenchRunTimeline()
    timeline.appendEvent('model.reasoning_summary.snapshot', {
      runId: '1', turnId: '10', sequence: '-1', type: 'model.reasoning_summary.snapshot',
      payloadJson: '{"llmTurnIndex":1,"text":"reasoning"}',
    })
    timeline.appendEvent('model.commentary.snapshot', {
      runId: '1', turnId: '10', sequence: '-1', type: 'model.commentary.snapshot',
      payloadJson: '{"llmTurnIndex":1,"text":"commentary"}',
    })
    timeline.appendEvent('model.commentary.completed', {
      runId: '1', turnId: '10', sequence: '8', type: 'model.commentary.completed',
      payloadJson: '{"llmTurnIndex":1,"text":"commentary"}',
    })

    expect(timeline.attempts.value[0]?.events.map((event) => event.type)).toEqual([
      'model.reasoning_summary.snapshot',
      'model.commentary.completed',
    ])

    timeline.appendEvent('model.reasoning_summary.completed', {
      runId: '1', turnId: '10', sequence: '9', type: 'model.reasoning_summary.completed',
      payloadJson: '{"llmTurnIndex":1,"text":"reasoning"}',
    })
    expect(timeline.attempts.value[0]?.events.map((event) => event.type)).toEqual([
      'model.reasoning_summary.completed',
      'model.commentary.completed',
    ])
  })

  it('derives live timing and failure details from streamed run events', () => {
    const timeline = useWorkbenchRunTimeline()
    timeline.appendEvent('run.started', {
      runId: '1', turnId: '10', sequence: '1', type: 'run.started', createdAt: '2026-07-21T09:00:00Z',
    })
    timeline.appendEvent('run.failed', {
      runId: '1', turnId: '10', sequence: '2', type: 'run.failed', createdAt: '2026-07-21T09:00:05Z',
      payloadJson: '{"errorCode":"TIMEOUT","errorMessage":"provider timed out"}',
    })

    expect(timeline.attempts.value[0]).toMatchObject({
      startedAt: '2026-07-21T09:00:00Z',
      finishedAt: '2026-07-21T09:00:05Z',
      runStatus: 'FAILED',
      lastErrorCode: 'TIMEOUT',
      lastErrorMessage: 'provider timed out',
    })
  })

  it('merges durable history without deleting newer live events or connection state', () => {
    const timeline = useWorkbenchRunTimeline()
    timeline.appendEvent('llm.turn.started', {
      runId: '1', turnId: '10', sequence: '12', type: 'llm.turn.started', createdAt: '2026-07-21T09:00:12Z',
    })
    timeline.setConnectionState('1', 'connected')

    timeline.mergeHistory([{
      runId: '1', turnId: '10', runStatus: 'RUNNING', latestSequence: '3', startedAt: '2026-07-21T09:00:00Z',
      events: [{ runId: '1', turnId: '10', sequence: '1', type: 'run.started', createdAt: '2026-07-21T09:00:00Z' }],
    }])

    expect(timeline.attempts.value[0]).toMatchObject({
      latestSequence: 12,
      connectionState: 'connected',
      startedAt: '2026-07-21T09:00:00Z',
    })
    expect(timeline.attempts.value[0]?.events.map((event) => event.sequence)).toEqual([1, 12])
  })

  it('redacts secrets recursively before raw JSON can reach the UI', () => {
    expect(redactEventPayload({ apiKey: 'secret', nested: { authorization: 'Bearer token', ok: 'visible' } })).toEqual({
      apiKey: '[已脱敏]',
      nested: { authorization: '[已脱敏]', ok: 'visible' },
    })
  })

  it('keeps output bound to its exact run history record', () => {
    const timeline = useWorkbenchRunTimeline()
    timeline.replaceHistory([{
      runId: '1', turnId: '10', runStatus: 'RUNNING', events: [],
      output: { text: 'current partial', offset: '15', sequence: null, state: 'partial', updatedAt: '2026-07-21T10:00:00Z' },
    }])

    expect(timeline.attempts.value[0]?.output).toEqual({
      text: 'current partial', offset: 15, sequence: null, state: 'partial', updatedAt: '2026-07-21T10:00:00Z',
    })
  })
})
