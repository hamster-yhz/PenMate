import { describe, expect, it, vi } from 'vitest'
import { createWorkbenchSessionState } from '@/stores/workbenchSession'
import { useWorkbenchSessionRecovery } from '../useWorkbenchSessionRecovery'

const flushPromises = async (times = 6) => {
  for (let index = 0; index < times; index += 1) {
    await Promise.resolve()
  }
}

describe('useWorkbenchSessionRecovery', () => {
  it('passes_operator_id_to_resume_session_payload', async () => {
    const resumeSession = vi.fn().mockResolvedValue({
      session: { sessionId: '90001', title: '第三章', status: 'ACTIVE', boundStyle: null },
      activeTask: null,
      pendingApproval: null,
      messages: [],
      workbenchContext: { chapterId: '301', selectedText: '', activePlugins: [], modelConfigId: 'mcfg-001' },
    })

    const recoveryController = useWorkbenchSessionRecovery({
      getSessionRecovery: vi.fn(),
      resumeSession,
      openTurnStream: vi.fn(() => ({ close: vi.fn() } as unknown as EventSource)),
      hydrateStore: vi.fn(),
    })

    await (recoveryController.restore as (...args: any[]) => Promise<unknown>)('101', '90001', '201')

    expect(resumeSession).toHaveBeenCalledWith('101', '90001', {
      trigger: 'WORKBENCH_ENTER',
      operatorId: '201',
    })
  })

  it('hydrates_store_from_recovery_snapshot_and_reconnects_running_task', async () => {
    const sessionState = createWorkbenchSessionState()
    const openTurnStream = vi.fn(() => ({ close: vi.fn() } as unknown as EventSource))
    const recovery = {
      session: {
        sessionId: '90001',
        title: '第三章',
        status: 'ACTIVE',
        boundStyle: { styleId: '81', name: '冷峻悬疑' },
      },
      activeTask: {
        turnId: '50001',
        taskId: '70001',
        taskStatus: 'RUNNING',
        streamChannelKey: 'agent-turn-50001',
      },
      pendingApproval: null,
      messages: [],
      workbenchContext: {
        chapterId: '301',
        selectedText: '',
        activePlugins: ['outline.search'],
        modelConfigId: 'mcfg-001',
      },
    }

    const recoveryController = useWorkbenchSessionRecovery({
      getSessionRecovery: vi.fn().mockResolvedValue(recovery),
      resumeSession: vi.fn().mockResolvedValue(recovery),
      openTurnStream,
      hydrateStore: (snapshot: any) => {
        sessionState.sessionId = String(snapshot?.session?.sessionId ?? '') || null
        sessionState.title = String(snapshot?.session?.title ?? '')
        sessionState.status = String(snapshot?.session?.status ?? '')
        sessionState.boundStyle = {
          styleId: String(snapshot?.session?.boundStyle?.styleId ?? '') || null,
          name: String(snapshot?.session?.boundStyle?.name ?? ''),
        }
        sessionState.activeTask = {
          turnId: String(snapshot?.activeTask?.turnId ?? '') || null,
          taskId: String(snapshot?.activeTask?.taskId ?? '') || null,
          taskStatus: String(snapshot?.activeTask?.taskStatus ?? ''),
          streamChannelKey: String(snapshot?.activeTask?.streamChannelKey ?? ''),
        }
        sessionState.pendingApproval = snapshot?.pendingApproval ?? null
        sessionState.messages = Array.isArray(snapshot?.messages) ? snapshot.messages : []
        sessionState.workbenchContext = {
          chapterId: String(snapshot?.workbenchContext?.chapterId ?? '') || null,
          selectedText: String(snapshot?.workbenchContext?.selectedText ?? ''),
          activePlugins: Array.isArray(snapshot?.workbenchContext?.activePlugins) ? snapshot.workbenchContext.activePlugins : [],
          modelConfigId: String(snapshot?.workbenchContext?.modelConfigId ?? ''),
        }
      },
    })

    await recoveryController.restore('101', '90001')
    await flushPromises()

    expect(sessionState.sessionId).toBe('90001')
    expect(sessionState.title).toBe('第三章')
    expect(sessionState.status).toBe('ACTIVE')
    expect(sessionState.boundStyle).toEqual({ styleId: '81', name: '冷峻悬疑' })
    expect(sessionState.activeTask).toEqual({
      turnId: '50001',
      taskId: '70001',
      taskStatus: 'RUNNING',
      streamChannelKey: 'agent-turn-50001',
    })
    expect(sessionState.workbenchContext).toEqual({
      chapterId: '301',
      selectedText: '',
      activePlugins: ['outline.search'],
      modelConfigId: 'mcfg-001',
    })
    expect(openTurnStream).toHaveBeenCalledWith('101', '90001', '50001')
  })

  it('keeps_business_ids_as_strings_when_hydrating_store_from_recovery_snapshot', async () => {
    const oversizedSessionId = '90071992547409931234'
    const oversizedStyleId = '90071992547409939876'
    const oversizedTaskId = '90071992547409935678'
    const oversizedChapterId = '90071992547409933456'
    const sessionState = createWorkbenchSessionState() as unknown as Record<string, unknown>
    const recovery = {
      session: {
        sessionId: oversizedSessionId,
        title: '第三章',
        status: 'ACTIVE',
        boundStyle: { styleId: oversizedStyleId, name: '冷峻悬疑' },
      },
      activeTask: {
        turnId: '90071992547409936789',
        taskId: oversizedTaskId,
        taskStatus: 'RUNNING',
        streamChannelKey: 'agent-turn-90071992547409936789',
      },
      pendingApproval: null,
      messages: [],
      workbenchContext: {
        chapterId: oversizedChapterId,
        selectedText: '',
        activePlugins: ['outline.search'],
        modelConfigId: 'mcfg-001',
      },
    }

    const recoveryController = useWorkbenchSessionRecovery({
      getSessionRecovery: vi.fn().mockResolvedValue(recovery),
      resumeSession: vi.fn().mockResolvedValue(recovery),
      openTurnStream: vi.fn(() => ({ close: vi.fn() } as unknown as EventSource)),
      hydrateStore: (snapshot: any) => {
        sessionState.sessionId = String(snapshot?.session?.sessionId ?? '')
        sessionState.title = String(snapshot?.session?.title ?? '')
        sessionState.status = String(snapshot?.session?.status ?? '')
        sessionState.boundStyle = {
          styleId: String(snapshot?.session?.boundStyle?.styleId ?? ''),
          name: String(snapshot?.session?.boundStyle?.name ?? ''),
        }
        sessionState.activeTask = {
          turnId: String(snapshot?.activeTask?.turnId ?? ''),
          taskId: String(snapshot?.activeTask?.taskId ?? ''),
          taskStatus: String(snapshot?.activeTask?.taskStatus ?? ''),
          streamChannelKey: String(snapshot?.activeTask?.streamChannelKey ?? ''),
        }
        sessionState.workbenchContext = {
          chapterId: String(snapshot?.workbenchContext?.chapterId ?? ''),
          selectedText: String(snapshot?.workbenchContext?.selectedText ?? ''),
          activePlugins: Array.isArray(snapshot?.workbenchContext?.activePlugins) ? snapshot.workbenchContext.activePlugins : [],
          modelConfigId: String(snapshot?.workbenchContext?.modelConfigId ?? ''),
        }
      },
    })

    await recoveryController.restore('101', oversizedSessionId, '201')
    await flushPromises()

    expect(sessionState.sessionId).toBe(oversizedSessionId)
    expect(sessionState.boundStyle).toEqual({ styleId: oversizedStyleId, name: '冷峻悬疑' })
    expect(sessionState.activeTask).toEqual({
      turnId: '90071992547409936789',
      taskId: oversizedTaskId,
      taskStatus: 'RUNNING',
      streamChannelKey: 'agent-turn-90071992547409936789',
    })
    expect(sessionState.workbenchContext).toEqual({
      chapterId: oversizedChapterId,
      selectedText: '',
      activePlugins: ['outline.search'],
      modelConfigId: 'mcfg-001',
    })
  })

  it('preserves_oversized_turn_id_when_reconnecting_running_task_during_restore', async () => {
    const oversizedTurnId = '90071992547409931234'
    const openTurnStream = vi.fn(() => ({ close: vi.fn() } as unknown as EventSource))
    const recovery = {
      session: {
        sessionId: '90001',
        title: '第三章',
        status: 'ACTIVE',
        boundStyle: null,
      },
      activeTask: {
        turnId: oversizedTurnId,
        taskId: '70001',
        taskStatus: 'RUNNING',
        streamChannelKey: `agent-turn-${oversizedTurnId}`,
      },
      pendingApproval: null,
      messages: [],
      workbenchContext: {
        chapterId: '301',
        selectedText: '',
        activePlugins: [],
        modelConfigId: 'mcfg-001',
      },
    }

    const recoveryController = useWorkbenchSessionRecovery({
      getSessionRecovery: vi.fn().mockResolvedValue(recovery),
      resumeSession: vi.fn().mockResolvedValue(recovery),
      openTurnStream,
      hydrateStore: vi.fn(),
    })

    await (recoveryController.restore as (...args: any[]) => Promise<unknown>)('101', '90001', '201')
    await flushPromises()

    expect(openTurnStream).toHaveBeenCalledWith('101', '90001', oversizedTurnId)
  })

  it('opens_turn_stream_directly_when_resume_running_task_handler_is_not_provided', async () => {
    const openTurnStream = vi.fn(() => ({ close: vi.fn() } as unknown as EventSource))
    const recovery = {
      session: { sessionId: '90001', title: '第三章', status: 'ACTIVE', boundStyle: null },
      activeTask: { turnId: '50001', taskId: '70001', taskStatus: 'RUNNING', streamChannelKey: 'agent-turn-50001' },
      pendingApproval: null,
      messages: [],
      workbenchContext: { chapterId: '301', selectedText: '', activePlugins: [], modelConfigId: 'mcfg-001' },
    }

    const recoveryController = useWorkbenchSessionRecovery({
      getSessionRecovery: vi.fn().mockResolvedValue(recovery),
      resumeSession: vi.fn().mockResolvedValue(recovery),
      openTurnStream,
      hydrateStore: vi.fn(),
    })

    await recoveryController.restore('101', '90001')
    await flushPromises()

    expect(openTurnStream).toHaveBeenCalledWith('101', '90001', '50001')
  })

  it('prefers_resume_running_task_over_direct_stream_open_when_handler_is_provided', async () => {
    const openTurnStream = vi.fn(() => ({ close: vi.fn() } as unknown as EventSource))
    const resumeRunningTask = vi.fn().mockResolvedValue(undefined)
    const recovery = {
      session: { sessionId: '90001', title: '第三章', status: 'ACTIVE', boundStyle: null },
      activeTask: { turnId: '50001', taskId: '70001', taskStatus: 'RUNNING', streamChannelKey: 'agent-turn-50001' },
      pendingApproval: null,
      messages: [],
      workbenchContext: { chapterId: '301', selectedText: '', activePlugins: [], modelConfigId: 'mcfg-001' },
    }

    const recoveryController = useWorkbenchSessionRecovery({
      getSessionRecovery: vi.fn().mockResolvedValue(recovery),
      resumeSession: vi.fn().mockResolvedValue(recovery),
      openTurnStream,
      resumeRunningTask,
      hydrateStore: vi.fn(),
    })

    await recoveryController.restore('101', '90001')
    await flushPromises()

    expect(resumeRunningTask).toHaveBeenCalledWith('101', '90001', '50001')
    expect(openTurnStream).not.toHaveBeenCalled()
  })
})
