import { beforeEach, describe, expect, it, vi } from 'vitest'
import { clearSession, getSession, setSession } from '@/stores/session'

const fetchEventSourceMock = vi.hoisted(() => vi.fn())
const refreshAccessTokenMock = vi.hoisted(() => vi.fn())

vi.mock('@microsoft/fetch-event-source', () => ({
  EventStreamContentType: 'text/event-stream',
  fetchEventSource: fetchEventSourceMock,
}))

vi.mock('@/utils/request', () => ({
  refreshAccessToken: refreshAccessTokenMock,
}))

import { createAgentRunEventStream } from './agentRunStream'

type StreamOptions = {
  fetch: typeof fetch
  onopen: (response: Response) => Promise<void>
  onmessage: (message: { event: string; data: string; id: string }) => void
  onerror: (error: unknown) => number | void
}

const getOptions = () => fetchEventSourceMock.mock.calls[0]?.[1] as StreamOptions

describe('createAgentRunEventStream', () => {
  beforeEach(() => {
    clearSession()
    vi.clearAllMocks()
    fetchEventSourceMock.mockReturnValue(new Promise(() => undefined))
  })

  it('sends the current Bearer token and initial replay cursor', async () => {
    setSession({ accessToken: 'access-one' })
    const fetchMock = vi.fn().mockResolvedValue(new Response())
    vi.stubGlobal('fetch', fetchMock)

    const stream = createAgentRunEventStream('/api/v1/novels/10/agent/runs/61/stream?after=16')
    await vi.waitFor(() => expect(fetchEventSourceMock).toHaveBeenCalledOnce())
    await getOptions().fetch('/api/run-stream', { headers: { Accept: 'text/event-stream' } })

    const requestInit = fetchMock.mock.calls[0]?.[1] as RequestInit
    const headers = new Headers(requestInit.headers)
    expect(headers.get('Authorization')).toBe('Bearer access-one')
    expect(headers.get('Last-Event-ID')).toBe('16')
    expect(requestInit.credentials).toBe('include')
    stream.close()
  })

  it('refreshes once after a 401 and retries with the new token', async () => {
    setSession({ accessToken: 'expired-token' })
    refreshAccessTokenMock.mockImplementation(async () => {
      setSession({ accessToken: 'refreshed-token' })
      return getSession().accessToken
    })
    const fetchMock = vi.fn().mockResolvedValue(new Response())
    vi.stubGlobal('fetch', fetchMock)

    const stream = createAgentRunEventStream('/api/run-stream?after=3')
    await vi.waitFor(() => expect(fetchEventSourceMock).toHaveBeenCalledOnce())
    const options = getOptions()
    const unauthorizedError = await options
      .onopen(new Response(null, { status: 401 }))
      .then(() => null)
      .catch((error) => error)

    expect(refreshAccessTokenMock).toHaveBeenCalledOnce()
    expect(options.onerror(unauthorizedError)).toBe(0)
    await options.fetch('/api/run-stream', {})
    const headers = new Headers((fetchMock.mock.calls[0]?.[1] as RequestInit).headers)
    expect(headers.get('Authorization')).toBe('Bearer refreshed-token')
    stream.close()
  })

  it('adapts named and generic server events to existing listeners', async () => {
    const stream = createAgentRunEventStream('/api/run-stream?after=0')
    const namedListener = vi.fn()
    const genericListener = vi.fn()
    stream.addEventListener('run.completed', namedListener)
    stream.addEventListener('agent.event', genericListener)
    await vi.waitFor(() => expect(fetchEventSourceMock).toHaveBeenCalledOnce())

    getOptions().onmessage({ event: 'run.completed', data: '{"status":"DONE"}', id: '12' })
    getOptions().onmessage({ event: 'agent.event', data: '{"type":"future.event"}', id: '13' })

    expect(namedListener.mock.calls[0]?.[0]).toMatchObject({
      type: 'run.completed',
      data: '{"status":"DONE"}',
      lastEventId: '12',
    })
    expect(genericListener.mock.calls[0]?.[0]).toMatchObject({ lastEventId: '13' })
    stream.close()
  })
})
