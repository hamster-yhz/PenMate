import { describe, expect, it, vi } from 'vitest'

type ChatMessage = {
  id: number
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
  conversationId: number
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
  currentConversationId: { value: number | null }
  conversationLoading: { value: boolean }
  showConversationPanel: { value: boolean }
  currentModelName: { value: string }
  loadConversationList: (projectId: number) => Promise<void>
  loadConversationHistory: (projectId: number, operatorId: number) => Promise<void>
  selectConversation: (conversationId: number) => Promise<void>
  sendMessage: () => Promise<void>
  toggleConversationPanel: () => Promise<void>
}

const loadUseWorkbenchChat = async (): Promise<UseWorkbenchChatFactory> => {
  const modulePath = '../useWorkbenchChat'
  return (await import(/* @vite-ignore */ modulePath)).useWorkbenchChat as UseWorkbenchChatFactory
}

const flushPromises = async (times = 8) => {
  for (let i = 0; i < times; i += 1) {
    await Promise.resolve()
  }
}

describe('useWorkbenchChat', () => {
  it('loads_conversation_list_and_normalizes_items', async () => {
    const useWorkbenchChat = await loadUseWorkbenchChat()
    const listConversations = vi.fn().mockResolvedValue([
      { conversationId: 81, title: '第一轮', updatedAt: '2026-04-26 20:00:00' },
      { conversationId: 0, title: 'invalid', updatedAt: 'ignored' },
      { conversationId: 82, title: '', createdAt: '2026-04-26 20:02:00' },
    ])

    const chat = useWorkbenchChat({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      getCurrentProjectId: () => 101,
      getActiveChapterKey: () => '301',
      getActivePlugins: () => ['outline.search'],
      ensureConversationId: vi.fn(),
      ensureModelConfigId: vi.fn(),
      refreshActiveModelInfo: vi.fn(),
      listConversations,
      listMessages: vi.fn(),
      createMessage: vi.fn(),
      createGeneration: vi.fn(),
      getGeneration: vi.fn(),
      openGenerationStream: vi.fn(),
      addStreamListener: vi.fn(),
      closeGenerationStream: vi.fn(),
      revealAssistantText: vi.fn(),
      scrollChat: vi.fn(),
      nextTick: async () => undefined,
      notifyWarning: vi.fn(),
      debugChatState: vi.fn(),
      onRequireModelSelection: vi.fn(),
      enablePollingFallback: false,
    })

    await chat.loadConversationList(101)

    expect(listConversations).toHaveBeenCalledWith(101)
    expect(chat.conversationList.value).toEqual([
      { conversationId: 81, title: '第一轮', updatedAt: '2026-04-26 20:00:00' },
      { conversationId: 82, title: '', updatedAt: '2026-04-26 20:02:00' },
    ])
    expect(chat.conversationLoading.value).toBe(false)
  })

  it('loads_history_by_ensuring_conversation_and_mapping_messages', async () => {
    const useWorkbenchChat = await loadUseWorkbenchChat()
    const ensureConversationId = vi.fn().mockResolvedValue(88)
    const listMessages = vi.fn().mockResolvedValue([
      {
        messageId: 9,
        role: 'assistant',
        contentMd: '你好\n世界',
        approvalId: 42,
        approvalType: 'WORLD_SETTING_CREATE',
        approvalStatus: 'pending',
      },
      { messageId: 10, role: 'tool', contentMd: 'tool output' },
    ])
    const listConversations = vi.fn().mockResolvedValue([
      { conversationId: 88, title: '当前会话', updatedAt: '2026-04-26 20:03:00' },
    ])
    const scrollChat = vi.fn()

    const chat = useWorkbenchChat({
      getContext: () => ({ projectId: 101, operatorId: 201 }),
      getCurrentProjectId: () => 101,
      getActiveChapterKey: () => '301',
      getActivePlugins: () => [],
      ensureConversationId,
      ensureModelConfigId: vi.fn(),
      refreshActiveModelInfo: vi.fn(),
      listConversations,
      listMessages,
      createMessage: vi.fn(),
      createGeneration: vi.fn(),
      getGeneration: vi.fn(),
      openGenerationStream: vi.fn(),
      addStreamListener: vi.fn(),
      closeGenerationStream: vi.fn(),
      revealAssistantText: vi.fn(),
      scrollChat,
      nextTick: async () => undefined,
      notifyWarning: vi.fn(),
      debugChatState: vi.fn(),
      onRequireModelSelection: vi.fn(),
      enablePollingFallback: false,
    })

    await chat.loadConversationHistory(101, 201)

    expect(ensureConversationId).toHaveBeenCalledWith(101, 201)
    expect(listMessages).toHaveBeenCalledWith(101, 88)
    expect(listConversations).toHaveBeenCalledWith(101)
    expect(chat.currentConversationId.value).toBe(88)
    expect(chat.messages.value).toEqual([
      {
        id: 9,
        role: 'assistant',
        text: '你好<br/>世界',
        approval: {
          id: '42',
          message: '检测到待审批变更（WORLD_SETTING_CREATE）',
          time: '',
          resolved: false,
        },
      },
      { id: 10, role: 'system', text: 'tool output' },
    ])
    expect(scrollChat).toHaveBeenCalled()
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
      openGenerationStream: vi.fn(),
      addStreamListener: vi.fn(),
      closeGenerationStream: vi.fn(),
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
      openGenerationStream: vi.fn(),
      addStreamListener: vi.fn(),
      closeGenerationStream: vi.fn(),
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
      openGenerationStream: vi.fn().mockReturnValue(stream),
      addStreamListener,
      closeGenerationStream: vi.fn(),
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
    await flushPromises()

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
      openGenerationStream: vi.fn().mockReturnValue(stream),
      addStreamListener,
      closeGenerationStream: vi.fn(),
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
    await flushPromises()

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
      openGenerationStream: vi.fn().mockReturnValue(stream),
      addStreamListener,
      closeGenerationStream: vi.fn(),
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
    await flushPromises()

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
      openGenerationStream: vi.fn().mockReturnValue(stream),
      addStreamListener,
      closeGenerationStream: vi.fn(),
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
    await flushPromises()

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
      openGenerationStream: vi.fn().mockReturnValue(stream),
      addStreamListener,
      closeGenerationStream: vi.fn(),
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
    await flushPromises()

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
      openGenerationStream: vi.fn(() => {
        throw new Error('stream unavailable')
      }),
      addStreamListener: vi.fn(),
      closeGenerationStream: vi.fn(),
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
      openGenerationStream: vi.fn(() => {
        throw new Error('stream unavailable')
      }),
      addStreamListener: vi.fn(),
      closeGenerationStream: vi.fn(),
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

    expect(getGeneration).toHaveBeenCalledWith(101, 9002)
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
      openGenerationStream: vi.fn(() => {
        throw new Error('stream unavailable')
      }),
      addStreamListener: vi.fn(),
      closeGenerationStream: vi.fn(),
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
    const closeGenerationStream = vi.fn()
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
      openGenerationStream: vi.fn().mockReturnValue(stream),
      addStreamListener,
      closeGenerationStream,
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
    await flushPromises()

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
    expect(closeGenerationStream).toHaveBeenCalled()
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
      openGenerationStream: vi.fn(() => {
        throw '流服务断开'
      }),
      addStreamListener: vi.fn(),
      closeGenerationStream: vi.fn(),
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
      openGenerationStream: vi.fn().mockReturnValue(stream),
      addStreamListener,
      closeGenerationStream: vi.fn(),
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
    await flushPromises()

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
})
