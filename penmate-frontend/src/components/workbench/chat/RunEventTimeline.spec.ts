import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import RunEventTimeline from './RunEventTimeline.vue'

describe('RunEventTimeline', () => {
  afterEach(() => vi.useRealTimers())

  it('renders only user-facing events and keeps failures visible', async () => {
    const wrapper = mount(RunEventTimeline, {
      props: {
        latest: true,
        attempt: {
          runId: '1', turnId: '2', runStatus: 'FAILED', runPhase: 'failed', attemptCount: 1,
          latestSequence: 2, connectionState: 'closed', lastErrorMessage: 'provider unavailable',
          events: [
            { runId: '1', turnId: '2', sequence: 1, type: 'tool.call.started', payload: { toolCode: 'draft' } },
            { runId: '1', turnId: '2', sequence: 2, type: 'future.event', payload: { value: 1 } },
          ],
        },
      },
    })

    expect(wrapper.text()).toContain('工具调用开始')
    expect(wrapper.text()).not.toContain('未分类事件')
    expect(wrapper.text()).toContain('provider unavailable')
    expect(wrapper.findAll('.event-raw')).toHaveLength(0)
    expect(wrapper.find('.attempt-spinner').exists()).toBe(false)
  })

  it('collapses completed attempts by default and still allows manual expansion', async () => {
    const wrapper = mount(RunEventTimeline, {
      props: {
        latest: true,
        attempt: {
          runId: '1', turnId: '2', runStatus: 'DONE', runPhase: 'completed', attemptCount: 1,
          latestSequence: 1, connectionState: 'closed',
          events: [{ runId: '1', turnId: '2', sequence: 1, type: 'run.completed', payload: {} }],
        },
      },
    })

    expect(wrapper.get('.attempt-summary').attributes('aria-expanded')).toBe('false')
    expect(wrapper.find('.event-list').exists()).toBe(false)

    await wrapper.get('.attempt-summary').trigger('click')
    expect(wrapper.find('.event-list').exists()).toBe(true)
  })

  it('automatically collapses when a running attempt completes', async () => {
    const attempt = {
      runId: '1', turnId: '2', runStatus: 'RUNNING', runPhase: 'executing', attemptCount: 1,
      latestSequence: 1, connectionState: 'connected' as const,
      events: [{ runId: '1', turnId: '2', sequence: 1, type: 'run.started', payload: {} }],
    }
    const wrapper = mount(RunEventTimeline, { props: { latest: true, attempt } })
    expect(wrapper.get('.attempt-summary').attributes('aria-expanded')).toBe('true')

    await wrapper.setProps({ attempt: { ...attempt, runStatus: 'DONE', runPhase: 'completed' } })

    expect(wrapper.get('.attempt-summary').attributes('aria-expanded')).toBe('false')
    expect(wrapper.find('.event-list').exists()).toBe(false)
  })

  it('updates elapsed time every second while the Run is active', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-07-21T09:00:00Z'))
    const wrapper = mount(RunEventTimeline, {
      props: {
        latest: true,
        attempt: {
          runId: '1', turnId: '2', runStatus: 'RUNNING', runPhase: 'executing', attemptCount: 1,
          latestSequence: 1, connectionState: 'connected', startedAt: '2026-07-21T09:00:00Z', events: [],
        },
      },
    })

    expect(wrapper.get('.attempt-elapsed').text()).toBe('0 秒')
    await vi.advanceTimersByTimeAsync(2_000)
    expect(wrapper.get('.attempt-elapsed').text()).toBe('2 秒')
    wrapper.unmount()
  })

  it('shows meaningful phases without duplicate terminal events for a simple run', async () => {
    const wrapper = mount(RunEventTimeline, {
      props: {
        latest: true,
        attempt: {
          runId: '1', turnId: '2', runStatus: 'DONE', runPhase: 'completed', attemptCount: 1,
          latestSequence: 7, connectionState: 'closed',
          events: [
            { runId: '1', turnId: '2', sequence: 1, type: 'run.started', payload: {} },
            { runId: '1', turnId: '2', sequence: 2, type: 'turn.route.completed', payload: {} },
            { runId: '1', turnId: '2', sequence: 3, type: 'context.resolved', payload: {} },
            { runId: '1', turnId: '2', sequence: 4, type: 'llm.turn.started', payload: { llmTurnIndex: 0 } },
            { runId: '1', turnId: '2', sequence: 5, type: 'llm.turn.completed', payload: { llmTurnIndex: 0 } },
            { runId: '1', turnId: '2', sequence: 6, type: 'message.completed', payload: {} },
            { runId: '1', turnId: '2', sequence: 7, type: 'run.completed', payload: {} },
          ],
        },
      },
    })

    await wrapper.get('.attempt-summary').trigger('click')
    expect(wrapper.text()).toContain('已开始')
    expect(wrapper.text()).toContain('上下文准备完成')
    expect(wrapper.text()).toContain('正在生成回复')
    expect(wrapper.text()).toContain('运行完成')
    expect(wrapper.text()).not.toContain('正在准备上下文')
    expect(wrapper.text()).not.toContain('模型轮次完成')
    expect(wrapper.text()).not.toContain('回答已生成')
  })
})
