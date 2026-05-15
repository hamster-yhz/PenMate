import { describe, expect, it, vi } from 'vitest'
import { createTaskRuntime } from './useWorkbenchTaskRuntime'

type RuntimeEventListener = (event: MessageEvent<string>) => void

const emitRuntimeEvent = (listener: RuntimeEventListener | undefined, payload: Record<string, unknown>) => {
  listener?.({ data: JSON.stringify(payload) } as MessageEvent<string>)
}

const createRuntimeHarness = () => {
  const listeners = new Map<string, RuntimeEventListener>()
  const stream = { close: vi.fn() } as unknown as EventSource
  const setRuntimeEventSource = vi.fn()
  const onToken = vi.fn()
  const onToolCall = vi.fn()
  const onWaitingApproval = vi.fn()

  const runtime = createTaskRuntime({
    getGenerationTaskStatus: () => 'running',
    setGenerationTaskStatus: vi.fn(),
    setAgentStatusDetailText: vi.fn(),
    getGenerationPhase: () => 'streaming',
    setGenerationPhase: vi.fn(),
    getGenerationStream: () => null,
    setGenerationStream: vi.fn(),
    openGenerationStream: vi.fn(() => stream),
    addStreamListener: vi.fn((_: EventSource, eventName: string, listener: RuntimeEventListener) => {
      listeners.set(eventName, listener)
    }),
    closeGenerationStream: vi.fn(),
    scrollChat: vi.fn(),
    setRuntimeEventSource,
    onToken,
    onToolCall,
    onWaitingApproval,
  } as any)

  return {
    runtime,
    listeners,
    setRuntimeEventSource,
    onToken,
    onToolCall,
    onWaitingApproval,
  }
}

