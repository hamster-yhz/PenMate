<template>
  <div class="workbench-page">
    <WorkbenchHeader
      :novel-title="novelTitle"
      :word-count="wordCount"
      :save-hint="saveHint"
      :username="username"
      :user-email="userEmail"
      :user-menu-open="userMenuOpen"
      :can-access-rbac-admin="canAccessRbacAdmin"
      :workbench-mode="workbenchMode"
      @go-home="router.push('/')"
      @update-title="updateTitle"
      @open-style-manager="showStyleManager = true"
      @open-plugin-workshop="showPluginWorkshop = true"
      @open-model-settings="showModelSettings = true"
      @toggle-user-menu="userMenuOpen = !userMenuOpen"
      @close-user-menu="userMenuOpen = false"
      @go-profile="navigateFromUserMenu('/profile')"
      @go-mybooks="navigateFromUserMenu('/mybooks')"
      @go-rbac-admin="navigateFromUserMenu('/admin/rbac')"
      @logout="handleLogout"
      @update:workbench-mode="setWorkbenchMode"
    />

    <div class="wb-main workbench-shell">
      <WorkbenchLeftPanel
        v-if="workbenchMode === 'writing'"
        :collapsed="leftCollapsed"
        :left-tabs="leftTabs"
        :active-left-tab="activeLeftTab"
        :outline-data="outlineData"
        :active-chapter="activeChapter"
        :outline-op-busy="outlineOpBusy"
        @toggle-collapse="leftCollapsed = !leftCollapsed"
        @update:active-left-tab="activeLeftTab = $event"
        @select-chapter="handleOutlineSelectChapter"
        @rename-node="renameNode($event as any)"
        @move-node="moveNode($event as any)"
        @add-volume="addVolume"
        @add-chapter="addChapter($event as any)"
        @delete-volume="deleteVolume($event as any)"
        @delete-chapter="deleteChapter($event as any)"
      />

      <WorkbenchEditorPanel
        v-if="workbenchMode === 'writing'"
        :current-chapter-title="currentChapterTitle"
        :selected-version-no="selectedVersionNo"
        :version-busy="versionBusy"
        :active-chapter="activeChapter"
        :versions="getCurrentChapterVersions()"
        :editor-textarea-ref="bindEditorTextarea"
        :editor-content="editorContent"
        :selected-text="selectedText"
        :version-diff-summary="versionDiffSummary"
        :current-line="currentLine"
        :current-col="currentCol"
        :selected-version-content="selectedVersionContent"
        @save="saveContent"
        @undo="editorUndo"
        @redo="editorRedo"
        @wrap-selection="wrapSelection($event[0], $event[1])"
        @insert-prefix="insertPrefix($event as string)"
        @update:selected-version-no="selectedVersionNo = $event"
        @restore-version="restoreSelectedVersion"
        @view-version="viewSelectedVersion"
        @publish-chapter="publishCurrentChapter"
        @update:editor-content="editorContent = $event"
        @input="onEditorInput"
        @cursor-activity="updateCursorPos"
      />

      <StoryBibleWorkspace
        v-else
        ref="storyBibleWorkspaceRef"
        :project-id="getCurrentProjectId()"
        :operator-id="resolveOperatorId()"
        :user-id="session.userId"
        :session-id="currentConversationId || undefined"
        :chapter-id="activeChapter"
        :chapters="storyBibleChapters"
        :project-title="novelTitle"
        :initial-node-id="storyBibleNodeId"
      />

      <WorkbenchRightPanel
        :collapsed="rightCollapsed"
        :current-model-name="currentModelName"
        :generation-status-text="generationStatusText"
        :agent-status-detail-text="agentStatusDetailText"
        :is-generating="isGenerating"
        :generation-phase="generationPhase"
        :show-conversation-panel="showConversationPanel"
        :conversation-loading="conversationLoading"
        :conversation-list="conversationList"
        :current-conversation-id="currentConversationId"
        :bound-style-name="boundStyleName"
        :bind-chat-container="bindChatContainer"
        :messages="visibleMessages"
        :streaming-assistant-msg-id="streamingAssistantMsgId"
        :is-approval-busy="isApprovalBusy"
        :chat-input="chatInput"
        :active-plugins="activePlugins"
        @toggle-collapse="rightCollapsed = !rightCollapsed"
        @toggle-history="toggleConversationPanel"
        @create-session="handleCreateSession"
        @select-conversation="handleSelectConversation"
        @merge-to-editor="handleMergeToEditor"
        @replace-selected="handleReplaceSelected"
        @approve="handleApprove"
        @reject="handleReject"
        @open-story-bible="openStoryBible"
        @update:chat-input="chatInput = $event"
        @send="sendMessage"
        @open-model-settings="showModelSettings = true"
      />

      <button
        type="button"
        class="mobile-chat-toggle"
        :title="rightCollapsed ? '打开 Agent 对话' : '关闭 Agent 对话'"
        @click="rightCollapsed = !rightCollapsed"
      >
        <MessageOutlined v-if="rightCollapsed" />
        <CloseOutlined v-else />
      </button>
    </div>

    <StyleManager
      :visible="showStyleManager"
      :project-id="getCurrentProjectId()"
      :operator-id="resolveOperatorId()"
      :session-id="currentConversationId"
      @close="showStyleManager = false"
    />
    <PluginWorkshop :visible="showPluginWorkshop" @close="showPluginWorkshop = false" />
    <ModelSettings :visible="showModelSettings" @close="showModelSettings = false" @saved="onModelConfigSaved" />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { CloseOutlined, MessageOutlined } from '@ant-design/icons-vue'
