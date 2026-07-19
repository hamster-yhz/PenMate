import { describe, expect, it, vi } from 'vitest'

type UseWorkbenchEditorFactory = (deps: any) => {
  editorRef: { value: HTMLTextAreaElement | null }
  editorContent: { value: string }
  wordCount: { value: number }
  currentLine: { value: number }
  currentCol: { value: number }
  selectedText: { value: string }
  saveHint: { value: string }
  onEditorInput: () => void
  updateCursorPos: () => void
  editorUndo: () => void
  editorRedo: () => void
  wrapSelection: (before: string, after: string) => void | Promise<void>
  insertPrefix: (prefix: string) => void | Promise<void>
  mergeToEditor: (msg: ChatMessageLike) => void
  replaceSelected: (msg: ChatMessageLike) => void
  saveContent: () => Promise<void>
  selectChapterDraft: (content: string) => void
}

type ChatMessageLike = {
  text: string
}

const createTextarea = (value: string, selectionStart: number, selectionEnd: number) => {
  const el = document.createElement('textarea')
  el.value = value
  el.selectionStart = selectionStart
  el.selectionEnd = selectionEnd
  el.focus = vi.fn()
  el.setSelectionRange = vi.fn((start: number, end: number) => {
    el.selectionStart = start
    el.selectionEnd = end
  })
  return el
}

const loadUseWorkbenchEditor = async (): Promise<UseWorkbenchEditorFactory> => {
  try {
    const modulePath = '../useWorkbenchEditor'
    return (await import(/* @vite-ignore */ modulePath)).useWorkbenchEditor as UseWorkbenchEditorFactory
  } catch {
    return ((deps: any) => ({
      editorRef: { value: null },
      editorContent: { value: '' },
      wordCount: { value: 0 },
      currentLine: { value: 1 },
      currentCol: { value: 1 },
      selectedText: { value: '' },
      saveHint: { value: '' },
      onEditorInput: () => undefined,
      updateCursorPos: () => undefined,
      editorUndo: () => undefined,
      editorRedo: () => undefined,
      wrapSelection: () => undefined,
      insertPrefix: () => undefined,
      mergeToEditor: () => undefined,
      replaceSelected: () => undefined,
      saveContent: async () => undefined,
      selectChapterDraft: () => undefined,
      __deps: deps,
    })) as unknown as UseWorkbenchEditorFactory
  }
}

