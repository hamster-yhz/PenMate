<template>
  <div class="workbench-page">
    <div class="workbench-backdrop" aria-hidden="true">
      <span class="workbench-orb orb-left"></span>
      <span class="workbench-orb orb-right"></span>
    </div>

    <WorkbenchHeader
      :novel-title="novelTitle"
      :word-count="wordCount"
      :save-hint="saveHint"
      :username="username"
      :user-email="userEmail"
      :user-menu-open="userMenuOpen"
      :can-access-rbac-admin="canAccessRbacAdmin"
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
    />

    <div class="wb-main workbench-shell">
      <WorkbenchLeftPanel
        :collapsed="leftCollapsed"
        :left-tabs="leftTabs"
        :active-left-tab="activeLeftTab"
        :outline-data="outlineData"
        :active-chapter="activeChapter"
        :outline-op-busy="outlineOpBusy"
        :character-cards="characterCards"
        :world-cards="worldCards"
        :project-cards="projectCards"
        :card-relations="cardRelations"
        :relation-from-id="relationFromId"
        :relation-to-id="relationToId"
        :relation-type="relationType"
        :card-name-by-id="cardNameById"
        @toggle-collapse="leftCollapsed = !leftCollapsed"
        @update:active-left-tab="activeLeftTab = $event"
        @select-chapter="handleOutlineSelectChapter"
        @rename-node="renameNode($event as any)"
        @move-node="moveNode($event as any)"
        @add-volume="addVolume"
        @add-chapter="addChapter($event as any)"
        @delete-volume="deleteVolume($event as any)"
        @delete-chapter="deleteChapter($event as any)"
        @create-character-card="createCardQuick('CHARACTER')"
        @create-world-card="createCardQuick('WORLD')"
        @toggle-card-expand="toggleCardExpanded($event as any)"
        @update-card-draft="updateCardDraft($event as any)"
        @save-card="saveCard($event as any)"
        @delete-card="deleteCardById($event as any)"
        @update:relation-from-id="relationFromId = $event"
        @update:relation-to-id="relationToId = $event"
        @update:relation-type="relationType = $event"
        @create-relation="createRelation"
        @delete-relation="deleteRelationById($event as any)"
      />

      <WorkbenchEditorPanel
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

      <WorkbenchRightPanel
        :collapsed="rightCollapsed"
        :current-model-name="currentModelName"
        :generation-status-text="generationStatusText"
        :is-generating="isGenerating"
        :generation-phase="generationPhase"
        :show-conversation-panel="showConversationPanel"
        :conversation-loading="conversationLoading"
        :conversation-list="conversationList"
        :current-conversation-id="currentConversationId"
        :bound-style-name="boundStyleName"
        :bind-chat-container="bindChatContainer"
        :messages="messages"
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
        @update:chat-input="chatInput = $event"
        @send="sendMessage"
        @open-model-settings="showModelSettings = true"
      />
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
import { nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { IdLike } from '@/api/types'
import StyleManager from '@/components/workbench/StyleManager.vue'
import PluginWorkshop from '@/components/workbench/PluginWorkshop.vue'
import ModelSettings from '@/components/workbench/ModelSettings.vue'
import WorkbenchHeader from '@/components/workbench/WorkbenchHeader.vue'
import WorkbenchLeftPanel from '@/components/workbench/WorkbenchLeftPanel.vue'
import WorkbenchEditorPanel from '@/components/workbench/WorkbenchEditorPanel.vue'
import WorkbenchRightPanel from '@/components/workbench/WorkbenchRightPanel.vue'
import { novelApi } from '@/api/modules/novel.api'
import { outlineApi } from '@/api/modules/outline.api'
import { chapterApi } from '@/api/modules/chapter.api'
import { cardApi } from '@/api/modules/card.api'
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
import { useWorkbenchCards } from '@/composables/workbench/useWorkbenchCards'
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
import iconCharacter from '@/assets/images/icon-character.png'
import iconWorld from '@/assets/images/icon-world.png'

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

const getCurrentProjectId = () => ensureContext().projectId || initialProjectId
const resolveOperatorId = () => {
  const { operatorId } = ensureContext()
  return operatorId || initialOperatorId || 0
}
const getContext = () => {
  const { projectId, operatorId } = ensureContext()
  return { projectId, operatorId }
}

const username = ref('墨客')
const userEmail = ref('moke@penmate.com')
const userMenuOpen = ref(false)
const canAccessRbacAdmin = ref(false)
const novelTitle = ref('未命名小说')
const leftCollapsed = ref(false)
const rightCollapsed = ref(false)
const showStyleManager = ref(false)
const showPluginWorkshop = ref(false)
const showModelSettings = ref(false)
const chatRef = ref<HTMLElement | null>(null)
const chapterContents = ref<Record<string, string>>({})
const activeLeftTab = ref('outline')
const leftTabs = ref([
  { key: 'outline', label: '大纲', icon: iconOutline },
  { key: 'characters', label: '角色', icon: iconCharacter },
  { key: 'world', label: '世界', icon: iconWorld },
])

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
  notify: (warningMessage: string) => {
    message.warning(warningMessage)
  },
  notifySuccess: (successMessage: string) => {
    message.success(successMessage)
  },
})