import StyleManager from '@/components/workbench/StyleManager.vue'
import PluginWorkshop from '@/components/workbench/PluginWorkshop.vue'
import ModelSettings from '@/components/workbench/ModelSettings.vue'
import WorkbenchHeader from '@/components/workbench/WorkbenchHeader.vue'
import WorkbenchLeftPanel from '@/components/workbench/WorkbenchLeftPanel.vue'
import WorkbenchEditorPanel from '@/components/workbench/WorkbenchEditorPanel.vue'
import WorkbenchRightPanel from '@/components/workbench/WorkbenchRightPanel.vue'
import StoryBibleWorkspace from '@/components/workbench/story-bible/StoryBibleWorkspace.vue'
import type { StoryBibleChapterOption } from '@/components/workbench/story-bible/storyBibleTypes'
import { novelApi } from '@/api/modules/novel.api'
import { outlineApi } from '@/api/modules/outline.api'
import { chapterApi } from '@/api/modules/chapter.api'
import { agentApi } from '@/api/modules/agent.api'
import { approvalApi } from '@/api/modules/approval.api'
import { pluginApi } from '@/api/modules/plugin.api'
import { modelApi } from '@/api/modules/model.api'
import { getSession, clearSession } from '@/stores/session'
import { authApi } from '@/api/modules/auth.api'
import { useWorkbenchContext } from '@/composables/workbench/useWorkbenchContext'
import { createChapterLoadGuard, useWorkbenchDraft } from '@/composables/workbench/useWorkbenchDraft'
import type { ChatMessage } from '@/components/workbench/workbenchTypes'
import type { OutlineChapterNode } from '@/composables/workbench/workbenchOutline'
import { useWorkbenchOutline } from '@/composables/workbench/useWorkbenchOutline'
import { useWorkbenchEditor } from '@/composables/workbench/useWorkbenchEditor'
import { useWorkbenchVersions } from '@/composables/workbench/useWorkbenchVersions'
import { useWorkbenchChat } from '@/composables/workbench/useWorkbenchChat'
import { useWorkbenchApprovals } from '@/composables/workbench/useWorkbenchApprovals'
import { useWorkbenchSessionRecovery } from '@/composables/workbench/useWorkbenchSessionRecovery'
import {
  hasObjectKeyInStorageUrl,
  normalizeObjectStorageUrl,
  resolveDirectUploadTarget,
} from '@/composables/workbench/workbenchStorage'
import iconOutline from '@/assets/images/icon-outline.png'
import { pickBusinessArray, pickBusinessRecord } from '@/utils/apiPayload'

