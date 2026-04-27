<template>
  <div class="workbench-page">
    <WorkbenchHeader
      :novel-title="novelTitle"
      :word-count="wordCount"
      :save-hint="saveHint"
      :username="username"
      :user-email="userEmail"
      :user-menu-open="userMenuOpen"
      @go-home="router.push('/')"
      @update-title="updateTitle"
      @open-style-manager="showStyleManager = true"
      @open-plugin-workshop="showPluginWorkshop = true"
      @open-model-settings="showModelSettings = true"
      @toggle-user-menu="userMenuOpen = !userMenuOpen"
      @close-user-menu="userMenuOpen = false"
      @go-profile="navigateFromUserMenu('/profile')"
      @go-mybooks="navigateFromUserMenu('/mybooks')"
      @go-domain-console="navigateFromUserMenu('/domain-console')"
      @logout="handleLogout"
    />

    <div class="wb-main">
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
        :bind-chat-container="bindChatContainer"
        :messages="messages"
        :streaming-assistant-msg-id="streamingAssistantMsgId"
        :is-approval-busy="isApprovalBusy"
        :chat-input="chatInput"
        :active-plugins="activePlugins"
        @toggle-collapse="rightCollapsed = !rightCollapsed"
        @toggle-history="toggleConversationPanel"
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

    <StyleManager :visible="showStyleManager" @close="showStyleManager = false" />
    <PluginWorkshop :visible="showPluginWorkshop" @close="showPluginWorkshop = false" />
    <ModelSettings :visible="showModelSettings" @close="showModelSettings = false" @saved="onModelConfigSaved" />
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref, type ComponentPublicInstance, type Ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
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
const { saveDraft, clearDraft, resolveStoredDraft, resolveEditorSeedContent, resolveChapterContent } = useWorkbenchDraft()
const chapterLoadGuard = createChapterLoadGuard()
const getCurrentProjectId = () => ensureContext().projectId || initialProjectId
const resolveOperatorId = () => ensureContext().operatorId || initialOperatorId
const getContext = () => {
  const { projectId, operatorId } = ensureContext()
  return { projectId, operatorId }
}

const username = ref('墨客')
const userEmail = ref('moke@penmate.com')
const userMenuOpen = ref(false)
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

