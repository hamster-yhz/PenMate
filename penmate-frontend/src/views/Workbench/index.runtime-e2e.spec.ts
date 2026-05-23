import { shallowMount } from '@vue/test-utils'
import { nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createBaseRecoverySnapshot as baseRecoverySnapshot,
  createRuntimeFailedEvent,
  createStoryBibleWaitingApprovalRecoverySnapshot,
  createTodoReviewRecoverySnapshot,
} from '@/test/workbenchRuntimeContract.fixture'

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
  throw lastError instanceof Error ? lastError : new Error('绛夊緟鏂█鎴愮珛瓒呮椂')
}

const WorkbenchLeftPanelHarness = {
  name: 'WorkbenchLeftPanel',
  template: '<aside data-testid="layout-left-panel">left</aside>',
}

const WorkbenchEditorPanelHarness = {
  name: 'WorkbenchEditorPanel',
  template: '<main data-testid="layout-editor-panel">editor</main>',
}

const WorkbenchRightPanelHarness = {
  name: 'WorkbenchRightPanel',
  props: [
    'generationStatusText',
    'messages',
    'chatInput',
    'isGenerating',
  ],
  emits: ['select-conversation', 'update:chat-input', 'send'],
  methods: {
    emitChatInput(this: { $emit: (eventName: string, value: string) => void }, event: Event) {
      this.$emit('update:chat-input', (event.target as HTMLTextAreaElement | null)?.value || '')
    },
  },
  template: `
    <aside data-testid="layout-right-panel">
      <div data-testid="agent-status">{{ generationStatusText }}</div>
      <textarea data-testid="chat-input" :value="chatInput" @input="emitChatInput"></textarea>
      <button data-testid="chat-send" type="button" :disabled="isGenerating" @click="$emit('send')">send</button>
      <button data-testid="resume-session-90001" @click="$emit('select-conversation', '90001')">resume</button>
    </aside>
  `,
}