const router = useRouter()
const route = useRoute()
const session = getSession()
const {
  projectId: initialProjectId,
  operatorId: initialOperatorId,
  ensureContext,
  username: sessionUsername,
  userEmail: sessionUserEmail,
} = useWorkbenchContext({
  query: route.query,
  session,
})
const { saveDraft, clearDraft, resolveStoredDraft, resolveEditorSeedContent } = useWorkbenchDraft()
const chapterLoadGuard = createChapterLoadGuard()

const getCurrentProjectId = () => ensureContext().projectId || initialProjectId || ''
const resolveOperatorId = () => {
  const { operatorId } = ensureContext()
  return operatorId || initialOperatorId || ''
}
const getContext = () => {
  const { projectId, operatorId } = ensureContext()
  return {
    projectId: projectId || null,
    operatorId: operatorId || null,
  }
}
const getAgentProjectId = () => getCurrentProjectId()
const getAgentOperatorId = () => resolveOperatorId()
const getAgentContext = () => {
  const projectId = getAgentProjectId()
  const operatorId = getAgentOperatorId()
  return {
    projectId: projectId || null,
    operatorId: operatorId || null,
  }
}

const username = ref('墨客')
const userEmail = ref('moke@penmate.com')
const userMenuOpen = ref(false)
const canAccessRbacAdmin = ref(false)
const novelTitle = ref('未命名小说')
const leftCollapsed = ref(false)
const rightCollapsed = ref(typeof window !== 'undefined' && window.matchMedia('(max-width: 1080px)').matches)
const showStyleManager = ref(false)
const showPluginWorkshop = ref(false)
const showModelSettings = ref(false)
const chatRef = ref<HTMLElement | null>(null)
const chapterContents = ref<Record<string, string>>({})
const storyBibleChapters = ref<StoryBibleChapterOption[]>([])
const activeLeftTab = ref('outline')
const leftTabs = ref([
  { key: 'outline', label: '大纲', icon: iconOutline },
])
const workbenchMode = ref<'writing' | 'story-bible'>(route.query.mode === 'story-bible' ? 'story-bible' : 'writing')
const storyBibleNodeId = computed(() => typeof route.query.nodeId === 'string' ? route.query.nodeId : '')
const storyBibleWorkspaceRef = ref<{ reload: () => Promise<void> } | null>(null)
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
    if (projectId) {
      await loadWorkbenchData(projectId)
    }
  },
  createOutlineNode: outlineApi.createNode,
  createChapter: novelApi.createChapter,
  deleteOutlineNode: outlineApi.deleteNode,
  deleteChapter: novelApi.deleteChapter,
  updateOutlineNode: outlineApi.updateNode,
  moveOutlineNode: outlineApi.moveNode,
  moveChapter: novelApi.moveChapter,
  notify: (warningMessage: string) => {
    message.warning(warningMessage)
  },
  notifySuccess: (successMessage: string) => {
    message.success(successMessage)
  },
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
  if (instance instanceof HTMLTextAreaElement) {
    editorRef.value = instance
    return
  }
  if (instance && '$el' in instance && instance.$el instanceof HTMLTextAreaElement) {
    editorRef.value = instance.$el
    return
  }
  editorRef.value = null
}

const fetchText = async (url: string) => {
  const response = await fetch(url)
  return response.text()
}

