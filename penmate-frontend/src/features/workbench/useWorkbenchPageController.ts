import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { StoryBibleChapterOption } from '@/components/workbench/story-bible/storyBibleTypes'
import { novelApi } from '@/api/modules/novel.api'
import { outlineApi } from '@/api/modules/outline.api'
import { chapterApi } from '@/api/modules/chapter.api'
import { getSession } from '@/stores/session'
import { logoutCurrentSession } from '@/composables/auth/useAuthSession'
import { useWorkbenchContext } from '@/composables/workbench/useWorkbenchContext'
import { createChapterLoadGuard, useWorkbenchDraft } from '@/composables/workbench/useWorkbenchDraft'
import { useWorkbenchOutline } from '@/composables/workbench/useWorkbenchOutline'
import { useWorkbenchEditor } from '@/composables/workbench/useWorkbenchEditor'
import { useWorkbenchVersions } from '@/composables/workbench/useWorkbenchVersions'
import {
  hasObjectKeyInStorageUrl,
  normalizeObjectStorageUrl,
  resolveDirectUploadTarget,
} from '@/composables/workbench/workbenchStorage'
import iconOutline from '@/assets/images/icon-outline.webp'
import { useWorkbenchIntegrations } from './useWorkbenchIntegrations'
import { useWorkbenchAgentController } from './useWorkbenchAgentController'
import { useWorkbenchProjectController } from './useWorkbenchProjectController'

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
  const { saveDraft, clearDraft, resolveStoredDraft, resolveEditorSeedContent } = useWorkbenchDraft()
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
  const leftCollapsed = ref(false)
  const rightCollapsed = ref(typeof window !== 'undefined' && window.matchMedia('(max-width: 1080px)').matches)
  const showStyleManager = ref(false)
  const showPluginWorkshop = ref(false)
  const showModelSettings = ref(false)
  const chapterContents = ref<Record<string, string>>({})
  const storyBibleChapters = ref<StoryBibleChapterOption[]>([])
  const activeLeftTab = ref('outline')
  const leftTabs = ref([{ key: 'outline', label: '大纲', icon: iconOutline }])
  const workbenchMode = ref<'writing' | 'story-bible'>(route.query.mode === 'story-bible' ? 'story-bible' : 'writing')
  const storyBibleNodeId = computed(() => (typeof route.query.nodeId === 'string' ? route.query.nodeId : ''))
  const storyBibleWorkspaceRef = ref<{ reload: () => Promise<void> } | null>(null)
  const bindStoryBibleWorkspace = (instance: unknown) => {
    storyBibleWorkspaceRef.value = instance as { reload: () => Promise<void> } | null
  }

  const setWorkbenchMode = (mode: 'writing' | 'story-bible') => {
    workbenchMode.value = mode
    const query = { ...route.query }
    if (mode === 'story-bible') query.mode = 'story-bible'
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
    currentChapterTitle,
    outlineOpBusy,
    loadOutline,
    selectChapter,
    addVolume,
    addChapter,
    deleteVolume,
    deleteChapter,
    renameNode,
    moveNode,
  } = useWorkbenchOutline({
    getContext,
    reloadOutline: async () => {
      const projectId = getCurrentProjectId()
      if (projectId) await loadWorkbenchData(projectId)
    },
    createOutlineNode: outlineApi.createNode,
    createChapter: novelApi.createChapter,
    deleteOutlineNode: outlineApi.deleteNode,
    deleteChapter: novelApi.deleteChapter,
    updateOutlineNode: outlineApi.updateNode,
    moveOutlineNode: outlineApi.moveNode,
    moveChapter: novelApi.moveChapter,
    notify: (text) => message.warning(text),
    notifySuccess: (text) => message.success(text),
  })

  const {
    editorRef,
    editorContent,
    wordCount,
    currentLine,
    currentCol,
    selectedText,
    saveHint,
    onEditorInput,
    updateCursorPos,
    editorUndo,
    editorRedo,
    wrapSelection,
    insertPrefix,
    mergeToEditor,
    replaceSelected,
    saveContent,
    selectChapterDraft,
  } = useWorkbenchEditor({
    getActiveChapterKey: () => activeChapter.value,
    getProjectId: getCurrentProjectId,
    saveDraft,
    setChapterContent,
  })

  const bindEditorTextarea = (instance: Element | { $el?: Element } | null) => {
    if (instance instanceof HTMLTextAreaElement) editorRef.value = instance
    else if (instance && '$el' in instance && instance.$el instanceof HTMLTextAreaElement)
      editorRef.value = instance.$el
    else editorRef.value = null
  }

  const fetchText = async (url: string) => (await fetch(url)).text()
  const uploadText = async (url: string, content: string) => {
    const response = await fetch(url, { method: 'PUT', body: content, headers: { 'Content-Type': 'text/plain' } })
    return {
      ok: response.ok,
      status: response.status,
      etag: response.headers.get('etag') || undefined,
      checksum: response.headers.get('x-amz-checksum-sha256') || undefined,
    }
  }

  const {
    selectedVersionNo,
    selectedVersionContent,
    versionBusy,
    versionDiffSummary,
    getCurrentChapterVersions,
    loadChapterVersions,
    viewSelectedVersion,
    restoreSelectedVersion,
    publishCurrentChapter,
    refreshEditorFromRemote,
  } = useWorkbenchVersions({
    getProjectId: getCurrentProjectId,
    getActiveChapterKey: () => activeChapter.value,
    getOperatorId: resolveOperatorId,
    getEditorContent: () => editorContent.value,
    setEditorContent: (content) => {
      editorContent.value = content
      setChapterContent(activeChapter.value, content)
    },
    setWordCount: (count) => {
      wordCount.value = count
    },
    setLastSnapshot: selectChapterDraft,
    resolveChapterContent: (projectId, chapterId, remoteContent, options) => {
      if (options?.preferRemote) {
        clearDraft(projectId, chapterId)
        return remoteContent
      }
      return resolveStoredDraft(projectId, chapterId) ?? remoteContent
    },
    resolveStoredDraft,
    clearDraft,
    beginChapterRequest: (chapterId) => chapterLoadGuard.begin(chapterId),
    isChapterRequestCurrent: (chapterId, requestId) => chapterLoadGuard.isCurrent(chapterId, requestId),
    listVersions: (projectId, chapterId) => chapterApi.listVersions(projectId, chapterId),
    getVersionSnapshotUrl: (projectId, chapterId, versionNo) =>
      chapterApi.getVersionSnapshotUrl(projectId, chapterId, String(versionNo)),
    getContentUrl: (projectId, chapterId) => chapterApi.getContentUrl(projectId, chapterId),
    restoreVersion: async (projectId, chapterId, versionNo, operatorId) => {
      await chapterApi.restoreVersion(projectId, chapterId, String(versionNo), operatorId)
    },
    publishChapter: async (projectId, chapterId, operatorId) => {
      await chapterApi.publishChapter(projectId, chapterId, operatorId)
    },
    getContentUploadUrl: (projectId, chapterId) => chapterApi.getContentUploadUrl(projectId, chapterId),
    commitContent: async (projectId, chapterId, operatorId, payload) => {
      await chapterApi.commitContent(projectId, chapterId, operatorId, payload)
    },
    createVersion: async (projectId, chapterId, payload) => {
      await chapterApi.createVersion(projectId, chapterId, payload)
    },
    resolveUploadTarget: resolveDirectUploadTarget,
    normalizeStorageUrl: normalizeObjectStorageUrl,
    hasObjectKeyInStorageUrl,
    fetchText,
    uploadText,
    notify: (text) => message.warning(text),
    notifySuccess: (text) => message.success(text),
  })

  const { activePlugins, currentModelName, loadActivePlugins, refreshActiveModelInfo, ensureModelConfigId } =
    useWorkbenchIntegrations({ getUserId: () => session.userId })
  const {
    boundStyleName,
    visibleMessages,
    showConversationPanel,
    conversationLoading,
    conversationList,
    chatInput,
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
    toggleConversationPanel,
    sendMessage,
    cancelCurrentRun,
    retryCurrentRun,
    isApprovalBusy,
    handleApprove,
    handleReject,
    bindChatContainer,
    selectConversation: handleSelectConversation,
    createSession: handleCreateSession,
    mergeMessageToEditor: handleMergeToEditor,
    replaceMessageSelection: handleReplaceSelected,
    resumeLatestSession,
    dispose: disposeChat,
  } = useWorkbenchAgentController({
    getContext,
    getProjectId: getAgentProjectId,
    getOperatorId: getAgentOperatorId,
    getActiveChapterKey: () => activeChapter.value,
    getSelectedText: () => selectedText.value,
    activePlugins,
    ensureModelConfigId,
    refreshActiveModelInfo,
    requestModelSelection: () => {
      showModelSettings.value = true
    },
    mergeToEditor,
    replaceSelected,
    onRecoveryContext: (context) => {
      const chapterId = String(context.chapterId ?? '').trim()
      if (chapterId && chapterId !== '0') activeChapter.value = chapterId
      const plugins = Array.isArray(context.activePlugins) ? context.activePlugins : []
      activePlugins.value = plugins.map((item) => String(item)).filter(Boolean)
    },
  })
  const onModelConfigSaved = () => void refreshActiveModelInfo()

  const {
    updateTitle,
    selectOutlineChapter: handleOutlineSelectChapter,
    loadProject,
  } = useWorkbenchProjectController({
    novelTitle,
    storyBibleChapters,
    activeChapter,
    editorContent,
    chapterContents,
    getProjectId: getCurrentProjectId,
    saveDraft,
    selectChapter,
    chapterLoadGuard,
    resolveStoredDraft,
    resolveEditorSeedContent,
    selectChapterDraft,
    refreshEditorFromRemote,
    loadChapterVersions,
    loadOutline,
    loadActivePlugins,
    refreshActiveModelInfo,
  })
  loadWorkbenchData = loadProject

  const navigateFromUserMenu = (path: string) => {
    userMenuOpen.value = false
    void router.push(path)
  }
  const handleLogout = async () => {
    await logoutCurrentSession()
    await router.replace('/login')
  }
  const initializeWorkbench = async () => {
    workbenchInitError.value = ''
    username.value = sessionUsername || username.value
    userEmail.value = sessionUserEmail || userEmail.value
    try {
      const projectId = getCurrentProjectId()
      if (projectId) {
        await loadWorkbenchData(projectId)
        await resumeLatestSession()
      } else await refreshActiveModelInfo()
    } catch (error) {
      workbenchInitError.value = error instanceof Error ? error.message : '工作台初始化失败'
    }
  }

  onMounted(() => void initializeWorkbench())
  onUnmounted(disposeChat)
  watch(editorContent, (value) => {
    chapterContents.value[activeChapter.value] = value
  })
  watch(
    () => route.query.mode,
    (mode) => {
      workbenchMode.value = mode === 'story-bible' ? 'story-bible' : 'writing'
    },
  )
  watch(runtimeEventSource, (event) => {
    if (event?.eventName === 'run.completed' && storyBibleWorkspaceRef.value) {
      void storyBibleWorkspaceRef.value.reload()
    }
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
    leftCollapsed,
    rightCollapsed,
    showStyleManager,
    showPluginWorkshop,
    showModelSettings,
    storyBibleChapters,
    activeLeftTab,
    leftTabs,
    workbenchMode,
    storyBibleNodeId,
    bindStoryBibleWorkspace,
    setWorkbenchMode,
    openStoryBible,
    getCurrentProjectId,
    resolveOperatorId,
    outlineData,
    activeChapter,
    currentChapterTitle,
    outlineOpBusy,
    renameNode,
    moveNode,
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
    onEditorInput,
    updateCursorPos,
    editorUndo,
    editorRedo,
    wrapSelection,
    insertPrefix,
    saveContent,
    bindEditorTextarea,
    selectedVersionNo,
    selectedVersionContent,
    versionBusy,
    versionDiffSummary,
    getCurrentChapterVersions,
    viewSelectedVersion,
    restoreSelectedVersion,
    publishCurrentChapter,
    activePlugins,
    boundStyleName,
    currentModelName,
    visibleMessages,
    showConversationPanel,
    conversationLoading,
    conversationList,
    chatInput,
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
    toggleConversationPanel,
    sendMessage,
    cancelCurrentRun,
    retryCurrentRun,
    isApprovalBusy,
    handleApprove,
    handleReject,
    bindChatContainer,
    handleSelectConversation,
    handleCreateSession,
    handleMergeToEditor,
    handleReplaceSelected,
    handleOutlineSelectChapter,
    updateTitle,
    navigateFromUserMenu,
    handleLogout,
    initializeWorkbench,
    onModelConfigSaved,
  }
}
