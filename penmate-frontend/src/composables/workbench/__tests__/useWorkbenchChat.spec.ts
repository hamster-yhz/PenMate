import { describe, expect, it, vi } from 'vitest'

type ChatMessage = {
  id: number | string
  role: 'user' | 'assistant' | 'system'
  text: string
  toolCallId?: string
  approval?: {
    id: string
    message: string
    time: string
    resolved: boolean
    resolvedAction?: 'approved' | 'rejected'
  }
}

type ConversationItem = {
  conversationId: string
  title: string
  updatedAt: string
}

type UseWorkbenchChatFactory = (deps: any) => {
  messages: { value: ChatMessage[] }
  chatInput: { value: string }
  isGenerating: { value: boolean }
  generationPhase: { value: 'idle' | 'preparing' | 'streaming' | 'waiting_approval' | 'failed' }
  generationTaskStatus: { value: string }
  generationStatusText: { value: string }
  streamingAssistantMsgId: { value: number | null }
  conversationList: { value: ConversationItem[] }
  currentConversationId: { value: string | null }
  conversationLoading: { value: boolean }
  showConversationPanel: { value: boolean }
  currentModelName: { value: string }
  loadConversationList: (projectId: string) => Promise<void>
  loadConversationHistory: (projectId: string, operatorId: string) => Promise<void>
  selectConversation: (conversationId: string) => Promise<void>
  sendMessage: () => Promise<void>
  resumeRunningTask: (projectId: string, sessionId: string, turnId: string) => Promise<void>
  hydrateFromRecoverySnapshot: (snapshot: Record<string, unknown> | null | undefined) => void
  toggleConversationPanel: () => Promise<void>
}

const loadUseWorkbenchChat = async (): Promise<UseWorkbenchChatFactory> => {
  const modulePath = '../useWorkbenchChat'
  const actual = (await import(/* @vite-ignore */ modulePath)).useWorkbenchChat as UseWorkbenchChatFactory
  return ((rawDeps: Record<string, any>) => {
    const deps = { ...rawDeps }

    if (!deps.listSessions && deps.listConversations) {
      deps.listSessions = deps.listConversations
    }

    if (!deps.listSessions) {
      deps.listSessions = vi.fn().mockResolvedValue([])
    }

    if (!deps.getSessionRecovery && deps.listMessages) {
      deps.getSessionRecovery = async (projectId: string, sessionId: string) => ({
        session: { sessionId },
        messages: await deps.listMessages(projectId, sessionId),
      })
    }

    if (!deps.createSession) {
      deps.createSession = vi.fn().mockResolvedValue({ sessionId: '90001', title: '新会话', status: 'ACTIVE' })
    }

    if (!deps.createTurn && deps.createGeneration) {
      deps.createTurn = async (projectId: string, sessionId: string, payload: Record<string, unknown>) => {
        const result = await deps.createGeneration(projectId, payload.operatorId, payload)
        const resultRecord = (result || {}) as Record<string, unknown>
        const activeTaskRecord = (resultRecord.activeTask || {}) as Record<string, unknown>
        const resolvedTaskId = activeTaskRecord.taskId ?? resultRecord.taskId
        const resolvedTurnId = activeTaskRecord.turnId ?? resultRecord.turnId ?? resolvedTaskId
        return {
          ...resultRecord,
          activeTask: resultRecord.activeTask ?? {
            turnId: resolvedTurnId,
            taskId: resolvedTaskId,
            taskStatus: resultRecord.status,
          },
          session: resultRecord.session ?? {
            sessionId,
            title: '会话',
            status: 'ACTIVE',
            boundStyle: null,
          },
        }
      }
    }

    if (!deps.getTask && deps.getGeneration) {
      deps.getTask = deps.getGeneration
    }

    if (!deps.closeTaskStream && deps.closeGenerationStream) {
      deps.closeTaskStream = deps.closeGenerationStream
    }

    return actual(deps)
  }) as UseWorkbenchChatFactory
}

const flushPromises = async (times = 8) => {
  for (let i = 0; i < times; i += 1) {
    await Promise.resolve()
  }
}