const uploadText = async (url: string, content: string) => {
  const response = await fetch(url, {
    method: 'PUT',
    body: content,
    headers: { 'Content-Type': 'text/plain' },
  })
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
  setEditorContent: (content: string) => {
    editorContent.value = content
    setChapterContent(activeChapter.value, content)
  },
  setWordCount: (count: number) => {
    wordCount.value = count
  },
  setLastSnapshot: (content: string) => {
    selectChapterDraft(content)
  },
  resolveChapterContent: (projectId: string, chapterId: string, remoteContent: string, options?: { preferRemote?: boolean }) => {
    if (options?.preferRemote) {
      clearDraft(projectId, chapterId)
      return remoteContent
    }
    const draft = resolveStoredDraft(projectId, chapterId)
    return draft ?? remoteContent
  },
  resolveStoredDraft,
  clearDraft,
  beginChapterRequest: (chapterId: string) => chapterLoadGuard.begin(chapterId),
  isChapterRequestCurrent: (chapterId: string, requestId: number) => chapterLoadGuard.isCurrent(chapterId, requestId),
  listVersions: async (projectId: string, chapterId: string) => chapterApi.listVersions(projectId, chapterId),
  getVersionSnapshotUrl: async (projectId: string, chapterId: string, versionNo: number) => chapterApi.getVersionSnapshotUrl(projectId, chapterId, String(versionNo)),
  getContentUrl: async (projectId: string, chapterId: string) => chapterApi.getContentUrl(projectId, chapterId),
  restoreVersion: async (projectId: string, chapterId: string, versionNo: number, operatorId: string) => {
    await chapterApi.restoreVersion(projectId, chapterId, String(versionNo), operatorId)
  },
  publishChapter: async (projectId: string, chapterId: string, operatorId: string) => {
    await chapterApi.publishChapter(projectId, chapterId, operatorId)
  },
  getContentUploadUrl: async (projectId: string, chapterId: string) => chapterApi.getContentUploadUrl(projectId, chapterId),
  commitContent: async (projectId: string, chapterId: string, operatorId: string, payload: Record<string, unknown>) => {
    await chapterApi.commitContent(projectId, chapterId, operatorId, payload)
  },
  createVersion: async (projectId: string, chapterId: string, payload: Record<string, unknown>) => {
    await chapterApi.createVersion(projectId, chapterId, payload)
  },
  resolveUploadTarget: resolveDirectUploadTarget,
  normalizeStorageUrl: normalizeObjectStorageUrl,
  hasObjectKeyInStorageUrl,
  fetchText,
  uploadText,
  notify: (warningMessage: string) => {
    message.warning(warningMessage)
  },
  notifySuccess: (successMessage: string) => {
    message.success(successMessage)
  },
})

const activePlugins = ref<string[]>([])
const activeModelConfigId = ref<string | null>(null)
const boundStyleName = ref('')
const pickModelConfigId = (item: Record<string, unknown>) => {
  if (typeof item.modelConfigId !== 'string') {
    return null
  }
  const trimmed = item.modelConfigId.trim()
  return trimmed || null
}
const pickConversationId = (item: Record<string, unknown>) => String(item.sessionId ?? '').trim()
const syncBoundStyleName = (session: Record<string, unknown> | null | undefined) => {
  const boundStyle = session && typeof session === 'object'
    ? (session.boundStyle as Record<string, unknown> | null | undefined)
    : null
  boundStyleName.value = String(boundStyle?.name || '')
}

const debugChatState = (stage: string, extra: Record<string, unknown> = {}) => {
  console.info('[agent-ui] chat-state', {
    stage,
    isGenerating: isGenerating.value,
    generationPhase: generationPhase.value,
    generationTaskStatus: generationTaskStatus.value,
    messageCount: messages.value.length,
    lastMessageRole: messages.value[messages.value.length - 1]?.role || '',
    lastMessageLength: messages.value[messages.value.length - 1]?.text?.length || 0,
    ...extra,
  })
}

const openAgentRunStream = (projectId: string, runId: string, after = '0') => {
  console.info('[agent-ui] run-stream-open', {
    projectId,
    runId,
    after,
  })
  return agentApi.openRunStream(projectId, runId, after)
}

const bindChatContainer = (element: HTMLElement | null) => {
  chatRef.value = element
}

const scrollChat = () => {
  const container = chatRef.value
  if (!container) return
  container.scrollTop = container.scrollHeight
}

const toPluginName = (item: Record<string, unknown>) => {
  const name = String(item.pluginName || item.name || item.pluginCode || '').trim()
  return name || '未命名插件'
}

const loadActivePlugins = async (projectId: string) => {
  if (!projectId) {
    activePlugins.value = []
    return
  }
  try {
    const installs = (await pluginApi.listProjectPlugins(projectId)) as Array<Record<string, unknown>>
    activePlugins.value = installs.filter((item) => item.enabled !== false).map(toPluginName)
  } catch {
    activePlugins.value = []
  }
}