describe('createTaskRuntime', () => {
  it('publishes_structured_runtime_source_for_generation_protocol_events', async () => {
    const { runtime, listeners, setRuntimeEventSource } = createRuntimeHarness()

    const consumePromise = runtime.consumeGenerationStream('101', '90001', '50001')

    emitRuntimeEvent(listeners.get('generation.started'), {
      taskId: '70001',
      sessionId: '90001',
      turnId: '50001',
      phase: 'planning',
      message: '正在分析请求',
      recoverable: true,
      nextAction: 'plan_chapter',
    })
    emitRuntimeEvent(listeners.get('generation.status'), {
      taskId: '70001',
      sessionId: '90001',
      turnId: '50001',
      phase: 'quality_review',
      message: '正在审查质量',
      toolCall: {
        toolCallId: 'call-quality-1',
        toolCode: 'quality_review',
        toolName: '质量审查',
        status: 'running',
        iteration: 1,
        argumentsPreview: '{"chapterId":"301"}',
        output: '{"reviewSummary":"存在剧情逻辑问题"}',
        errorMessage: '',
      },
      recoverable: true,
      nextAction: 'wait_tool_result',
    })
    emitRuntimeEvent(listeners.get('generation.tool_call'), {
      taskId: '70001',
      sessionId: '90001',
      turnId: '50001',
      phase: 'todo_review',
      message: '正在整理待办',
      toolCall: {
        toolCallId: 'call-todo-1',
        toolCode: 'todo_planner',
        toolName: 'Todo 规划',
        status: 'running',
        iteration: 2,
        argumentsPreview: '{"planningMode":"FOLLOW_UP_MODIFICATION"}',
        output: '{"planTitle":"第三章修订待办"}',
        errorMessage: '',
      },
      recoverable: true,
      nextAction: 'review_todo_plan',
    })
    emitRuntimeEvent(listeners.get('generation.waiting_approval'), {
      taskId: '70001',
      sessionId: '90001',
      turnId: '50001',
      phase: 'waiting_approval',
      message: '故事圣经更新待确认',
      approval: {
        approvalId: '88001',
        approvalType: 'STORY_BIBLE_UPDATE',
        toolCallId: 'call-story-bible-1',
        nextAction: 'await_approval',
      },
      toolCall: {
        toolCallId: 'call-story-bible-1',
        toolCode: 'story_bible_update',
        toolName: '故事圣经整理',
        status: 'waiting_approval',
        iteration: 3,
        argumentsPreview: '{"chapterId":"301"}',
        output: '{"proposalSummary":"建议补充侍从知晓密令的设定"}',
        errorMessage: '',
      },
      recoverable: true,
      nextAction: 'await_approval',
    })
    emitRuntimeEvent(listeners.get('generation.done'), {
      taskId: '70001',
      sessionId: '90001',
      turnId: '50001',
      phase: 'done',
      message: '已完成',
      recoverable: true,
      nextAction: 'view_result',
      status: 'done',
    })

    await expect(consumePromise).resolves.toBe('done')

    expect(setRuntimeEventSource).toHaveBeenCalledWith(expect.objectContaining({
      eventName: 'generation.started',
      phase: 'planning',
      message: '正在分析请求',
      nextAction: 'plan_chapter',
    }))
    expect(setRuntimeEventSource).toHaveBeenCalledWith(expect.objectContaining({
      eventName: 'generation.status',
      phase: 'quality_review',
      message: '正在审查质量',
      nextAction: 'wait_tool_result',
      toolCall: expect.objectContaining({
        toolCode: 'quality_review',
        toolName: '质量审查',
      }),
    }))
    expect(setRuntimeEventSource).toHaveBeenCalledWith(expect.objectContaining({
      eventName: 'generation.tool_call',
      phase: 'todo_review',
      message: '正在整理待办',
      toolCall: expect.objectContaining({
        toolCode: 'todo_planner',
        toolName: 'Todo 规划',
      }),
    }))
    expect(setRuntimeEventSource).toHaveBeenCalledWith(expect.objectContaining({
      eventName: 'generation.waiting_approval',
      phase: 'waiting_approval',
      message: '故事圣经更新待确认',
      approval: expect.objectContaining({
        approvalId: '88001',
        approvalType: 'STORY_BIBLE_UPDATE',
      }),
    }))
    expect(setRuntimeEventSource).toHaveBeenCalledWith(expect.objectContaining({
      eventName: 'generation.done',
      phase: 'done',
      message: '已完成',
      nextAction: 'view_result',
    }))
  })

  it('keeps_chat_projection_outside_runtime_and_emits_callbacks_instead_of_mutating_assistant_state', async () => {
    const { runtime, listeners, onToken, onToolCall, onWaitingApproval } = createRuntimeHarness()

    const consumePromise = runtime.consumeGenerationStream('101', '90001', '50003')

    emitRuntimeEvent(listeners.get('generation.token'), {
      token: '第一段正文',
    })
    emitRuntimeEvent(listeners.get('generation.tool_call'), {
      taskId: '70003',
      phase: 'todo_review',
      message: '正在整理待办',
      toolCall: {
        toolCallId: 'call-todo-3',
        toolCode: 'todo_planner',
        toolName: 'Todo 规划',
        status: 'running',
      },
      nextAction: 'review_todo_plan',
    })
    emitRuntimeEvent(listeners.get('generation.waiting_approval'), {
      taskId: '70003',
      phase: 'waiting_approval',
      message: '故事圣经更新待确认',
      approval: {
        approvalId: '88003',
        approvalType: 'STORY_BIBLE_UPDATE',
        toolCallId: 'call-story-3',
      },
      toolCall: {
        toolCallId: 'call-story-3',
        toolCode: 'story_bible_update',
        toolName: '故事圣经整理',
        status: 'waiting_approval',
      },
      nextAction: 'await_approval',
    })
    emitRuntimeEvent(listeners.get('generation.done'), {
      taskId: '70003',
      phase: 'done',
      status: 'done',
    })

    await expect(consumePromise).resolves.toBe('done')
    expect(onToken).toHaveBeenCalledWith('第一段正文')
    expect(onToolCall).toHaveBeenCalledWith(expect.objectContaining({
      taskId: '70003',
      nextAction: 'review_todo_plan',
    }))
    expect(onWaitingApproval).toHaveBeenCalledWith(expect.objectContaining({
      taskId: '70003',
      approval: expect.objectContaining({ approvalId: '88003' }),
    }))
  })

  it('publishes_failure_reason_and_stops_exposing_legacy_polling_fallback', async () => {
    const { runtime, listeners, setRuntimeEventSource } = createRuntimeHarness()

    const consumePromise = runtime.consumeGenerationStream('101', '90001', '50002')

    emitRuntimeEvent(listeners.get('generation.failed'), {
      taskId: '70002',
      sessionId: '90001',
      turnId: '50002',
      phase: 'failed',
      message: '执行失败',
      errorMsg: '质量审查超时',
      recoverable: true,
      nextAction: 'retry_generation',
    })

    await expect(consumePromise).rejects.toThrow('质量审查超时')
    expect(setRuntimeEventSource).toHaveBeenCalledWith(expect.objectContaining({
      eventName: 'generation.failed',
      phase: 'failed',
      message: '执行失败',
      errorMsg: '质量审查超时',
      nextAction: 'retry_generation',
    }))
    expect(Object.prototype.hasOwnProperty.call(runtime, 'pollGenerationAsFallback')).toBe(false)
  })
})
