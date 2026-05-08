import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readWorkbenchSource = () => readFileSync(resolve(currentDir, 'index.vue'), 'utf-8')

describe('Workbench index refactor', () => {
  it('reduces the page to four shell components', () => {
    const source = readWorkbenchSource()

    expect(source).toContain("import WorkbenchHeader from '@/components/workbench/WorkbenchHeader.vue'")
    expect(source).toContain("import WorkbenchLeftPanel from '@/components/workbench/WorkbenchLeftPanel.vue'")
    expect(source).toContain("import WorkbenchEditorPanel from '@/components/workbench/WorkbenchEditorPanel.vue'")
    expect(source).toContain("import WorkbenchRightPanel from '@/components/workbench/WorkbenchRightPanel.vue'")

    expect(source).toContain('<WorkbenchHeader')
    expect(source).toContain('<WorkbenchLeftPanel')
    expect(source).toContain('<WorkbenchEditorPanel')
    expect(source).toContain('<WorkbenchRightPanel')

    expect(source).not.toContain("import OutlineTree from '@/components/workbench/outline/OutlineTree.vue'")
    expect(source).not.toContain("import CharacterCardList from '@/components/workbench/cards/CharacterCardList.vue'")
    expect(source).not.toContain("import WorldCardList from '@/components/workbench/cards/WorldCardList.vue'")
    expect(source).not.toContain("import CardRelationPanel from '@/components/workbench/cards/CardRelationPanel.vue'")
    expect(source).not.toContain("import EditorToolbar from '@/components/workbench/editor/EditorToolbar.vue'")
    expect(source).not.toContain("import EditorTextarea from '@/components/workbench/editor/EditorTextarea.vue'")
    expect(source).not.toContain("import EditorStatusbar from '@/components/workbench/editor/EditorStatusbar.vue'")
    expect(source).not.toContain("import VersionPreviewPane from '@/components/workbench/editor/VersionPreviewPane.vue'")
    expect(source).not.toContain("import AgentSessionHeader from '@/components/workbench/chat/AgentSessionHeader.vue'")
    expect(source).not.toContain("import ConversationHistoryPanel from '@/components/workbench/chat/ConversationHistoryPanel.vue'")
    expect(source).not.toContain("import ChatMessageList from '@/components/workbench/chat/ChatMessageList.vue'")
    expect(source).not.toContain("import ChatComposer from '@/components/workbench/chat/ChatComposer.vue'")

    expect(source).not.toContain('<OutlineTree')
    expect(source).not.toContain('<CharacterCardList')
    expect(source).not.toContain('<WorldCardList')
    expect(source).not.toContain('<CardRelationPanel')
    expect(source).not.toContain('<EditorToolbar')
    expect(source).not.toContain('<EditorTextarea')
    expect(source).not.toContain('<EditorStatusbar')
    expect(source).not.toContain('<VersionPreviewPane')
    expect(source).not.toContain('<AgentSessionHeader')
    expect(source).not.toContain('<ConversationHistoryPanel')
    expect(source).not.toContain('<ChatMessageList')
    expect(source).not.toContain('<ChatComposer')
  })

  it('delegates editor and version logic to composables instead of keeping inline implementations', () => {
    const source = readWorkbenchSource()

    expect(source).toContain('useWorkbenchEditor({')
    expect(source).toContain('useWorkbenchVersions({')

    expect(source).toContain("import WorkbenchEditorPanel from '@/components/workbench/WorkbenchEditorPanel.vue'")
    expect(source).toContain('<WorkbenchEditorPanel')

    expect(source).not.toContain("import EditorTextarea from '@/components/workbench/editor/EditorTextarea.vue'")
    expect(source).not.toContain("import EditorStatusbar from '@/components/workbench/editor/EditorStatusbar.vue'")
    expect(source).not.toContain("import VersionPreviewPane from '@/components/workbench/editor/VersionPreviewPane.vue'")
    expect(source).not.toContain('<EditorTextarea')
    expect(source).not.toContain('<EditorStatusbar')
    expect(source).not.toContain('<VersionPreviewPane')

    expect(source).not.toContain('<textarea\n            ref="editorRef"')
    expect(source).not.toContain('<div class="editor-statusbar">')
    expect(source).not.toContain('<div class="version-preview" v-if="selectedVersionContent">')

    expect(source).not.toMatch(/const\s+onEditorInput\s*=\s*\(\)\s*=>/)
    expect(source).not.toMatch(/const\s+updateCursorPos\s*=\s*\(\)\s*=>/)
    expect(source).not.toMatch(/const\s+editorUndo\s*=\s*\(\)\s*=>/)
    expect(source).not.toMatch(/const\s+editorRedo\s*=\s*\(\)\s*=>/)
    expect(source).not.toMatch(/const\s+wrapSelection\s*=\s*\(/)
    expect(source).not.toMatch(/const\s+insertPrefix\s*=\s*\(/)
    expect(source).not.toMatch(/const\s+saveContent\s*=\s*async\s*\(\)\s*=>/)
    expect(source).not.toMatch(/const\s+mergeToEditor\s*=\s*\(/)
    expect(source).not.toMatch(/const\s+replaceSelected\s*=\s*\(/)

    expect(source).not.toMatch(/const\s+loadChapterVersions\s*=\s*async\s*\(/)
    expect(source).not.toMatch(/const\s+viewSelectedVersion\s*=\s*async\s*\(\)\s*=>/)
    expect(source).not.toMatch(/const\s+refreshEditorFromRemote\s*=\s*async\s*\(/)
    expect(source).not.toMatch(/const\s+restoreSelectedVersion\s*=\s*async\s*\(\)\s*=>/)
    expect(source).not.toMatch(/const\s+publishCurrentChapter\s*=\s*async\s*\(\)\s*=>/)
    expect(source).not.toMatch(/const\s+uploadAndCommitContent\s*=\s*async\s*\(/)

    expect(source).not.toContain('setLastSnapshot: () => undefined')
    expect(source).not.toContain('restoreVersion: chapterApi.restoreVersion')
    expect(source).not.toContain('publishChapter: chapterApi.publishChapter')
    expect(source).not.toContain('commitContent: chapterApi.commitContent')
    expect(source).not.toContain('createVersion: chapterApi.createVersion')
  })

  it('delegates chat and approval logic to composables and chat components instead of keeping inline implementations', () => {
    const source = readWorkbenchSource()

    expect(source).toContain('useWorkbenchChat({')
    expect(source).toContain('useWorkbenchApprovals({')

    expect(source).toContain("import WorkbenchRightPanel from '@/components/workbench/WorkbenchRightPanel.vue'")
    expect(source).toContain('<WorkbenchRightPanel')

    expect(source).not.toContain("import AgentSessionHeader from '@/components/workbench/chat/AgentSessionHeader.vue'")
    expect(source).not.toContain("import ConversationHistoryPanel from '@/components/workbench/chat/ConversationHistoryPanel.vue'")
    expect(source).not.toContain("import ChatMessageList from '@/components/workbench/chat/ChatMessageList.vue'")
    expect(source).not.toContain("import ChatComposer from '@/components/workbench/chat/ChatComposer.vue'")

    expect(source).not.toContain('<AgentSessionHeader')
    expect(source).not.toContain('<ConversationHistoryPanel')
    expect(source).not.toContain('<ChatMessageList')
    expect(source).not.toContain('<ChatComposer')

    expect(source).not.toContain('<div class="agent-header">')
    expect(source).not.toContain('<div class="conversation-panel" v-if="showConversationPanel">')
    expect(source).not.toContain('<div class="chat-input-area">')

    expect(source).not.toMatch(/const\s+sendMessage\s*=\s*async\s*\(\)\s*=>/)
    expect(source).not.toMatch(/const\s+consumeGenerationStream\s*=\s*\(/)
    expect(source).not.toMatch(/const\s+pollGenerationAsFallback\s*=\s*async\s*\(/)
    expect(source).not.toMatch(/const\s+loadConversationHistory\s*=\s*async\s*\(/)
    expect(source).not.toMatch(/const\s+loadConversationList\s*=\s*async\s*\(/)
    expect(source).not.toMatch(/const\s+selectConversation\s*=\s*async\s*\(/)
    expect(source).not.toMatch(/const\s+toggleConversationPanel\s*=\s*async\s*\(\)\s*=>/)
    expect(source).not.toMatch(/const\s+handleApprove\s*=\s*async\s*\(/)
    expect(source).not.toMatch(/const\s+handleReject\s*=\s*async\s*\(/)
    expect(source).not.toMatch(/const\s+isApprovalBusy\s*=\s*\(/)

    expect(source).not.toContain('approvalApi.approve(')
    expect(source).not.toContain('approvalApi.reject(')
  })

  it('keeps_workbench_business_ids_string_only_without_numeric_or_fallback_contracts', () => {
    const source = readWorkbenchSource()

    expect(source).not.toContain('Number(chapterIdLike)')
    expect(source).not.toContain('Number(activeChapter.value)')
    expect(source).not.toContain('Number(chapterKey)')
    expect(source).not.toContain('outlineNodeId > 0 && chapterId > 0')
    expect(source).not.toContain('conversationId ?? sessionId')
  })

  it('wraps the workspace in a home-style atmospheric shell', () => {
    const source = readWorkbenchSource()

    expect(source).toContain('class="workbench-backdrop"')
    expect(source).toContain('class="workbench-orb orb-left"')
    expect(source).toContain('class="workbench-orb orb-right"')
    expect(source).toContain('class="wb-main workbench-shell"')
  })

})