const currentModelName = ref('')

const extractPreferenceRecord = (payload: unknown): Record<string, unknown> => {
  if (!payload || typeof payload !== 'object') {
    return {}
  }

  const record = pickBusinessRecord(payload)
  const nestedPreferences = record.preferences
  if (nestedPreferences && typeof nestedPreferences === 'object') {
    return nestedPreferences as Record<string, unknown>
  }

  const nestedConfig = record.config
  if (nestedConfig && typeof nestedConfig === 'object') {
    return nestedConfig as Record<string, unknown>
  }

  return record
}

const refreshActiveModelInfo = async () => {
  const userId = session.userId
  if (!userId) {
    activeModelConfigId.value = null
    currentModelName.value = ''
    return null
  }
  try {
    const detail = pickBusinessRecord(await modelApi.getUserModelPreferences(userId))
    const preferenceRecord = extractPreferenceRecord(detail)
    const configs = Array.isArray(detail.candidateConfigs)
      ? (detail.candidateConfigs as Array<Record<string, unknown>>)
      : Array.isArray(preferenceRecord.candidateConfigs)
        ? (preferenceRecord.candidateConfigs as Array<Record<string, unknown>>)
        : Array.isArray(detail.modelConfigs)
          ? (detail.modelConfigs as Array<Record<string, unknown>>)
          : Array.isArray(preferenceRecord.modelConfigs)
            ? (preferenceRecord.modelConfigs as Array<Record<string, unknown>>)
            : []
    const preferredId = typeof preferenceRecord.mainAgentModelConfigId === 'string' ? preferenceRecord.mainAgentModelConfigId.trim() : ''
    const preferred = configs.find((item) => pickModelConfigId(item) === preferredId) || configs[0]
    const modelConfigId = preferred ? pickModelConfigId(preferred) : null
    activeModelConfigId.value = modelConfigId
    currentModelName.value = String(preferred?.modelName || '').trim()
    return activeModelConfigId.value
  } catch {
    activeModelConfigId.value = null
    currentModelName.value = ''
    return null
  }
}

const ensureModelConfigId = async () => (await refreshActiveModelInfo()) || ''

const {
  messages,
  showConversationPanel,
  conversationLoading,
  conversationList,
  chatInput,
  isGenerating,
  generationPhase,
  generationTaskStatus,
  generationStatusText,
  agentStatusDetailText,
  streamingAssistantMsgId,
  runtimeEventSource,
  currentConversationId,
  loadConversationList,
  toggleConversationPanel,
  sendMessage,
  resumeRunningRun,
  hydrateFromRecoverySnapshot,
} = useWorkbenchChat({
  getContext: getAgentContext,
  getCurrentProjectId: getAgentProjectId,
  getActiveChapterKey: () => activeChapter.value,
  getSelectedText: () => selectedText.value,
  getActivePlugins: () => activePlugins.value,
  ensureModelConfigId,
  refreshActiveModelInfo,
  listSessions: (projectId) => agentApi.listSessions(projectId),
  createSession: (projectId, payload) => agentApi.createSession(projectId, payload),
  getSessionRecovery: (projectId, sessionId) => agentApi.getSessionRecovery(projectId, sessionId),
  createTurn: async (projectId, sessionId, payload) => {
    debugChatState('create-turn-request', {
      projectId,
      sessionId,
      operatorId: payload.operatorId,
      taskType: (payload.taskRequest as Record<string, unknown> | undefined)?.taskType || '',
      chapterId: (payload.taskRequest as Record<string, unknown> | undefined)?.chapterId ?? null,
      activePluginCount: Array.isArray((payload.taskRequest as Record<string, unknown> | undefined)?.activePlugins)
        ? ((payload.taskRequest as Record<string, unknown> | undefined)?.activePlugins as unknown[]).length
        : 0,
      userMessageLength: String(payload.userMessage || '').length,
    })
    const result = pickBusinessRecord(await agentApi.createTurn(projectId, sessionId, payload)) as Record<string, unknown>
    syncBoundStyleName((result.session as Record<string, unknown> | null | undefined) || null)
    debugChatState('create-turn-created', {
      projectId,
      sessionId,
      runId: String((result.activeRun as Record<string, unknown> | null | undefined)?.runId ?? ''),
      runStatus: String((result.activeRun as Record<string, unknown> | null | undefined)?.runStatus ?? ''),
      sessionStatus: String((result.session as Record<string, unknown> | null | undefined)?.status ?? ''),
    })
    return result
  },
  openRunStream: (projectId, runId, after) => {
    const resolvedRunId = String(runId ?? '').trim()
    if (!resolvedRunId) {
      throw new Error('缺少 sessionId，无法打开 turn stream')
    }
    return openAgentRunStream(projectId, resolvedRunId, after)
  },
  addStreamListener: agentApi.addStreamListener,
  scrollChat,
  nextTick,
  notifyWarning: (warningMessage: string) => {
    message.warning(warningMessage)
  },
  debugChatState,
  onRequireModelSelection: () => {
    showModelSettings.value = true
  },
})

