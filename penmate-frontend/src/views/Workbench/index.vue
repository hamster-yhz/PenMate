<template>
  <div class="workbench-page">
    <div v-if="workbenchInitError" class="workbench-error" role="alert">
      <span>{{ workbenchInitError }}</span>
      <button type="button" @click="initializeWorkbench">重试</button>
    </div>
    <WorkbenchHeader
      :novel-title="novelTitle"
      :save-hint="saveHint"
      :username="username"
      :can-access-rbac-admin="canAccessRbacAdmin"
      :workbench-mode="workbenchMode"
      :layout-preset="layoutPreset"
      :directory-collapsed="leftCollapsed"
      :ai-collapsed="rightCollapsed"
      @go-profile="navigateFromUserMenu('/profile')"
      @go-mybooks="navigateFromUserMenu('/mybooks')"
      @go-rbac-admin="navigateFromUserMenu('/admin')"
      @logout="handleLogout"
      @open-project-settings="router.push(`/projects/${encodeURIComponent(getCurrentProjectId())}/settings`)"
      @update:workbench-mode="setWorkbenchMode"
      @update:layout-preset="applyLayoutPreset"
      @restore-directory="toggleLeftPanel"
      @restore-ai="toggleRightPanel"
    />

    <nav v-if="workbenchMode === 'writing'" class="mobile-pane-tabs" aria-label="工作台视图">
      <button type="button" aria-label="目录" :aria-pressed="mobilePane === 'directory'" :class="{ active: mobilePane === 'directory' }" @click="selectMobilePane('directory')">
        <UnorderedListOutlined aria-hidden="true" /><span>目录</span>
      </button>
      <button type="button" aria-label="正文" :aria-pressed="mobilePane === 'editor'" :class="{ active: mobilePane === 'editor' }" @click="selectMobilePane('editor')">
        <EditOutlined aria-hidden="true" /><span>正文</span>
      </button>
      <button type="button" aria-label="AI" :aria-pressed="mobilePane === 'ai'" :class="{ active: mobilePane === 'ai' }" @click="selectMobilePane('ai')">
        <MessageOutlined aria-hidden="true" /><span>AI</span>
      </button>
    </nav>

    <div class="wb-main workbench-shell" :class="{ 'directory-overlay-mode': directoryOverlayMode && !leftCollapsed }">
      <WorkbenchLeftPanel
        v-if="workbenchMode === 'writing'"
        :class="{ 'mobile-pane-hidden': mobilePane !== 'directory', 'mobile-pane-active': mobilePane === 'directory' }"
        :collapsed="leftCollapsed"
        :panel-width="leftPanelWidth"
        :outline-data="outlineData"
        :active-chapter="activeChapter"
        :outline-op-busy="outlineOpBusy"
        :pending-move-undo="pendingMoveUndo"
        @toggle-collapse="toggleLeftPanel"
        @update:panel-width="leftPanelWidth = $event"
        @reset-panel-width="resetLayoutPreset"
        @select-chapter="selectMobileChapter"
        @rename-node="renameNode($event as any)"
        @move-node="moveNode($event as any)"
        @undo-move="undoLastMove"
        @add-volume="addVolume"
        @add-chapter="addChapter($event as any)"
        @delete-volume="deleteVolume($event as any)"
        @delete-chapter="deleteChapter($event as any)"
      />

      <Suspense v-if="workbenchMode === 'writing'">
        <WorkbenchEditorPanel
          :class="{ 'mobile-pane-hidden': mobilePane !== 'editor', 'mobile-pane-active': mobilePane === 'editor' }"
          :current-chapter-title="currentChapterTitle"
          :active-chapter="activeChapter"
          :editor-content="editorContent"
          :selected-text="selectedText"
          :current-line="currentLine"
          :current-col="currentCol"
          :word-count="wordCount"
          :save-hint="saveHint"
          :read-only="chapterReadOnly"
          :lock-reason="chapterLockReason"
          :ai-editing="aiEditingCurrentChapter"
          :ai-preview-content="aiPreviewContent"
          :ai-undo-available="Boolean(currentChapterAiUndo)"
          :ai-undo-busy="Boolean(aiUndoBusyOperationId)"
          @save="saveContent"
          @update:editor-content="editorContent = $event"
          @input="onEditorInput"
          @selection-change="updateCursorPos"
          @undo-ai="currentChapterAiUndo && undoAiEdit(currentChapterAiUndo.operationId)"
        />
        <template #fallback><div class="workbench-panel-skeleton editor-skeleton" :class="{ 'mobile-pane-hidden': mobilePane !== 'editor', 'mobile-pane-active': mobilePane === 'editor' }" aria-label="正在加载正文编辑器"></div></template>
      </Suspense>

      <StoryBibleWorkspace
        v-else
        :ref="bindStoryBibleWorkspace"
        :project-id="getCurrentProjectId()"
        :operator-id="resolveOperatorId()"
        :user-id="session.userId"
        :session-id="currentConversationId || undefined"
        :chapter-id="activeChapter"
        :chapters="storyBibleChapters"
        :project-title="novelTitle"
        :initial-node-id="storyBibleNodeId"
      />

      <Suspense>
        <WorkbenchRightPanel
          :class="{ 'mobile-pane-hidden': mobilePane !== 'ai', 'mobile-pane-active': mobilePane === 'ai' }"
          :collapsed="rightCollapsed && mobilePane !== 'ai'"
          :focused="chatFocused"
          :panel-width="effectiveChatPanelWidth"
          :current-model-name="currentModelName"
          :generation-status-text="generationStatusText"
          :agent-status-detail-text="agentStatusDetailText"
          :is-generating="isGenerating"
          :can-cancel-run="canCancelRun"
          :is-cancelling="isCancelling"
          :can-retry-run="canRetryRun"
          :is-retrying="isRetrying"
          :generation-phase="generationPhase"
          :show-conversation-panel="showConversationPanel"
          :conversation-loading="conversationLoading"
          :conversation-list="conversationList"
          :deleted-conversation-list="deletedConversationList"
          :recently-deleted-conversation="recentlyDeletedConversation"
          :current-conversation-id="currentConversationId"
          :bound-style-name="boundStyleName"
          :bind-chat-container="bindChatContainer"
          :show-scroll-to-bottom="showScrollToBottom"
          :messages="visibleMessages"
          :run-attempts="runAttempts"
          :streaming-assistant-msg-id="streamingAssistantMsgId"
          :is-approval-busy="isApprovalBusy"
          :chat-input="chatInput"
          :skill-catalog="skillCatalog"
          :active-skills="activeSkills"
          :skill-catalog-loading="skillCatalogLoading"
          :active-plugins="activePlugins"
          :active-chapter-title="currentChapterTitle"
          :selected-text="selectedText"
          :ai-undo-operations="aiUndoOperations"
          :ai-undo-busy-operation-id="aiUndoBusyOperationId"
          :ai-undo-busy-run-id="aiUndoBusyRunId"
          @toggle-collapse="toggleRightPanel"
          @toggle-focus="chatFocused = !chatFocused"
          @update:panel-width="chatPanelWidth = $event"
          @reset-panel-width="resetLayoutPreset"
          @toggle-history="toggleConversationPanel"
          @create-session="handleCreateSession"
          @select-conversation="handleSelectConversation"
          @load-deleted-conversations="loadDeletedConversations"
          @rename-conversation="renameConversation($event.conversationId, $event.title)"
          @delete-conversation="deleteConversation"
          @restore-conversation="restoreConversation"
          @approve="handleApprove"
          @reject="handleReject"
          @open-story-bible="openStoryBible"
          @update:chat-input="chatInput = $event"
          @add-skill="addActiveSkill"
          @remove-skill="removeActiveSkill"
          @refresh-skill-catalog="loadSkillCatalog"
          @send="sendMessage"
          @cancel-run="cancelCurrentRun"
          @retry-run="retryCurrentRun"
          @open-model-settings="router.push('/profile?section=agent')"
          @clear-selected-text="selectedText = ''"
          @scroll-to-bottom="scrollChatToBottom"
          @undo-ai="undoAiEdit"
          @undo-ai-run="undoAiRun"
        />
        <template #fallback><div class="workbench-panel-skeleton ai-skeleton" :class="{ collapsed: rightCollapsed && mobilePane !== 'ai', 'mobile-pane-hidden': mobilePane !== 'ai', 'mobile-pane-active': mobilePane === 'ai' }" aria-label="正在加载 AI 面板"></div></template>
      </Suspense>

    </div>

    <StyleManager
      v-if="showStyleManager"
      :visible="showStyleManager"
      :project-id="getCurrentProjectId()"
      :operator-id="resolveOperatorId()"
      :session-id="currentConversationId"
      @close="showStyleManager = false"
    />
    <PluginWorkshop v-if="showPluginWorkshop" :visible="showPluginWorkshop" @close="showPluginWorkshop = false" />
  </div>