describe('useWorkbenchEditor', () => {
  it('tracks_editor_input_word_count_undo_stack_and_local_draft_save', async () => {
    const useWorkbenchEditor = await loadUseWorkbenchEditor()
    const saveDraft = vi.fn()
    const editor = useWorkbenchEditor({
      getActiveChapterKey: () => '301',
      getProjectId: () => 101,
      saveDraft,
    })

    editor.editorContent.value = '第一段 文本'
    editor.onEditorInput()
    editor.editorContent.value = '第一段 文本\n第二段'
    editor.onEditorInput()

    expect(editor.wordCount.value).toBe(8)
    expect(saveDraft).toHaveBeenNthCalledWith(1, 101, '301', '第一段 文本')
    expect(saveDraft).toHaveBeenNthCalledWith(2, 101, '301', '第一段 文本\n第二段')

    editor.editorUndo()
    expect(editor.editorContent.value).toBe('第一段 文本')

    editor.editorRedo()
    expect(editor.editorContent.value).toBe('第一段 文本\n第二段')
  })

  it('wraps_selection_and_updates_cursor_state', async () => {
    const useWorkbenchEditor = await loadUseWorkbenchEditor()
    const editor = useWorkbenchEditor({
      getActiveChapterKey: () => '301',
      getProjectId: () => 101,
      saveDraft: vi.fn(),
    })

    const textarea = createTextarea('山河无恙', 0, 2)
    editor.editorRef.value = textarea
    editor.editorContent.value = textarea.value

    await editor.wrapSelection('**', '**')
    editor.updateCursorPos()

    expect(editor.editorContent.value).toBe('**山河**无恙')
    expect(editor.selectedText.value).toBe('山河')
    expect(editor.currentLine.value).toBe(1)
    expect(editor.currentCol.value).toBe(3)
  })

  it('persists_draft_and_tracks_undo_for_wrap_selection', async () => {
    const useWorkbenchEditor = await loadUseWorkbenchEditor()
    const saveDraft = vi.fn()
    const editor = useWorkbenchEditor({
      getActiveChapterKey: () => '301',
      getProjectId: () => 101,
      saveDraft,
    })

    const textarea = createTextarea('山河无恙', 0, 2)
    editor.editorRef.value = textarea
    editor.editorContent.value = textarea.value
    editor.onEditorInput()

    await editor.wrapSelection('**', '**')

    expect(saveDraft).toHaveBeenLastCalledWith(101, '301', '**山河**无恙')

    editor.editorUndo()
    expect(editor.editorContent.value).toBe('山河无恙')
  })

  it('inserts_prefix_merges_ai_text_and_replaces_selected_range', async () => {
    const useWorkbenchEditor = await loadUseWorkbenchEditor()
    const editor = useWorkbenchEditor({
      getActiveChapterKey: () => '301',
      getProjectId: () => 101,
      saveDraft: vi.fn(),
    })

    const textarea = createTextarea('第一行\n第二行', 4, 4)
    editor.editorRef.value = textarea
    editor.editorContent.value = textarea.value

    await editor.insertPrefix('> ')
    expect(editor.editorContent.value).toBe('第一行\n> 第二行')

    editor.mergeToEditor({ text: 'AI建议\n续写' } as ChatMessageLike)
    expect(editor.editorContent.value).toContain('AI建议\n续写')

    textarea.value = editor.editorContent.value
    textarea.selectionStart = 0
    textarea.selectionEnd = 3
    editor.replaceSelected({ text: '序章' } as ChatMessageLike)

    expect(editor.editorContent.value.startsWith('序章')).toBe(true)
  })

  it('persists_draft_and_tracks_undo_for_insert_prefix', async () => {
    const useWorkbenchEditor = await loadUseWorkbenchEditor()
    const saveDraft = vi.fn()
    const editor = useWorkbenchEditor({
      getActiveChapterKey: () => '301',
      getProjectId: () => 101,
      saveDraft,
    })

    const textarea = createTextarea('第一行\n第二行', 4, 4)
    editor.editorRef.value = textarea
    editor.editorContent.value = textarea.value
    editor.onEditorInput()

    await editor.insertPrefix('> ')

    expect(saveDraft).toHaveBeenLastCalledWith(101, '301', '第一行\n> 第二行')

    editor.editorUndo()
    expect(editor.editorContent.value).toBe('第一行\n第二行')
  })

  it('persists_draft_and_tracks_undo_for_merge_to_editor', async () => {
    const useWorkbenchEditor = await loadUseWorkbenchEditor()
    const saveDraft = vi.fn()
    const editor = useWorkbenchEditor({
      getActiveChapterKey: () => '301',
      getProjectId: () => 101,
      saveDraft,
    })

    editor.editorContent.value = '当前正文'
    editor.onEditorInput()

    editor.mergeToEditor({ text: 'AI建议\n续写' } as ChatMessageLike)

    expect(saveDraft).toHaveBeenLastCalledWith(101, '301', '当前正文\n\nAI建议\n续写')

    editor.editorUndo()
    expect(editor.editorContent.value).toBe('当前正文')
  })

  it('persists_draft_and_tracks_undo_for_replace_selected', async () => {
    const useWorkbenchEditor = await loadUseWorkbenchEditor()
    const saveDraft = vi.fn()
    const editor = useWorkbenchEditor({
      getActiveChapterKey: () => '301',
      getProjectId: () => 101,
      saveDraft,
    })

    const textarea = createTextarea('山河无恙', 0, 2)
    editor.editorRef.value = textarea
    editor.editorContent.value = textarea.value
    editor.onEditorInput()

    editor.replaceSelected({ text: '序章' } as ChatMessageLike)

    expect(saveDraft).toHaveBeenLastCalledWith(101, '301', '序章无恙')

    editor.editorUndo()
    expect(editor.editorContent.value).toBe('山河无恙')
  })

  it('hydrates_selected_chapter_draft_and_save_hint', async () => {
    const useWorkbenchEditor = await loadUseWorkbenchEditor()
    const saveDraft = vi.fn()
    const editor = useWorkbenchEditor({
      getActiveChapterKey: () => '301',
      getProjectId: () => 101,
      saveDraft,
    })

    editor.selectChapterDraft('远端正文')
    expect(editor.editorContent.value).toBe('远端正文')
    expect(editor.wordCount.value).toBe(4)

    editor.editorContent.value = '本地暂存稿'
    await editor.saveContent()

    expect(saveDraft).toHaveBeenLastCalledWith(101, '301', '本地暂存稿')
    expect(editor.saveHint.value).toBe('✓ 已本地保存')
  })
})
