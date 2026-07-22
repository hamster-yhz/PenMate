import { describe, expect, it, vi } from 'vitest'

import { useWorkbenchEditor } from '../useWorkbenchEditor'

describe('useWorkbenchEditor', () => {
  it('tracks pure-text content and selection state', () => {
    const saveDraft = vi.fn()
    const setChapterContent = vi.fn()
    const editor = useWorkbenchEditor({
      getActiveChapterKey: () => 'chapter-301',
      getProjectId: () => 'project-101',
      saveDraft,
      setChapterContent,
    })

    editor.onEditorInput('第一段 文本\n第二段')
    editor.updateCursorPos({ line: 2, column: 3, selectedText: '第二段' })

    expect(editor.editorContent.value).toBe('第一段 文本\n第二段')
    expect(editor.wordCount.value).toBe(8)
    expect(editor.currentLine.value).toBe(2)
    expect(editor.currentCol.value).toBe(3)
    expect(editor.selectedText.value).toBe('第二段')
    expect(saveDraft).toHaveBeenCalledWith('project-101', 'chapter-301', '第一段 文本\n第二段')
    expect(setChapterContent).toHaveBeenCalledWith('chapter-301', '第一段 文本\n第二段')
  })

  it('delegates undo and redo to CodeMirror', () => {
    const editor = useWorkbenchEditor({
      getActiveChapterKey: () => 'chapter-301',
      getProjectId: () => 'project-101',
      saveDraft: vi.fn(),
    })
    const editorApi = { undo: vi.fn(), redo: vi.fn(), focus: vi.fn(), find: vi.fn() }
    editor.bindEditor(editorApi)

    editor.editorUndo()
    editor.editorRedo()

    expect(editorApi.undo).toHaveBeenCalledOnce()
    expect(editorApi.redo).toHaveBeenCalledOnce()
  })

  it('hydrates a chapter without creating hand-written history snapshots', () => {
    const editor = useWorkbenchEditor({
      getActiveChapterKey: () => 'chapter-301',
      getProjectId: () => 'project-101',
      saveDraft: vi.fn(),
    })

    editor.selectChapterDraft('远端正文')

    expect(editor.editorContent.value).toBe('远端正文')
    expect(editor.wordCount.value).toBe(4)
    expect(editor.saveHint.value).toBe('')
  })

  it('does not mutate正文 through direct chat merge actions', () => {
    const editor = useWorkbenchEditor({
      getActiveChapterKey: () => 'chapter-301',
      getProjectId: () => 'project-101',
      saveDraft: vi.fn(),
    })
    editor.selectChapterDraft('当前正文')

    editor.replaceSelected({ text: 'AI 改写' })

    expect(editor.editorContent.value).toBe('当前正文')
  })
})
