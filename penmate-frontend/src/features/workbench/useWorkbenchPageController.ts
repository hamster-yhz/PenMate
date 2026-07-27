import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { StoryBibleChapterOption } from '@/components/workbench/story-bible/storyBibleTypes'
import { novelApi } from '@/api/modules/novel.api'
import { chapterApi } from '@/api/modules/chapter.api'
import { rbacApi } from '@/api/modules/rbac.api'
import type { ChapterAiUndoOperation } from '@/entities/chapter/model'
import { getSession } from '@/stores/session'
import { logoutCurrentSession } from '@/composables/auth/useAuthSession'
import { useWorkbenchContext } from '@/composables/workbench/useWorkbenchContext'
import { createChapterLoadGuard, useWorkbenchDraft } from '@/composables/workbench/useWorkbenchDraft'
import { useWorkbenchOutline } from '@/composables/workbench/useWorkbenchOutline'
import type { OutlineChapterNode } from '@/composables/workbench/workbenchOutline'
import { useWorkbenchEditor } from '@/composables/workbench/useWorkbenchEditor'
import { useChapterEditingSession } from '@/composables/workbench/useChapterEditingSession'
import { useWorkbenchIntegrations } from './useWorkbenchIntegrations'
import { useWorkbenchAgentController } from './useWorkbenchAgentController'
import { useWorkbenchProjectController } from './useWorkbenchProjectController'
import {
  layoutForPreset,
  normalizeStoredLayout,
  resolveResponsiveWorkbenchLayout,
  type WorkbenchLayoutPreset,
} from './workbenchLayout'

