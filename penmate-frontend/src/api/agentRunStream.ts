import { EventStreamContentType, fetchEventSource, type EventSourceMessage } from '@microsoft/fetch-event-source'
import { refreshAccessToken } from '@/utils/request'
import { getSession } from '@/stores/session'

export type AgentRunStreamListener = (event: MessageEvent<string>) => void

export interface AgentRunEventStream {
  addEventListener(eventName: string, listener: AgentRunStreamListener): void
  close(): void
}

class FatalStreamError extends Error {
  readonly status?: number

  constructor(message: string, status?: number) {
    super(message)
    this.status = status
  }
}

class RetriableStreamError extends Error {
  readonly status?: number

  constructor(message: string, status?: number) {
    super(message)
    this.status = status
  }
}

const toMessageEvent = (type: string, data: string, lastEventId = '') =>
  ({ type, data, lastEventId }) as MessageEvent<string>

class FetchAgentRunEventStream implements AgentRunEventStream {
  private readonly controller = new AbortController()
  private readonly listeners = new Map<string, Set<AgentRunStreamListener>>()
  private closed = false
  private retryAttempt = 0
  private refreshedAfterUnauthorized = false
  private readonly url: string
  private readonly initialLastEventId: string

  constructor(url: string) {
    this.url = url
    this.initialLastEventId = new URL(url, window.location.origin).searchParams.get('after')?.trim() ?? ''
    queueMicrotask(() => void this.connect())
  }

  addEventListener(eventName: string, listener: AgentRunStreamListener) {
    const listeners = this.listeners.get(eventName) ?? new Set<AgentRunStreamListener>()
    listeners.add(listener)
    this.listeners.set(eventName, listeners)
  }

  close() {
    if (this.closed) return
    this.closed = true
    this.controller.abort()
    this.listeners.clear()
  }

  private emit(eventName: string, data: string, lastEventId = '') {
    const event = toMessageEvent(eventName, data, lastEventId)
    for (const listener of this.listeners.get(eventName) ?? []) listener(event)
  }

  private emitError(error: unknown, fatal: boolean) {
    const message = error instanceof Error ? error.message : '事件流连接失败'
    const status = error instanceof FatalStreamError || error instanceof RetriableStreamError ? error.status : undefined
    this.emit('error', JSON.stringify({ message, status, fatal }))
  }

  private authorizedFetch: typeof fetch = (input, init) => {
    const headers = new Headers(init?.headers)
    const accessToken = getSession().accessToken.trim()
    if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)
    if (this.initialLastEventId && !headers.has('Last-Event-ID')) {
      headers.set('Last-Event-ID', this.initialLastEventId)
    }
    return window.fetch(input, { ...init, headers, credentials: 'include' })
  }

  private async handleOpen(response: Response) {
    if (response.status === 401) {
      if (this.refreshedAfterUnauthorized) throw new FatalStreamError('事件流认证失败，请重新登录', 401)
      try {
        await refreshAccessToken()
      } catch {
        throw new FatalStreamError('登录状态已失效，请重新登录', 401)
      }
      this.refreshedAfterUnauthorized = true
      throw new RetriableStreamError('登录状态已刷新，正在重新连接事件流', 401)
    }

    const contentType = response.headers.get('content-type') ?? ''
    if (response.ok && contentType.startsWith(EventStreamContentType)) {
      this.retryAttempt = 0
      this.refreshedAfterUnauthorized = false
      return
    }
    if (response.status === 429 || response.status >= 500) {
      throw new RetriableStreamError(`事件流服务暂不可用 (${response.status})`, response.status)
    }
    throw new FatalStreamError(`无法建立事件流 (${response.status})`, response.status)
  }

  private handleMessage(message: EventSourceMessage) {
    this.emit(message.event || 'message', message.data, message.id)
  }

  private retryDelay() {
    const exponential = Math.min(1_000 * 2 ** Math.min(this.retryAttempt, 4), 15_000)
    this.retryAttempt += 1
    return exponential + Math.floor(Math.random() * 250)
  }

  private async connect() {
    try {
      await fetchEventSource(this.url, {
        method: 'GET',
        credentials: 'include',
        signal: this.controller.signal,
        openWhenHidden: true,
        fetch: this.authorizedFetch,
        onopen: (response) => this.handleOpen(response),
        onmessage: (message) => this.handleMessage(message),
        onclose: () => {
          if (!this.closed) throw new RetriableStreamError('事件流已断开，正在重新连接')
        },
        onerror: (error) => {
          if (this.closed) throw error
          const fatal = error instanceof FatalStreamError
          this.emitError(error, fatal)
          if (fatal) throw error
          return error instanceof RetriableStreamError && error.status === 401 ? 0 : this.retryDelay()
        },
      })
    } catch (error) {
      if (!this.closed && !(error instanceof FatalStreamError)) this.emitError(error, true)
    }
  }
}

export const createAgentRunEventStream = (url: string): AgentRunEventStream =>
  new FetchAgentRunEventStream(url)