</template>

<script setup lang="ts">
import { defineAsyncComponent, ref } from 'vue'
import { EditOutlined, MessageOutlined, UnorderedListOutlined } from '@ant-design/icons-vue'
import WorkbenchHeader from '@/components/workbench/WorkbenchHeader.vue'
import WorkbenchLeftPanel from '@/components/workbench/WorkbenchLeftPanel.vue'
import { useWorkbenchPageController } from '@/features/workbench/useWorkbenchPageController'

const StyleManager = defineAsyncComponent(() => import('@/components/workbench/StyleManager.vue'))
const PluginWorkshop = defineAsyncComponent(() => import('@/components/workbench/PluginWorkshop.vue'))
const StoryBibleWorkspace = defineAsyncComponent(() => import('@/components/workbench/story-bible/StoryBibleWorkspace.vue'))
const WorkbenchEditorPanel = defineAsyncComponent(() => import('@/components/workbench/WorkbenchEditorPanel.vue'))
const WorkbenchRightPanel = defineAsyncComponent(() => import('@/components/workbench/WorkbenchRightPanel.vue'))

const mobilePane = ref<'directory' | 'editor' | 'ai'>('editor')

const {
  router,
  session,
  username,
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
  currentChapterTitle,
  outlineOpBusy,
  pendingMoveUndo,
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
  aiEditingCurrentChapter,
  aiPreviewContent,
  currentChapterAiUndo,
  aiUndoOperations,
  aiUndoBusyOperationId,
  aiUndoBusyRunId,
  undoAiEdit,
  undoAiRun,
  onEditorInput,
  updateCursorPos,
  saveContent,
  activePlugins,
  boundStyleName,
  currentModelName,
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
  isApprovalBusy,
  handleApprove,
  handleReject,
  bindChatContainer,
  showScrollToBottom,
  scrollChatToBottom,
  handleSelectConversation,
  handleCreateSession,
  handleOutlineSelectChapter,
  navigateFromUserMenu,
  handleLogout,
  initializeWorkbench,
} = useWorkbenchPageController()

const selectMobilePane = (pane: 'directory' | 'editor' | 'ai') => {
  mobilePane.value = pane
  if (pane === 'directory') openLeftPanel()
  if (pane === 'ai' && rightCollapsed.value) toggleRightPanel()
}

const selectMobileChapter = async (chapter: Parameters<typeof handleOutlineSelectChapter>[0]) => {
  await handleOutlineSelectChapter(chapter)
  mobilePane.value = 'editor'
}
</script>
<style src="./workbench.less" lang="less"></style>