const {
  outlineData,
  activeChapter,
  currentChapterTitle,
  outlineOpBusy,
  loadOutline,
  selectChapter: selectOutlineChapter,
  addVolume,
  addChapter,
  deleteVolume,
  deleteChapter,
  renameNode,
  moveNode,
} = useWorkbenchOutline({
  getContext,
  reloadOutline: async () => {
    await loadWorkbenchData()
  },
  createOutlineNode: outlineApi.createNode,
  createChapter: novelApi.createChapter,
  deleteOutlineNode: outlineApi.deleteNode,
  deleteChapter: novelApi.deleteChapter,
  updateOutlineNode: outlineApi.updateNode,
  moveOutlineNode: outlineApi.moveNode,
  notify: (warningMessage) => {
    message.warning(warningMessage)
  },
  notifySuccess: (successMessage) => {
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
  promptCardName: (defaultName) => window.prompt('请输入卡片名称（必填）', defaultName),
  notify: (warningMessage) => {
    message.warning(warningMessage)
  },
  notifySuccess: (successMessage) => {
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
  setChapterContent: (chapterId, content) => {
    chapterContents.value[chapterId] = content
  },
})

type EditorTextareaExpose = ComponentPublicInstance & {
  textarea?: HTMLTextAreaElement | Ref<HTMLTextAreaElement | null> | null
}

const isHtmlTextareaElement = (value: unknown): value is HTMLTextAreaElement =>
  typeof HTMLTextAreaElement !== 'undefined' && value instanceof HTMLTextAreaElement

const resolveEditorTextareaElement = (instance: Element | ComponentPublicInstance | null): HTMLTextAreaElement | null => {
  if (!instance || !('textarea' in instance)) return null
  const textarea = (instance as EditorTextareaExpose).textarea
  if (isHtmlTextareaElement(textarea)) return textarea
  if (textarea && typeof textarea === 'object' && 'value' in textarea) {
    return isHtmlTextareaElement(textarea.value) ? textarea.value : null
  }
  return null
}

const bindEditorTextarea = (instance: Element | ComponentPublicInstance | null) => {
  editorRef.value = resolveEditorTextareaElement(instance)
}

const getRequiredOperatorId = () => {
  const operatorId = resolveOperatorId()
  if (typeof operatorId !== 'number' || operatorId <= 0) {
    throw new Error('缺少操作人ID')
  }
  return operatorId
}

const syncEditorFromVersion = (content: string) => {
  selectChapterDraft(content)
  chapterContents.value[activeChapter.value] = content
}

const fetchText = async (url: string) => {
  const response = await fetch(url)
  if (!response.ok) throw new Error('读取文本失败')
  return response.text()
}

const uploadText = async (url: string, content: string) => {
  let uploadResponse: Response
  try {
    uploadResponse = await fetch(url, {
      method: 'PUT',
      headers: { 'Content-Type': 'text/plain; charset=utf-8' },
      body: content,
    })
  } catch {
    throw new Error('直传 OSS 请求失败，请检查网络/CORS/预检配置')
  }

  return {
    ok: uploadResponse.ok,
    status: uploadResponse.status,
    etag: (uploadResponse.headers.get('etag') || '').replace(/"/g, '').trim(),
    checksum: (uploadResponse.headers.get('x-amz-checksum-crc32') || '').trim(),
  }
}

const {
  selectedVersionNo,
  versionBusy,
  selectedVersionContent,
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
  getOperatorId: getRequiredOperatorId,
  getEditorContent: () => editorContent.value,
  setEditorContent: syncEditorFromVersion,
  setWordCount: (count) => {
    wordCount.value = count
  },
  setLastSnapshot: syncEditorFromVersion,
  resolveChapterContent,
  resolveStoredDraft,
  clearDraft,
  beginChapterRequest: chapterLoadGuard.begin,
  isChapterRequestCurrent: chapterLoadGuard.isCurrent,
  listVersions: chapterApi.listVersions,
  getVersionSnapshotUrl: chapterApi.getVersionSnapshotUrl,
  getContentUrl: chapterApi.getContentUrl,
  restoreVersion: async (projectId, chapterId, versionNo, operatorId) => {
    await chapterApi.restoreVersion(projectId, chapterId, versionNo, operatorId)
  },
  publishChapter: async (projectId, chapterId, operatorId) => {
    await chapterApi.publishChapter(projectId, chapterId, operatorId)
  },
  getContentUploadUrl: chapterApi.getContentUploadUrl,
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
  notify: (warningMessage) => {
    message.warning(warningMessage)
  },
  notifySuccess: (successMessage) => {
    message.success(successMessage)
  },
})

const activePlugins = ref<string[]>([])
const activeModelConfigId = ref<number | null>(null)
const ENABLE_POLLING_FALLBACK = String(import.meta.env.VITE_AGENT_POLLING_FALLBACK || 'false').toLowerCase() === 'true'
const pickModelConfigId = (item: Record<string, unknown>) => Number(item.projectPolicyId ?? 0)
const pickConversationId = (item: Record<string, unknown>) => Number(item.conversationId ?? 0)

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

const loadActivePlugins = async () => {
  const projectId = getCurrentProjectId()
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

const ensureConversationId = async (projectId: number, operatorId: number) => {
  if (currentConversationId.value) return currentConversationId.value
  const conversations = (await agentApi.listConversations(projectId)) as Array<Record<string, unknown>>
  const existing = conversations[0]
  const existingConversationId = existing ? pickConversationId(existing) : 0
  if (existingConversationId > 0) {
    currentConversationId.value = existingConversationId
    return currentConversationId.value
  }
  const created = (await agentApi.createConversation(projectId, operatorId, {
    userId: operatorId,
    title: 'Workbench 会话',
    contextScopeJson: '{}',
    status: 'ACTIVE',
  })) as Record<string, unknown>
  currentConversationId.value = pickConversationId(created) || null
  return currentConversationId.value
}

const refreshActiveModelInfo = async (projectId: number) => {
  if (!projectId) {
    activeModelConfigId.value = null
    currentModelName.value = ''
    return null
  }
  try {
    const configs = (await modelApi.listConfigs(projectId)) as Array<Record<string, unknown>>
    const preferred = configs.find((item) => Boolean(item.isDefault)) || configs[0]
    const modelConfigId = preferred ? pickModelConfigId(preferred) : 0
    activeModelConfigId.value = modelConfigId > 0 ? modelConfigId : null
    currentModelName.value = String(preferred?.modelName || '').trim()
    return activeModelConfigId.value
  } catch {
    activeModelConfigId.value = null
    currentModelName.value = ''
    return null
  }
}

const ensureModelConfigId = async (projectId: number) => refreshActiveModelInfo(projectId)

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
  currentModelName,
  loadConversationHistory,
  selectConversation,
  toggleConversationPanel,
  sendMessage,
} = useWorkbenchChat({
  getContext,
  getCurrentProjectId,
  getActiveChapterKey: () => activeChapter.value,
  getActivePlugins: () => activePlugins.value,
  ensureConversationId,
  ensureModelConfigId,
  refreshActiveModelInfo,
  listConversations: agentApi.listConversations,
  listMessages: agentApi.listMessages,
  createMessage: agentApi.createMessage,
  createGeneration: agentApi.createGeneration,
  getGeneration: agentApi.getGeneration,
  openGenerationStream: agentApi.openGenerationStream,
  addStreamListener: agentApi.addStreamListener,
  scrollChat,
  nextTick,
  notifyWarning: (warningMessage) => {
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
  notifyWarning: (warningMessage) => {
    message.warning(warningMessage)
  },
})

const handleSelectConversation = async (conversationId: number) => {
  if (!conversationId) return
  await selectConversation(conversationId)
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
  void refreshActiveModelInfo(getCurrentProjectId())
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

const handleOutlineSelectChapter = async (ch: OutlineChapterNode) => {
  const chapterKey = String(ch.chapterId || ch.key)
  const prevProjectId = getCurrentProjectId()
  if (prevProjectId && activeChapter.value) {
    saveDraft(prevProjectId, activeChapter.value, editorContent.value)
  }
  chapterContents.value[activeChapter.value] = editorContent.value
  selectOutlineChapter(ch)
  const requestId = chapterLoadGuard.begin(chapterKey)
  const currentProjectId = getCurrentProjectId()
  const localDraft = currentProjectId ? resolveStoredDraft(currentProjectId, chapterKey) : null
  const chapterContent = chapterContents.value[chapterKey]
  selectChapterDraft(resolveEditorSeedContent(chapterContent, localDraft))
  void loadChapterVersions(getCurrentProjectId(), chapterKey)
  await tryLoadChapterRemoteContent(chapterKey, requestId)
  nextTick(() => editorRef.value?.focus())
}

const navigateFromUserMenu = (path: string) => {
  userMenuOpen.value = false
  router.push(path)
}

const handleLogout = () => {
  userMenuOpen.value = false
  authApi.logout().catch(() => undefined).finally(() => {
    clearSession()
    router.push('/login')
  })
}

const loadWorkbenchData = async () => {
  const projectId = getCurrentProjectId()
  if (!projectId) return
  try {
    chapterContents.value = {}
    outlineData.value = []
    activeChapter.value = ''
    currentChapterTitle.value = ''
    selectChapterDraft('')

    const [project, outlines, chapters] = await Promise.all([
      novelApi.getProject(projectId),
      outlineApi.listOutlineTree(projectId),
      novelApi.listChapters(projectId),
    ])

    novelTitle.value = String((project as Record<string, any>)?.title ?? novelTitle.value)

    const chapterList = (chapters || []) as Array<Record<string, any>>
    const chapterByOutlineNodeId: Record<string, string> = {}
    chapterList.forEach((chapter) => {
      const key = String(chapter.chapterId ?? '')
      if (!key) return
      const localDraft = resolveStoredDraft(projectId, key)
      chapterContents.value[key] = resolveEditorSeedContent(undefined, localDraft)
      const outlineNodeId = String(chapter.outlineNodeId ?? '')
      if (outlineNodeId) {
        chapterByOutlineNodeId[outlineNodeId] = key
      }
    })

    const mappedOutline = loadOutline((outlines || []) as Array<Record<string, unknown>>, chapterByOutlineNodeId)
    await loadCardsAndRelations(projectId)
    const first = mappedOutline[0]?.children?.[0]
    if (first) {
      selectOutlineChapter(first)
      const firstChapterKey = activeChapter.value
      selectChapterDraft(resolveEditorSeedContent(chapterContents.value[firstChapterKey], null))
      const requestId = chapterLoadGuard.begin(firstChapterKey)
      await tryLoadChapterRemoteContent(firstChapterKey, requestId)
      await loadChapterVersions(projectId, firstChapterKey)
    }
  } catch (error: any) {
    message.warning(error?.message || '工作台数据加载失败')
  }
}

onMounted(() => {
  void refreshActiveModelInfo(getCurrentProjectId())
  if (sessionUsername) username.value = sessionUsername
  if (sessionUserEmail) userEmail.value = sessionUserEmail
  selectChapterDraft(resolveEditorSeedContent(chapterContents.value[activeChapter.value], null))
  loadWorkbenchData()
  loadActivePlugins()
  const { projectId, operatorId } = getContext()
  if (projectId && operatorId) {
    void loadConversationHistory(projectId, operatorId)
  }
})

watch(
  () => showPluginWorkshop.value,
  (visible, prevVisible) => {
    if (prevVisible && !visible) {
      void loadActivePlugins()
    }
  },
)
</script>

<style lang="less" scoped>
.workbench-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
  background: var(--bg-primary);
}

.wb-main {
  flex: 1;
  display: flex;
  overflow: hidden;
}
</style>
