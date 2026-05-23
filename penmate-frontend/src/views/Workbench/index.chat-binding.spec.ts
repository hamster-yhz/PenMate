import { shallowMount } from '@vue/test-utils'
import { nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createRuntimeWaitingApprovalEvent,
  createStoryBibleWaitingApprovalRecoverySnapshot,
  createTodoReviewRecoverySnapshot,
} from '@/test/workbenchRuntimeContract.fixture'

type ModelConfigCandidate = {
  modelConfigId: string
  modelName: string
  keySourceType?: string
  providerName?: string
}

type ModelPreferencePayload = {
  mainAgentModelConfigId?: string | null
  dirtyWorkAgentModelConfigId?: string | null
  candidateConfigs?: ModelConfigCandidate[]
  modelConfigs?: ModelConfigCandidate[]
  preferences?: {
    mainAgentModelConfigId?: string | null
    dirtyWorkAgentModelConfigId?: string | null
    candidateConfigs?: ModelConfigCandidate[]
    modelConfigs?: ModelConfigCandidate[]
  }
}

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
  props: [
    'chatInput',
    'generationStatusText',
    'isGenerating',
    'messages',
    'streamingAssistantMsgId',
  ],
  emits: ['update:chat-input', 'send', 'toggle-history', 'select-conversation'],
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
      <button data-testid="toggle-history" type="button" @click="$emit('toggle-history')">history</button>
      <button data-testid="select-conversation" type="button" @click="$emit('select-conversation', '90001')">resume</button>
      <button data-testid="chat-send" type="button" :disabled="isGenerating" @click="$emit('send')">send</button>
    </aside>
  `,
}

const agentApiMock = {
  listSessions: vi.fn(async () => [{ sessionId: '1', title: 'Workbench 会话', updatedAt: '2026-04-26 23:00:00' }]),
  createSession: vi.fn<(...args: any[]) => Promise<any>>(async () => ({ sessionId: '90002', title: '新会话', status: 'ACTIVE' })),
  getSessionRecovery: vi.fn(async () => null as any),
  resumeSession: vi.fn(async () => null as any),
  createTurn: vi.fn<(...args: any[]) => Promise<any>>(async () => ({
    session: { sessionId: '90001', title: '第三章夜雨追踪', status: 'ACTIVE', boundStyle: { styleId: '81', name: '冷峻悬疑' } },
    activeTask: { turnId: '77', taskId: '77', taskStatus: 'RUNNING', requestContextId: '70101' },
    taskType: 'WRITE',
    userMessage: '测试消息',
  })),
  getTask: vi.fn(async () => ({ status: 'done' })),
  openTurnStream: vi.fn(() => ({ close: vi.fn() } as unknown as EventSource)),
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

const buildModelPreferencePayload = (overrides: ModelPreferencePayload = {}): ModelPreferencePayload => ({
  mainAgentModelConfigId: 'mcfg-9001',
  dirtyWorkAgentModelConfigId: 'mcfg-9002',
  candidateConfigs: [{ modelConfigId: 'mcfg-9001', modelName: 'DeepSeek-R1', keySourceType: 'USER_KEY' }],
  ...overrides,
})

const modelApiMock = {
  getUserModelPreferences: vi.fn<() => Promise<ModelPreferencePayload>>(async () => buildModelPreferencePayload()),
}

vi.mock('@/api/modules/model.api', () => ({
  modelApi: modelApiMock,
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

const mountWorkbench = async (_caseKey?: string) => {
  vi.resetModules()
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
    agentApiMock.openTurnStream.mockClear()
    agentApiMock.addStreamListener.mockClear()
    agentApiMock.listSessions.mockClear()
    modelApiMock.getUserModelPreferences.mockReset()
    modelApiMock.getUserModelPreferences.mockResolvedValue(buildModelPreferencePayload())
    agentApiMock.getSessionRecovery = vi.fn(async () => ({
      session: {
        sessionId: '90001',
        title: '第三章夜雨追踪',
        status: 'ACTIVE',
        boundStyle: { styleId: '81', name: '冷峻悬疑' },
      },
      activeTask: {
        taskId: '70001',
        taskStatus: 'WAITING_APPROVAL',
        streamChannelKey: 'agent-task-70001',
      },
      pendingApproval: {
        approvalId: '45',
        approvalType: 'chapter_patch',
        approvalMessage: '请先审批改写方案',
        approvalTime: '2026-04-26 23:10:00',
        approvalStatus: 'pending',
      },
      messages: [
        {
          messageId: '1',
          role: 'assistant',
          contentMd: '',
          approvalId: '45',
          approvalType: 'chapter_patch',
          approvalMessage: '请先审批改写方案',
          approvalTime: '2026-04-26 23:10:00',
          approvalStatus: 'pending',
        },
      ],
      workbenchContext: {
        chapterId: '301',
        selectedText: '',
        activePlugins: ['outline.search'],
        modelConfigId: 'mcfg-9001',
      },
    }))
    agentApiMock.resumeSession = vi.fn(async () => ({
      session: {
        sessionId: '90001',
        title: '第三章夜雨追踪',
        status: 'ACTIVE',
        boundStyle: { styleId: '81', name: '冷峻悬疑' },
      },
      activeTask: {
        taskId: '70001',
        taskStatus: 'WAITING_APPROVAL',
        streamChannelKey: 'agent-task-70001',
      },
      pendingApproval: {
        approvalId: '45',
        approvalType: 'chapter_patch',
        approvalMessage: '请先审批改写方案',
        approvalTime: '2026-04-26 23:10:00',
        approvalStatus: 'pending',
      },
      messages: [
        {
          messageId: '1',
          role: 'assistant',
          contentMd: '',
          approvalId: '45',
          approvalType: 'chapter_patch',
          approvalMessage: '请先审批改写方案',
          approvalTime: '2026-04-26 23:10:00',
          approvalStatus: 'pending',
        },
      ],
      workbenchContext: {
        chapterId: '301',
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
    expect(agentApiMock.resumeSession).toHaveBeenCalledWith('101', '1', expect.objectContaining({
      trigger: 'WORKBENCH_ENTER',
      operatorId: '201',
    }))
    expect(agentApiMock.getSessionRecovery).not.toHaveBeenCalled()
  })

  it('does_not_pass_runtime_cards_to_right_panel_after_resume_recovery', async () => {
    agentApiMock.resumeSession = vi.fn(async () => createStoryBibleWaitingApprovalRecoverySnapshot() as any)

    const wrapper = await mountWorkbench()

    await waitForAssertion(() => {
      const rightPanel = wrapper.findComponent(WorkbenchRightPanelHarness)
      expect(WorkbenchRightPanelHarness.props).not.toContain('runtimeStatusCard')
      expect(WorkbenchRightPanelHarness.props).not.toContain('toolCallCard')
      expect(WorkbenchRightPanelHarness.props).not.toContain('todoPlanCard')
      expect(WorkbenchRightPanelHarness.props).not.toContain('storyBibleApprovalCard')
      expect(rightPanel.props('runtimeStatusCard')).toBeUndefined()
      expect(rightPanel.props('toolCallCard')).toBeUndefined()
      expect(rightPanel.props('todoPlanCard')).toBeUndefined()
      expect(rightPanel.props('storyBibleApprovalCard')).toBeUndefined()
      expect(rightPanel.props('generationStatusText')).toBe('等待审批')
    })
  })

  it('closes_history_overlay_after_selecting_a_conversation', async () => {
    const wrapper = await mountWorkbench()
    const rightPanel = wrapper.findComponent(WorkbenchRightPanelHarness)

    await rightPanel.get('[data-testid="toggle-history"]').trigger('click')
    await nextTick()
    expect((wrapper.vm as unknown as { showConversationPanel: boolean }).showConversationPanel).toBe(true)

    await rightPanel.get('[data-testid="select-conversation"]').trigger('click')

    await waitForAssertion(() => {
      expect(agentApiMock.resumeSession).toHaveBeenCalledWith('101', '90001', expect.objectContaining({
        trigger: 'WORKBENCH_ENTER',
        operatorId: '201',
      }))
      expect((wrapper.vm as unknown as { showConversationPanel: boolean }).showConversationPanel).toBe(false)
    })
  })

  it('bridges_chapters_when_list_chapters_payload_is_still_nested_under_data_field', async () => {
    const loadOutline = vi.fn(() => [])
    const listOutlineTree = vi.fn(async () => [
      { outlineNodeId: 'node-11', title: '第一章', nodeType: 'CHAPTER', parentId: 'node-10' },
      { outlineNodeId: 'node-10', title: '第一卷', nodeType: 'VOLUME' },
    ])
    const listChapters = vi.fn(async () => ({
      data: [
        { chapterId: 'chapter-301', outlineNodeId: 'node-11', title: '第一章' },
      ],
    }))

    vi.doMock('@/api/modules/outline.api', () => ({
      outlineApi: {
        listOutlineTree,
        createNode: vi.fn(async () => ({})),
        deleteNode: vi.fn(async () => ({})),
        updateNode: vi.fn(async () => ({})),
        moveNode: vi.fn(async () => ({})),
      },
    }))
    vi.doMock('@/api/modules/novel.api', () => ({
      novelApi: {
        getProject: vi.fn(async () => ({ title: '测试小说' })),
        listChapters,
        updateProject: vi.fn(async () => ({})),
        createChapter: vi.fn(async () => ({})),
        deleteChapter: vi.fn(async () => ({})),
      },
    }))
    vi.doMock('@/composables/workbench/useWorkbenchOutline', () => ({
      useWorkbenchOutline: () => ({
        outlineData: ref([]),
        activeChapter: ref('1'),
        currentChapterTitle: ref('第一章'),
        outlineOpBusy: ref(false),
        loadOutline,
        selectChapter: vi.fn(),
        addVolume: vi.fn(),
        addChapter: vi.fn(),
        deleteVolume: vi.fn(),
        deleteChapter: vi.fn(),
        renameNode: vi.fn(),
        moveNode: vi.fn(),
      }),
    }))

    await mountWorkbench('chapters-data-nested')
    await waitForAssertion(() => {
      expect(loadOutline).toHaveBeenCalledWith(
        [
          { outlineNodeId: 'node-11', title: '第一章', nodeType: 'CHAPTER', parentId: 'node-10' },
          { outlineNodeId: 'node-10', title: '第一卷', nodeType: 'VOLUME' },
        ],
        { 'node-11': 'chapter-301' }
      )
    })
  })

  it('falls_back_to_chapter_tree_when_outline_tree_is_empty_but_chapters_exist', async () => {
    const loadOutline = vi.fn(() => [])
    const listOutlineTree = vi.fn(async () => ({
      data: [],
    }))
    const listChapters = vi.fn(async () => ({
      data: [
        { chapterId: 'chapter-301', outlineNodeId: 'node-11', title: '第一章' },
        { chapterId: 'chapter-302', outlineNodeId: 'node-12', title: '第二章' },
      ],
    }))

    vi.doMock('@/api/modules/outline.api', () => ({
      outlineApi: {
        listOutlineTree,
        createNode: vi.fn(async () => ({})),
        deleteNode: vi.fn(async () => ({})),
        updateNode: vi.fn(async () => ({})),
        moveNode: vi.fn(async () => ({})),
      },
    }))
    vi.doMock('@/api/modules/novel.api', () => ({
      novelApi: {
        getProject: vi.fn(async () => ({ title: '测试小说' })),
        listChapters,
        updateProject: vi.fn(async () => ({})),
        createChapter: vi.fn(async () => ({})),
        deleteChapter: vi.fn(async () => ({})),
      },
    }))
    vi.doMock('@/composables/workbench/useWorkbenchOutline', () => ({
      useWorkbenchOutline: () => ({
        outlineData: ref([]),
        activeChapter: ref('1'),
        currentChapterTitle: ref('第一章'),
        outlineOpBusy: ref(false),
        loadOutline,
        selectChapter: vi.fn(),
        addVolume: vi.fn(),
        addChapter: vi.fn(),
        deleteVolume: vi.fn(),
        deleteChapter: vi.fn(),
        renameNode: vi.fn(),
        moveNode: vi.fn(),
      }),
    }))

    await mountWorkbench('outline-empty-chapters-fallback')

    await waitForAssertion(() => {
      expect(loadOutline).toHaveBeenCalledWith(
        [
          { outlineNodeId: 'virtual-volume-root', title: '未分卷', nodeType: 'VOLUME' },
          { outlineNodeId: 'node-11', chapterId: 'chapter-301', title: '第一章', nodeType: 'CHAPTER', parentId: 'virtual-volume-root' },
          { outlineNodeId: 'node-12', chapterId: 'chapter-302', title: '第二章', nodeType: 'CHAPTER', parentId: 'virtual-volume-root' },
        ],
        {
          'node-11': 'chapter-301',
          'node-12': 'chapter-302',
        }
      )
    })
  })

  it('falls_back_to_chapter_tree_when_outline_tree_has_no_volume_nodes_but_chapters_exist', async () => {
    const loadOutline = vi.fn(() => [])
    const listOutlineTree = vi.fn(async () => ({
      data: [
        { outlineNodeId: 'node-11', title: '第一章', nodeType: 'CHAPTER', parentId: 'node-10' },
      ],
    }))
    const listChapters = vi.fn(async () => ({
      data: [
        { chapterId: 'chapter-301', outlineNodeId: 'node-11', title: '第一章' },
      ],
    }))

    vi.doMock('@/api/modules/outline.api', () => ({
      outlineApi: {
        listOutlineTree,
        createNode: vi.fn(async () => ({})),
        deleteNode: vi.fn(async () => ({})),
        updateNode: vi.fn(async () => ({})),
        moveNode: vi.fn(async () => ({})),
      },
    }))
    vi.doMock('@/api/modules/novel.api', () => ({
      novelApi: {
        getProject: vi.fn(async () => ({ title: '测试小说' })),
        listChapters,
        updateProject: vi.fn(async () => ({})),
        createChapter: vi.fn(async () => ({})),
        deleteChapter: vi.fn(async () => ({})),
      },
    }))
    vi.doMock('@/composables/workbench/useWorkbenchOutline', () => ({
      useWorkbenchOutline: () => ({
        outlineData: ref([]),
        activeChapter: ref('1'),
        currentChapterTitle: ref('第一章'),
        outlineOpBusy: ref(false),
        loadOutline,
        selectChapter: vi.fn(),
        addVolume: vi.fn(),
        addChapter: vi.fn(),
        deleteVolume: vi.fn(),
        deleteChapter: vi.fn(),
        renameNode: vi.fn(),
        moveNode: vi.fn(),
      }),
    }))

    await mountWorkbench('outline-chapter-only-fallback')

    await waitForAssertion(() => {
      expect(loadOutline).toHaveBeenCalledWith(
        [
          { outlineNodeId: 'virtual-volume-root', title: '未分卷', nodeType: 'VOLUME' },
          { outlineNodeId: 'node-11', chapterId: 'chapter-301', title: '第一章', nodeType: 'CHAPTER', parentId: 'virtual-volume-root' },
        ],
        {
          'node-11': 'chapter-301',
        }
      )
    })
  })

  it('drops_chapters_without_real_outline_node_id_instead_of_generating_virtual_chapter_nodes', async () => {
    const loadOutline = vi.fn(() => [])
    const listOutlineTree = vi.fn(async () => ({
      data: [],
    }))
    const listChapters = vi.fn(async () => ({
      data: [
        { chapterId: 'chapter-301', outlineNodeId: null, title: '第一章' },
        { chapterId: 'chapter-302', outlineNodeId: undefined, title: '第二章' },
      ],
    }))

    vi.doMock('@/api/modules/outline.api', () => ({
      outlineApi: {
        listOutlineTree,
        createNode: vi.fn(async () => ({})),
        deleteNode: vi.fn(async () => ({})),
        updateNode: vi.fn(async () => ({})),
        moveNode: vi.fn(async () => ({})),
      },
    }))
    vi.doMock('@/api/modules/novel.api', () => ({
      novelApi: {
        getProject: vi.fn(async () => ({ title: '测试小说' })),
        listChapters,
        updateProject: vi.fn(async () => ({})),
        createChapter: vi.fn(async () => ({})),
        deleteChapter: vi.fn(async () => ({})),
      },
    }))
    vi.doMock('@/composables/workbench/useWorkbenchOutline', () => ({
      useWorkbenchOutline: () => ({
        outlineData: ref([]),
        activeChapter: ref('1'),
        currentChapterTitle: ref('第一章'),
        outlineOpBusy: ref(false),
        loadOutline,
        selectChapter: vi.fn(),
        addVolume: vi.fn(),
        addChapter: vi.fn(),
        deleteVolume: vi.fn(),
        deleteChapter: vi.fn(),
        renameNode: vi.fn(),
        moveNode: vi.fn(),
      }),
    }))

    await mountWorkbench('chapters-without-real-outline-node-id')

    await waitForAssertion(() => {
      expect(loadOutline).toHaveBeenCalledWith([], {})
    })
  })

  it('reconnects_running_session_on_mount_and_consumes_stream_events', async () => {
    agentApiMock.resumeSession = vi.fn(async () => ({
      session: {
        sessionId: '90001',
        title: '第三章夜雨追踪',
        status: 'ACTIVE',
        boundStyle: { styleId: '81', name: '冷峻悬疑' },
      },
      activeTask: {
        turnId: '70001',
        taskId: '70001',
        taskStatus: 'RUNNING',
        streamChannelKey: 'agent-turn-70001',
      },
      pendingApproval: null,
      messages: [
        {
          messageId: '1',
          role: 'assistant',
          contentMd: '',
        },
      ],
      workbenchContext: {
        chapterId: '301',
        selectedText: '',
        activePlugins: ['outline.search'],
        modelConfigId: 'mcfg-9001',
      },
    }))

    const wrapper = await mountWorkbench()

    await waitForAssertion(() => {
      expect(agentApiMock.resumeSession).toHaveBeenCalledTimes(1)
      expect(agentApiMock.openTurnStream).toHaveBeenCalledWith('101', '1', '70001')
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

  it('filters_blank_non_streaming_assistant_placeholder_messages_from_chat_binding', async () => {
    agentApiMock.resumeSession = vi.fn(async () => ({
      session: {
        sessionId: '90001',
        title: '第三章夜雨追踪',
        status: 'ACTIVE',
        boundStyle: { styleId: '81', name: '冷峻悬疑' },
      },
      activeTask: null,
      pendingApproval: null,
      messages: [
        {
          messageId: '1',
          role: 'assistant',
          contentMd: '',
        },
        {
          messageId: '2',
          role: 'user',
          contentMd: '保留的用户消息',
        },
      ],
      workbenchContext: {
        chapterId: '301',
        selectedText: '',
        activePlugins: ['outline.search'],
        modelConfigId: 'mcfg-9001',
      },
    }))

    const wrapper = await mountWorkbench()

    await waitForAssertion(() => {
      const rightPanelMessages = wrapper.findComponent(WorkbenchRightPanelHarness).props('messages') as Array<{
        id: string
        role: string
        text: string
      }>
      expect(rightPanelMessages).toEqual([
        { id: '2', role: 'user', text: '保留的用户消息' },
      ])
    })
  })

  it('preserves_oversized_string_ids_when_reconnecting_running_session_on_mount', async () => {
    const oversizedSessionId = '90071992547409939876'
    const oversizedTaskId = '90071992547409931234'
    const oversizedChapterId = '90071992547409935678'
    agentApiMock.listSessions = vi.fn(async () => [{ sessionId: oversizedSessionId, title: 'Workbench 会话', updatedAt: '2026-04-26 23:00:00' }])
    agentApiMock.resumeSession = vi.fn(async () => ({
      session: {
        sessionId: oversizedSessionId,
        title: '第三章夜雨追踪',
        status: 'ACTIVE',
        boundStyle: { styleId: '81', name: '冷峻悬疑' },
      },
      activeTask: {
        turnId: oversizedTaskId,
        taskId: oversizedTaskId,
        taskStatus: 'RUNNING',
        streamChannelKey: `agent-turn-${oversizedTaskId}`,
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
        chapterId: oversizedChapterId,
        selectedText: '',
        activePlugins: ['outline.search'],
        modelConfigId: 'mcfg-9001',
      },
    }))

    const wrapper = await mountWorkbench()

    await waitForAssertion(() => {
      expect(agentApiMock.resumeSession).toHaveBeenCalledWith('101', oversizedSessionId, {
        operatorId: '201',
        trigger: 'WORKBENCH_ENTER',
      })
      expect(agentApiMock.openTurnStream).toHaveBeenCalledWith('101', oversizedSessionId, oversizedTaskId)
    })

    expect((wrapper.vm as unknown as { currentConversationId: string }).currentConversationId).toBe(oversizedSessionId)
    expect((wrapper.vm as unknown as { activeChapter: string }).activeChapter).toBe(oversizedChapterId)
  })

  it('passes_recovered_oversized_session_id_to_create_turn_without_precision_loss', async () => {
    const oversizedSessionId = '2052639275832553472'
    const oversizedTaskId = '2052639275832553999'
    agentApiMock.listSessions = vi.fn(async () => [{ sessionId: oversizedSessionId, title: 'Workbench 会话', updatedAt: '2026-04-26 23:00:00' }])
    agentApiMock.resumeSession = vi.fn(async () => ({
      session: {
        sessionId: oversizedSessionId,
        title: '第三章夜雨追踪',
        status: 'ACTIVE',
        boundStyle: { styleId: '81', name: '冷峻悬疑' },
      },
      activeTask: {
        turnId: oversizedTaskId,
        taskId: oversizedTaskId,
        taskStatus: 'WAITING_APPROVAL',
        streamChannelKey: `agent-turn-${oversizedTaskId}`,
      },
      pendingApproval: {
        approvalId: '45',
        approvalType: 'chapter_patch',
        approvalMessage: '请先审批改写方案',
        approvalTime: '2026-04-26 23:10:00',
        approvalStatus: 'pending',
      },
      messages: [
        {
          messageId: '1',
          role: 'assistant',
          contentMd: '',
          approvalId: '45',
          approvalType: 'chapter_patch',
          approvalMessage: '请先审批改写方案',
          approvalTime: '2026-04-26 23:10:00',
          approvalStatus: 'pending',
        },
      ],
      workbenchContext: {
        chapterId: '301',
        selectedText: '',
        activePlugins: ['outline.search'],
        modelConfigId: 'mcfg-9001',
      },
    }))
    agentApiMock.createTurn = vi.fn(async (_projectId: string, sessionId: string) => ({
      session: { sessionId, title: '第三章夜雨追踪', status: 'ACTIVE', boundStyle: { styleId: '81', name: '冷峻悬疑' } },
      activeTask: { turnId: 'turn-77', taskId: 'task-77', taskStatus: 'RUNNING', requestContextId: '70101' },
      taskType: 'WRITE',
      userMessage: '继续生成正文',
    }))

    const wrapper = await mountWorkbench()

    await waitForAssertion(() => {
      expect((wrapper.vm as unknown as { currentConversationId: string }).currentConversationId).toBe(oversizedSessionId)
    })

    await wrapper.get('[data-testid="chat-input"]').setValue('继续生成正文')
    await wrapper.get('[data-testid="chat-send"]').trigger('click')

    await waitForAssertion(() => {
      expect(agentApiMock.createTurn).toHaveBeenCalledTimes(1)
    })

    expect(agentApiMock.createTurn).toHaveBeenCalledWith('101', oversizedSessionId, expect.objectContaining({
      operatorId: '201',
      userMessage: '继续生成正文',
    }))
  })

  it('clears_bound_style_when_turn_response_has_no_bound_style', async () => {
    agentApiMock.createTurn = vi.fn(async () => ({
      session: { sessionId: '90001', title: '第三章夜雨追踪', status: 'ACTIVE', boundStyle: null },
      activeTask: { turnId: '77', taskId: '77', taskStatus: 'RUNNING', requestContextId: '70101' },
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

  it('passes_nested_turn_payload_to_generation_flow_when_backend_keeps_turn_result_under_data_field', async () => {
    agentApiMock.createTurn = vi.fn(async () => ({
      data: {
        session: { sessionId: '90001', title: '第三章夜雨追踪', status: 'ACTIVE', boundStyle: { styleId: '81', name: '冷峻悬疑' } },
        activeTask: { turnId: 'nested-turn-77', taskId: 'nested-task-77', taskStatus: 'RUNNING', requestContextId: '70101' },
        taskType: 'WRITE',
        userMessage: '嵌套 turn 响应',
      },
    }))

    const wrapper = await mountWorkbench()

    await waitForAssertion(() => {
      expect(wrapper.get('[data-testid="agent-status"]').text()).toContain('等待审批')
    })

    await wrapper.get('[data-testid="chat-input"]').setValue('嵌套 turn 响应')
    await wrapper.get('[data-testid="chat-send"]').trigger('click')

    await waitForAssertion(() => {
      expect(agentApiMock.createTurn).toHaveBeenCalledTimes(1)
      expect(agentApiMock.openTurnStream).toHaveBeenCalledWith('101', '90001', 'nested-turn-77')
    })
  })

  it('creates_new_session_from_nested_payload_when_clicking_create_session_button', async () => {
    agentApiMock.createSession = vi.fn(async () => ({
      data: {
        sessionId: 'session-nested-90001',
        title: '新会话',
        status: 'ACTIVE',
      },
    }))

    const wrapper = await mountWorkbench()
    const createSessionHandler = wrapper.findComponent(WorkbenchRightPanelHarness)
    createSessionHandler.vm.$emit('create-session')
    await nextTick()
    await nextTick()

    await waitForAssertion(() => {
      expect(agentApiMock.createSession).toHaveBeenCalledTimes(1)
    })

    expect((wrapper.vm as unknown as { currentConversationId: string }).currentConversationId).toBe('session-nested-90001')
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
      ...createRuntimeWaitingApprovalEvent(),
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

  it('passes_live_runtime_todo_and_story_bible_cards_to_right_panel_after_stream_events', async () => {
    const wrapper = await mountWorkbench()

    await waitForAssertion(() => {
      wrapper.get('[data-testid="chat-input"]')
    })

    await wrapper.get('[data-testid="chat-input"]').setValue('请输出实时运行态卡片')
    await wrapper.get('[data-testid="chat-send"]').trigger('click')

    await waitForAssertion(() => {
      expect(agentApiMock.createTurn).toHaveBeenCalledTimes(1)
      expect(streamListeners.get('generation.status')?.length || 0).toBeGreaterThan(0)
      expect(streamListeners.get('generation.waiting_approval')?.length || 0).toBeGreaterThan(0)
    })

    emitStreamEvent('generation.waiting_approval', {
      ...createRuntimeWaitingApprovalEvent(),
    })

    await waitForAssertion(() => {
      const rightPanel = wrapper.findComponent(WorkbenchRightPanelHarness)
      expect(rightPanel.props('todoPlanCard')).toBeUndefined()
      expect(rightPanel.props('storyBibleApprovalCard')).toBeUndefined()
      expect(rightPanel.props('generationStatusText')).toBe('等待审批')
    })
  })

  it('updates_right_panel_todo_plan_card_when_todo_crud_stream_event_mutates_existing_item', async () => {
    agentApiMock.resumeSession = vi.fn(async () => createTodoReviewRecoverySnapshot())

    const wrapper = await mountWorkbench()

    await waitForAssertion(() => {
      expect(streamListeners.get('generation.tool_call')?.length || 0).toBeGreaterThan(0)
    })

    emitStreamEvent('generation.tool_call', {
      eventName: 'generation.tool_call',
      taskId: '70001',
      sessionId: '90001',
      turnId: '50001',
      phase: 'tool_call',
      message: '待办 CRUD',
      nextAction: 'continue_tool_loop',
      toolCall: {
        toolCallId: 'call-todo-crud-1',
        toolCode: 'todo_crud',
        toolName: '待办 CRUD',
        status: 'done',
        output: {
          operation: 'update',
          todoId: 'todo-1',
          sessionId: '90001',
          taskId: '70001',
          title: '修复密令来源',
          sourceType: 'PLANNING',
          todoStatus: 'BLOCKED',
        },
      },
      recoverable: true,
    })

    await waitForAssertion(() => {
      const rightPanel = wrapper.findComponent(WorkbenchRightPanelHarness)
      expect(rightPanel.props('todoPlanCard')).toBeUndefined()
      expect(rightPanel.props('generationStatusText')).toBe('就绪')
    })
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
    modelApiMock.getUserModelPreferences.mockResolvedValue(buildModelPreferencePayload({
      preferences: {
        mainAgentModelConfigId: 'mcfg-nested-9001',
        dirtyWorkAgentModelConfigId: 'mcfg-nested-9002',
        modelConfigs: [{ modelConfigId: 'mcfg-nested-9001', modelName: 'DeepSeek-R1', keySourceType: 'USER_KEY' }],
      },
      candidateConfigs: undefined,
    }))

    const wrapper = await mountWorkbench()

    await waitForAssertion(() => {
      wrapper.get('[data-testid="chat-input"]')
    })

    await wrapper.get('[data-testid="chat-input"]').setValue('使用 nested preferences.modelConfigs 生成内容')
    await wrapper.get('[data-testid="chat-send"]').trigger('click')

    await waitForAssertion(() => {
      expect(agentApiMock.createTurn).toHaveBeenCalled()
    })

    const generationPayload = (agentApiMock.createTurn.mock.calls.at(-1) as unknown[] | undefined)?.[2] as Record<string, unknown> | undefined
    expect(generationPayload).toEqual(
      expect.objectContaining({
        taskRequest: expect.objectContaining({
          modelConfigId: 'mcfg-nested-9001',
        }),
      })
    )
    expect(modelApiMock.getUserModelPreferences).toHaveBeenCalled()
  })

  it('keeps_generation_payload_bound_to_current_preference_after_latest_session_recovery_contains_stale_model_config', async () => {
    modelApiMock.getUserModelPreferences.mockResolvedValue(buildModelPreferencePayload({
      mainAgentModelConfigId: 'mcfg-9002',
      dirtyWorkAgentModelConfigId: 'mcfg-9003',
      candidateConfigs: [
        { modelConfigId: 'mcfg-9002', modelName: 'Longcat-Flash-Thinking-202605', keySourceType: 'USER_KEY' },
        { modelConfigId: 'mcfg-9001', modelName: 'longcat-flash-thinking-2601-platform', keySourceType: 'USER_KEY' },
      ],
    }))
    agentApiMock.listSessions.mockResolvedValue([{ sessionId: 'stale-session-1', title: '旧会话', updatedAt: '2026-04-26 23:00:00' }])
    agentApiMock.resumeSession.mockResolvedValue({
      session: { sessionId: 'stale-session-1', title: '旧会话', status: 'ACTIVE', boundStyle: null },
      activeTask: { turnId: '501', taskId: '601', taskStatus: 'SUCCEEDED', requestContextId: '701' },
      pendingApproval: null,
      messages: [],
      workbenchContext: {
        chapterId: '1',
        selectedText: '',
        activePlugins: [],
        modelConfigId: 'mcfg-9001',
      },
    })

    const wrapper = await mountWorkbench()

    await waitForAssertion(() => {
      wrapper.get('[data-testid="chat-input"]')
    })

    await wrapper.get('[data-testid="chat-input"]').setValue('重启恢复后继续生成')
    await wrapper.get('[data-testid="chat-send"]').trigger('click')

    await waitForAssertion(() => {
      expect(agentApiMock.createTurn).toHaveBeenCalled()
    })

    const generationPayload = (agentApiMock.createTurn.mock.calls.at(-1) as unknown[] | undefined)?.[2] as Record<string, unknown> | undefined
    expect(generationPayload).toEqual(
      expect.objectContaining({
        taskRequest: expect.objectContaining({
          modelConfigId: 'mcfg-9002',
        }),
      })
    )
  })

  it('does_not_pass_runtime_cards_to_right_panel_when_latest_recovery_session_is_not_running', async () => {
    agentApiMock.listSessions.mockResolvedValue([{ sessionId: 'stale-session-1', title: '旧会话', updatedAt: '2026-04-26 23:00:00' }])
    agentApiMock.resumeSession.mockResolvedValue({
      ...createTodoReviewRecoverySnapshot(),
      session: { sessionId: 'stale-session-1', title: '旧会话', status: 'ACTIVE', boundStyle: null },
      activeTask: { turnId: '501', taskId: '601', taskStatus: 'SUCCEEDED', requestContextId: '701' },
    })

    const wrapper = await mountWorkbench()

    await waitForAssertion(() => {
      const rightPanel = wrapper.findComponent(WorkbenchRightPanelHarness)
      expect(rightPanel.props('generationStatusText')).toBe('就绪')
      expect(rightPanel.props('toolCallCard')).toBeUndefined()
      expect(rightPanel.props('todoPlanCard')).toBeUndefined()
      expect(rightPanel.props('storyBibleApprovalCard')).toBeUndefined()
    })
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
