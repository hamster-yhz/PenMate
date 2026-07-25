import { computed, getCurrentScope, onScopeDispose, ref } from 'vue'
import DOMPurify from 'dompurify'
import {
  novelApi,
  type NovelImportDraft,
  type NovelImportSession,
} from '@/api/modules/novel.api'
import { getErrorMessage } from '@/utils/errors'

const MAX_FILE_SIZE = 20 * 1024 * 1024
const PAGE_SIZE = 100
const supportedFile = /\.(txt|md|markdown|docx)$/i

const copyDraft = (draft: NovelImportDraft): NovelImportDraft => ({
  projectTitle: String(draft.projectTitle || '').trim(),
  sourceFormat: draft.sourceFormat,
  volumes: (draft.volumes || []).map((volume) => ({
    title: String(volume.title || '').trim(),
    chapters: (volume.chapters || []).map((chapter) => ({
      title: String(chapter.title || '').trim(),
      content: String(chapter.content || ''),
    })),
  })),
})

const richPasteToMarkdown = (html: string, plainText: string) => {
  if (!html.trim()) return { content: plainText, extension: 'txt' }
  const safe = DOMPurify.sanitize(html)
  const document = new DOMParser().parseFromString(safe, 'text/html')
  let hasHeadings = false
  const blocks: string[] = []
  document.body.querySelectorAll('h1,h2,h3,h4,h5,h6,p,div,li,pre').forEach((element) => {
    const value = element.textContent?.trim() || ''
    if (!value) return
    const match = element.tagName.match(/^H([1-6])$/)
    if (match) {
      hasHeadings = true
      blocks.push(`${'#'.repeat(Number(match[1]))} ${value}`)
    } else {
      blocks.push(value)
    }
  })
  return { content: blocks.join('\n\n') || plainText, extension: hasHeadings ? 'md' : 'txt' }
}