const visibleMessages = computed(() => messages.value.filter((messageItem) => {
  if (messageItem.role !== 'assistant') {
    return true
  }
  const hasText = String(messageItem.text || '').trim().length > 0
  const hasApproval = !!messageItem.approval
  const isStreamingMessage = streamingAssistantMsgId.value != null
    && String(messageItem.id) === String(streamingAssistantMsgId.value)
  return hasText || hasApproval || isStreamingMessage
}))

const { isApprovalBusy, handleApprove, handleReject } = useWorkbenchApprovals({
  getContext,
  getMessages: () => messages.value,
  approve: approvalApi.approve,
  reject: approvalApi.reject,
  notifyWarning: (warningMessage: string) => {
    message.warning(warningMessage)
  },
})

const sessionRecovery = useWorkbenchSessionRecovery({
  getSessionRecovery: (projectId, sessionId) => agentApi.getSessionRecovery(projectId, sessionId),
  resumeSession: (projectId, sessionId, payload) => agentApi.resumeSession(projectId, sessionId, payload),
  openRunStream: (projectId, runId, after) => openAgentRunStream(projectId, runId, after),
  resumeRunningRun,
  hydrateStore: (snapshot) => {
    const normalizedSnapshot = pickBusinessRecord(snapshot)
    hydrateFromRecoverySnapshot(normalizedSnapshot)
    syncBoundStyleName((normalizedSnapshot.session as Record<string, unknown> | null | undefined) || null)
    const workbenchContext = (normalizedSnapshot.workbenchContext || {}) as Record<string, unknown>
    const chapterId = String(workbenchContext.chapterId ?? '').trim()
    if (chapterId && chapterId !== '0') {
      activeChapter.value = chapterId
    }
    const plugins = Array.isArray(workbenchContext.activePlugins) ? workbenchContext.activePlugins : []
    activePlugins.value = plugins.map((item: unknown) => String(item)).filter(Boolean)
  },
})

const resumeWorkbenchSession = async (sessionId: string) => {
  const projectId = getAgentProjectId()
  const operatorId = getAgentOperatorId()
  if (!projectId || !sessionId || !operatorId) return
  await sessionRecovery.restore(projectId, sessionId, operatorId)
}

const handleSelectConversation = async (conversationId: string) => {
  if (!conversationId) return
  await resumeWorkbenchSession(conversationId)
  showConversationPanel.value = false
}

const handleCreateSession = async () => {
  const projectId = getAgentProjectId()
  const operatorId = getAgentOperatorId()
  if (!projectId || !operatorId) return
  const created = pickBusinessRecord(await agentApi.createSession(projectId, {
    userId: operatorId,
    title: '新会话',
  })) as Record<string, unknown>
  const sessionId = pickConversationId(created)
  if (!sessionId) return
  currentConversationId.value = sessionId
  messages.value = []
  boundStyleName.value = ''
  if (showConversationPanel.value) {
    await loadConversationList(projectId)
  }
}