const agentApiMock = {
  listSessions: vi.fn(async () => [{ sessionId: '90001', title: '第三章夜雨追踪', updatedAt: '2026-05-14 23:00:00' }]),
  createSession: vi.fn(async () => ({ sessionId: '90002', title: '新会话', status: 'ACTIVE' })),
  getSessionRecovery: vi.fn(async () => baseRecoverySnapshot()),
  resumeSession: vi.fn(async () => baseRecoverySnapshot()),
  createTurn: vi.fn(async () => ({
    session: { sessionId: '90001', title: '第三章夜雨追踪', status: 'ACTIVE', boundStyle: { styleId: '81', name: '冷峻悬疑' } },
    activeTask: { turnId: '50001', taskId: '70001', taskStatus: 'RUNNING', requestContextId: '71001' },
    taskType: 'WRITE',
    userMessage: '续写本章并检查人设',
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

const selectedTextRef = ref('')

vi.mock('@/composables/workbench/useWorkbenchEditor', () => ({
  useWorkbenchEditor: () => ({
    editorRef: ref(null),
    editorContent: ref(''),
    currentLine: ref(1),
    currentCol: ref(1),
    selectedText: selectedTextRef,
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

describe('Workbench runtime acceptance matrix', () => {
  beforeEach(() => {
    streamListeners.clear()
    selectedTextRef.value = ''
    vi.clearAllMocks()
    agentApiMock.listSessions.mockResolvedValue([{ sessionId: '90001', title: '第三章夜雨追踪', updatedAt: '2026-05-14 23:00:00' }])
    agentApiMock.getSessionRecovery.mockResolvedValue(baseRecoverySnapshot())
    agentApiMock.resumeSession.mockResolvedValue(baseRecoverySnapshot())
  })

  it('covers_case_A_showing_draft_and_quality_runtime_chain_after_resume', async () => {
    agentApiMock.resumeSession.mockResolvedValue({
      ...baseRecoverySnapshot(),
      activeTask: {
        turnId: '50001',
        taskId: '70001',
        taskStatus: 'running',
        requestContextId: '71001',
      },
      workbenchContext: {
        ...baseRecoverySnapshot().workbenchContext,
        activeTaskRuntime: {
          lastRuntimeStatus: 'quality_review',
          recoveryCursor: 'tool_call:quality_review:call-quality-1',
          activeToolCallsSnapshot: [
            {
              toolCallId: 'call-quality-1',
              toolCode: 'quality_review',
              toolName: '质量审查',
              status: 'running',
              argumentsPreview: '{"chapterId":"301"}',
              output: '{"reviewSummary":"存在剧情逻辑问题，需要修订。"}',
              errorMessage: '',
            },
          ],
        },
        resultSummary: {
          draftSummary: { draftText: '夜雨中的追踪在巷口停住。' },
          qualityReportSummary: { reviewSummary: '存在剧情逻辑问题，需要修订。' },
          todoSummary: null,
          storyBibleProposalSummary: null,
        },
      },
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

  it('covers_case_B_showing_todo_plan_from_recovery_without_local_todo_source', async () => {
    agentApiMock.resumeSession.mockResolvedValue(createTodoReviewRecoverySnapshot() as any)

    const wrapper = await mountWorkbench()

    await waitForAssertion(() => {
      const rightPanel = wrapper.findComponent(WorkbenchRightPanelHarness)
      expect(rightPanel.props('generationStatusText')).toBe('就绪')
      expect(rightPanel.props('toolCallCard')).toBeUndefined()
      expect(rightPanel.props('todoPlanCard')).toBeUndefined()
      expect(rightPanel.props('storyBibleApprovalCard')).toBeUndefined()
    })
  })

  it('covers_case_C_showing_story_bible_waiting_approval_card', async () => {
    agentApiMock.resumeSession.mockResolvedValue(createStoryBibleWaitingApprovalRecoverySnapshot() as any)

    const wrapper = await mountWorkbench()

    await waitForAssertion(() => {
      const rightPanel = wrapper.findComponent(WorkbenchRightPanelHarness)
      expect(rightPanel.props('generationStatusText')).toBe('等待审批')
      expect(rightPanel.props('toolCallCard')).toBeUndefined()
      expect(rightPanel.props('todoPlanCard')).toBeUndefined()
      expect(rightPanel.props('storyBibleApprovalCard')).toBeUndefined()
    })
  })

  it('covers_case_D_showing_rag_context_and_generated_draft_after_runtime_events', async () => {
    const wrapper = await mountWorkbench()

    await waitForAssertion(() => {
      expect(streamListeners.get('generation.started')?.length || 0).toBeGreaterThan(0)
      expect(streamListeners.get('generation.status')?.length || 0).toBeGreaterThan(0)
      expect(streamListeners.get('generation.done')?.length || 0).toBeGreaterThan(0)
    })

    emitStreamEvent('generation.started', {
      taskId: '70001',
      sessionId: '90001',
      turnId: '50001',
      phase: 'context_building',
      message: '姝ｅ湪瑙勫垝绔犺妭',
      nextAction: 'build_context',
    })
    emitStreamEvent('generation.status', {
      taskId: '70001',
      sessionId: '90001',
      turnId: '50001',
      phase: 'draft_generation',
      message: '正在生成正文',
      nextAction: 'generate_draft',
      toolCall: {
        toolCallId: 'call-draft-1',
        toolCode: 'draft_generation',
        toolName: '正文生成',
        status: 'running',
        argumentsPreview: '{"chapterId":"42","ragRefs":["chapter:42#伏笔-雨夜密令"]}',
        output: '{"draftText":"密令在雨夜的屋檐下被再次提起。"}',
        errorMessage: '',
      },
    })
    emitStreamEvent('generation.done', {
      taskId: '70001',
      sessionId: '90001',
      turnId: '50001',
      phase: 'done',
      message: '已完成',
      status: 'done',
      nextAction: 'view_result',
      todoPlan: {
        planTitle: '第三章修订待办',
        planSummary: '补齐密令来源链路',
        recommendedNextAction: 'apply_todo_plan',
        items: [
          { title: '修复密令来源', status: 'pending', priority: 'HIGH' },
          { title: '补充侍从转述桥段', status: 'pending', priority: 'MEDIUM' },
        ],
      },
      storyBibleApproval: {
        approvalId: '88001',
        approvalType: 'STORY_BIBLE_UPDATE',
        proposalSummary: '建议补充侍从知晓密令的设定',
        entryKeys: ['maid.secret_order'],
        nextAction: 'await_approval',
      },
    })

    await waitForAssertion(() => {
      const rightPanel = wrapper.findComponent(WorkbenchRightPanelHarness)
      expect(rightPanel.props('generationStatusText')).toBe('就绪')
      expect(rightPanel.props('toolCallCard')).toBeUndefined()
      expect(rightPanel.props('todoPlanCard')).toBeUndefined()
      expect(rightPanel.props('storyBibleApprovalCard')).toBeUndefined()
    })
  })

  it('covers_case_E_showing_failure_reason_and_retry_next_action', async () => {
    const wrapper = await mountWorkbench()

    await waitForAssertion(() => {
      expect(streamListeners.get('generation.failed')?.length || 0).toBeGreaterThan(0)
    })

    emitStreamEvent('generation.failed', {
      ...createRuntimeFailedEvent(),
    })

    await waitForAssertion(() => {
      const rightPanel = wrapper.findComponent(WorkbenchRightPanelHarness)
      expect(rightPanel.props('generationStatusText')).toBe('异常')
    })
  })

  it('covers_case_F_restoring_same_todo_card_after_refresh_without_duplicate_items', async () => {
    const recoveryWithTodos = {
      ...baseRecoverySnapshot(),
      workbenchContext: {
        ...baseRecoverySnapshot().workbenchContext,
        activeTaskRuntime: {
          lastRuntimeStatus: 'todo_review',
          recoveryCursor: 'tool_call:todo_planner:call-todo-1',
          activeToolCallsSnapshot: [
            {
              toolCallId: 'call-todo-1',
              toolCode: 'todo_planner',
              toolName: 'Todo 规划',
              status: 'done',
              output: '{"planTitle":"第三章修订待办"}',
              errorMessage: '',
            },
          ],
        },
        resultSummary: {
          draftSummary: null,
          qualityReportSummary: null,
          todoSummary: {
            planTitle: '第三章修订待办',
            items: [
              { todoId: 'todo-1', title: '修复密令来源', status: 'pending', priority: 'HIGH' },
              { todoId: 'todo-2', title: '补充侍从转述桥段', status: 'pending', priority: 'MEDIUM' },
            ],
            recommendedNextAction: 'apply_todo_plan',
          },
          storyBibleProposalSummary: null,
        },
      },
    }
    agentApiMock.resumeSession.mockResolvedValue(recoveryWithTodos as any)

    const wrapper = await mountWorkbench()

    await waitForAssertion(() => {
      const rightPanel = wrapper.findComponent(WorkbenchRightPanelHarness)
      expect(rightPanel.props('generationStatusText')).toBe('就绪')
      expect(rightPanel.props('toolCallCard')).toBeUndefined()
      expect(rightPanel.props('todoPlanCard')).toBeUndefined()
      expect(rightPanel.props('storyBibleApprovalCard')).toBeUndefined()
    })

    await wrapper.get('[data-testid="resume-session-90001"]').trigger('click')

    await waitForAssertion(() => {
      const rightPanel = wrapper.findComponent(WorkbenchRightPanelHarness)
      expect(rightPanel.props('generationStatusText')).toBe('就绪')
      expect(rightPanel.props('toolCallCard')).toBeUndefined()
      expect(rightPanel.props('todoPlanCard')).toBeUndefined()
      expect(rightPanel.props('storyBibleApprovalCard')).toBeUndefined()
    })
  })

  it('reuses_selected_text_restored_from_recovery_when_sending_follow_up_turn_after_refresh', async () => {
    agentApiMock.resumeSession.mockResolvedValue({
      ...baseRecoverySnapshot(),
      activeTask: {
        turnId: '50001',
        taskId: '70001',
        taskStatus: 'done',
        requestContextId: '71001',
      },
      workbenchContext: {
        ...baseRecoverySnapshot().workbenchContext,
        selectedText: '恢复后的选中文本',
      },
    })

    const wrapper = await mountWorkbench()

    await waitForAssertion(() => {
      wrapper.get('[data-testid="chat-input"]')
    })

    selectedTextRef.value = '恢复后的选中文本'
    await wrapper.get('[data-testid="chat-input"]').setValue('基于刚才选中的段落继续修订')
    await wrapper.get('[data-testid="chat-send"]').trigger('click')

    await waitForAssertion(() => {
      expect(agentApiMock.createTurn).toHaveBeenCalled()
    })

    const generationPayload = (agentApiMock.createTurn.mock.calls.at(-1) as unknown[] | undefined)?.[2] as Record<string, unknown> | undefined
    expect(generationPayload).toEqual(
      expect.objectContaining({
        taskRequest: expect.objectContaining({
          selectedText: '恢复后的选中文本',
        }),
      })
    )
  })
})
