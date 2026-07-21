<template>
  <div class="workbench-page">
    <div v-if="workbenchInitError" class="workbench-error" role="alert">
      <span>{{ workbenchInitError }}</span>
      <button type="button" @click="initializeWorkbench">重试</button>
    </div>
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

      <WorkbenchRightPanel
        :collapsed="rightCollapsed"
        :focused="chatFocused"
        :panel-width="chatPanelWidth"
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
        :active-plugins="activePlugins"
        :active-chapter-title="currentChapterTitle"
        :selected-text="selectedText"
        @toggle-collapse="rightCollapsed = !rightCollapsed"
        @toggle-focus="chatFocused = !chatFocused"
        @update:panel-width="chatPanelWidth = $event"
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
        @send="sendMessage"
        @cancel-run="cancelCurrentRun"
        @retry-run="retryCurrentRun"
        @open-model-settings="showModelSettings = true"
        @clear-selected-text="selectedText = ''"
        @scroll-to-bottom="scrollChatToBottom"
      />

      <button
        v-if="!chatFocused"
        type="button"
        class="mobile-chat-toggle"
        :class="{ open: !rightCollapsed }"
        :title="rightCollapsed ? '打开 Agent 对话' : '关闭 Agent 对话'"
        :aria-label="rightCollapsed ? '打开 Agent 对话' : '关闭 Agent 对话'"
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
import { CloseOutlined, MessageOutlined } from '@ant-design/icons-vue'
import StyleManager from '@/components/workbench/StyleManager.vue'
import PluginWorkshop from '@/components/workbench/PluginWorkshop.vue'
import ModelSettings from '@/components/workbench/ModelSettings.vue'
import WorkbenchHeader from '@/components/workbench/WorkbenchHeader.vue'
import WorkbenchLeftPanel from '@/components/workbench/WorkbenchLeftPanel.vue'
import WorkbenchEditorPanel from '@/components/workbench/WorkbenchEditorPanel.vue'
import WorkbenchRightPanel from '@/components/workbench/WorkbenchRightPanel.vue'
import StoryBibleWorkspace from '@/components/workbench/story-bible/StoryBibleWorkspace.vue'
import { useWorkbenchPageController } from '@/features/workbench/useWorkbenchPageController'

const {
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
  chatFocused,
  chatPanelWidth,
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
  deletedConversationList,
  recentlyDeletedConversation,
  runAttempts,
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
  loadDeletedConversations,
  renameConversation,
  deleteConversation,
  restoreConversation,
  sendMessage,
  cancelCurrentRun,
  retryCurrentRun,
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
  onModelConfigSaved,
} = useWorkbenchPageController()
</script>
<style lang="less">
.workbench-page {
  min-height: 100vh;
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.workbench-error {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-height: 40px;
  padding: 8px 16px;
  color: #fff2f0;
  background: #5c1d1d;
  border-bottom: 1px solid #ff7875;
}

.workbench-error button {
  padding: 4px 10px;
  color: #fff;
  background: transparent;
  border: 1px solid currentcolor;
  border-radius: 4px;
  cursor: pointer;
}

.workbench-shell {
  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: row;
  align-items: stretch;
  overflow: hidden;
}

.mobile-chat-toggle {
  display: none;
}

@media (max-width: 1080px) {
  .workbench-shell > .panel-right {
    position: fixed;
    top: 48px;
    right: 0;
    bottom: 0;
    z-index: 240;
    width: min(92vw, 420px) !important;
    height: auto;
    border-left: 1px solid var(--border-gold);
    background: rgba(11, 17, 32, 0.99);
    box-shadow: var(--shadow-lg);
  }
  .workbench-shell > .panel-right.collapsed {
    display: none;
  }
  .workbench-shell > .panel-right.focused {
    width: 100% !important;
  }
  .workbench-shell > .panel-right .panel-toggle {
    display: none;
  }
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
  .mobile-chat-toggle.open {
    top: 56px;
    right: min(92vw, 420px);
    bottom: auto;
    width: 32px;
    height: 40px;
    border-right: 0;
    border-radius: 4px 0 0 4px;
  }
}
</style>