const handleMergeToEditor = (messageItem: ChatMessage) => {
  if (!messageItem) return
  mergeToEditor(messageItem)
}

const handleReplaceSelected = (messageItem: ChatMessage) => {
  if (!messageItem) return
  replaceSelected(messageItem)
}

const onModelConfigSaved = () => {
  void refreshActiveModelInfo()
}

const normalizeBusinessId = (value: unknown) => {
  const normalized = String(value ?? '').trim()
  return normalized || null
}

const buildFallbackOutlineFromChapters = (chapters: Array<Record<string, unknown>>): Record<string, unknown>[] => {
  const volumeNodeId = 'virtual-volume-root'
  const chapterNodes = chapters
    .map((chapter) => {
      const chapterId = normalizeBusinessId(chapter.chapterId)
      const outlineNodeId = normalizeBusinessId(chapter.outlineNodeId)
      if (!outlineNodeId || !chapterId) {
        return null
      }
      return {
        outlineNodeId,
        chapterId,
        title: String(chapter.title ?? chapter.chapterTitle ?? '未命名章节'),
        nodeType: 'CHAPTER',
        parentId: volumeNodeId,
      } as Record<string, unknown>
    })
    .filter((item): item is Record<string, unknown> => item !== null)

  if (chapterNodes.length === 0) {
    return []
  }

  return [
    {
      outlineNodeId: volumeNodeId,
      title: '未分卷',
      nodeType: 'VOLUME',
    } as Record<string, unknown>,
    ...chapterNodes,
  ]
}

const hasRenderableVolumeNodes = (nodes: Array<Record<string, unknown>>) => nodes.some((node) => {
  const nodeType = String(node.nodeType ?? node.type ?? '').toUpperCase()
  return nodeType.includes('VOLUME')
})

const tryLoadChapterRemoteContent = async (chapterIdInput: string, requestId: number) => {
  const projectId = getCurrentProjectId()
  const chapterId = normalizeBusinessId(chapterIdInput)
  if (!projectId || !chapterId) return
  try {
    const loaded = await refreshEditorFromRemote(projectId, chapterId, requestId)
    if (!loaded) {
      if (!chapterLoadGuard.isCurrent(chapterId, requestId)) return
      const localDraft = resolveStoredDraft(projectId, chapterId)
      if (localDraft !== null) {
        chapterContents.value[chapterId] = localDraft
        selectChapterDraft(localDraft)
      }
    }
  } catch {
    if (!chapterLoadGuard.isCurrent(chapterId, requestId)) return
    const localDraft = resolveStoredDraft(projectId, chapterId)
    if (localDraft !== null) {
      chapterContents.value[chapterId] = localDraft
      selectChapterDraft(localDraft)
    }
  }
}

const updateTitle = (e: Event) => {
  const target = e.target as HTMLElement
  const nextTitle = String(target.textContent || '').trim() || '未命名小说'
  novelTitle.value = nextTitle
  const projectId = getCurrentProjectId()
  if (!projectId) return
  void novelApi.updateProject(projectId, { title: nextTitle }).catch(() => undefined)
}

const handleOutlineSelectChapter = async (chapter: OutlineChapterNode) => {
  const chapterKey = String(chapter.chapterId || chapter.key)
  const prevProjectId = getCurrentProjectId()
  if (prevProjectId && activeChapter.value) {
    saveDraft(prevProjectId, activeChapter.value, editorContent.value)
  }

  activeChapter.value = chapterKey
  selectChapter(chapter)

  const requestId = chapterLoadGuard.begin(chapterKey)
  const currentProjectId = getCurrentProjectId()
  if (currentProjectId) {
    const localDraft = resolveStoredDraft(currentProjectId, chapterKey)
    if (localDraft !== null) {
      chapterContents.value[chapterKey] = localDraft
      selectChapterDraft(localDraft)
    } else {
      const seedContent = resolveEditorSeedContent(undefined, null)
      chapterContents.value[chapterKey] = seedContent
      selectChapterDraft(seedContent)
    }
  }

  await tryLoadChapterRemoteContent(chapterKey, requestId)
  const projectId = getCurrentProjectId()
  if (projectId) {
    await loadChapterVersions(projectId, chapterKey)
  }
}

