import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import RunAttemptGroup from './RunAttemptGroup.vue'

const attempt = (runId: string, attemptCount: number, status: string) => ({
  runId,
  turnId: '10',
  runStatus: status,
  runPhase: status.toLowerCase(),
  attemptCount,
  latestSequence: attemptCount,
  connectionState: 'closed' as const,
  events: [],
})

describe('RunAttemptGroup', () => {
  it('switches the timeline and final output as one retry version', async () => {
    const wrapper = mount(RunAttemptGroup, {
      props: {
        attempts: [attempt('1', 1, 'FAILED'), attempt('2', 2, 'DONE')],
        assistantMessages: [
          { id: 'a1', role: 'assistant', text: 'first failed output', turnId: '10', runId: '1' },
          { id: 'a2', role: 'assistant', text: 'second successful output', turnId: '10', runId: '2' },
        ],
      },
    })

    expect(wrapper.text()).toContain('2 / 2')
    expect(wrapper.text()).toContain('second successful output')
    expect(wrapper.text()).not.toContain('first failed output')
    expect(wrapper.get('[data-testid="run-attempt"]').classes()).toContain('status-done')

    await wrapper.get('[aria-label="上一次尝试"]').trigger('click')

    expect(wrapper.text()).toContain('1 / 2')
    expect(wrapper.text()).toContain('first failed output')
    expect(wrapper.text()).not.toContain('second successful output')
    expect(wrapper.get('[data-testid="run-attempt"]').classes()).toContain('status-failed')
  })

  it('does not borrow an unbound assistant output for a new run', () => {
    const wrapper = mount(RunAttemptGroup, {
      props: {
        attempts: [attempt('2', 1, 'RUNNING')],
        assistantMessages: [
          { id: 'old', role: 'assistant', text: 'previous output', turnId: '10' },
        ],
      },
    })

    expect(wrapper.text()).not.toContain('previous output')
  })

  it('renders the output supplied by the selected run history', () => {
    const current = {
      ...attempt('2', 1, 'DONE'),
      output: { text: 'exact run output', offset: 16, sequence: 4, state: 'final' },
    }
    const wrapper = mount(RunAttemptGroup, { props: { attempts: [current] } })

    expect(wrapper.text()).toContain('exact run output')
  })
})
