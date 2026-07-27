import { describe, expect, it, vi } from 'vitest'
import { useWorkbenchSessionRecovery } from '../useWorkbenchSessionRecovery'

describe('useWorkbenchSessionRecovery', () => {
  it('restores a running session without waiting for its Run to finish', async () => {
    const resumeRunningRun = vi.fn(() => new Promise<void>(() => undefined))
    const hydrateStore = vi.fn()
    const recovery = useWorkbenchSessionRecovery({
      getSessionRecovery: async () => ({}),
      resumeSession: async () => ({
        session: { sessionId: '30' },
        activeRun: { runId: '60', runStatus: 'RUNNING', latestSequence: '8' },
        messages: [],
      }),
      openRunStream: vi.fn(),
      hydrateStore,
      resumeRunningRun,
    })

    await expect(recovery.restore('10', '30', '50')).resolves.toMatchObject({
      session: { sessionId: '30' },
      activeRun: { runId: '60' },
    })
    expect(hydrateStore).toHaveBeenCalledOnce()
    expect(resumeRunningRun).toHaveBeenCalledWith('10', '60', '8')
  })

  it.each(['PENDING', 'SUSPENDED'])('reconnects an active %s Run from its durable cursor', async (runStatus) => {
    const resumeRunningRun = vi.fn().mockResolvedValue(undefined)
    const recovery = useWorkbenchSessionRecovery({
      getSessionRecovery: async () => ({}),
      resumeSession: async () => ({
        session: { sessionId: '30' },
        activeRun: { runId: '60', runStatus, latestSequence: '12' },
        messages: [],
      }),
      openRunStream: vi.fn(),
      hydrateStore: vi.fn(),
      resumeRunningRun,
    })

    await recovery.restore('10', '30', '50')

    expect(resumeRunningRun).toHaveBeenCalledWith('10', '60', '12')
  })
})