describe('useWorkbenchChat', () => {
  it('loads_conversation_list_and_normalizes_items', async () => {
    const useWorkbenchChat = await loadUseWorkbenchChat()
    const listSessions = vi.fn().mockResolvedValue([
      { sessionId: '81', title: '第一轮', updatedAt: '2026-04-26 20:00:00' },
      { sessionId: '', title: 'invalid', updatedAt: 'ignored' },
      { sessionId: '82', title: '', createdAt: '2026-04-26 20:02:00' },
    ])

    const chat = useWorkbenchChat({
      getContext: () => ({ projectId: '101', operatorId: '201' }),
      getCurrentProjectId: () => '101',
      getActiveChapterKey: () => '301',
      getActivePlugins: () => ['outline.search'],
      ensureModelConfigId: vi.fn(),
      refreshActiveModelInfo: vi.fn(),
      listSessions,
      createSession: vi.fn(),
      getSessionRecovery: vi.fn(),
      createTurn: vi.fn(),
      getTask: vi.fn(),
      openTurnStream: vi.fn(),
      addStreamListener: vi.fn(),
      closeTaskStream: vi.fn(),
      revealAssistantText: vi.fn(),
      scrollChat: vi.fn(),
      nextTick: async () => undefined,
      notifyWarning: vi.fn(),
      debugChatState: vi.fn(),
      onRequireModelSelection: vi.fn(),
      enablePollingFallback: false,
    })

    await chat.loadConversationList('101')

    expect(listSessions).toHaveBeenCalledWith('101')
    expect(chat.conversationList.value).toEqual([
      { conversationId: '81', title: '第一轮', updatedAt: '2026-04-26 20:00:00' },
      { conversationId: '82', title: '', updatedAt: '2026-04-26 20:02:00' },
    ])
    expect(chat.conversationLoading.value).toBe(false)
  })

  it('does_not_accept_conversation_id_fallback_when_listing_sessions', async () => {
    const useWorkbenchChat = await loadUseWorkbenchChat()
    const listSessions = vi.fn().mockResolvedValue([
      { conversationId: 'legacy-81', title: '旧会话', updatedAt: '2026-04-26 20:00:00' },
    ])

    const chat = useWorkbenchChat({
      getContext: () => ({ projectId: '101', operatorId: '201' }),
      getCurrentProjectId: () => '101',
      getActiveChapterKey: () => '301',
      getActivePlugins: () => [],
      ensureModelConfigId: vi.fn(),
      refreshActiveModelInfo: vi.fn(),
      listSessions,
      createSession: vi.fn(),
      getSessionRecovery: vi.fn(),
      createTurn: vi.fn(),
      getTask: vi.fn(),
      openTurnStream: vi.fn(),
      addStreamListener: vi.fn(),
      closeTaskStream: vi.fn(),
      revealAssistantText: vi.fn(),
      scrollChat: vi.fn(),
      nextTick: async () => undefined,
      notifyWarning: vi.fn(),
      debugChatState: vi.fn(),
      onRequireModelSelection: vi.fn(),
      enablePollingFallback: false,
    })

    await chat.loadConversationList('101')

    expect(chat.conversationList.value).toEqual([])
  })

  it('loads_history_from_latest_session_recovery_and_maps_messages', async () => {
    const useWorkbenchChat = await loadUseWorkbenchChat()
    const listSessions = vi.fn().mockResolvedValue([
      { sessionId: '88', title: '当前会话', updatedAt: '2026-04-26 20:03:00' },
    ])
    const getSessionRecovery = vi.fn().mockResolvedValue({
      session: { sessionId: '88' },
      messages: [
        {
          messageId: 9,
          role: 'assistant',
          contentMd: '你好\n世界',
          approvalId: 42,
          approvalType: 'WORLD_SETTING_CREATE',
          approvalStatus: 'pending',
        },
        { messageId: 10, role: 'tool', contentMd: 'tool output' },
      ],
    })
    const scrollChat = vi.fn()

    const chat = useWorkbenchChat({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      getCurrentProjectId: () => 101,
      getActiveChapterKey: () => '301',
      getActivePlugins: () => [],
      ensureModelConfigId: vi.fn(),
      refreshActiveModelInfo: vi.fn(),
      listSessions,
      createSession: vi.fn(),
      getSessionRecovery,
      createTurn: vi.fn(),
      getTask: vi.fn(),
      openTurnStream: vi.fn(),
      addStreamListener: vi.fn(),
      closeTaskStream: vi.fn(),
      revealAssistantText: vi.fn(),
      scrollChat,
      nextTick: async () => undefined,
      notifyWarning: vi.fn(),
      debugChatState: vi.fn(),
      onRequireModelSelection: vi.fn(),
      enablePollingFallback: false,
    })

    await chat.loadConversationHistory('101', '201')

    expect(listSessions).toHaveBeenCalledWith('101')
    expect(getSessionRecovery).toHaveBeenCalledWith('101', '88')
    expect(chat.currentConversationId.value).toBe('88')
    expect(chat.messages.value).toEqual([
      {
        id: '9',
        role: 'assistant',
        text: '你好<br/>世界',
        approval: {
          id: '42',
          message: '检测到待审批变更（WORLD_SETTING_CREATE）',
          time: '',
          resolved: false,
          preview: undefined,
        },
      },
      { id: '10', role: 'system', text: 'tool output' },
    ])
  })

  it('keeps_oversized_message_and_approval_ids_as_strings_when_loading_history', async () => {
    const useWorkbenchChat = await loadUseWorkbenchChat()
    const oversizedMessageId = '90071992547409931234'
    const oversizedApprovalId = '90071992547409939876'
    const listSessions = vi.fn().mockResolvedValue([
      { sessionId: '88', title: '当前会话', updatedAt: '2026-04-26 20:03:00' },
    ])
    const getSessionRecovery = vi.fn().mockResolvedValue({
      session: { sessionId: '88' },
      messages: [
        {
          messageId: oversizedMessageId,
          role: 'assistant',
          contentMd: '你好\n世界',
          approvalId: oversizedApprovalId,
          approvalType: 'WORLD_SETTING_CREATE',
          approvalStatus: 'pending',
        },
      ],
    })

    const chat = useWorkbenchChat({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      getCurrentProjectId: () => 101,
      getActiveChapterKey: () => '301',
      getActivePlugins: () => [],
      ensureModelConfigId: vi.fn(),
      refreshActiveModelInfo: vi.fn(),
      listSessions,
      createSession: vi.fn(),
      getSessionRecovery,
      createTurn: vi.fn(),
      getTask: vi.fn(),
      openTurnStream: vi.fn(),
      addStreamListener: vi.fn(),
      closeTaskStream: vi.fn(),
      revealAssistantText: vi.fn(),
      scrollChat: vi.fn(),
      nextTick: async () => undefined,
      notifyWarning: vi.fn(),
      debugChatState: vi.fn(),
      onRequireModelSelection: vi.fn(),
      enablePollingFallback: false,
    })

    await chat.loadConversationHistory('101', '201')

    expect(chat.messages.value).toEqual([
      {
        id: oversizedMessageId,
        role: 'assistant',
        text: '你好<br/>世界',
        approval: {
          id: oversizedApprovalId,
          message: '检测到待审批变更（WORLD_SETTING_CREATE）',
          time: '',
          resolved: false,
        },
      },
    ])
  })

  it('requires_model_selection_before_generation_when_no_model_available', async () => {
    const useWorkbenchChat = await loadUseWorkbenchChat()
    const onRequireModelSelection = vi.fn()
    const debugChatState = vi.fn()
    const nextTick = vi.fn(async () => undefined)
    const scrollChat = vi.fn()

    const chat = useWorkbenchChat({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      getCurrentProjectId: () => 101,
      getActiveChapterKey: () => '301',
      getActivePlugins: () => ['outline.search'],
      ensureConversationId: vi.fn().mockResolvedValue(77),
      ensureModelConfigId: vi.fn().mockResolvedValue(null),
      refreshActiveModelInfo: vi.fn(),
      listConversations: vi.fn(),
      listMessages: vi.fn(),
      createMessage: vi.fn().mockResolvedValue({}),
      createGeneration: vi.fn(),
      getGeneration: vi.fn(),
      openTurnStream: vi.fn(),
      addStreamListener: vi.fn(),
      closeTaskStream: vi.fn(),
      revealAssistantText: vi.fn(),
      scrollChat,
      nextTick,
      notifyWarning: vi.fn(),
      debugChatState,
      onRequireModelSelection,
      enablePollingFallback: false,
    })

    chat.chatInput.value = '继续写第三章'
    await chat.sendMessage()

    expect(onRequireModelSelection).toHaveBeenCalledTimes(1)
    expect(chat.messages.value[0]).toEqual({ id: 1, role: 'user', text: '继续写第三章' })
    expect(chat.messages.value[1]).toEqual({
      id: 2,
      role: 'assistant',
      text: '生成失败：未选择可用模型，请先在模型设置中保存并切换模型',
    })
    expect(chat.isGenerating.value).toBe(false)
    expect(chat.generationPhase.value).toBe('failed')
    expect(chat.generationTaskStatus.value).toBe('failed')
    expect(chat.chatInput.value).toBe('')
    expect(debugChatState).toHaveBeenCalled()
    expect(nextTick).toHaveBeenCalled()
    expect(scrollChat).toHaveBeenCalled()
  })

  it('creates_new_session_before_first_turn_when_no_existing_sessions', async () => {
    const useWorkbenchChat = await loadUseWorkbenchChat()
    const createSession = vi.fn().mockResolvedValue({ sessionId: '99001', title: '新会话', status: 'ACTIVE' })
    const createTurn = vi.fn().mockResolvedValue({
      session: { sessionId: '99001', title: '新会话', status: 'ACTIVE', boundStyle: null },
      activeTask: { taskId: '70009', taskStatus: 'RUNNING' },
      taskType: 'WRITE',
      userMessage: '第一条消息',
    })

    const chat = useWorkbenchChat({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      getCurrentProjectId: () => 101,
      getActiveChapterKey: () => '301',
      getActivePlugins: () => [],
      ensureModelConfigId: vi.fn().mockResolvedValue('mcfg-9001'),
      refreshActiveModelInfo: vi.fn(),
      listSessions: vi.fn().mockResolvedValue([]),
      createSession,
      getSessionRecovery: vi.fn(),
      createTurn,
      getTask: vi.fn().mockResolvedValue({ status: 'done' }),
      openTurnStream: vi.fn(() => {
        throw new Error('stream unavailable')
      }),
      addStreamListener: vi.fn(),
      closeTaskStream: vi.fn(),
      revealAssistantText: vi.fn(),
      scrollChat: vi.fn(),
      nextTick: async () => undefined,
      notifyWarning: vi.fn(),
      debugChatState: vi.fn(),
      onRequireModelSelection: vi.fn(),
      enablePollingFallback: true,
      getGeneration: vi.fn().mockResolvedValue({ status: 'done' }),
    })

    chat.chatInput.value = '第一条消息'
    await chat.sendMessage()

    expect(createSession).toHaveBeenCalledWith(101, expect.objectContaining({ userId: 201 }))
    expect(createTurn).toHaveBeenCalledWith(101, '99001', expect.objectContaining({
      operatorId: 201,
      userMessage: '第一条消息',
      taskRequest: expect.objectContaining({ chapterId: '301' }),
    }))
    expect(chat.currentConversationId.value).toBe('99001')
  })

  it('keeps_business_ids_as_strings_when_creating_and_sending_agent_turn', async () => {
    const useWorkbenchChat = await loadUseWorkbenchChat()
    const createSession = vi.fn().mockResolvedValue({ sessionId: 'session-99001', title: '新会话', status: 'ACTIVE' })
    const createTurn = vi.fn().mockResolvedValue({
      session: { sessionId: 'session-99001', title: '新会话', status: 'ACTIVE', boundStyle: null },
      activeTask: { taskId: 'task-70009', taskStatus: 'RUNNING' },
      taskType: 'WRITE',
      userMessage: '第一条消息',
    })

    const chat = useWorkbenchChat({
      getContext: () => ({ projectId: 'novel-101', operatorId: 'user-201' }),
      getCurrentProjectId: () => 'novel-101',
      getActiveChapterKey: () => 'chapter-301',
      getActivePlugins: () => [],
      ensureModelConfigId: vi.fn().mockResolvedValue('mcfg-9001'),
      refreshActiveModelInfo: vi.fn(),
      listSessions: vi.fn().mockResolvedValue([]),
      createSession,
      getSessionRecovery: vi.fn(),
      createTurn,
      getTask: vi.fn().mockResolvedValue({ status: 'done' }),
      openTurnStream: vi.fn(() => {
        throw new Error('stream unavailable')
      }),
      addStreamListener: vi.fn(),
      closeTaskStream: vi.fn(),
      revealAssistantText: vi.fn(),
      scrollChat: vi.fn(),
      nextTick: async () => undefined,
      notifyWarning: vi.fn(),
      debugChatState: vi.fn(),
      onRequireModelSelection: vi.fn(),
      enablePollingFallback: true,
      getGeneration: vi.fn().mockResolvedValue({ status: 'done' }),
    })

    chat.chatInput.value = '第一条消息'
    await chat.sendMessage()

    expect(createSession).toHaveBeenCalledWith('novel-101', expect.objectContaining({ userId: 'user-201' }))
    expect(createTurn).toHaveBeenCalledWith('novel-101', 'session-99001', expect.objectContaining({
      operatorId: 'user-201',
      userMessage: '第一条消息',
      taskRequest: expect.objectContaining({
        chapterId: 'chapter-301',
        modelConfigId: 'mcfg-9001',
      }),
    }))
    expect(chat.currentConversationId.value).toBe('session-99001')
  })

  it('does_not_fallback_to_latest_history_session_after_explicit_new_session_creation', async () => {
    const useWorkbenchChat = await loadUseWorkbenchChat()
    const createTurn = vi.fn().mockResolvedValue({
      session: { sessionId: '99001', title: '新会话', status: 'ACTIVE', boundStyle: null },
      activeTask: { taskId: '70010', taskStatus: 'RUNNING' },
      taskType: 'WRITE',
      userMessage: '新会话第一条消息',
    })

    const chat = useWorkbenchChat({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      getCurrentProjectId: () => 101,
      getActiveChapterKey: () => '301',
      getActivePlugins: () => [],
      ensureModelConfigId: vi.fn().mockResolvedValue('mcfg-9001'),
      refreshActiveModelInfo: vi.fn(),
      listSessions: vi.fn().mockResolvedValue([
        { sessionId: '88001', title: '历史会话', status: 'ACTIVE' },
      ]),
      createSession: vi.fn(),
      getSessionRecovery: vi.fn().mockResolvedValue({
        session: { sessionId: '99001', title: '新会话', status: 'ACTIVE' },
        messages: [],
      }),
      createTurn,
      getTask: vi.fn().mockResolvedValue({ status: 'done' }),
      openTurnStream: vi.fn(() => {
        throw new Error('stream unavailable')
      }),
      addStreamListener: vi.fn(),
      closeTaskStream: vi.fn(),
      revealAssistantText: vi.fn(),
      scrollChat: vi.fn(),
      nextTick: async () => undefined,
      notifyWarning: vi.fn(),
      debugChatState: vi.fn(),
      onRequireModelSelection: vi.fn(),
      enablePollingFallback: true,
      getGeneration: vi.fn().mockResolvedValue({ status: 'done' }),
    })

    await chat.selectConversation('99001')
    chat.currentConversationId.value = null
    chat.chatInput.value = '新会话第一条消息'
    await chat.sendMessage()

    expect(createTurn).toHaveBeenCalledWith(101, '99001', expect.objectContaining({
      operatorId: 201,
      userMessage: '新会话第一条消息',
      taskRequest: expect.objectContaining({ chapterId: '301' }),
    }))
  })

  it('escapes_optimistic_user_message_before_rendering_html', async () => {
    const useWorkbenchChat = await loadUseWorkbenchChat()

    const chat = useWorkbenchChat({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      getCurrentProjectId: () => 101,
      getActiveChapterKey: () => '301',
      getActivePlugins: () => [],
      ensureConversationId: vi.fn().mockResolvedValue(77),
      ensureModelConfigId: vi.fn().mockResolvedValue(null),
      refreshActiveModelInfo: vi.fn(),
      listConversations: vi.fn(),
      listMessages: vi.fn(),
      createMessage: vi.fn().mockResolvedValue({}),
      createGeneration: vi.fn(),
      getGeneration: vi.fn(),
      openTurnStream: vi.fn(),
      addStreamListener: vi.fn(),
      closeTaskStream: vi.fn(),
      revealAssistantText: vi.fn(),
      scrollChat: vi.fn(),
      nextTick: async () => undefined,
      notifyWarning: vi.fn(),
      debugChatState: vi.fn(),
      onRequireModelSelection: vi.fn(),
      enablePollingFallback: false,
    })

    chat.chatInput.value = '<img src=x onerror=alert(1)>\n第二行'
    await chat.sendMessage()

    expect(chat.messages.value[0]).toEqual({
      id: 1,
      role: 'user',
      text: '&' + 'lt;img src=x onerror=alert(1)&' + 'gt;<br/>第二行',
    })
  })

  it('attaches_approval_card_when_generation_enters_waiting_approval', async () => {
    const useWorkbenchChat = await loadUseWorkbenchChat()
    const listeners = new Map<string, (event: MessageEvent<string>) => void>()
    const addStreamListener = vi.fn((_: EventSource, eventName: string, listener: (event: MessageEvent<string>) => void) => {
      listeners.set(eventName, listener)
    })
    const stream = { close: vi.fn() } as unknown as EventSource

    const chat = useWorkbenchChat({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      getCurrentProjectId: () => 101,
      getActiveChapterKey: () => '301',
      getActivePlugins: () => ['outline.search'],
      ensureConversationId: vi.fn().mockResolvedValue(77),
      ensureModelConfigId: vi.fn().mockResolvedValue('mcfg-oversized-205172327654749798400000-501'),
      refreshActiveModelInfo: vi.fn(),
      listConversations: vi.fn(),
      listMessages: vi.fn(),
      createMessage: vi.fn().mockResolvedValue({}),
      createGeneration: vi.fn().mockResolvedValue({ taskId: 9001, status: 'running' }),
      getGeneration: vi.fn(),
      openTurnStream: vi.fn().mockReturnValue(stream),
      addStreamListener,
      closeTaskStream: vi.fn(),
      revealAssistantText: vi.fn(),
      scrollChat: vi.fn(),
      nextTick: async () => undefined,
      notifyWarning: vi.fn(),
      debugChatState: vi.fn(),
      onRequireModelSelection: vi.fn(),
      enablePollingFallback: false,
    })

    chat.chatInput.value = '创建新的世界观设定'
    const sendPromise = chat.sendMessage()
    await flushPromises(20)

    listeners.get('generation.started')?.({ data: '{}' } as MessageEvent<string>)
    listeners.get('generation.waiting_approval')?.({
      data: JSON.stringify({
        approvalId: 42,
        approvalType: 'WORLD_SETTING_CREATE',
        toolCode: 'book_crud',
        toolDisplayName: '书籍 CRUD',
        riskLevel: 2,
        operationCode: 'delete',
        status: 'waiting_approval',
      }),
    } as MessageEvent<string>)
    listeners.get('generation.done')?.({ data: JSON.stringify({ status: 'done' }) } as MessageEvent<string>)

    await sendPromise

    expect(chat.messages.value[1]).toMatchObject({
      id: 2,
      role: 'assistant',
      approval: {
        id: '42',
        message: '检测到待审批工具变更（书籍 CRUD）',
        time: '',
        toolCode: 'book_crud',
        toolDisplayName: '书籍 CRUD',
        riskLevel: 2,
        operationCode: 'delete',
        resolved: false,
      },
    })
  })

  it('links_tool_call_metadata_onto_waiting_approval_assistant_message', async () => {
    const useWorkbenchChat = await loadUseWorkbenchChat()
    const listeners = new Map<string, (event: MessageEvent<string>) => void>()
    const addStreamListener = vi.fn((_: EventSource, eventName: string, listener: (event: MessageEvent<string>) => void) => {
      listeners.set(eventName, listener)
    })
    const stream = { close: vi.fn() } as unknown as EventSource

    const chat = useWorkbenchChat({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      getCurrentProjectId: () => 101,
      getActiveChapterKey: () => '301',
      getActivePlugins: () => ['outline.search'],
      ensureConversationId: vi.fn().mockResolvedValue(77),
      ensureModelConfigId: vi.fn().mockResolvedValue('mcfg-oversized-205172327654749798400000-501'),
      refreshActiveModelInfo: vi.fn(),
      listConversations: vi.fn(),
      listMessages: vi.fn(),
      createMessage: vi.fn().mockResolvedValue({}),
      createGeneration: vi.fn().mockResolvedValue({ taskId: 9016, status: 'running' }),
      getGeneration: vi.fn(),
      openTurnStream: vi.fn().mockReturnValue(stream),
      addStreamListener,
      closeTaskStream: vi.fn(),
      revealAssistantText: vi.fn(),
      scrollChat: vi.fn(),
      nextTick: async () => undefined,
      notifyWarning: vi.fn(),
      debugChatState: vi.fn(),
      onRequireModelSelection: vi.fn(),
      enablePollingFallback: false,
    })

    chat.chatInput.value = '创建新的世界观设定'
    const sendPromise = chat.sendMessage()
    await flushPromises(20)

    listeners.get('generation.started')?.({ data: '{}' } as MessageEvent<string>)
    listeners.get('generation.tool_call')?.({
      data: JSON.stringify({
        taskId: 9016,
        toolCallId: 'call_9',
        pluginCode: 'book_crud',
        toolName: 'delete_book',
        status: 'waiting_approval',
      }),
    } as MessageEvent<string>)
    listeners.get('generation.waiting_approval')?.({
      data: JSON.stringify({
        approvalId: 42,
        approvalType: 'WORLD_SETTING_CREATE',
        toolCode: 'book_crud',
        toolDisplayName: '书籍 CRUD',
        riskLevel: 2,
        operationCode: 'delete',
        status: 'waiting_approval',
      }),
    } as MessageEvent<string>)
    listeners.get('generation.done')?.({ data: JSON.stringify({ status: 'done' }) } as MessageEvent<string>)

    await sendPromise

    expect(chat.messages.value[1]).toMatchObject({
      id: 2,
      role: 'assistant',
      toolCallId: 'call_9',
      approval: {
        id: '42',
        message: '检测到待审批工具变更（书籍 CRUD）',
        time: '',
        toolCode: 'book_crud',
        toolDisplayName: '书籍 CRUD',
        riskLevel: 2,
        operationCode: 'delete',
        resolved: false,
      },
    })
  })

  it('prefers_waiting_approval_tool_call_id_over_stale_tool_call_metadata', async () => {
    const useWorkbenchChat = await loadUseWorkbenchChat()
    const listeners = new Map<string, (event: MessageEvent<string>) => void>()
    const addStreamListener = vi.fn((_: EventSource, eventName: string, listener: (event: MessageEvent<string>) => void) => {
      listeners.set(eventName, listener)
    })
    const stream = { close: vi.fn() } as unknown as EventSource

    const chat = useWorkbenchChat({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      getCurrentProjectId: () => 101,
      getActiveChapterKey: () => '301',
      getActivePlugins: () => ['outline.search'],
      ensureConversationId: vi.fn().mockResolvedValue(77),
      ensureModelConfigId: vi.fn().mockResolvedValue('mcfg-oversized-205172327654749798400000-501'),
      refreshActiveModelInfo: vi.fn(),
      listConversations: vi.fn(),
      listMessages: vi.fn(),
      createMessage: vi.fn().mockResolvedValue({}),
      createGeneration: vi.fn().mockResolvedValue({ taskId: 9017, status: 'running' }),
      getGeneration: vi.fn(),
      openTurnStream: vi.fn().mockReturnValue(stream),
      addStreamListener,
      closeTaskStream: vi.fn(),
      revealAssistantText: vi.fn(),
      scrollChat: vi.fn(),
      nextTick: async () => undefined,
      notifyWarning: vi.fn(),
      debugChatState: vi.fn(),
      onRequireModelSelection: vi.fn(),
      enablePollingFallback: false,
    })

    chat.chatInput.value = '创建新的世界观设定'
    const sendPromise = chat.sendMessage()
    await flushPromises(20)

    listeners.get('generation.started')?.({ data: '{}' } as MessageEvent<string>)
    listeners.get('generation.tool_call')?.({
      data: JSON.stringify({
        taskId: 9017,
        toolCallId: 'stale_call',
        pluginCode: 'book_crud',
        toolName: 'delete_book',
        status: 'waiting_approval',
      }),
    } as MessageEvent<string>)
    listeners.get('generation.waiting_approval')?.({
      data: JSON.stringify({
        toolCallId: 'call_17',
        approvalId: 42,
        approvalType: 'WORLD_SETTING_CREATE',
        toolCode: 'book_crud',
        toolDisplayName: '书籍 CRUD',
        riskLevel: 2,
        operationCode: 'delete',
        status: 'waiting_approval',
      }),
    } as MessageEvent<string>)
    listeners.get('generation.done')?.({ data: JSON.stringify({ status: 'done' }) } as MessageEvent<string>)

    await sendPromise

    expect(chat.messages.value[1]).toMatchObject({
      id: 2,
      role: 'assistant',
      toolCallId: 'call_17',
      approval: {
        id: '42',
        message: '检测到待审批工具变更（书籍 CRUD）',
        time: '',
        toolCode: 'book_crud',
        toolDisplayName: '书籍 CRUD',
        riskLevel: 2,
        operationCode: 'delete',
        resolved: false,
      },
    })
  })

  it('preserves_oversized_task_id_when_opening_stream_after_send_message', async () => {
    const useWorkbenchChat = await loadUseWorkbenchChat()
    const listeners = new Map<string, (event: MessageEvent<string>) => void>()
    const addStreamListener = vi.fn((_: EventSource, eventName: string, listener: (event: MessageEvent<string>) => void) => {
      listeners.set(eventName, listener)
    })
    const stream = { close: vi.fn() } as unknown as EventSource
    const oversizedTaskId = '90071992547409931234'
    const oversizedTurnId = '90071992547409939876'
    const openTurnStream = vi.fn().mockReturnValue(stream)

    const chat = useWorkbenchChat({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      getCurrentProjectId: () => 101,
      getActiveChapterKey: () => '301',
      getActivePlugins: () => ['outline.search'],
      ensureConversationId: vi.fn().mockResolvedValue(77),
      ensureModelConfigId: vi.fn().mockResolvedValue('mcfg-oversized-205172327654749798400000-501'),
      refreshActiveModelInfo: vi.fn(),
      listConversations: vi.fn(),
      listMessages: vi.fn(),
      createMessage: vi.fn().mockResolvedValue({}),
      createGeneration: vi.fn().mockResolvedValue({
        taskId: oversizedTaskId,
        activeTask: { turnId: oversizedTurnId, taskId: oversizedTaskId, taskStatus: 'RUNNING' },
        status: 'running',
      }),
      getGeneration: vi.fn(),
      openTurnStream,
      addStreamListener,
      closeTaskStream: vi.fn(),
      revealAssistantText: vi.fn(),
      scrollChat: vi.fn(),
      nextTick: async () => undefined,
      notifyWarning: vi.fn(),
      debugChatState: vi.fn(),
      onRequireModelSelection: vi.fn(),
      enablePollingFallback: false,
    })

    chat.chatInput.value = '继续生成'
    const sendPromise = chat.sendMessage()
    await flushPromises(20)

    listeners.get('generation.started')?.({ data: '{}' } as MessageEvent<string>)
    listeners.get('generation.done')?.({ data: JSON.stringify({ status: 'done' }) } as MessageEvent<string>)

    await sendPromise

    expect(openTurnStream).toHaveBeenCalledWith(101, '90001', oversizedTurnId)
  })

  it('does_not_append_completion_text_after_waiting_approval_followed_by_done_event', async () => {
    const useWorkbenchChat = await loadUseWorkbenchChat()
    const listeners = new Map<string, (event: MessageEvent<string>) => void>()
    const addStreamListener = vi.fn((_: EventSource, eventName: string, listener: (event: MessageEvent<string>) => void) => {
      listeners.set(eventName, listener)
    })
    const stream = { close: vi.fn() } as unknown as EventSource

    const chat = useWorkbenchChat({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      getCurrentProjectId: () => 101,
      getActiveChapterKey: () => '301',
      getActivePlugins: () => ['outline.search'],
      ensureConversationId: vi.fn().mockResolvedValue(77),
      ensureModelConfigId: vi.fn().mockResolvedValue(501),
      refreshActiveModelInfo: vi.fn(),
      listConversations: vi.fn(),
      listMessages: vi.fn(),
      createMessage: vi.fn().mockResolvedValue({}),
      createGeneration: vi.fn().mockResolvedValue({ taskId: 9005, status: 'running' }),
      getGeneration: vi.fn(),
      openTurnStream: vi.fn().mockReturnValue(stream),
      addStreamListener,
      closeTaskStream: vi.fn(),
      revealAssistantText: vi.fn(),
      scrollChat: vi.fn(),
      nextTick: async () => undefined,
      notifyWarning: vi.fn(),
      debugChatState: vi.fn(),
      onRequireModelSelection: vi.fn(),
      enablePollingFallback: false,
    })

    chat.chatInput.value = '创建新的世界观设定'
    const sendPromise = chat.sendMessage()
    await flushPromises(20)

    listeners.get('generation.started')?.({ data: '{}' } as MessageEvent<string>)
    listeners.get('generation.waiting_approval')?.({
      data: JSON.stringify({ approvalId: 42, approvalType: 'WORLD_SETTING_CREATE', status: 'waiting_approval' }),
    } as MessageEvent<string>)
    listeners.get('generation.done')?.({ data: JSON.stringify({ status: 'done' }) } as MessageEvent<string>)

    await sendPromise

    expect(chat.messages.value[1]).toMatchObject({
      id: 2,
      role: 'assistant',
      text: '',
      approval: {
        id: '42',
        message: '检测到待审批变更（WORLD_SETTING_CREATE）',
        time: '',
        resolved: false,
      },
    })
    expect(chat.messages.value[1].text).not.toContain('生成任务已完成')
  })

  it('keeps_waiting_approval_status_visible_after_stream_finishes_with_pending_approval', async () => {
    const useWorkbenchChat = await loadUseWorkbenchChat()
    const listeners = new Map<string, (event: MessageEvent<string>) => void>()
    const addStreamListener = vi.fn((_: EventSource, eventName: string, listener: (event: MessageEvent<string>) => void) => {
      listeners.set(eventName, listener)
    })
    const stream = { close: vi.fn() } as unknown as EventSource

    const chat = useWorkbenchChat({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      getCurrentProjectId: () => 101,
      getActiveChapterKey: () => '301',
      getActivePlugins: () => ['outline.search'],
      ensureConversationId: vi.fn().mockResolvedValue(77),
      ensureModelConfigId: vi.fn().mockResolvedValue(501),
      refreshActiveModelInfo: vi.fn(),
      listConversations: vi.fn(),
      listMessages: vi.fn(),
      createMessage: vi.fn().mockResolvedValue({}),
      createGeneration: vi.fn().mockResolvedValue({ taskId: 9015, status: 'running' }),
      getGeneration: vi.fn(),
      openTurnStream: vi.fn().mockReturnValue(stream),
      addStreamListener,
      closeTaskStream: vi.fn(),
      revealAssistantText: vi.fn(),
      scrollChat: vi.fn(),
      nextTick: async () => undefined,
      notifyWarning: vi.fn(),
      debugChatState: vi.fn(),
      onRequireModelSelection: vi.fn(),
      enablePollingFallback: false,
    })

    chat.chatInput.value = '创建新的世界观设定'
    const sendPromise = chat.sendMessage()
    await flushPromises(20)

    listeners.get('generation.started')?.({ data: '{}' } as MessageEvent<string>)
    listeners.get('generation.waiting_approval')?.({
      data: JSON.stringify({ approvalId: 42, approvalType: 'WORLD_SETTING_CREATE', status: 'waiting_approval' }),
    } as MessageEvent<string>)
    listeners.get('generation.done')?.({ data: JSON.stringify({ status: 'done' }) } as MessageEvent<string>)

    await sendPromise

    expect(chat.isGenerating.value).toBe(false)
    expect(chat.generationPhase.value).toBe('waiting_approval')
    expect(chat.generationTaskStatus.value).toBe('waiting_approval')
    expect(chat.generationStatusText.value).toBe('等待审批')
  })

  it('waits_between_non_terminal_fallback_polls_before_reaching_waiting_approval', async () => {
    const useWorkbenchChat = await loadUseWorkbenchChat()
    const waitForPolling = vi.fn().mockResolvedValue(undefined)
    const getGeneration = vi.fn()
      .mockResolvedValueOnce({ status: 'running' })
      .mockResolvedValueOnce({ status: 'running' })
      .mockResolvedValueOnce({
        status: 'waiting_approval',
        approvalId: 42,
        approvalType: 'WORLD_SETTING_CREATE',
      })

    const chat = useWorkbenchChat({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      getCurrentProjectId: () => 101,
      getActiveChapterKey: () => '301',
      getActivePlugins: () => ['outline.search'],
      ensureConversationId: vi.fn().mockResolvedValue(77),
      ensureModelConfigId: vi.fn().mockResolvedValue(501),
      refreshActiveModelInfo: vi.fn(),
      listConversations: vi.fn(),
      listMessages: vi.fn(),
      createMessage: vi.fn().mockResolvedValue({}),
      createGeneration: vi.fn().mockResolvedValue({ taskId: 9006, status: 'pending' }),
      getGeneration,
      waitForPolling,
      openTurnStream: vi.fn(() => {
        throw new Error('stream unavailable')
      }),
      addStreamListener: vi.fn(),
      closeTaskStream: vi.fn(),
      revealAssistantText: vi.fn(),
      scrollChat: vi.fn(),
      nextTick: async () => undefined,
      notifyWarning: vi.fn(),
      debugChatState: vi.fn(),
      onRequireModelSelection: vi.fn(),
      enablePollingFallback: true,
    })

    chat.chatInput.value = '创建新的世界观设定'
    await chat.sendMessage()

    expect(getGeneration).toHaveBeenCalledTimes(3)
    expect(waitForPolling).toHaveBeenCalledTimes(2)
    expect(chat.messages.value[1]).toMatchObject({
      id: 2,
      role: 'assistant',
      approval: {
        id: '42',
        message: '检测到待审批变更（WORLD_SETTING_CREATE）',
        time: '',
        resolved: false,
      },
    })
  })

  it('restores_waiting_approval_message_from_recovery_snapshot_without_manual_listMessages', async () => {
    const useWorkbenchChat = await loadUseWorkbenchChat()
    const listMessages = vi.fn()

    const chat = useWorkbenchChat({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      getCurrentProjectId: () => 101,
      getActiveChapterKey: () => '301',
      getActivePlugins: () => ['outline.search'],
      ensureConversationId: vi.fn().mockResolvedValue(77),
      ensureModelConfigId: vi.fn().mockResolvedValue(501),
      refreshActiveModelInfo: vi.fn(),
      listConversations: vi.fn(),
      listMessages,
      createMessage: vi.fn().mockResolvedValue({}),
      createGeneration: vi.fn(),
      getGeneration: vi.fn(),
      openTurnStream: vi.fn(),
      addStreamListener: vi.fn(),
      closeTaskStream: vi.fn(),
      revealAssistantText: vi.fn(),
      scrollChat: vi.fn(),
      nextTick: async () => undefined,
      notifyWarning: vi.fn(),
      debugChatState: vi.fn(),
      onRequireModelSelection: vi.fn(),
      enablePollingFallback: false,
    })

    chat.messages.value = [
      {
        id: 1,
        role: 'assistant',
        text: '',
        approval: {
          id: '60001',
          message: '检测到待审批变更（WORLD_SETTING_CREATE）',
          time: '',
          resolved: false,
        },
      },
    ]

    expect(chat.messages.value[0].approval?.id).toBe('60001')
    expect(listMessages).not.toHaveBeenCalled()
  })

  it('restores_approval_card_from_polling_fallback_without_faking_completion_text', async () => {
    const useWorkbenchChat = await loadUseWorkbenchChat()
    const getGeneration = vi.fn().mockResolvedValue({
      status: 'waiting_approval',
      approvalId: 42,
      approvalType: 'WORLD_SETTING_CREATE',
    })

    const chat = useWorkbenchChat({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      getCurrentProjectId: () => 101,
      getActiveChapterKey: () => '301',
      getActivePlugins: () => ['outline.search'],
      ensureConversationId: vi.fn().mockResolvedValue(77),
      ensureModelConfigId: vi.fn().mockResolvedValue(501),
      refreshActiveModelInfo: vi.fn(),
      listConversations: vi.fn(),
      listMessages: vi.fn(),
      createMessage: vi.fn().mockResolvedValue({}),
      createGeneration: vi.fn().mockResolvedValue({ taskId: 9002, status: 'pending' }),
      getGeneration,
      openTurnStream: vi.fn(() => {
        throw new Error('stream unavailable')
      }),
      addStreamListener: vi.fn(),
      closeTaskStream: vi.fn(),
      revealAssistantText: vi.fn(),
      scrollChat: vi.fn(),
      nextTick: async () => undefined,
      notifyWarning: vi.fn(),
      debugChatState: vi.fn(),
      onRequireModelSelection: vi.fn(),
      enablePollingFallback: true,
    })

    chat.chatInput.value = '创建新的世界观设定'
    await chat.sendMessage()

    expect(getGeneration).toHaveBeenCalledWith(101, '9002')
    expect(chat.messages.value).toHaveLength(2)
    expect(chat.messages.value[1]).toMatchObject({
      id: 2,
      role: 'assistant',
      approval: {
        id: '42',
        message: '检测到待审批变更（WORLD_SETTING_CREATE）',
        time: '',
        resolved: false,
      },
    })
    expect(chat.messages.value[1].text).not.toContain('生成任务已完成')
    expect(chat.isGenerating.value).toBe(false)
    expect(chat.streamingAssistantMsgId.value).toBe(null)
  })

  it('fails_instead_of_faking_completion_when_polling_fallback_never_reaches_terminal_status', async () => {
    const useWorkbenchChat = await loadUseWorkbenchChat()
    const getGeneration = vi.fn().mockResolvedValue({ status: 'running' })

    const chat = useWorkbenchChat({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      getCurrentProjectId: () => 101,
      getActiveChapterKey: () => '301',
      getActivePlugins: () => ['outline.search'],
      ensureConversationId: vi.fn().mockResolvedValue(77),
      ensureModelConfigId: vi.fn().mockResolvedValue(501),
      refreshActiveModelInfo: vi.fn(),
      listConversations: vi.fn(),
      listMessages: vi.fn(),
      createMessage: vi.fn().mockResolvedValue({}),
      createGeneration: vi.fn().mockResolvedValue({ taskId: 9004, status: 'pending' }),
      getGeneration,
      openTurnStream: vi.fn(() => {
        throw new Error('stream unavailable')
      }),
      addStreamListener: vi.fn(),
      closeTaskStream: vi.fn(),
      revealAssistantText: vi.fn(),
      scrollChat: vi.fn(),
      nextTick: async () => undefined,
      notifyWarning: vi.fn(),
      debugChatState: vi.fn(),
      onRequireModelSelection: vi.fn(),
      enablePollingFallback: true,
    })

    chat.chatInput.value = '继续生成'
    await chat.sendMessage()

    expect(getGeneration).toHaveBeenCalledTimes(12)
    expect(chat.messages.value).toEqual([
      { id: 1, role: 'user', text: '继续生成' },
      { id: 2, role: 'assistant', text: '生成失败：生成任务轮询超时，状态：running' },
    ])
    expect(chat.generationPhase.value).toBe('failed')
    expect(chat.generationTaskStatus.value).toBe('failed')
    expect(chat.streamingAssistantMsgId.value).toBe(null)
    expect(chat.isGenerating.value).toBe(false)
  })

  it('reuses_placeholder_assistant_message_when_generation_fails', async () => {
    const useWorkbenchChat = await loadUseWorkbenchChat()
    const listeners = new Map<string, (event: MessageEvent<string>) => void>()
    const addStreamListener = vi.fn((_: EventSource, eventName: string, listener: (event: MessageEvent<string>) => void) => {
      listeners.set(eventName, listener)
    })
    const closeTaskStream = vi.fn()
    const stream = { close: vi.fn() } as unknown as EventSource

    const chat = useWorkbenchChat({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      getCurrentProjectId: () => 101,
      getActiveChapterKey: () => '301',
      getActivePlugins: () => ['outline.search'],
      ensureConversationId: vi.fn().mockResolvedValue(77),
      ensureModelConfigId: vi.fn().mockResolvedValue(501),
      refreshActiveModelInfo: vi.fn(),
      listConversations: vi.fn(),
      listMessages: vi.fn(),
      createMessage: vi.fn().mockResolvedValue({}),
      createGeneration: vi.fn().mockResolvedValue({ taskId: 9003, status: 'running' }),
      getGeneration: vi.fn(),
      openTurnStream: vi.fn().mockReturnValue(stream),
      addStreamListener,
      closeTaskStream,
      revealAssistantText: vi.fn(),
      scrollChat: vi.fn(),
      nextTick: async () => undefined,
      notifyWarning: vi.fn(),
      debugChatState: vi.fn(),
      onRequireModelSelection: vi.fn(),
      enablePollingFallback: false,
    })

    chat.chatInput.value = '继续生成'
    const sendPromise = chat.sendMessage()
    await flushPromises(20)

    listeners.get('generation.started')?.({ data: '{}' } as MessageEvent<string>)
    listeners.get('generation.failed')?.({
      data: JSON.stringify({ errorMsg: '模型限流' }),
    } as MessageEvent<string>)

    await sendPromise

    expect(chat.messages.value).toEqual([
      { id: 1, role: 'user', text: '继续生成' },
      { id: 2, role: 'assistant', text: '生成失败：模型限流' },
    ])
    expect(chat.generationPhase.value).toBe('failed')
    expect(chat.generationTaskStatus.value).toBe('failed')
    expect(chat.streamingAssistantMsgId.value).toBe(null)
    expect(chat.isGenerating.value).toBe(false)
    expect(closeTaskStream).toHaveBeenCalled()
    expect(stream.close).toHaveBeenCalled()
  })

  it('surfaces_string_stream_errors_in_failure_message', async () => {
    const useWorkbenchChat = await loadUseWorkbenchChat()

    const chat = useWorkbenchChat({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      getCurrentProjectId: () => 101,
      getActiveChapterKey: () => '301',
      getActivePlugins: () => ['outline.search'],
      ensureConversationId: vi.fn().mockResolvedValue(77),
      ensureModelConfigId: vi.fn().mockResolvedValue(501),
      refreshActiveModelInfo: vi.fn(),
      listConversations: vi.fn(),
      listMessages: vi.fn(),
      createMessage: vi.fn().mockResolvedValue({}),
      createGeneration: vi.fn().mockResolvedValue({ taskId: 9007, status: 'running' }),
      getGeneration: vi.fn(),
      openTurnStream: vi.fn(() => {
        throw '流服务断开'
      }),
      addStreamListener: vi.fn(),
      closeTaskStream: vi.fn(),
      revealAssistantText: vi.fn(),
      scrollChat: vi.fn(),
      nextTick: async () => undefined,
      notifyWarning: vi.fn(),
      debugChatState: vi.fn(),
      onRequireModelSelection: vi.fn(),
      enablePollingFallback: false,
    })

    chat.chatInput.value = '继续生成'
    await chat.sendMessage()

    expect(chat.messages.value).toEqual([
      { id: 1, role: 'user', text: '继续生成' },
      { id: 2, role: 'assistant', text: '生成失败：流服务断开' },
    ])
    expect(chat.generationPhase.value).toBe('failed')
    expect(chat.generationTaskStatus.value).toBe('failed')
  })

  it('marks_partial_stream_output_as_failed_when_generation_fails_after_tokens', async () => {
    const useWorkbenchChat = await loadUseWorkbenchChat()
    const listeners = new Map<string, (event: MessageEvent<string>) => void>()
    const addStreamListener = vi.fn((_: EventSource, eventName: string, listener: (event: MessageEvent<string>) => void) => {
      listeners.set(eventName, listener)
    })
    const stream = { close: vi.fn() } as unknown as EventSource

    const chat = useWorkbenchChat({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      getCurrentProjectId: () => 101,
      getActiveChapterKey: () => '301',
      getActivePlugins: () => ['outline.search'],
      ensureConversationId: vi.fn().mockResolvedValue(77),
      ensureModelConfigId: vi.fn().mockResolvedValue(501),
      refreshActiveModelInfo: vi.fn(),
      listConversations: vi.fn(),
      listMessages: vi.fn(),
      createMessage: vi.fn().mockResolvedValue({}),
      createGeneration: vi.fn().mockResolvedValue({ taskId: 9008, status: 'running' }),
      getGeneration: vi.fn(),
      openTurnStream: vi.fn().mockReturnValue(stream),
      addStreamListener,
      closeTaskStream: vi.fn(),
      revealAssistantText: vi.fn(),
      scrollChat: vi.fn(),
      nextTick: async () => undefined,
      notifyWarning: vi.fn(),
      debugChatState: vi.fn(),
      onRequireModelSelection: vi.fn(),
      enablePollingFallback: false,
    })

    chat.chatInput.value = '继续生成'
    const sendPromise = chat.sendMessage()
    await flushPromises(20)

    listeners.get('generation.started')?.({ data: '{}' } as MessageEvent<string>)
    listeners.get('generation.token')?.({
      data: JSON.stringify({ token: '第一句' }),
    } as MessageEvent<string>)
    listeners.get('generation.failed')?.({
      data: JSON.stringify({ errorMsg: '模型限流' }),
    } as MessageEvent<string>)

    await sendPromise

    expect(chat.messages.value).toEqual([
      { id: 1, role: 'user', text: '继续生成' },
      { id: 2, role: 'assistant', text: '第一句\n\n生成失败：模型限流' },
    ])
    expect(chat.generationPhase.value).toBe('failed')
    expect(chat.generationTaskStatus.value).toBe('failed')
    expect(chat.streamingAssistantMsgId.value).toBe(null)
    expect(chat.isGenerating.value).toBe(false)
  })

  it('resumes_running_task_and_consumes_stream_events_with_existing_assistant_message', async () => {
    const useWorkbenchChat = await loadUseWorkbenchChat()
    const listeners = new Map<string, (event: MessageEvent<string>) => void>()
    const addStreamListener = vi.fn((_: EventSource, eventName: string, listener: (event: MessageEvent<string>) => void) => {
      listeners.set(eventName, listener)
    })
    const closeTaskStream = vi.fn()
    const stream = { close: vi.fn() } as unknown as EventSource

    const chat = useWorkbenchChat({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      getCurrentProjectId: () => 101,
      getActiveChapterKey: () => '301',
      getActivePlugins: () => ['outline.search'],
      ensureConversationId: vi.fn().mockResolvedValue(77),
      ensureModelConfigId: vi.fn().mockResolvedValue(501),
      refreshActiveModelInfo: vi.fn(),
      listConversations: vi.fn(),
      listMessages: vi.fn(),
      createMessage: vi.fn().mockResolvedValue({}),
      createGeneration: vi.fn(),
      getGeneration: vi.fn(),
      openTurnStream: vi.fn().mockReturnValue(stream),
      addStreamListener,
      closeTaskStream,
      revealAssistantText: vi.fn(),
      scrollChat: vi.fn(),
      nextTick: async () => undefined,
      notifyWarning: vi.fn(),
      debugChatState: vi.fn(),
      onRequireModelSelection: vi.fn(),
      enablePollingFallback: false,
    })

    chat.hydrateFromRecoverySnapshot({
      session: { sessionId: '90001' },
      activeTask: { turnId: '50001', taskId: '70001', taskStatus: 'RUNNING' },
      messages: [
        { messageId: 1, role: 'assistant', contentMd: '' },
      ],
    })

    const resumePromise = chat.resumeRunningTask('101', '90001', '50001')
    await flushPromises(20)

    listeners.get('generation.started')?.({ data: '{}' } as MessageEvent<string>)
    listeners.get('generation.token')?.({
      data: JSON.stringify({ token: '恢复续写' }),
    } as MessageEvent<string>)
    listeners.get('generation.done')?.({
      data: JSON.stringify({ status: 'done' }),
    } as MessageEvent<string>)

    await resumePromise

    expect(chat.messages.value).toEqual([
      { id: '1', role: 'assistant', text: '恢复续写' },
    ])
    expect(chat.streamingAssistantMsgId.value).toBe(null)
    expect(chat.isGenerating.value).toBe(false)
    expect(chat.generationPhase.value).toBe('idle')
    expect(chat.generationTaskStatus.value).toBe('')
    expect(closeTaskStream).toHaveBeenCalled()
    expect(stream.close).toHaveBeenCalled()
  })

  it('does_not_reuse_assistant_message_with_existing_approval_when_resuming_running_task', async () => {
    const useWorkbenchChat = await loadUseWorkbenchChat()
    const listeners = new Map<string, (event: MessageEvent<string>) => void>()
    const addStreamListener = vi.fn((_: EventSource, eventName: string, listener: (event: MessageEvent<string>) => void) => {
      listeners.set(eventName, listener)
    })
    const closeTaskStream = vi.fn()
    const stream = { close: vi.fn() } as unknown as EventSource

    const chat = useWorkbenchChat({
      getContext: () => ({ projectId: '101', operatorId: '201' }),
      getCurrentProjectId: () => '101',
      getActiveChapterKey: () => '301',
      getActivePlugins: () => ['outline.search'],
      ensureModelConfigId: vi.fn().mockResolvedValue('501'),
      refreshActiveModelInfo: vi.fn(),
      listSessions: vi.fn(),
      createSession: vi.fn(),
      getSessionRecovery: vi.fn(),
      createTurn: vi.fn(),
      getTask: vi.fn(),
      openTurnStream: vi.fn().mockReturnValue(stream),
      addStreamListener,
      closeTaskStream,
      revealAssistantText: vi.fn(),
      scrollChat: vi.fn(),
      nextTick: async () => undefined,
      notifyWarning: vi.fn(),
      debugChatState: vi.fn(),
      onRequireModelSelection: vi.fn(),
      enablePollingFallback: false,
    })

    chat.hydrateFromRecoverySnapshot({
      session: { sessionId: '90001' },
      activeTask: { turnId: '50001', taskId: '70001', taskStatus: 'RUNNING' },
      messages: [
        {
          messageId: 1,
          role: 'assistant',
          contentMd: '',
          approvalId: 42,
          approvalType: 'WORLD_SETTING_CREATE',
          approvalStatus: 'pending',
        },
      ],
    })

    const resumePromise = chat.resumeRunningTask('101', '90001', '50001')
    await flushPromises(20)

    listeners.get('generation.started')?.({ data: '{}' } as MessageEvent<string>)
    listeners.get('generation.token')?.({
      data: JSON.stringify({ token: '新的恢复续写' }),
    } as MessageEvent<string>)
    listeners.get('generation.done')?.({
      data: JSON.stringify({ status: 'done' }),
    } as MessageEvent<string>)

    await resumePromise

    expect(chat.messages.value).toEqual([
      {
        id: '1',
        role: 'assistant',
        text: '',
        approval: {
          id: '42',
          message: '检测到待审批变更（WORLD_SETTING_CREATE）',
          time: '',
          resolved: false,
        },
      },
      { id: 2, role: 'assistant', text: '新的恢复续写' },
    ])
    expect(chat.streamingAssistantMsgId.value).toBe(null)
    expect(chat.isGenerating.value).toBe(false)
    expect(closeTaskStream).toHaveBeenCalled()
    expect(stream.close).toHaveBeenCalled()
  })

  it('resumes_running_task_with_oversized_string_turn_id_without_precision_loss', async () => {
    const useWorkbenchChat = await loadUseWorkbenchChat()
    const listeners = new Map<string, (event: MessageEvent<string>) => void>()
    const addStreamListener = vi.fn((_: EventSource, eventName: string, listener: (event: MessageEvent<string>) => void) => {
      listeners.set(eventName, listener)
    })
    const stream = { close: vi.fn() } as unknown as EventSource
    const oversizedTurnId = '90071992547409931234'
    const openGenerationStream = vi.fn().mockReturnValue(stream)

    const chat = useWorkbenchChat({
      getContext: () => ({ projectId: '101', operatorId: '201' }),
      getCurrentProjectId: () => '101',
      getActiveChapterKey: () => '301',
      getActivePlugins: () => ['outline.search'],
      ensureModelConfigId: vi.fn().mockResolvedValue('501'),
      refreshActiveModelInfo: vi.fn(),
      listSessions: vi.fn(),
      createSession: vi.fn(),
      getSessionRecovery: vi.fn(),
      createTurn: vi.fn(),
      getTask: vi.fn(),
      openTurnStream: openGenerationStream,
      addStreamListener,
      closeTaskStream: vi.fn(),
      revealAssistantText: vi.fn(),
      scrollChat: vi.fn(),
      nextTick: async () => undefined,
      notifyWarning: vi.fn(),
      debugChatState: vi.fn(),
      onRequireModelSelection: vi.fn(),
      enablePollingFallback: false,
    })

    chat.hydrateFromRecoverySnapshot({
      session: { sessionId: '90001' },
      activeTask: { turnId: oversizedTurnId, taskId: '70001', taskStatus: 'RUNNING' },
      messages: [
        { messageId: 1, role: 'assistant', contentMd: '' },
      ],
    })

    const resumePromise = chat.resumeRunningTask('101', '90001', oversizedTurnId)
    await flushPromises(20)

    listeners.get('generation.started')?.({ data: '{}' } as MessageEvent<string>)
    listeners.get('generation.done')?.({
      data: JSON.stringify({ status: 'done' }),
    } as MessageEvent<string>)

    await resumePromise

    expect(openGenerationStream).toHaveBeenCalledWith('101', '90001', oversizedTurnId)
  })

  it('resumes_running_task_and_falls_back_to_polling_when_stream_fails', async () => {
    const useWorkbenchChat = await loadUseWorkbenchChat()
    const getGeneration = vi.fn()
      .mockResolvedValueOnce({ status: 'running' })
      .mockResolvedValueOnce({ status: 'done' })

    const chat = useWorkbenchChat({
      getContext: () => ({ projectId: '101', operatorId: '201' }),
      getCurrentProjectId: () => '101',
      getActiveChapterKey: () => '301',
      getActivePlugins: () => ['outline.search'],
      ensureModelConfigId: vi.fn().mockResolvedValue('501'),
      refreshActiveModelInfo: vi.fn(),
      listSessions: vi.fn(),
      createSession: vi.fn(),
      getSessionRecovery: vi.fn(),
      createTurn: vi.fn(),
      getTask: getGeneration,
      openTurnStream: vi.fn(() => {
        throw new Error('stream unavailable')
      }),
      addStreamListener: vi.fn(),
      closeTaskStream: vi.fn(),
      revealAssistantText: vi.fn(),
      scrollChat: vi.fn(),
      nextTick: async () => undefined,
      waitForPolling: async () => undefined,
      notifyWarning: vi.fn(),
      debugChatState: vi.fn(),
      onRequireModelSelection: vi.fn(),
      enablePollingFallback: true,
    })

    chat.hydrateFromRecoverySnapshot({
      session: { sessionId: '90001' },
      activeTask: { turnId: '50001', taskId: '70001', taskStatus: 'RUNNING' },
      messages: [
        { messageId: 1, role: 'assistant', contentMd: '' },
      ],
    })

    await chat.resumeRunningTask('101', '90001', '50001')

    expect(getGeneration).toHaveBeenCalledWith('101', '70001')
    expect(chat.messages.value).toEqual([
      { id: '1', role: 'assistant', text: '生成任务已完成，状态：done' },
    ])
    expect(chat.streamingAssistantMsgId.value).toBe(null)
    expect(chat.isGenerating.value).toBe(false)
    expect(chat.generationPhase.value).toBe('idle')
    expect(chat.generationTaskStatus.value).toBe('')
  })
})