export const useNovelImport = () => {
  const draft = ref<NovelImportDraft | null>(null)
  const session = ref<NovelImportSession | null>(null)
  const sessionId = ref('')
  const filename = ref('')
  const previewing = ref(false)
  const submitting = ref(false)
  const error = ref('')
  const selectedVolumeIndex = ref(0)
  const selectedChapterIndex = ref(0)
  const chapterPage = ref(0)
  let pollTimer: ReturnType<typeof setTimeout> | undefined

  const selectedVolume = computed(() => draft.value?.volumes[selectedVolumeIndex.value] || null)
  const selectedChapter = computed(() => selectedVolume.value?.chapters[selectedChapterIndex.value] || null)
  const chapterCount = computed(() => draft.value?.volumes.reduce((sum, volume) => sum + volume.chapters.length, 0) || 0)
  const pageCount = computed(() => Math.max(1, Math.ceil((selectedVolume.value?.chapters.length || 0) / PAGE_SIZE)))
  const visibleChapters = computed(() => {
    const start = chapterPage.value * PAGE_SIZE
    return (selectedVolume.value?.chapters || []).slice(start, start + PAGE_SIZE)
      .map((chapter, index) => ({ chapter, index: start + index }))
  })
  const issues = computed(() => {
    const result: Array<{ severity: 'warning' | 'error'; message: string }> = []
    if (!draft.value) return result
    const titles = new Set<string>()
    draft.value.volumes.forEach((volume) => {
      if (!volume.chapters.length) result.push({ severity: 'error', message: `“${volume.title || '未命名卷'}”没有章节` })
      volume.chapters.forEach((chapter) => {
        if (!chapter.content.trim()) result.push({ severity: 'warning', message: `“${chapter.title || '未命名章节'}”正文为空` })
        if (titles.has(chapter.title.trim())) result.push({ severity: 'warning', message: `章节名“${chapter.title}”重复` })
        titles.add(chapter.title.trim())
      })
    })
    return result.slice(0, 20)
  })
  const canConfirm = computed(() => Boolean(draft.value?.projectTitle.trim()
    && draft.value.volumes.length
    && draft.value.volumes.every((volume) => volume.title.trim() && volume.chapters.length
      && volume.chapters.every((chapter) => chapter.title.trim()))
    && !submitting.value))
  const running = computed(() => ['READY', 'QUEUED', 'IMPORTING'].includes(session.value?.status || ''))
  const progressPercent = computed(() => {
    const total = Number(session.value?.progressTotal || session.value?.totalChapters || 0)
    const current = Number(session.value?.progressCurrent || session.value?.checkpointChapter || 0)
    return total ? Math.min(100, Math.round((current / total) * 100)) : 0
  })

  const clearPoll = () => { if (pollTimer) clearTimeout(pollTimer); pollTimer = undefined }
  const reset = () => {
    clearPoll()
    draft.value = null
    session.value = null
    sessionId.value = ''
    filename.value = ''
    previewing.value = false
    submitting.value = false
    error.value = ''
    selectedVolumeIndex.value = 0
    selectedChapterIndex.value = 0
    chapterPage.value = 0
  }

  const selectFile = async (file: File) => {
    error.value = ''
    if (!supportedFile.test(file.name)) { error.value = '请选择 TXT、Markdown 或 DOCX 文件'; return false }
    if (file.size > MAX_FILE_SIZE) { error.value = '文件不能超过 20 MB'; return false }
    previewing.value = true
    try {
      const preview = await novelApi.previewNovelImport(file)
      sessionId.value = String(preview.sessionId)
      draft.value = copyDraft(preview.draft)
      filename.value = file.name
      return true
    } catch (cause) {
      error.value = getErrorMessage(cause, '无法解析这个文件')
      return false
    } finally {
      previewing.value = false
    }
  }

  const selectPaste = async (html: string, plainText: string) => {
    const converted = richPasteToMarkdown(html, plainText)
    if (!converted.content.trim()) { error.value = '请先粘贴正文'; return false }
    const file = new File([converted.content], `粘贴导入.${converted.extension}`, { type: 'text/plain;charset=utf-8' })
    return selectFile(file)
  }

  const selectVolume = (index: number) => {
    selectedVolumeIndex.value = index
    selectedChapterIndex.value = 0
    chapterPage.value = 0
  }
  const selectChapter = (index: number) => {
    selectedChapterIndex.value = index
    chapterPage.value = Math.floor(index / PAGE_SIZE)
  }
  const addVolume = () => {
    if (!draft.value || draft.value.volumes.length >= 100) return
    draft.value.volumes.push({ title: `第 ${draft.value.volumes.length + 1} 卷`, chapters: [] })
    selectVolume(draft.value.volumes.length - 1)
  }
  const removeVolume = (index: number) => {
    if (!draft.value || draft.value.volumes.length <= 1 || draft.value.volumes[index]?.chapters.length) return
    draft.value.volumes.splice(index, 1)
    selectVolume(Math.min(index, draft.value.volumes.length - 1))
  }
  const moveVolume = (index: number, delta: number) => {
    if (!draft.value) return
    const target = index + delta
    if (target < 0 || target >= draft.value.volumes.length) return
    const [volume] = draft.value.volumes.splice(index, 1)
    if (!volume) return
    draft.value.volumes.splice(target, 0, volume)
    selectVolume(target)
  }
  const addChapter = () => {
    const volume = selectedVolume.value
    if (!volume) return
    volume.chapters.push({ title: `第 ${chapterCount.value + 1} 章`, content: '' })
    selectChapter(volume.chapters.length - 1)
  }
  const deleteChapter = (index: number) => {
    const volume = selectedVolume.value
    if (!volume || volume.chapters.length <= 1) return
    volume.chapters.splice(index, 1)
    selectChapter(Math.min(index, volume.chapters.length - 1))
  }
  const moveChapterOrder = (index: number, delta: number) => {
    const volume = selectedVolume.value
    const target = index + delta
    if (!volume || target < 0 || target >= volume.chapters.length) return
    const [chapter] = volume.chapters.splice(index, 1)
    if (!chapter) return
    volume.chapters.splice(target, 0, chapter)
    selectChapter(target)
  }
  const moveChapterToVolume = (targetVolumeIndex: number) => {
    if (!draft.value) return
    const source = selectedVolume.value
    const target = draft.value.volumes[targetVolumeIndex]
    const chapter = selectedChapter.value
    if (!source || !target || !chapter || target === source) return
    source.chapters.splice(selectedChapterIndex.value, 1)
    target.chapters.push(chapter)
    selectVolume(targetVolumeIndex)
    selectChapter(target.chapters.length - 1)
  }
  const splitChapter = (offset: number) => {
    const volume = selectedVolume.value
    const chapter = selectedChapter.value
    if (!volume || !chapter || offset <= 0 || offset >= chapter.content.length) return
    const tail = { title: `${chapter.title}（下）`, content: chapter.content.slice(offset).trimStart() }
    chapter.content = chapter.content.slice(0, offset).trimEnd()
    volume.chapters.splice(selectedChapterIndex.value + 1, 0, tail)
    selectChapter(selectedChapterIndex.value + 1)
  }
  const mergePrevious = () => {
    const volume = selectedVolume.value
    const index = selectedChapterIndex.value
    if (!volume || index <= 0) return
    const previous = volume.chapters[index - 1]
    const current = volume.chapters[index]
    if (!previous || !current) return
    previous.content = [previous.content.trimEnd(), current.content.trimStart()].filter(Boolean).join('\n\n')
    volume.chapters.splice(index, 1)
    selectChapter(index - 1)
  }

  const poll = async () => {
    if (!sessionId.value) return
    try {
      session.value = await novelApi.getNovelImport(sessionId.value)
      if (['QUEUED', 'IMPORTING', 'READY'].includes(session.value.status)) {
        pollTimer = setTimeout(poll, 800)
      }
    } catch (cause) {
      error.value = getErrorMessage(cause, '无法获取导入进度')
      pollTimer = setTimeout(poll, 2000)
    }
  }
  const confirmImport = async () => {
    if (!draft.value || !canConfirm.value) return false
    submitting.value = true
    error.value = ''
    try {
      session.value = await novelApi.confirmNovelImport(sessionId.value, copyDraft(draft.value))
      void poll()
      return true
    } catch (cause) {
      error.value = getErrorMessage(cause, '无法开始导入')
      return false
    } finally { submitting.value = false }
  }
  const runAction = async (action: () => Promise<NovelImportSession>, fallback: string, repoll = false) => {
    error.value = ''
    try {
      session.value = await action()
      if (repoll) void poll()
    } catch (cause) {
      error.value = getErrorMessage(cause, fallback)
    }
  }
  const pause = async () => { if (sessionId.value) await runAction(() => novelApi.pauseNovelImport(sessionId.value), '无法暂停导入') }
  const resume = async () => { if (sessionId.value) await runAction(() => novelApi.resumeNovelImport(sessionId.value), '无法继续导入', true) }
  const cancel = async () => { if (sessionId.value) { clearPoll(); await runAction(() => novelApi.cancelNovelImport(sessionId.value), '无法取消导入') } }
  const retry = async () => { if (sessionId.value) await runAction(() => novelApi.retryNovelImport(sessionId.value), '无法重试导入', true) }

  if (getCurrentScope()) onScopeDispose(clearPoll)
  return {
    draft, session, filename, previewing, submitting, error, selectedVolumeIndex, selectedChapterIndex,
    chapterPage, selectedVolume, selectedChapter, chapterCount, pageCount, visibleChapters, issues,
    canConfirm, running, progressPercent, reset, selectFile, selectPaste, selectVolume, selectChapter,
    addVolume, removeVolume, moveVolume, addChapter, deleteChapter, moveChapterOrder,
    moveChapterToVolume, splitChapter, mergePrevious, confirmImport, pause, resume, cancel, retry,
  }
}