const loadWorkbenchData = async (projectId: string) => {
  if (!projectId) return
  const outlineResp = pickBusinessArray<Record<string, unknown>>(await outlineApi.listOutlineTree(projectId))
  const chapterResp = pickBusinessArray<Record<string, unknown>>(await novelApi.listChapters(projectId))
  storyBibleChapters.value = chapterResp.flatMap((chapter) => {
    const chapterId = normalizeBusinessId(chapter.chapterId)
    const displayNo = Number(chapter.displayNo)
    if (!chapterId || !Number.isInteger(displayNo) || displayNo < 1) return []
    return [{
      chapterId,
      displayNo,
      title: String(chapter.title ?? '').trim() || '未命名章节',
    }]
  })
  const chapterByOutlineNodeId = Object.fromEntries(
    chapterResp
      .map((chapter: Record<string, unknown>) => {
        const outlineNodeId = normalizeBusinessId(chapter.outlineNodeId)
        const chapterId = normalizeBusinessId(chapter.chapterId)
        return outlineNodeId && chapterId ? [outlineNodeId, chapterId] : null
      })
      .filter((entry): entry is [string, string] => Array.isArray(entry))
  )
  const outlineNodes = outlineResp.length > 0 && hasRenderableVolumeNodes(outlineResp)
    ? outlineResp
    : buildFallbackOutlineFromChapters(chapterResp)

  loadOutline(outlineNodes, chapterByOutlineNodeId)

  await Promise.all([
    loadActivePlugins(projectId),
    refreshActiveModelInfo(),
  ])
}

const navigateFromUserMenu = (path: string) => {
  userMenuOpen.value = false
  router.push(path)
}

const handleLogout = async () => {
  try {
    await authApi.logout()
  } finally {
    clearSession()
    router.push('/login')
  }
}

onMounted(async () => {
  username.value = sessionUsername || username.value
  userEmail.value = sessionUserEmail || userEmail.value
  const projectId = getCurrentProjectId()
  if (projectId) {
    await loadWorkbenchData(projectId)
    const conversations = (await agentApi.listSessions(String(projectId))) as Array<Record<string, unknown>>
    const latestSessionId = pickConversationId(conversations[0] || {})
    if (latestSessionId) {
      await resumeWorkbenchSession(latestSessionId)
    }
  } else {
    await refreshActiveModelInfo()
  }
})

watch(editorContent, (value) => {
  chapterContents.value[activeChapter.value] = value
})
watch(() => route.query.mode, (mode) => {
  workbenchMode.value = mode === 'story-bible' ? 'story-bible' : 'writing'
})
watch(runtimeEventSource, (event) => {
  if (event?.eventName === 'run.completed' && storyBibleWorkspaceRef.value) {
    void storyBibleWorkspaceRef.value.reload()
  }
})
</script>

<style lang="less">
.workbench-page {
  min-height: 100vh;
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.workbench-shell {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: row;
  align-items: stretch;
  overflow: hidden;
}

.mobile-chat-toggle { display: none; }

@media (max-width: 1080px) {
  .workbench-shell > .panel-right {
    position: fixed;
    top: 48px;
    right: 0;
    bottom: 0;
    z-index: 240;
    width: min(92vw, 360px);
    border-left: 1px solid var(--border-gold);
    background: rgba(11, 17, 32, 0.99);
    box-shadow: var(--shadow-lg);
  }
  .workbench-shell > .panel-right.collapsed { display: none; }
  .workbench-shell > .panel-right .panel-toggle { display: none; }
  .mobile-chat-toggle {
    position: fixed;
    right: 12px;
    bottom: 12px;
    z-index: 260;
    width: 40px;
    height: 40px;
    display: grid;
    place-items: center;
    border: 1px solid var(--border-gold);
    border-radius: 50%;
    color: var(--amber-gold);
    background: rgba(17, 24, 39, 0.98);
    box-shadow: var(--shadow-lg);
    cursor: pointer;
  }
}
</style>