export const useWorkbenchPageController = () => {
  const router = useRouter()
  const route = useRoute()
  const session = getSession()
  const {
    projectId: initialProjectId,
    operatorId: initialOperatorId,
    ensureContext,
    username: sessionUsername,
    userEmail: sessionUserEmail,
  } = useWorkbenchContext({ query: route.query, session })
  const {
    saveDraft,
    flushDraft,
    clearDraft,
    markDraftSynced,
    markDraftConflicted,
    resolveStoredDraft,
    resolveEditorSeedContent,
  } = useWorkbenchDraft()
  const chapterLoadGuard = createChapterLoadGuard()

  const getCurrentProjectId = () => ensureContext().projectId || initialProjectId || ''
  const resolveOperatorId = () => {
    const { operatorId } = ensureContext()
    return operatorId || initialOperatorId || ''
  }
  const getContext = () => {
    const { projectId, operatorId } = ensureContext()
    return { projectId: projectId || null, operatorId: operatorId || null }
  }
  const getAgentProjectId = () => getCurrentProjectId()
  const getAgentOperatorId = () => resolveOperatorId()

  const username = ref('墨客')
  const userEmail = ref('moke@penmate.com')
  const userMenuOpen = ref(false)
  const workbenchInitError = ref('')
  const canAccessRbacAdmin = ref(false)
  const novelTitle = ref('未命名小说')
  const layoutPreset = ref<WorkbenchLayoutPreset>('balanced')
  const leftCollapsedPreference = ref(false)
  const leftPanelWidth = ref(220)
  const rightCollapsed = ref(false)
  const chatFocused = ref(false)
  const chatPanelWidth = ref(440)
  const viewportWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1440)
  const directoryOverlayOpen = ref(false)
  const showStyleManager = ref(false)
  const showPluginWorkshop = ref(false)
  const chapterContents = ref<Record<string, string>>({})
  const storyBibleChapters = ref<StoryBibleChapterOption[]>([])
  const attachedChapterIds = ref<string[]>([])
  const routeWorkbenchMode = () => route.query.mode === 'story-bible' ? 'story-bible'
    : route.query.mode === 'ledger' ? 'ledger' : 'writing'
  const workbenchMode = ref<'writing' | 'story-bible' | 'ledger'>(routeWorkbenchMode())
  const storyBibleNodeId = computed(() => (typeof route.query.nodeId === 'string' ? route.query.nodeId : ''))
  const storyBibleWorkspaceRef = ref<{ reload: () => Promise<void> } | null>(null)

  const responsiveLayout = computed(() => resolveResponsiveWorkbenchLayout({
    viewportWidth: viewportWidth.value,
    leftPanelWidth: leftPanelWidth.value,
    chatPanelWidth: chatPanelWidth.value,
    leftCollapsed: leftCollapsedPreference.value,
    rightCollapsed: rightCollapsed.value,
  }))
  const directoryOverlayMode = computed(() => workbenchMode.value === 'writing' && responsiveLayout.value.directoryOverlay)
  const leftCollapsed = computed(() => leftCollapsedPreference.value
    || (directoryOverlayMode.value && !directoryOverlayOpen.value))
  const effectiveChatPanelWidth = computed(() => responsiveLayout.value.chatPanelWidth)

  const layoutStorageKey = () => `penmate.layout.${session.userId || 'anonymous'}.${getCurrentProjectId() || 'none'}.${workbenchMode.value}`
  const applyLayoutState = (layout: ReturnType<typeof layoutForPreset>) => {
    layoutPreset.value = layout.preset
    leftPanelWidth.value = layout.leftPanelWidth
    chatPanelWidth.value = layout.chatPanelWidth
    leftCollapsedPreference.value = layout.leftCollapsed
    rightCollapsed.value = layout.rightCollapsed
    directoryOverlayOpen.value = false
  }
  const applyLayoutPreset = (preset: WorkbenchLayoutPreset) => applyLayoutState(layoutForPreset(preset))
  const resetLayoutPreset = () => applyLayoutPreset(layoutPreset.value)
  const restoreLayout = () => {
    try {
      const raw = localStorage.getItem(layoutStorageKey())
      const stored = raw ? JSON.parse(raw) : null
      applyLayoutState(normalizeStoredLayout(stored))
    } catch {
      applyLayoutState(normalizeStoredLayout(null))
    }
  }
  const toggleLeftPanel = () => {
    if (leftCollapsed.value) {
      leftCollapsedPreference.value = false
      directoryOverlayOpen.value = directoryOverlayMode.value
      return
    }
    directoryOverlayOpen.value = false
    leftCollapsedPreference.value = true
  }
  const openLeftPanel = () => {
    if (leftCollapsed.value) toggleLeftPanel()
  }
  const toggleRightPanel = () => {
    rightCollapsed.value = !rightCollapsed.value
  }
  const updateViewportWidth = () => {
    viewportWidth.value = window.innerWidth
  }
  const bindStoryBibleWorkspace = (instance: unknown) => {
    storyBibleWorkspaceRef.value = instance as { reload: () => Promise<void> } | null
  }

  const setWorkbenchMode = (mode: 'writing' | 'story-bible' | 'ledger') => {
    workbenchMode.value = mode
    const query = { ...route.query }
    if (mode === 'story-bible' || mode === 'ledger') query.mode = mode
    else {
      delete query.mode
      delete query.nodeId
    }
    void router.replace({ query })
  }
  const openStoryBible = (nodeId = '') => {
    workbenchMode.value = 'story-bible'
    void router.replace({ query: { ...route.query, mode: 'story-bible', ...(nodeId ? { nodeId } : {}) } })
  }
  const setChapterContent = (chapterId: string, content: string) => {
    chapterContents.value[chapterId] = content
  }

  let loadWorkbenchData: (projectId: string) => Promise<void> = async () => undefined

  const {
    outlineData,
    activeChapter,
    selectedChapterIds,
    currentChapterTitle,
    outlineOpBusy,
    pendingMoveUndo,
    loadOutline,
    selectChapter,
    toggleChapterSelection,
    addVolume,
    addChapter,
    deleteVolume,
    deleteChapter,
    renameNode,
    moveNode,
    undoLastMove,
  } = useWorkbenchOutline({
    getContext,
    reloadOutline: async () => {
      const projectId = getCurrentProjectId()
      if (projectId) await loadWorkbenchData(projectId)
    },
    createVolume: novelApi.createVolume,
    createChapter: novelApi.createChapter,
    deleteVolume: novelApi.deleteVolume,
    deleteChapter: novelApi.deleteChapter,
    updateVolume: novelApi.updateVolume,
    updateChapter: novelApi.updateChapter,
    moveDirectoryItem: novelApi.moveDirectoryItem,
    notify: (text) => message.warning(text),
    notifySuccess: (text) => message.success(text),
  })

  const orderedChapters = computed(() => outlineData.value.flatMap((volume) => volume.children)
    .map((chapter, index) => ({
      chapterId: chapter.chapterId || chapter.key,
      title: chapter.title,
      displayNo: index + 1,
    })))
  const resolvedAttachedChapterIds = computed(() => {
    const attached = new Set(attachedChapterIds.value)
    return orderedChapters.value.filter((chapter) => attached.has(chapter.chapterId)).map((chapter) => chapter.chapterId)
  })
  const attachedChapterRanges = computed(() => {
    const attached = new Set(resolvedAttachedChapterIds.value)
    const selected = orderedChapters.value.filter((chapter) => attached.has(chapter.chapterId))
    const ranges: Array<{ key: string; label: string; chapterIds: string[] }> = []
    for (const chapter of selected) {
      const previous = ranges[ranges.length - 1]
      const previousLastId = previous?.chapterIds[previous.chapterIds.length - 1]
      const previousIndex = previousLastId
        ? orderedChapters.value.findIndex((item) => item.chapterId === previousLastId)
        : -2
      const chapterIndex = orderedChapters.value.findIndex((item) => item.chapterId === chapter.chapterId)
      if (previous && chapterIndex === previousIndex + 1) {
        previous.chapterIds.push(chapter.chapterId)
        const first = orderedChapters.value.find((item) => item.chapterId === previous.chapterIds[0])!
        previous.key = previous.chapterIds.join(':')
        previous.label = `第${first.displayNo}章到第${chapter.displayNo}章`
      } else {
        ranges.push({
          key: chapter.chapterId,
          label: `第${chapter.displayNo}章 · ${chapter.title}`,
          chapterIds: [chapter.chapterId],
        })
      }
    }
    return ranges
  })
  const addAttachedChapters = (chapterIds: string[]) => {
    attachedChapterIds.value = [...new Set([...attachedChapterIds.value, ...chapterIds])]
  }
  const removeAttachedChapters = (chapterIds: string[]) => {
    const removed = new Set(chapterIds)
    attachedChapterIds.value = attachedChapterIds.value.filter((chapterId) => !removed.has(chapterId))
  }

  const {
    editorContent,
    wordCount,
    currentLine,
    currentCol,
    selectedText,
    saveHint: localSaveHint,
    onEditorInput: handleLocalEditorInput,
    updateCursorPos,
    editorUndo,
    editorRedo,
    saveContent: saveLocalContent,
    selectChapterDraft,
    bindEditor,
  } = useWorkbenchEditor({
    getActiveChapterKey: () => activeChapter.value,
    getProjectId: getCurrentProjectId,
    saveDraft,
    setChapterContent,
  })

  const chapterConflict = ref<{ chapterId: string; content: string } | null>(null)
  const chapterEditing = useChapterEditingSession({
    onSaved: markDraftSynced,
    onConflict: async (projectId, chapterId, content) => {
      await flushDraft(projectId, chapterId)
      await markDraftConflicted(projectId, chapterId)
      chapterConflict.value = { chapterId, content }
    },
  })
  const online = ref(typeof navigator === 'undefined' || navigator.onLine)
  const aiEditingChapterId = ref('')
  const aiGeneratedContent = ref('')
  const aiUndoOperations = ref<ChapterAiUndoOperation[]>([])
  const aiUndoBusyOperationId = ref('')
  const aiUndoBusyRunId = ref('')
  const aiUndoDismissBusyOperationId = ref('')
  const aiUndoDismissAllBusy = ref(false)
  const aiUndoClock = ref(Date.now())
  const availableAiUndoOperations = computed(() => aiUndoOperations.value
    .filter((operation) => {
      if (operation.status !== 'AVAILABLE') return false
      if (!operation.expiresAt) return true
      const expiresAt = Date.parse(operation.expiresAt)
      return !Number.isFinite(expiresAt) || expiresAt > aiUndoClock.value
    })
    .sort((left, right) => right.sequenceNo - left.sequenceNo))
  const aiEditingCurrentChapter = computed(() => Boolean(activeChapter.value)
    && (aiEditingChapterId.value === activeChapter.value || chapterEditing.leaseOwnerType.value === 'AI'))
  const aiPreviewContent = computed(() => aiGeneratedContent.value || editorContent.value)
  const currentChapterAiUndo = computed(() => availableAiUndoOperations.value.find(
    (operation) => operation.chapterId === activeChapter.value && operation.status === 'AVAILABLE',
  ) || null)
  const chapterConflictPending = computed(() => chapterConflict.value?.chapterId === activeChapter.value)
  const saveHint = computed(() => aiEditingCurrentChapter.value
    ? 'AI 正在编辑'
    : !online.value && activeChapter.value
        ? '离线 · 正文已在本地暂存'
        : chapterEditing.saveStatus.value || localSaveHint.value)
  const chapterReadOnly = computed(() => Boolean(activeChapter.value)
    && (aiEditingCurrentChapter.value || !chapterEditing.editable.value))
  const chapterLockReason = computed(() => aiEditingCurrentChapter.value
    ? 'AI 正在编辑当前章节，完成前用户无法修改' : chapterEditing.lockReason.value)
  const onEditorInput = (content: string) => {
    handleLocalEditorInput(content)
    aiUndoOperations.value = aiUndoOperations.value.filter((operation) => operation.chapterId !== activeChapter.value)
    chapterEditing.scheduleSave(content)
  }
  const saveContent = async () => {
    await saveLocalContent()
    const projectId = getCurrentProjectId()
    if (projectId && activeChapter.value) await flushDraft(projectId, activeChapter.value)
    await chapterEditing.flush(editorContent.value)
  }

  const useLatestChapterVersion = async () => {
    const conflict = chapterConflict.value
    const projectId = getCurrentProjectId()
    if (!conflict || !projectId || conflict.chapterId !== activeChapter.value) return
    const remote = await chapterEditing.open(projectId, conflict.chapterId)
    if (!remote.editable) return
    chapterContents.value[conflict.chapterId] = remote.content
    selectChapterDraft(remote.content)
    await clearDraft(projectId, conflict.chapterId)
    chapterConflict.value = null
  }

  const continueWithLocalDraft = async () => {
    const conflict = chapterConflict.value
    const projectId = getCurrentProjectId()
    if (!conflict || !projectId || conflict.chapterId !== activeChapter.value) return
    const remote = await chapterEditing.open(projectId, conflict.chapterId)
    if (!remote.editable) return
    chapterContents.value[conflict.chapterId] = conflict.content
    selectChapterDraft(conflict.content)
    saveDraft(projectId, conflict.chapterId, conflict.content)
    chapterEditing.scheduleSave(conflict.content)
    await chapterEditing.flush(conflict.content)
    if (chapterEditing.saveStatus.value === '已保存') chapterConflict.value = null
  }

  const updateConnectivity = () => {
    online.value = navigator.onLine
    chapterEditing.setOnline(online.value)
    if (!online.value) {
      const projectId = getCurrentProjectId()
      if (projectId && activeChapter.value) void flushDraft(projectId, activeChapter.value)
    }
  }
  const handleWindowBlur = () => {
    if (activeChapter.value) void saveContent()
  }

  const refreshEditorFromRemote = async (projectId: string, chapterId: string, requestId: number) => {
    const lease = await chapterEditing.open(projectId, chapterId)
    if (!chapterLoadGuard.isCurrent(chapterId, requestId)) return false
    const remoteContent = lease.content
    const content = (await resolveStoredDraft(projectId, chapterId)) ?? remoteContent
    chapterContents.value[chapterId] = content
    selectChapterDraft(content)
    if (content !== remoteContent && lease.editable) chapterEditing.scheduleSave(content)
    return true
  }

  const { activePlugins, currentModelName, currentReasoningLabel, loadActivePlugins, refreshActiveModelInfo, ensureModelConfigId } =
    useWorkbenchIntegrations({ getUserId: () => session.userId, getProjectId: getCurrentProjectId })
  const {
    boundStyleName,
    visibleMessages,
    showConversationPanel,
    conversationLoading,
    conversationList,
    deletedConversationList,
    recentlyDeletedConversation,
    runAttempts,
    chatInput,
    skillCatalog,
    activeSkills,
    skillCatalogLoading,
    isGenerating,
    isCancelling,
    isRetrying,
    canCancelRun,
    canRetryRun,
    generationPhase,
    generationStatusText,
    agentStatusDetailText,
    streamingAssistantMsgId,
    runtimeEventSource,
    currentConversationId,
    queuedRequest,
    contextUsage,
    safetyMode,
    safetyModeSaving,
    toggleConversationPanel,
    loadDeletedConversations,
    renameConversation,
    deleteConversation,
    restoreConversation,
    sendMessage: sendAgentMessage,
    cancelCurrentRun,
    retryCurrentRun,
    loadSkillCatalog,
    addActiveSkill,
    removeActiveSkill,
    requestContextCompression,
    withdrawQueuedRequest,
    loadSafetyMode,
    saveSafetyMode,
    isApprovalBusy,
    handleApprove,
    handleReject,
    bindChatContainer,
    showScrollToBottom,
    scrollChatToBottom,
    selectConversation: handleSelectConversation,
    createSession: handleCreateSession,
    resumeLatestSession,
    dispose: disposeChat,
  } = useWorkbenchAgentController({
    getContext,
    getProjectId: getAgentProjectId,
    getOperatorId: getAgentOperatorId,
    getActiveChapterKey: () => resolvedAttachedChapterIds.value[0] || '',
    getAttachedChapterIds: () => [...resolvedAttachedChapterIds.value],
    getSelectedText: () => selectedText.value,
    activePlugins,
    ensureModelConfigId,
    refreshActiveModelInfo,
    requestModelSelection: () => {
      void router.push('/profile?section=agent')
    },
    onMessageRegistered: () => {
      attachedChapterIds.value = activeChapter.value ? [activeChapter.value] : []
    },
    onRecoveryContext: (context) => {
      const chapterId = String(context.chapterId ?? '').trim()
      if (chapterId && chapterId !== '0') activeChapter.value = chapterId
      const plugins = Array.isArray(context.activePlugins) ? context.activePlugins : []
      activePlugins.value = plugins.map((item) => String(item)).filter(Boolean)
    },
  })

  const {
    updateTitle,
    selectOutlineChapter: selectProjectOutlineChapter,
    loadProject,
  } = useWorkbenchProjectController({
    novelTitle,
    storyBibleChapters,
    activeChapter,
    editorContent,
    chapterContents,
    getProjectId: getCurrentProjectId,
    saveDraft,
    flushDraft,
    selectChapter,
    chapterLoadGuard,
    resolveStoredDraft,
    resolveEditorSeedContent,
    selectChapterDraft,
    refreshEditorFromRemote,
    loadOutline,
    loadActivePlugins,
    refreshActiveModelInfo,
  })

  const handleOutlineSelectChapter = async (chapter: OutlineChapterNode) => {
    const chapterId = chapter.chapterId || chapter.key
    attachedChapterIds.value = [chapterId]
    await selectProjectOutlineChapter(chapter)
  }
  loadWorkbenchData = loadProject

  const chapterTitleById = (chapterId: string) => {
    for (const volume of outlineData.value) {
      const chapter = volume.children.find((item) => String(item.chapterId || item.key) === chapterId)
      if (chapter) return chapter.title
    }
    return chapterId === activeChapter.value ? currentChapterTitle.value : '未命名章节'
  }

  const refreshChapterAiUndo = async (chapterId: string) => {
    const projectId = getCurrentProjectId()
    if (!projectId || !chapterId) return
    try {
      aiUndoOperations.value = await chapterApi.listProjectAiUndo(projectId)
    } catch {
      // Undo availability is supplemental; chapter editing remains usable when this lookup fails.
    }
  }

  const reloadChapterAfterAi = async (chapterId: string) => {
    if (chapterId !== activeChapter.value) return
    const projectId = getCurrentProjectId()
    if (!projectId) return
    const lease = await chapterEditing.open(projectId, chapterId)
    chapterContents.value[chapterId] = lease.content
    selectChapterDraft(lease.content)
    await markDraftSynced(projectId, chapterId, lease.content)
  }

  const clearAiPreview = (chapterId: string) => {
    if (aiEditingChapterId.value !== chapterId) return
    aiEditingChapterId.value = ''
    aiGeneratedContent.value = ''
  }

  const applyAiPreviewDelta = (payload: Record<string, unknown>) => {
    const chapterId = String(payload.chapterId || '')
    if (!chapterId || aiEditingChapterId.value !== chapterId) return
    const text = String(payload.text ?? '')
    const offset = Number(payload.offset)
    if (!Number.isSafeInteger(offset) || offset < 0) {
      aiGeneratedContent.value += text
      return
    }
    if (offset > aiGeneratedContent.value.length) return
    if (aiGeneratedContent.value.slice(offset, offset + text.length) === text) return
    aiGeneratedContent.value = `${aiGeneratedContent.value.slice(0, offset)}${text}`
  }

  const undoAiEdit = async (operationId: string) => {
    const projectId = getCurrentProjectId()
    if (!projectId || !operationId || aiUndoBusyOperationId.value) return
    aiUndoBusyOperationId.value = operationId
    try {
      const operation = aiUndoOperations.value.find((item) => item.operationId === operationId)
      await chapterApi.undoAiEdit(projectId, operationId)
      aiUndoOperations.value = aiUndoOperations.value.filter((item) => item.operationId !== operationId)
      if (operation) await reloadChapterAfterAi(operation.chapterId)
      message.success('已撤回 AI 修改')
    } catch (error) {
      message.warning(error instanceof Error ? error.message : 'AI 修改无法撤回')
      if (activeChapter.value) await refreshChapterAiUndo(activeChapter.value)
    } finally {
      aiUndoBusyOperationId.value = ''
    }
  }

  const undoAiRun = async (runId: string) => {
    const projectId = getCurrentProjectId()
    if (!projectId || !runId || aiUndoBusyRunId.value) return
    aiUndoBusyRunId.value = runId
    const affectedChapters = aiUndoOperations.value.filter((item) => item.runId === runId).map((item) => item.chapterId)
    try {
      await chapterApi.undoRunAiEdits(projectId, runId)
      aiUndoOperations.value = aiUndoOperations.value.filter((item) => item.runId !== runId)
      if (affectedChapters.includes(activeChapter.value)) await reloadChapterAfterAi(activeChapter.value)
      message.success('已撤回本次任务的全部 AI 修改')
    } catch (error) {
      message.warning(error instanceof Error ? error.message : '无法撤回本次全部修改')
      if (activeChapter.value) await refreshChapterAiUndo(activeChapter.value)
    } finally {
      aiUndoBusyRunId.value = ''
    }
  }

  const dismissAiUndo = async (operationId: string) => {
    const projectId = getCurrentProjectId()
    if (!projectId || !operationId || aiUndoDismissBusyOperationId.value || aiUndoDismissAllBusy.value) return
    aiUndoDismissBusyOperationId.value = operationId
    try {
      const result = await chapterApi.dismissAiUndo(projectId, [operationId])
      const dismissedIds = new Set((result.operationIds || [operationId]).map(String))
      aiUndoOperations.value = aiUndoOperations.value.filter((item) => !dismissedIds.has(item.operationId))
    } catch (error) {
      message.warning(error instanceof Error ? error.message : '无法放弃这条撤回记录')
      if (activeChapter.value) await refreshChapterAiUndo(activeChapter.value)
    } finally {
      aiUndoDismissBusyOperationId.value = ''
    }
  }

  const dismissAllAiUndo = async () => {
    const projectId = getCurrentProjectId()
    const operationIds = availableAiUndoOperations.value.map((operation) => operation.operationId)
    if (!projectId || !operationIds.length || aiUndoDismissAllBusy.value) return
    aiUndoDismissAllBusy.value = true
    try {
      const result = await chapterApi.dismissAiUndo(projectId, operationIds)
      const dismissedIds = new Set((result.operationIds || operationIds).map(String))
      aiUndoOperations.value = aiUndoOperations.value.filter((item) => !dismissedIds.has(item.operationId))
    } catch (error) {
      message.warning(error instanceof Error ? error.message : '无法放弃全部撤回记录')
      if (activeChapter.value) await refreshChapterAiUndo(activeChapter.value)
    } finally {
      aiUndoDismissAllBusy.value = false
    }
  }

  const sendMessage = async () => {
    if (activeChapter.value && !canCancelRun.value) {
      await saveContent()
      if (chapterEditing.saveStatus.value.includes('失败')
        || chapterEditing.saveStatus.value === '版本冲突'
        || chapterEditing.leaseOwnerType.value === 'AI') {
        message.warning('当前章节尚未同步，暂时不能启动 AI')
        return
      }
    }
    await sendAgentMessage()
  }

  const navigateFromUserMenu = (path: string) => {
    userMenuOpen.value = false
    void router.push(path)
  }
  const handleLogout = async () => {
    await logoutCurrentSession()
    await router.replace('/login')
  }
  const loadAdminAccess = async () => {
    canAccessRbacAdmin.value = false
    if (!session.userId) return
    try {
      const menus = await rbacApi.listProfileMenus(session.userId)
      canAccessRbacAdmin.value = (menus || []).some((menu) => String(menu.path || '').startsWith('/admin'))
    } catch {
      canAccessRbacAdmin.value = false
    }
  }
  const initializeWorkbench = async () => {
    workbenchInitError.value = ''
    username.value = sessionUsername || username.value
    userEmail.value = sessionUserEmail || userEmail.value
    try {
      restoreLayout()
      await Promise.all([loadAdminAccess(), loadSafetyMode()])
      const projectId = getCurrentProjectId()
      if (projectId) {
        await loadWorkbenchData(projectId)
        await loadSkillCatalog()
        await resumeLatestSession()
      } else await refreshActiveModelInfo()
    } catch (error) {
      workbenchInitError.value = error instanceof Error ? error.message : '工作台初始化失败'
    }
  }

  let undoExpiryTimerId: number | null = null
  onMounted(() => {
    window.addEventListener('resize', updateViewportWidth)
    window.addEventListener('online', updateConnectivity)
    window.addEventListener('offline', updateConnectivity)
    window.addEventListener('blur', handleWindowBlur)
    undoExpiryTimerId = window.setInterval(() => {
      aiUndoClock.value = Date.now()
      if (activeChapter.value) void refreshChapterAiUndo(activeChapter.value)
    }, 30_000)
    updateViewportWidth()
    updateConnectivity()
    void initializeWorkbench()
  })
  onUnmounted(() => {
    window.removeEventListener('resize', updateViewportWidth)
    window.removeEventListener('online', updateConnectivity)
    window.removeEventListener('offline', updateConnectivity)
    window.removeEventListener('blur', handleWindowBlur)
    if (undoExpiryTimerId !== null) window.clearInterval(undoExpiryTimerId)
    disposeChat()
    void saveContent().finally(() => chapterEditing.dispose())
  })
  watch(editorContent, (value) => {
    chapterContents.value[activeChapter.value] = value
  })
  watch(
    () => route.query.mode,
    (mode) => {
      workbenchMode.value = mode === 'story-bible' ? 'story-bible' : mode === 'ledger' ? 'ledger' : 'writing'
    },
  )
  watch(runtimeEventSource, (event) => {
    if (event?.eventName === 'run.completed' && storyBibleWorkspaceRef.value) {
      void storyBibleWorkspaceRef.value.reload()
    }
    const payload = event?.payload || {}
    const chapterId = String(payload.chapterId || '')
    if (event?.eventName === 'chapter.edit.started' && chapterId) {
      chapterEditing.lockForAi(chapterId)
      const projectId = getCurrentProjectId()
      if (projectId) void flushDraft(projectId, chapterId).then(() => markDraftConflicted(projectId, chapterId))
      aiEditingChapterId.value = chapterId
      aiGeneratedContent.value = ''
    }
    if (event?.eventName === 'chapter.edit.delta') applyAiPreviewDelta(payload)
    if (event?.eventName === 'chapter.edit.snapshot' && aiEditingChapterId.value === chapterId) {
      aiGeneratedContent.value = String(payload.content || '')
    }
    if (event?.eventName === 'chapter.edit.completed' && chapterId) {
      const operationId = String(payload.operationId || '')
      const runId = String(payload.runId || event.runId || '')
      if (operationId) {
        const operation: ChapterAiUndoOperation = {
          operationId,
          runId,
          chapterId,
          chapterTitle: chapterTitleById(chapterId),
          status: 'AVAILABLE',
          sequenceNo: 0,
          expiresAt: payload.undoExpiresAt == null ? null : String(payload.undoExpiresAt),
        }
        aiUndoOperations.value = [
          ...aiUndoOperations.value.filter((item) => item.operationId !== operationId),
          operation,
        ]
      }
      void reloadChapterAfterAi(chapterId).finally(() => clearAiPreview(chapterId))
    }
    if ((event?.eventName === 'chapter.edit.failed' || event?.eventName === 'chapter.edit.cancelled') && chapterId) {
      void reloadChapterAfterAi(chapterId).finally(() => clearAiPreview(chapterId))
    }
  })
  watch(activeChapter, (chapterId) => {
    if (chapterId) {
      attachedChapterIds.value = [chapterId]
      void refreshChapterAiUndo(chapterId)
    }
  })
  watch([layoutPreset, leftPanelWidth, chatPanelWidth, leftCollapsedPreference, rightCollapsed, workbenchMode], () => {
    localStorage.setItem(layoutStorageKey(), JSON.stringify({
      preset: layoutPreset.value,
      leftPanelWidth: leftPanelWidth.value,
      chatPanelWidth: chatPanelWidth.value,
      leftCollapsed: leftCollapsedPreference.value,
      rightCollapsed: rightCollapsed.value,
    }))
  })
  watch(workbenchMode, restoreLayout)
  watch(directoryOverlayMode, (overlayMode) => {
    if (!overlayMode) directoryOverlayOpen.value = false
  })

  return {
    router,
    session,
    username,
    userEmail,
    userMenuOpen,
    workbenchInitError,
    canAccessRbacAdmin,
    novelTitle,
    layoutPreset,
    applyLayoutPreset,
    resetLayoutPreset,
    leftCollapsed,
    directoryOverlayMode,
    toggleLeftPanel,
    openLeftPanel,
    leftPanelWidth,
    rightCollapsed,
    toggleRightPanel,
    chatFocused,
    chatPanelWidth,
    effectiveChatPanelWidth,
    showStyleManager,
    showPluginWorkshop,
    storyBibleChapters,
    workbenchMode,
    storyBibleNodeId,
    bindStoryBibleWorkspace,
    setWorkbenchMode,
    openStoryBible,
    getCurrentProjectId,
    resolveOperatorId,
    outlineData,
    activeChapter,
    selectedChapterIds,
    attachedChapterRanges,
    currentChapterTitle,
    outlineOpBusy,
    pendingMoveUndo,
    toggleChapterSelection,
    addAttachedChapters,
    removeAttachedChapters,
    renameNode,
    moveNode,
    undoLastMove,
    addVolume,
    addChapter,
    deleteVolume,
    deleteChapter,
    editorContent,
    wordCount,
    currentLine,
    currentCol,
    selectedText,
    saveHint,
    chapterReadOnly,
    chapterLockReason,
    chapterConflictPending,
    aiEditingCurrentChapter,
    aiPreviewContent,
    currentChapterAiUndo,
    aiUndoOperations: availableAiUndoOperations,
    aiUndoBusyOperationId,
    aiUndoBusyRunId,
    aiUndoDismissBusyOperationId,
    aiUndoDismissAllBusy,
    undoAiEdit,
    undoAiRun,
    dismissAiUndo,
    dismissAllAiUndo,
    onEditorInput,
    updateCursorPos,
    editorUndo,
    editorRedo,
    saveContent,
    useLatestChapterVersion,
    continueWithLocalDraft,
    bindEditor,
    activePlugins,
    boundStyleName,
    currentModelName,
    currentReasoningLabel,
    visibleMessages,
    showConversationPanel,
    conversationLoading,
    conversationList,
    deletedConversationList,
    recentlyDeletedConversation,
    runAttempts,
    chatInput,
    skillCatalog,
    activeSkills,
    skillCatalogLoading,
    isGenerating,
    isCancelling,
    isRetrying,
    canCancelRun,
    canRetryRun,
    generationPhase,
    generationStatusText,
    agentStatusDetailText,
    streamingAssistantMsgId,
    currentConversationId,
    queuedRequest,
    contextUsage,
    safetyMode,
    safetyModeSaving,
    toggleConversationPanel,
    loadDeletedConversations,
    renameConversation,
    deleteConversation,
    restoreConversation,
    sendMessage,
    cancelCurrentRun,
    retryCurrentRun,
    loadSkillCatalog,
    addActiveSkill,
    removeActiveSkill,
    requestContextCompression,
    withdrawQueuedRequest,
    saveSafetyMode,
    isApprovalBusy,
    handleApprove,
    handleReject,
    bindChatContainer,
    showScrollToBottom,
    scrollChatToBottom,
    handleSelectConversation,
    handleCreateSession,
    handleOutlineSelectChapter,
    updateTitle,
    navigateFromUserMenu,
    handleLogout,
    initializeWorkbench,
  }
}
