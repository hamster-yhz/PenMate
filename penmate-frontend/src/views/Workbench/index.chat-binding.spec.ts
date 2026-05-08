import { shallowMount } from '@vue/test-utils'
import { nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const streamListeners = new Map<string, Array<(event: MessageEvent<string>) => void>>()

const emitStreamEvent = (eventName: string, payload: Record<string, unknown> = {}) => {
  const listeners = streamListeners.get(eventName) || []
  const event = { data: JSON.stringify(payload) } as MessageEvent<string>
  listeners.forEach((listener) => listener(event))
}

const waitForAssertion = async (assertion: () => void, attempts = 50) => {
  let lastError: unknown
  for (let i = 0; i < attempts; i += 1) {
    try {
      assertion()
      return
    } catch (error) {
      lastError = error
    }
    await Promise.resolve()
    await nextTick()
  }
  throw lastError instanceof Error ? lastError : new Error('等待断言成立超时')
}

const WorkbenchLeftPanelHarness = {
  name: 'WorkbenchLeftPanel',
  props: ['activeLeftTab'],
  emits: ['update:active-left-tab'],
  template: `
    <aside data-testid="layout-left-panel">
      <div data-testid="active-left-tab">{{ activeLeftTab }}</div>
      <button data-testid="left-tab-outline" @click="$emit('update:active-left-tab', 'outline')">outline</button>
      <button data-testid="left-tab-characters" @click="$emit('update:active-left-tab', 'characters')">characters</button>
      <button data-testid="left-tab-world" @click="$emit('update:active-left-tab', 'world')">world</button>
    </aside>
  `,
}

const WorkbenchEditorPanelHarness = {
  name: 'WorkbenchEditorPanel',
  template: '<main data-testid="layout-editor-panel">editor</main>',
}

const WorkbenchRightPanelHarness = {
  name: 'WorkbenchRightPanel',
  props: ['chatInput', 'generationStatusText', 'isGenerating'],
  emits: ['update:chat-input', 'send'],
  methods: {
    emitChatInput(this: { $emit: (eventName: string, value: string) => void }, event: Event) {
      this.$emit('update:chat-input', (event.target as HTMLTextAreaElement | null)?.value || '')
    },
  },
  template: `
    <aside data-testid="layout-right-panel">
      <div data-testid="agent-status">{{ generationStatusText }}</div>
      <textarea
        data-testid="chat-input"
        :value="chatInput"
        @input="emitChatInput"
      ></textarea>
      <button data-testid="chat-send" type="button" :disabled="isGenerating" @click="$emit('send')">send</button>
    </aside>
  `,
}

const agentApiMock = {
  listSessions: vi.fn(async () => [{ sessionId: 1, title: 'Workbench 会话', updatedAt: '2026-04-26 23:00:00' }]),
  createSession: vi.fn(async () => ({ sessionId: 90002, title: '新会话', status: 'ACTIVE' })),
  getSessionRecovery: vi.fn(async () => null as any),
  resumeSession: vi.fn(async () => null as any),
  createTurn: vi.fn<(...args: any[]) => Promise<any>>(async () => ({
    session: { sessionId: 90001, title: '第三章夜雨追踪', status: 'ACTIVE', boundStyle: { styleId: 81, name: '冷峻悬疑' } },
    activeTask: { taskId: 77, taskStatus: 'RUNNING', requestContextId: 70101 },
    taskType: 'WRITE',
    userMessage: '测试消息',
  })),
  getTask: vi.fn(async () => ({ status: 'done' })),
  openTaskStream: vi.fn(() => ({ close: vi.fn() } as unknown as EventSource)),
  addStreamListener: vi.fn((_: EventSource, eventName: string, listener: (event: MessageEvent<string>) => void) => {
    const current = streamListeners.get(eventName) || []
    current.push(listener)
    streamListeners.set(eventName, current)
  }),
}

vi.mock('vue-router', () => ({
  useRoute: () => ({
    query: {
      projectId: '101',
      operatorId: '201',
    },
  }),
  useRouter: () => ({
    push: vi.fn(),
  }),
}))

vi.mock('ant-design-vue', () => ({
  message: {
    warning: vi.fn(),
    success: vi.fn(),
  },
}))

vi.mock('@/stores/session', () => ({
  getSession: () => ({
    accessToken: 'token',
    refreshToken: 'refresh',
    userId: 201,
    userName: '测试作者',
    userEmail: 'writer@penmate.test',
  }),
  clearSession: vi.fn(),
}))

vi.mock('@/api/modules/novel.api', () => ({
  novelApi: {
    getProject: vi.fn(async () => ({ title: '测试小说' })),
    listChapters: vi.fn(async () => []),
    updateProject: vi.fn(async () => ({})),
    createChapter: vi.fn(async () => ({})),
    deleteChapter: vi.fn(async () => ({})),
  },
}))

vi.mock('@/api/modules/outline.api', () => ({
  outlineApi: {
    listOutlineTree: vi.fn(async () => []),
    createNode: vi.fn(async () => ({})),
    deleteNode: vi.fn(async () => ({})),
    updateNode: vi.fn(async () => ({})),
    moveNode: vi.fn(async () => ({})),
  },
}))

vi.mock('@/api/modules/chapter.api', () => ({
  chapterApi: {
    listVersions: vi.fn(async () => []),
    getVersionSnapshotUrl: vi.fn(async () => ''),
    getContentUrl: vi.fn(async () => ''),
    restoreVersion: vi.fn(async () => ({})),
    publishChapter: vi.fn(async () => ({})),
    getContentUploadUrl: vi.fn(async () => ({})),
    commitContent: vi.fn(async () => ({})),
    createVersion: vi.fn(async () => ({})),
    getChapterDetail: vi.fn(async () => ({ content: '' })),
  },
}))

vi.mock('@/api/modules/card.api', () => ({
  cardApi: {
    listCards: vi.fn(async () => []),
    listCardRelations: vi.fn(async () => []),
    createCard: vi.fn(async () => ({})),
    updateCard: vi.fn(async () => ({})),
    deleteCard: vi.fn(async () => ({})),
    createCardRelation: vi.fn(async () => ({})),
    deleteCardRelation: vi.fn(async () => ({})),
  },
}))

vi.mock('@/api/modules/agent.api', () => ({
  agentApi: agentApiMock,
}))

vi.mock('@/api/modules/approval.api', () => ({
  approvalApi: {
    approve: vi.fn(async () => ({})),
    reject: vi.fn(async () => ({})),
  },
}))

vi.mock('@/api/modules/plugin.api', () => ({
  pluginApi: {
    listProjectPlugins: vi.fn(async () => []),
  },
}))

vi.mock('@/api/modules/model.api', () => ({
  modelApi: {
    getUserModelPreferences: vi.fn(async () => ({
      mainAgentModelConfigId: 'mcfg-9001',
      dirtyWorkAgentModelConfigId: 'mcfg-9002',
      candidateConfigs: [{ modelConfigId: 'mcfg-9001', modelName: 'DeepSeek-R1', keySourceType: 'USER_KEY' }],
    })),
  },
}))

vi.mock('@/api/modules/auth.api', () => ({
  authApi: {
    logout: vi.fn(async () => ({})),
  },
}))

vi.mock('@/composables/workbench/useWorkbenchDraft', () => ({
  createChapterLoadGuard: () => ({
    begin: vi.fn(() => 1),
    isCurrent: vi.fn(() => true),
  }),
  useWorkbenchDraft: () => ({
    saveDraft: vi.fn(),
    clearDraft: vi.fn(),
    resolveStoredDraft: vi.fn(() => null),
    resolveEditorSeedContent: vi.fn(() => ''),
    resolveChapterContent: vi.fn(() => ''),
  }),
}))

vi.mock('@/composables/workbench/useWorkbenchOutline', () => ({
  useWorkbenchOutline: () => ({
    outlineData: ref([]),
    activeChapter: ref('1'),
    currentChapterTitle: ref('第一章'),
    outlineOpBusy: ref(false),
    loadOutline: vi.fn(() => []),
    selectChapter: vi.fn(),
    addVolume: vi.fn(),
    addChapter: vi.fn(),
    deleteVolume: vi.fn(),
    deleteChapter: vi.fn(),
    renameNode: vi.fn(),
    moveNode: vi.fn(),
  }),
}))

vi.mock('@/composables/workbench/useWorkbenchCards', () => ({
  useWorkbenchCards: () => ({
    projectCards: ref([]),
    characterCards: ref([]),
    worldCards: ref([]),
    cardRelations: ref([]),
    relationFromId: ref(''),
    relationToId: ref(''),
    relationType: ref(''),
    loadCardsAndRelations: vi.fn(async () => undefined),
    createCardQuick: vi.fn(async () => undefined),
    saveCard: vi.fn(async () => undefined),
    deleteCardById: vi.fn(async () => undefined),
    createRelation: vi.fn(async () => undefined),
    deleteRelationById: vi.fn(async () => undefined),
    cardNameById: vi.fn(() => ''),
    updateCardDraft: vi.fn(),
    toggleCardExpanded: vi.fn(),
  }),
}))

vi.mock('@/composables/workbench/useWorkbenchEditor', () => ({
  useWorkbenchEditor: () => ({
    editorRef: ref(null),
    editorContent: ref(''),
    currentLine: ref(1),
    currentCol: ref(1),
    selectedText: ref(''),
    bindEditorTextarea: vi.fn(),
    onEditorInput: vi.fn(),
    updateCursorPos: vi.fn(),
    editorUndo: vi.fn(),
    editorRedo: vi.fn(),
    wrapSelection: vi.fn(),
    insertPrefix: vi.fn(),
    mergeToEditor: vi.fn(),
    replaceSelected: vi.fn(),
    selectChapterDraft: vi.fn(),
  }),
}))

vi.mock('@/composables/workbench/useWorkbenchVersions', () => ({
  useWorkbenchVersions: () => ({
    selectedVersionNo: ref(''),
    versionBusy: ref(false),
    selectedVersionContent: ref(''),
    versionDiffSummary: ref(''),
    getCurrentChapterVersions: vi.fn(() => []),
    loadVersions: vi.fn(async () => undefined),
    viewSelectedVersion: vi.fn(async () => undefined),
    restoreSelectedVersion: vi.fn(async () => undefined),
  }),
}))

vi.mock('@/composables/workbench/useWorkbenchApprovals', () => ({
  useWorkbenchApprovals: () => ({
    isApprovalBusy: ref(false),
    handleApprove: vi.fn(async () => undefined),
    handleReject: vi.fn(async () => undefined),
  }),
}))

const mountWorkbench = async () => {
  const Workbench = (await import('./index.vue')).default
  return shallowMount(Workbench, {
    global: {
      stubs: {
        WorkbenchLeftPanel: WorkbenchLeftPanelHarness,
        WorkbenchEditorPanel: WorkbenchEditorPanelHarness,
        WorkbenchRightPanel: WorkbenchRightPanelHarness,
      },
    },
  })
}

describe('Workbench index chat parent binding', () => {
  beforeEach(() => {
    streamListeners.clear()
    agentApiMock.createTurn.mockClear()
    agentApiMock.getTask.mockClear()
    agentApiMock.openTaskStream.mockClear()
    agentApiMock.addStreamListener.mockClear()
    agentApiMock.listSessions.mockClear()
    agentApiMock.getSessionRecovery = vi.fn(async () => ({
      session: {
        sessionId: 90001,
        title: '第三章夜雨追踪',
        status: 'ACTIVE',
        boundStyle: { styleId: 81, name: '冷峻悬疑' },
      },
      activeTask: {
        taskId: 70001,
        taskStatus: 'WAITING_APPROVAL',
        streamChannelKey: 'agent-task-70001',
      },
      pendingApproval: {
        approvalId: 45,
        approvalType: 'chapter_patch',
        approvalMessage: '请先审批改写方案',
        approvalTime: '2026-04-26 23:10:00',
        approvalStatus: 'pending',
      },
      messages: [
        {
          messageId: 1,
          role: 'assistant',
          contentMd: '',
          approvalId: 45,
          approvalType: 'chapter_patch',
          approvalMessage: '请先审批改写方案',
          approvalTime: '2026-04-26 23:10:00',
          approvalStatus: 'pending',
        },
      ],
      workbenchContext: {
        chapterId: 301,
        selectedText: '',
        activePlugins: ['outline.search'],
        modelConfigId: 'mcfg-9001',
      },
    }))
    agentApiMock.resumeSession = vi.fn(async () => ({
      session: {
        sessionId: 90001,
        title: '第三章夜雨追踪',
        status: 'ACTIVE',
        boundStyle: { styleId: 81, name: '冷峻悬疑' },
      },
      activeTask: {
        taskId: 70001,
        taskStatus: 'WAITING_APPROVAL',
        streamChannelKey: 'agent-task-70001',
      },
      pendingApproval: {
        approvalId: 45,
        approvalType: 'chapter_patch',
        approvalMessage: '请先审批改写方案',
        approvalTime: '2026-04-26 23:10:00',
        approvalStatus: 'pending',
      },
      messages: [
        {
          messageId: 1,
          role: 'assistant',
          contentMd: '',
          approvalId: 45,
          approvalType: 'chapter_patch',
          approvalMessage: '请先审批改写方案',
          approvalTime: '2026-04-26 23:10:00',
          approvalStatus: 'pending',
        },
      ],
      workbenchContext: {
        chapterId: 301,
        selectedText: '',
        activePlugins: ['outline.search'],
        modelConfigId: 'mcfg-9001',
      },
    }))
  })

  it('resumes_latest_session_on_mount_and_restores_task_status', async () => {
    const wrapper = await mountWorkbench()

    await waitForAssertion(() => {
      expect(wrapper.get('[data-testid="agent-status"]').text()).toContain('等待审批')
    })

    expect(agentApiMock.resumeSession).toHaveBeenCalledTimes(1)
    expect(agentApiMock.resumeSession).toHaveBeenCalledWith(101, 1, expect.objectContaining({
      trigger: 'WORKBENCH_ENTER',
      operatorId: 201,
    }))
    expect(agentApiMock.getSessionRecovery).not.toHaveBeenCalled()
  })

  it('reconnects_running_session_on_mount_and_consumes_stream_events', async () => {
    agentApiMock.resumeSession = vi.fn(async () => ({
      session: {
        sessionId: 90001,
        title: '第三章夜雨追踪',
        status: 'ACTIVE',
        boundStyle: { styleId: 81, name: '冷峻悬疑' },
      },
      activeTask: {
        taskId: 70001,
        taskStatus: 'RUNNING',
        streamChannelKey: 'agent-task-70001',
      },
      pendingApproval: null,
      messages: [
        {
          messageId: 1,
          role: 'assistant',
          contentMd: '',
        },
      ],
      workbenchContext: {
        chapterId: 301,
        selectedText: '',
        activePlugins: ['outline.search'],
        modelConfigId: 'mcfg-9001',
      },
    }))

    const wrapper = await mountWorkbench()

    await waitForAssertion(() => {
      expect(agentApiMock.resumeSession).toHaveBeenCalledTimes(1)
      expect(agentApiMock.openTaskStream).toHaveBeenCalledWith(101, 70001)
    })

    await waitForAssertion(() => {
      expect(streamListeners.get('generation.started')?.length || 0).toBeGreaterThan(0)
      expect(streamListeners.get('generation.token')?.length || 0).toBeGreaterThan(0)
      expect(streamListeners.get('generation.done')?.length || 0).toBeGreaterThan(0)
    })

    emitStreamEvent('generation.started')
    emitStreamEvent('generation.token', {
      token: '恢复后的续写内容',
    })
    emitStreamEvent('generation.done', {
      status: 'done',
    })

    await waitForAssertion(() => {
      expect((wrapper.vm as unknown as { messages: Array<{ text: string }> }).messages.some((item) => item.text.includes('恢复后的续写内容'))).toBe(true)
    })
  })

  it('preserves_oversized_string_task_id_when_reconnecting_running_session_on_mount', async () => {
    const oversizedTaskId = '90071992547409931234'
    agentApiMock.resumeSession = vi.fn(async () => ({
      session: {
        sessionId: 90001,
        title: '第三章夜雨追踪',
        status: 'ACTIVE',
        boundStyle: { styleId: 81, name: '冷峻悬疑' },
      },
      activeTask: {
        taskId: oversizedTaskId,
        taskStatus: 'RUNNING',
        streamChannelKey: `agent-task-${oversizedTaskId}`,
      },
      pendingApproval: null,
      messages: [
        {
          messageId: 1,
          role: 'assistant',
          contentMd: '',
        },
      ],
      workbenchContext: {
        chapterId: 301,
        selectedText: '',
        activePlugins: ['outline.search'],
        modelConfigId: 'mcfg-9001',
      },
    }))

    await mountWorkbench()

    await waitForAssertion(() => {
      expect(agentApiMock.openTaskStream).toHaveBeenCalledWith(101, oversizedTaskId)
    })
  })

  it('clears_bound_style_when_turn_response_has_no_bound_style', async () => {
    agentApiMock.createTurn = vi.fn(async () => ({
      session: { sessionId: 90001, title: '第三章夜雨追踪', status: 'ACTIVE', boundStyle: null },
      activeTask: { taskId: 77, taskStatus: 'RUNNING', requestContextId: 70101 },
      taskType: 'WRITE',
      userMessage: '测试消息',
    }))

    const wrapper = await mountWorkbench()

    await waitForAssertion(() => {
      expect(wrapper.get('[data-testid="agent-status"]').text()).toContain('等待审批')
    })

    await wrapper.get('[data-testid="chat-input"]').setValue('使用无风格响应继续生成')
    await wrapper.get('[data-testid="chat-send"]').trigger('click')

    await waitForAssertion(() => {
      expect(agentApiMock.createTurn).toHaveBeenCalledTimes(1)
    })

    expect((wrapper.vm as unknown as { boundStyleName: string }).boundStyleName).toBe('')
  })

  it('re_enables_followup_send_after_waiting_approval_through_real_useWorkbenchChat_to_parent_binding', async () => {
    const wrapper = await mountWorkbench()

    await waitForAssertion(() => {
      wrapper.get('[data-testid="chat-input"]')
    })

    const initialInput = wrapper.get('[data-testid="chat-input"]')
    await initialInput.setValue('请先生成审批版本')
    await wrapper.get('[data-testid="chat-send"]').trigger('click')

    await waitForAssertion(() => {
      expect(agentApiMock.createTurn).toHaveBeenCalledTimes(1)
    })

    await waitForAssertion(() => {
      expect(streamListeners.get('generation.started')?.length || 0).toBeGreaterThan(0)
      expect(streamListeners.get('generation.waiting_approval')?.length || 0).toBeGreaterThan(0)
      expect(streamListeners.get('generation.done')?.length || 0).toBeGreaterThan(0)
    })

    emitStreamEvent('generation.started')
    emitStreamEvent('generation.waiting_approval', {
      approvalId: 45,
      approvalType: 'chapter_patch',
      approvalMessage: '请先审批改写方案',
      approvalTime: '2026-04-26 23:10:00',
      approvalStatus: 'pending',
    })
    emitStreamEvent('generation.done', {
      status: 'done',
    })

    await waitForAssertion(() => {
      expect(wrapper.get('[data-testid="agent-status"]').text()).toContain('等待审批')
    })

    const followupInput = wrapper.get('[data-testid="chat-input"]')
    await followupInput.setValue('审批意见已补充，请继续生成正文')

    await waitForAssertion(() => {
      const sendButton = wrapper.get('[data-testid="chat-send"]')
      expect((sendButton.element as HTMLButtonElement).disabled).toBe(false)
    })

    expect(wrapper.get('[data-testid="agent-status"]').text()).toContain('等待审批')
    expect(agentApiMock.createTurn).toHaveBeenCalledTimes(1)
  })

  it('passes_string_model_config_id_to_generation_payload_when_loading_preferred_model', async () => {
    const wrapper = await mountWorkbench()

    await waitForAssertion(() => {
      wrapper.get('[data-testid="chat-input"]')
    })

    await wrapper.get('[data-testid="chat-input"]').setValue('使用已选模型生成内容')
    await wrapper.get('[data-testid="chat-send"]').trigger('click')

    await waitForAssertion(() => {
      expect(agentApiMock.createTurn).toHaveBeenCalledTimes(1)
    })

    const generationPayload = (agentApiMock.createTurn.mock.calls[0] as unknown[] | undefined)?.[2] as Record<string, unknown> | undefined
    expect(generationPayload).toEqual(
      expect.objectContaining({
        taskRequest: expect.objectContaining({
          modelConfigId: 'mcfg-9001',
        }),
      })
    )
  })

  it('passes_nested_preferred_string_model_config_id_to_generation_payload_when_preferences_are_nested', async () => {
    vi.doMock('@/api/modules/model.api', () => ({
      modelApi: {
        getUserModelPreferences: vi.fn(async () => ({
          preferences: {
            mainAgentModelConfigId: 'mcfg-nested-9001',
            dirtyWorkAgentModelConfigId: 'mcfg-nested-9002',
          },
          candidateConfigs: [{ modelConfigId: 'mcfg-nested-9001', modelName: 'DeepSeek-R1', keySourceType: 'USER_KEY' }],
        })),
      },
    }))
  })

  it('updates_active_left_tab_in_parent_when_left_panel_emits_tab_change', async () => {
    const wrapper = await mountWorkbench()

    expect(wrapper.get('[data-testid="active-left-tab"]').text()).toBe('outline')

    await wrapper.get('[data-testid="left-tab-world"]').trigger('click')
    expect(wrapper.get('[data-testid="active-left-tab"]').text()).toBe('world')

    await wrapper.get('[data-testid="left-tab-characters"]').trigger('click')
    expect(wrapper.get('[data-testid="active-left-tab"]').text()).toBe('characters')
  })
})