const {
  projectCards,
  characterCards,
  worldCards,
  cardRelations,
  relationFromId,
  relationToId,
  relationType,
  loadCardsAndRelations,
  createCardQuick,
  saveCard,
  deleteCardById,
  createRelation,
  deleteRelationById,
  cardNameById,
  updateCardDraft,
  toggleCardExpanded,
} = useWorkbenchCards({
  getContext,
  listCards: cardApi.listCards,
  listCardRelations: cardApi.listCardRelations,
  createCard: cardApi.createCard,
  updateCard: cardApi.updateCard,
  deleteCard: cardApi.deleteCard,
  createCardRelation: cardApi.createCardRelation,
  deleteCardRelation: cardApi.deleteCardRelation,
  promptCardName: (defaultName: string) => window.prompt('请输入资料卡名称', defaultName),
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
  resolveChapterContent: (projectId: number, chapterId: string | number, remoteContent: string, options?: { preferRemote?: boolean }) => {
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
  listVersions: async (projectId: number, chapterId: number) => chapterApi.listVersions(projectId, chapterId),
  getVersionSnapshotUrl: async (projectId: number, chapterId: number, versionNo: number) => chapterApi.getVersionSnapshotUrl(projectId, chapterId, versionNo),
  getContentUrl: async (projectId: number, chapterId: number) => chapterApi.getContentUrl(projectId, chapterId),
  restoreVersion: async (projectId: number, chapterId: number, versionNo: number, operatorId: number) => {
    await chapterApi.restoreVersion(projectId, chapterId, versionNo, operatorId)
  },
  publishChapter: async (projectId: number, chapterId: number, operatorId: number) => {
    await chapterApi.publishChapter(projectId, chapterId, operatorId)
  },
  getContentUploadUrl: async (projectId: number, chapterId: number) => chapterApi.getContentUploadUrl(projectId, chapterId),
  commitContent: async (projectId: number, chapterId: number, operatorId: number, payload: Record<string, unknown>) => {
    await chapterApi.commitContent(projectId, chapterId, operatorId, payload)
  },
  createVersion: async (projectId: number, chapterId: number, payload: Record<string, unknown>) => {
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
const ENABLE_POLLING_FALLBACK = String(import.meta.env.VITE_AGENT_POLLING_FALLBACK || 'false').toLowerCase() === 'true'
const pickModelConfigId = (item: Record<string, unknown>) => {
  if (typeof item.modelConfigId !== 'string') {
    return null
  }
  const trimmed = item.modelConfigId.trim()
  return trimmed || null
}
const pickConversationId = (item: Record<string, unknown>) => Number(item.sessionId ?? item.conversationId ?? 0)
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

const openAgentTaskStream = (projectId: number, taskId: IdLike) => {
  console.info('[agent-ui] task-stream-open', {
    projectId,
    taskId,
  })
  return agentApi.openTaskStream(projectId, taskId)
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

const loadActivePlugins = async (projectId: number) => {
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

  const record = payload as Record<string, unknown>
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
    const detail = (await modelApi.getUserModelPreferences(userId)) as Record<string, unknown>
    const preferenceRecord = extractPreferenceRecord(detail)
    const configs = Array.isArray(detail.candidateConfigs)
      ? (detail.candidateConfigs as Array<Record<string, unknown>>)
      : Array.isArray(preferenceRecord.candidateConfigs)
        ? (preferenceRecord.candidateConfigs as Array<Record<string, unknown>>)
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
  streamingAssistantMsgId,
  currentConversationId,
  loadConversationList,
  toggleConversationPanel,
  sendMessage,
  resumeRunningTask,
  hydrateFromRecoverySnapshot,
} = useWorkbenchChat({
  getContext,
  getCurrentProjectId,
  getActiveChapterKey: () => activeChapter.value,
  getActivePlugins: () => activePlugins.value,
  ensureModelConfigId,
  refreshActiveModelInfo,
  listSessions: agentApi.listSessions,
  createSession: agentApi.createSession,
  getSessionRecovery: agentApi.getSessionRecovery,
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
    const result = (await agentApi.createTurn(projectId, sessionId, payload)) as Record<string, unknown>
    syncBoundStyleName((result.session as Record<string, unknown> | null | undefined) || null)
    debugChatState('create-turn-created', {
      projectId,
      sessionId,
      taskId: Number((result.activeTask as Record<string, unknown> | null | undefined)?.taskId ?? 0),
      taskStatus: String((result.activeTask as Record<string, unknown> | null | undefined)?.taskStatus ?? ''),
      sessionStatus: String((result.session as Record<string, unknown> | null | undefined)?.status ?? ''),
    })
    return result
  },
  getTask: agentApi.getTask,
  openTaskStream: (projectId, taskId) => openAgentTaskStream(projectId, taskId),
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
  enablePollingFallback: ENABLE_POLLING_FALLBACK,
})

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
  getSessionRecovery: agentApi.getSessionRecovery,
  resumeSession: agentApi.resumeSession,
  openTaskStream: (projectId, taskId) => openAgentTaskStream(projectId, taskId),
  resumeRunningTask,
  hydrateStore: (snapshot) => {
    hydrateFromRecoverySnapshot(snapshot)
    syncBoundStyleName((snapshot?.session as Record<string, unknown> | null | undefined) || null)
    const workbenchContext = snapshot?.workbenchContext || {}
    const chapterId = Number(workbenchContext.chapterId ?? 0)
    if (chapterId > 0) {
      activeChapter.value = String(chapterId)
    }
    const plugins = Array.isArray(workbenchContext.activePlugins) ? workbenchContext.activePlugins : []
    activePlugins.value = plugins.map((item) => String(item)).filter(Boolean)
  },
})

const resumeWorkbenchSession = async (sessionId: number) => {
  const projectId = getCurrentProjectId()
  const operatorId = resolveOperatorId()
  if (!projectId || !sessionId || !operatorId) return
  await sessionRecovery.restore(projectId, sessionId, operatorId)
}

const handleSelectConversation = async (conversationId: number) => {
  if (!conversationId) return
  await resumeWorkbenchSession(conversationId)
}

const handleCreateSession = async () => {
  const projectId = getCurrentProjectId()
  const operatorId = resolveOperatorId()
  if (!projectId || !operatorId) return
  const created = (await agentApi.createSession(projectId, {
    userId: operatorId,
    title: '新会话',
  })) as Record<string, unknown>
  const sessionId = pickConversationId(created)
  if (sessionId <= 0) return
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

const tryLoadChapterRemoteContent = async (chapterIdLike: string, requestId: number) => {
  const projectId = getCurrentProjectId()
  const chapterId = Number(chapterIdLike)
  if (!projectId || !chapterId) return
  try {
    const loaded = await refreshEditorFromRemote(projectId, chapterId, requestId)
    if (!loaded) {
      if (!chapterLoadGuard.isCurrent(String(chapterId), requestId)) return
      const localDraft = resolveStoredDraft(projectId, chapterId)
      if (localDraft !== null) {
        chapterContents.value[String(chapterId)] = localDraft
        selectChapterDraft(localDraft)
      }
    }
  } catch {
    if (!chapterLoadGuard.isCurrent(String(chapterId), requestId)) return
    const localDraft = resolveStoredDraft(projectId, chapterId)
    if (localDraft !== null) {
      chapterContents.value[String(chapterId)] = localDraft
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
    saveDraft(prevProjectId, Number(activeChapter.value), editorContent.value)
  }

  activeChapter.value = chapterKey
  selectChapter(chapter)

  const requestId = chapterLoadGuard.begin(chapterKey)
  const currentProjectId = getCurrentProjectId()
  if (currentProjectId) {
    const localDraft = resolveStoredDraft(currentProjectId, Number(chapterKey))
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

const loadWorkbenchData = async (projectId: number) => {
  if (!projectId) return
  const outlineResp = await outlineApi.listOutlineTree(projectId)
  const chapterResp = await novelApi.listChapters(projectId)
  const chapterByOutlineNodeId = Object.fromEntries(
    (chapterResp || [])
      .map((chapter: Record<string, unknown>) => {
        const outlineNodeId = Number(chapter.outlineNodeId ?? 0)
        const chapterId = Number(chapter.chapterId ?? 0)
        return outlineNodeId > 0 && chapterId > 0 ? [String(outlineNodeId), String(chapterId)] : null
      })
      .filter((entry): entry is [string, string] => Array.isArray(entry))
  )

  loadOutline((outlineResp || []) as Array<Record<string, unknown>>, chapterByOutlineNodeId)

  await Promise.all([
    loadCardsAndRelations(projectId),
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
    const conversations = (await agentApi.listSessions(projectId)) as Array<Record<string, unknown>>
    const latestSessionId = pickConversationId(conversations[0] || {})
    if (latestSessionId > 0) {
      await resumeWorkbenchSession(latestSessionId)
    }
  } else {
    await refreshActiveModelInfo()
  }
})

watch(editorContent, (value) => {
  chapterContents.value[activeChapter.value] = value
})
</script>

<style lang="less">
.workbench-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.workbench-shell {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: row;
  align-items: stretch;
  overflow: hidden;
}
</style>
