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

    await agentApi.listSessions(101)
    await agentApi.createSession(101, { userId: 1001, title: '新会话' })
    await agentApi.getSessionRecovery(101, 90001)
    await agentApi.resumeSession(101, 90001, { trigger: 'WORKBENCH_ENTER' })
    await agentApi.createTurn(101, 90001, { message: '继续写第三章' })
    await agentApi.getTask(101, 70001)

    expect(requestMock.get).toHaveBeenNthCalledWith(1, '/v1/novels/101/agent/sessions')
    expect(requestMock.post).toHaveBeenNthCalledWith(1, '/v1/novels/101/agent/sessions', { userId: 1001, title: '新会话' })
    expect(requestMock.get).toHaveBeenNthCalledWith(2, '/v1/novels/101/agent/sessions/90001/recovery')
    expect(requestMock.post).toHaveBeenNthCalledWith(2, '/v1/novels/101/agent/sessions/90001/resume', { trigger: 'WORKBENCH_ENTER' })
    expect(requestMock.post).toHaveBeenNthCalledWith(3, '/v1/novels/101/agent/sessions/90001/turns', { message: '继续写第三章' })
    expect(requestMock.get).toHaveBeenNthCalledWith(3, '/v1/novels/101/agent/tasks/70001')
  })

  it('builds_task_stream_url_from_api_base', async () => {
    const { agentApi } = await import('./agent.api')

    expect(agentApi.getTaskStreamUrl(101, 70001)).toContain('/v1/novels/101/agent/tasks/70001/stream')
  })

  it('opens_task_stream_when_called_without_object_binding', async () => {
    const eventSourceMock = vi.fn().mockImplementation((url: string) => ({ url }))
    vi.stubGlobal('EventSource', eventSourceMock)

    const { agentApi } = await import('./agent.api')
    const openTaskStream = agentApi.openTaskStream

    const stream = openTaskStream(101, 70001)

    expect(eventSourceMock).toHaveBeenCalledWith(expect.stringContaining('/v1/novels/101/agent/tasks/70001/stream'))
    expect(stream).toEqual({ url: expect.stringContaining('/v1/novels/101/agent/tasks/70001/stream') })
  })
})
