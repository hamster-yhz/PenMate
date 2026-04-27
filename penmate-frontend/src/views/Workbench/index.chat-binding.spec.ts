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

const agentApiMock = {
  listConversations: vi.fn(async () => [{ conversationId: 1, title: 'Workbench 会话', updatedAt: '2026-04-26 23:00:00' }]),
  listMessages: vi.fn(async () => []),
  createConversation: vi.fn(async () => ({ conversationId: 1 })),
  createMessage: vi.fn(async () => ({ messageId: 11 })),
  createGeneration: vi.fn(async () => ({ taskId: 77, status: 'pending' })),
  getGeneration: vi.fn(async () => ({ status: 'done' })),
  openGenerationStream: vi.fn(() => ({ close: vi.fn() } as unknown as EventSource)),
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
    listConfigs: vi.fn(async () => [{ projectPolicyId: 9001, modelName: 'DeepSeek-R1', isDefault: true }]),
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
    wordCount: ref(0),
    currentLine: ref(1),
    currentCol: ref(1),
    selectedText: ref(''),
    saveHint: ref(''),
    onEditorInput: vi.fn(),
    updateCursorPos: vi.fn(),
    editorUndo: vi.fn(),
    editorRedo: vi.fn(),
    wrapSelection: vi.fn(),
    insertPrefix: vi.fn(),
    mergeToEditor: vi.fn(),
    replaceSelected: vi.fn(),
    saveContent: vi.fn(),
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
    loadChapterVersions: vi.fn(async () => undefined),
    viewSelectedVersion: vi.fn(async () => undefined),
    restoreSelectedVersion: vi.fn(async () => undefined),
    publishCurrentChapter: vi.fn(async () => undefined),
    refreshEditorFromRemote: vi.fn(async () => false),
  }),
}))

vi.mock('@/composables/workbench/useWorkbenchApprovals', () => ({
  useWorkbenchApprovals: () => ({
    isApprovalBusy: vi.fn(() => false),
    handleApprove: vi.fn(async () => undefined),
    handleReject: vi.fn(async () => undefined),
  }),
}))

const mountWorkbench = async () => {
  const Workbench = (await import('./index.vue')).default
  return shallowMount(Workbench, {
    global: {
      stubs: {
        WorkbenchRightPanel: false,
        ChatComposer: false,
        AgentSessionHeader: false,
      },
    },
  })
}

describe('Workbench index chat parent binding', () => {
  beforeEach(() => {
    streamListeners.clear()
    agentApiMock.createMessage.mockClear()
    agentApiMock.createGeneration.mockClear()
    agentApiMock.openGenerationStream.mockClear()
    agentApiMock.addStreamListener.mockClear()
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
      expect(agentApiMock.createMessage).toHaveBeenCalledTimes(1)
      expect(agentApiMock.createGeneration).toHaveBeenCalledTimes(1)
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
    expect(agentApiMock.createMessage).toHaveBeenCalledTimes(1)
    expect(agentApiMock.createGeneration).toHaveBeenCalledTimes(1)
  })
})
