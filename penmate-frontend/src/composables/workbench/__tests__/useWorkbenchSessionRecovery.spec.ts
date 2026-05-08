import { describe, expect, it, vi } from 'vitest'
import { createWorkbenchSessionState } from '@/stores/workbenchSession'
import { useWorkbenchSessionRecovery } from '../useWorkbenchSessionRecovery'

const flushPromises = async (times = 6) => {
  for (let index = 0; index < times; index += 1) {
    await Promise.resolve()
  }
}

describe('useWorkbenchSessionRecovery', () => {
  it('hydrates_store_from_recovery_snapshot_and_reconnects_running_task', async () => {
    const sessionState = createWorkbenchSessionState()
    const openTaskStream = vi.fn(() => ({ close: vi.fn() } as unknown as EventSource))
    const recovery = {
      session: {
        sessionId: 90001,
        title: '第三章',
        status: 'ACTIVE',
        boundStyle: { styleId: 81, name: '冷峻悬疑' },
      },
      activeTask: {
        taskId: 70001,
        taskStatus: 'RUNNING',
        streamChannelKey: 'agent-task-70001',
      },
      pendingApproval: null,
      messages: [],
      workbenchContext: {
        chapterId: 301,
        selectedText: '',
        activePlugins: ['outline.search'],
        modelConfigId: 'mcfg-001',
      },
    }

    const recoveryController = useWorkbenchSessionRecovery({
      getSessionRecovery: vi.fn().mockResolvedValue(recovery),
      resumeSession: vi.fn().mockResolvedValue(recovery),
      openTaskStream,
      hydrateStore: (snapshot: typeof recovery) => {
        sessionState.sessionId = Number(snapshot?.session?.sessionId ?? 0) || null
        sessionState.title = String(snapshot?.session?.title ?? '')
        sessionState.status = String(snapshot?.session?.status ?? '')
        sessionState.boundStyle = {
          styleId: Number(snapshot?.session?.boundStyle?.styleId ?? 0) || null,
          name: String(snapshot?.session?.boundStyle?.name ?? ''),
        }
        sessionState.activeTask = {
          taskId: Number(snapshot?.activeTask?.taskId ?? 0) || null,
          taskStatus: String(snapshot?.activeTask?.taskStatus ?? ''),
          streamChannelKey: String(snapshot?.activeTask?.streamChannelKey ?? ''),
        }
        sessionState.pendingApproval = snapshot?.pendingApproval ?? null
        sessionState.messages = Array.isArray(snapshot?.messages) ? snapshot.messages : []
        sessionState.workbenchContext = {
          chapterId: Number(snapshot?.workbenchContext?.chapterId ?? 0) || null,
          selectedText: String(snapshot?.workbenchContext?.selectedText ?? ''),
          activePlugins: Array.isArray(snapshot?.workbenchContext?.activePlugins) ? snapshot.workbenchContext.activePlugins : [],
          modelConfigId: String(snapshot?.workbenchContext?.modelConfigId ?? ''),
        }
      },
    })

    await recoveryController.restore(101, 90001)
    await flushPromises()

    expect(sessionState.sessionId).toBe(90001)
    expect(sessionState.title).toBe('第三章')
    expect(sessionState.status).toBe('ACTIVE')
    expect(sessionState.boundStyle).toEqual({ styleId: 81, name: '冷峻悬疑' })
    expect(sessionState.activeTask).toEqual({
      taskId: 70001,
      taskStatus: 'RUNNING',
      streamChannelKey: 'agent-task-70001',
    })
    expect(sessionState.workbenchContext).toEqual({
      chapterId: 301,
      selectedText: '',
      activePlugins: ['outline.search'],
      modelConfigId: 'mcfg-001',
    })
    expect(openTaskStream).toHaveBeenCalledWith(101, 70001)
  })

  it('prefers_resume_running_task_over_direct_stream_open_when_handler_is_provided', async () => {
    const openTaskStream = vi.fn(() => ({ close: vi.fn() } as unknown as EventSource))
    const resumeRunningTask = vi.fn().mockResolvedValue(undefined)
    const recovery = {
      session: { sessionId: 90001, title: '第三章', status: 'ACTIVE', boundStyle: null },
      activeTask: { taskId: 70001, taskStatus: 'RUNNING', streamChannelKey: 'agent-task-70001' },
      pendingApproval: null,
      messages: [],
      workbenchContext: { chapterId: 301, selectedText: '', activePlugins: [], modelConfigId: 'mcfg-001' },
    }

    const recoveryController = useWorkbenchSessionRecovery({
      getSessionRecovery: vi.fn().mockResolvedValue(recovery),
      resumeSession: vi.fn().mockResolvedValue(recovery),
      openTaskStream,
      resumeRunningTask,
      hydrateStore: vi.fn(),
    })

    await recoveryController.restore(101, 90001)
    await flushPromises()

    expect(resumeRunningTask).toHaveBeenCalledWith(101, 70001)
    expect(openTaskStream).not.toHaveBeenCalled()
  })
})
