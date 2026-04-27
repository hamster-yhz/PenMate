import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const currentDir = dirname(fileURLToPath(import.meta.url))

const readWorkbenchComponentSource = (relativePath: string) =>
  readFileSync(resolve(currentDir, relativePath), 'utf-8')

describe('workbench visual contract', () => {
  it('keeps themed style blocks on critical editor and chat components', () => {
    const criticalFiles = [
      './editor/EditorToolbar.vue',
      './editor/EditorTextarea.vue',
      './editor/VersionPreviewPane.vue',
      './editor/EditorStatusbar.vue',
      './chat/AgentSessionHeader.vue',
      './chat/ConversationHistoryPanel.vue',
      './chat/ChatComposer.vue',
      './chat/ChatMessageList.vue',
      './chat/ChatMessageItem.vue',
    ]

    criticalFiles.forEach((filePath) => {
      const source = readWorkbenchComponentSource(filePath)

      expect(source).toContain('<style scoped lang="less">')
      expect(source).toMatch(/--amber-gold|--border-subtle|--shadow-gold/)
      expect(source).toMatch(/rgba\(11,\s*17,\s*32|rgba\(17,\s*24,\s*39/)
    })
  })

  it('retains workbench-specific theme contracts for editor and chat surfaces', () => {
    const editorToolbarSource = readWorkbenchComponentSource('./editor/EditorToolbar.vue')
    const editorTextareaSource = readWorkbenchComponentSource('./editor/EditorTextarea.vue')
    const versionPreviewSource = readWorkbenchComponentSource('./editor/VersionPreviewPane.vue')
    const statusbarSource = readWorkbenchComponentSource('./editor/EditorStatusbar.vue')
    const agentHeaderSource = readWorkbenchComponentSource('./chat/AgentSessionHeader.vue')
    const historyPanelSource = readWorkbenchComponentSource('./chat/ConversationHistoryPanel.vue')
    const composerSource = readWorkbenchComponentSource('./chat/ChatComposer.vue')
    const messageListSource = readWorkbenchComponentSource('./chat/ChatMessageList.vue')
    const messageItemSource = readWorkbenchComponentSource('./chat/ChatMessageItem.vue')

    expect(editorToolbarSource).toContain('.toolbar-btn')
    expect(editorToolbarSource).toContain('.toolbar-select')
    expect(editorTextareaSource).toContain('.workbench-editor-textarea')
    expect(versionPreviewSource).toContain('.workbench-editor-textarea')
    expect(statusbarSource).toContain('.editor-statusbar')
    expect(statusbarSource).toContain('.diff-summary')
    expect(agentHeaderSource).toContain('.agent-history-btn')
    expect(agentHeaderSource).toContain('.agent-status.failed')
    expect(historyPanelSource).toContain('.conversation-item')
    expect(historyPanelSource).toContain('.conversation-empty')
    expect(composerSource).toContain('.theme-warning')
    expect(composerSource).toContain('.composer-textarea')
    expect(messageListSource).toContain('.chat-messages')
    expect(messageItemSource).toContain('.msg-btn')
    expect(messageItemSource).toContain('.msg-inline-typing')
  })
})
