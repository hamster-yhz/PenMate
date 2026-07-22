import { ref } from 'vue'
import type { EditorSelectionState, PlainTextEditorApi } from '@/components/workbench/editor/PlainTextEditor.vue'

type ChatMessageLike = { text: string }

type UseWorkbenchEditorDeps = {
  getActiveChapterKey: () => string
  getProjectId: () => string | null
  saveDraft: (projectId: string, chapterId: string, content: string) => void
  setChapterContent?: (chapterId: string, content: string) => void
}

const countWords = (content: string) => content.replace(/\s/g, '').length

export const useWorkbenchEditor = (deps: UseWorkbenchEditorDeps) => {
  const editorApi = ref<PlainTextEditorApi | null>(null)
  const editorContent = ref('')
  const wordCount = ref(0)
  const currentLine = ref(1)
  const currentCol = ref(1)
  const selectedText = ref('')
  const saveHint = ref('')

  const syncChapterContent = (content: string) => {
    const chapterKey = deps.getActiveChapterKey()
    if (chapterKey) deps.setChapterContent?.(chapterKey, content)
  }

  const persistDraft = (content: string) => {
    const projectId = deps.getProjectId()
    const chapterKey = deps.getActiveChapterKey()
    if (projectId && chapterKey) deps.saveDraft(projectId, chapterKey, content)
  }

  const onEditorInput = (content = editorContent.value) => {
    editorContent.value = content
    wordCount.value = countWords(content)
    syncChapterContent(content)
    persistDraft(content)
    saveHint.value = '本地暂存'
  }

  const updateCursorPos = (selection?: EditorSelectionState) => {
    if (!selection) return
    currentLine.value = selection.line
    currentCol.value = selection.column
    selectedText.value = selection.selectedText
  }

  const editorUndo = () => editorApi.value?.undo()
  const editorRedo = () => editorApi.value?.redo()

  const mergeToEditor = (message: ChatMessageLike) => {
    const separator = editorContent.value ? '\n\n' : ''
    onEditorInput(`${editorContent.value}${separator}${message.text}`)
  }

  const replaceSelected = (message: ChatMessageLike) => {
    void message
    // AI edits use the chapter_edit tool. Direct chat-to-selection mutation is intentionally unavailable.
  }

  const saveContent = async () => {
    saveHint.value = '正在同步'
    syncChapterContent(editorContent.value)
    persistDraft(editorContent.value)
    saveHint.value = '本地已暂存'
  }

  const selectChapterDraft = (content: string) => {
    editorContent.value = content
    wordCount.value = countWords(content)
    currentLine.value = 1
    currentCol.value = 1
    selectedText.value = ''
    saveHint.value = ''
  }

  const bindEditor = (instance: PlainTextEditorApi | null) => {
    editorApi.value = instance
  }

  return {
    editorApi,
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
    mergeToEditor,
    replaceSelected,
    saveContent,
    selectChapterDraft,
    bindEditor,
  }
}
