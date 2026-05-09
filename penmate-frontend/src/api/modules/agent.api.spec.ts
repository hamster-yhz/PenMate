import { describe, expect, it, vi, beforeEach } from 'vitest'

const requestMock = {
  get: vi.fn(),
  post: vi.fn(),
}

vi.mock('@/utils/request', () => ({
  default: requestMock,
}))

describe('agentApi', () => {
  beforeEach(() => {
    requestMock.get.mockReset()
    requestMock.post.mockReset()
  })

  it('builds_session_recovery_and_turn_endpoints', async () => {
    const { agentApi } = await import('./agent.api')

    requestMock.get.mockResolvedValue({})
    requestMock.post.mockResolvedValue({})

    await agentApi.listSessions('novel-101')
    await agentApi.createSession('novel-101', { userId: 'user-1001', title: '新会话' })
    await agentApi.getSessionRecovery('novel-101', 'session-90001')
    await agentApi.resumeSession('novel-101', 'session-90001', { operatorId: 'user-1001', trigger: 'WORKBENCH_ENTER' })
    await agentApi.createTurn('novel-101', 'session-90001', {
      operatorId: 'user-1001',
      userMessage: '继续写第三章',
      taskRequest: {
        chapterId: 'chapter-301',
        taskType: 'WRITE',
      },
    })
    await agentApi.getTask('novel-101', 'task-70001')

    expect(requestMock.get).toHaveBeenNthCalledWith(1, '/v1/novels/novel-101/agent/sessions')
    expect(requestMock.post).toHaveBeenNthCalledWith(1, '/v1/novels/novel-101/agent/sessions', { userId: 'user-1001', title: '新会话' })
    expect(requestMock.get).toHaveBeenNthCalledWith(2, '/v1/novels/novel-101/agent/sessions/session-90001/recovery')
    expect(requestMock.post).toHaveBeenNthCalledWith(2, '/v1/novels/novel-101/agent/sessions/session-90001/resume', {
      operatorId: 'user-1001',
      trigger: 'WORKBENCH_ENTER',
    })
    expect(requestMock.post).toHaveBeenNthCalledWith(3, '/v1/novels/novel-101/agent/sessions/session-90001/turns', {
      operatorId: 'user-1001',
      userMessage: '继续写第三章',
      taskRequest: {
        chapterId: 'chapter-301',
        taskType: 'WRITE',
      },
    })
    expect(requestMock.get).toHaveBeenNthCalledWith(3, '/v1/novels/novel-101/agent/tasks/task-70001')
  })

  it('builds_agent_endpoints_with_string_business_ids_only', async () => {
    const { agentApi } = await import('./agent.api')

    requestMock.get.mockResolvedValue({})
    requestMock.post.mockResolvedValue({})

    await agentApi.createSession('novel-101', { userId: 'user-1001', title: '新会话' })
    await agentApi.getSessionRecovery('novel-101', 'session-90001')
    await agentApi.resumeSession('novel-101', 'session-90001', { operatorId: 'user-1001', trigger: 'WORKBENCH_ENTER' })
    await agentApi.createTurn('novel-101', 'session-90001', {
      operatorId: 'user-1001',
      userMessage: '继续写第三章',
      taskRequest: {
        chapterId: 'chapter-301',
        taskType: 'WRITE',
      },
    })
    await agentApi.getTask('novel-101', 'task-70001')

    expect(requestMock.post).toHaveBeenNthCalledWith(1, '/v1/novels/novel-101/agent/sessions', {
      userId: 'user-1001',
      title: '新会话',
    })
    expect(requestMock.get).toHaveBeenNthCalledWith(1, '/v1/novels/novel-101/agent/sessions/session-90001/recovery')
    expect(requestMock.post).toHaveBeenNthCalledWith(2, '/v1/novels/novel-101/agent/sessions/session-90001/resume', {
      operatorId: 'user-1001',
      trigger: 'WORKBENCH_ENTER',
    })
    expect(requestMock.post).toHaveBeenNthCalledWith(3, '/v1/novels/novel-101/agent/sessions/session-90001/turns', {
      operatorId: 'user-1001',
      userMessage: '继续写第三章',
      taskRequest: {
        chapterId: 'chapter-301',
        taskType: 'WRITE',
      },
    })
    expect(requestMock.get).toHaveBeenNthCalledWith(2, '/v1/novels/novel-101/agent/tasks/task-70001')
  })

  it('builds_turn_stream_url_from_api_base', async () => {
    const { agentApi } = await import('./agent.api')

    expect(agentApi.getTurnStreamUrl('novel-101', 'session-90001', 'turn-50001')).toContain('/v1/novels/novel-101/agent/sessions/session-90001/turns/turn-50001/stream')
  })

  it('opens_turn_stream_when_called_without_object_binding', async () => {
    const eventSourceMock = vi.fn().mockImplementation((url: string) => ({ url }))
    vi.stubGlobal('EventSource', eventSourceMock)

    const { agentApi } = await import('./agent.api')
    const openTurnStream = agentApi.openTurnStream

    const stream = openTurnStream('novel-101', 'session-90001', 'turn-50001')

    expect(eventSourceMock).toHaveBeenCalledWith(expect.stringContaining('/v1/novels/novel-101/agent/sessions/session-90001/turns/turn-50001/stream'))
    expect(stream).toEqual({ url: expect.stringContaining('/v1/novels/novel-101/agent/sessions/session-90001/turns/turn-50001/stream') })
  })
})
