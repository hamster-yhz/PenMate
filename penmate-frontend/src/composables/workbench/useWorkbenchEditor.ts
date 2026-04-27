import { nextTick, ref } from 'vue'

type ChatMessageLike = {
  text: string
}

type UseWorkbenchEditorDeps = {
  getActiveChapterKey: () => string
  getProjectId: () => number
  saveDraft: (projectId: number, chapterId: string | number, content: string) => void
  setChapterContent?: (chapterId: string, content: string) => void
}

const countWords = (content: string) => content.replace(/\s/g, '').length
const stripHtml = (content: string) => String(content || '').replace(/<[^>]*>/g, '')

export const useWorkbenchEditor = (deps: UseWorkbenchEditorDeps) => {
  const editorRef = ref<HTMLTextAreaElement | null>(null)
  const editorContent = ref('')
  const wordCount = ref(0)
  const currentLine = ref(1)
  const currentCol = ref(1)
  const selectedText = ref('')
  const saveHint = ref('')
  const undoStack = ref<string[]>([])
  const redoStack = ref<string[]>([])

  let lastSnapshot = ''

  const syncChapterContent = (content: string) => {
    const chapterKey = deps.getActiveChapterKey()
    if (!chapterKey) return
    deps.setChapterContent?.(chapterKey, content)
  }

  const syncWordCount = (content: string) => {
    wordCount.value = countWords(content)
  }

  const persistDraft = (content: string) => {
    const projectId = deps.getProjectId()
    const chapterKey = deps.getActiveChapterKey()
    if (!projectId || !chapterKey) return
    deps.saveDraft(projectId, chapterKey, content)
  }

  const applyEditorChange = (content: string) => {
    editorContent.value = content
    syncChapterContent(content)
    syncWordCount(content)
    persistDraft(content)
    if (content !== lastSnapshot) {
      undoStack.value.push(lastSnapshot)
      if (undoStack.value.length > 50) undoStack.value.shift()
      redoStack.value = []
      lastSnapshot = content
    }
  }

  const updateCursorPos = () => {
    if (!editorRef.value) return
    const el = editorRef.value
    const pos = el.selectionStart
    const text = editorContent.value.slice(0, pos)
    const lines = text.split('\n')
    currentLine.value = lines.length
    currentCol.value = (lines[lines.length - 1]?.length || 0) + 1
    const start = el.selectionStart
    const end = el.selectionEnd
    selectedText.value = start !== end ? editorContent.value.slice(start, end) : ''
  }

  const onEditorInput = () => {
    applyEditorChange(editorContent.value)
  }

  const editorUndo = () => {
    if (undoStack.value.length === 0) return
    redoStack.value.push(editorContent.value)
    const prev = undoStack.value.pop() || ''
    editorContent.value = prev
    lastSnapshot = prev
    syncChapterContent(prev)
    syncWordCount(prev)
  }

  const editorRedo = () => {
    if (redoStack.value.length === 0) return
    undoStack.value.push(editorContent.value)
    const next = redoStack.value.pop() || ''
    editorContent.value = next
    lastSnapshot = next
    syncChapterContent(next)
    syncWordCount(next)
  }

  const wrapSelection = async (before: string, after: string) => {
    if (!editorRef.value) return
    const el = editorRef.value
    const start = el.selectionStart
    const end = el.selectionEnd
    const selected = editorContent.value.slice(start, end)
    const body = selected || '文本'
    const replacement = `${before}${body}${after}`
    applyEditorChange(editorContent.value.slice(0, start) + replacement + editorContent.value.slice(end))
    await nextTick()
    el.focus()
    const newPos = start + before.length + body.length
    el.setSelectionRange(start + before.length, newPos)
  }

  const insertPrefix = async (prefix: string) => {
    if (!editorRef.value) return
    const el = editorRef.value
    const pos = el.selectionStart
    const lineStart = editorContent.value.lastIndexOf('\n', pos - 1) + 1
    applyEditorChange(editorContent.value.slice(0, lineStart) + prefix + editorContent.value.slice(lineStart))
    await nextTick()
    el.focus()
    el.setSelectionRange(pos + prefix.length, pos + prefix.length)
  }

  const mergeToEditor = (msg: ChatMessageLike) => {
    applyEditorChange(`${editorContent.value}\n\n${stripHtml(msg.text)}`)
  }

  const replaceSelected = (msg: ChatMessageLike) => {
    if (!editorRef.value) return
    const el = editorRef.value
    const start = el.selectionStart
    const end = el.selectionEnd
    if (start === end) return
    const plainText = stripHtml(msg.text)
    applyEditorChange(editorContent.value.slice(0, start) + plainText + editorContent.value.slice(end))
  }

  const saveContent = async () => {
    syncChapterContent(editorContent.value)
    saveHint.value = '⌛ 保存中...'

    const projectId = deps.getProjectId()
    const chapterKey = deps.getActiveChapterKey()

    if (projectId && chapterKey) {
      deps.saveDraft(projectId, chapterKey, editorContent.value)
    }

    saveHint.value = '✓ 已本地保存'
    setTimeout(() => {
      saveHint.value = ''
    }, 2000)
  }

  const selectChapterDraft = (content: string) => {
    editorContent.value = content
    syncWordCount(content)
    undoStack.value = []
    redoStack.value = []
    lastSnapshot = content
  }

  return {
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
  }
}
