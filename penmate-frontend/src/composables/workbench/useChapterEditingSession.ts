import { ref } from 'vue'
import { chapterApi } from '@/api/modules/chapter.api'
import { getErrorMessage } from '@/utils/errors'

const SAVE_DELAY_MS = 1000
const MAX_SAVE_DELAY_MS = 5000

type ChapterEditingSessionOptions = {
  onSaved?: (projectId: string, chapterId: string, content: string) => void | Promise<void>
  onConflict?: (projectId: string, chapterId: string, content: string) => void | Promise<void>
}

type AppErrorLike = Error & { errorCode?: string }

const hasActiveAiLease = (chapter: Record<string, unknown>) => {
  if (String(chapter.leaseOwnerType || '') !== 'AI') return false
  const expiresAt = Date.parse(String(chapter.leaseExpiresAt || ''))
  return !Number.isFinite(expiresAt) || expiresAt > Date.now()
}

export const useChapterEditingSession = (options: ChapterEditingSessionOptions = {}) => {
  const editable = ref(false)
  const lockReason = ref('')
  const leaseOwnerType = ref('')
  const contentRevision = ref(1)
  const saveStatus = ref('')

  let projectId = ''
  let chapterId = ''
  let pendingContent = ''
  let savedContent = ''
  let dirty = false
  let debounceTimer: ReturnType<typeof setTimeout> | null = null
  let maxTimer: ReturnType<typeof setTimeout> | null = null
  let savePromise: Promise<void> | null = null
  let online = typeof navigator === 'undefined' || navigator.onLine

  const clearSaveTimers = () => {
    if (debounceTimer) clearTimeout(debounceTimer)
    if (maxTimer) clearTimeout(maxTimer)
    debounceTimer = null
    maxTimer = null
  }

  const blockSaving = (ownerType: string, reason: string, status: string) => {
    clearSaveTimers()
    editable.value = false
    leaseOwnerType.value = ownerType
    lockReason.value = reason
    saveStatus.value = status
  }

  const runSave = async () => {
    clearSaveTimers()
    if (!dirty || !editable.value || !projectId || !chapterId) return
    if (!online) {
      saveStatus.value = '离线'
      return
    }
    if (savePromise) return savePromise

    savePromise = (async () => {
      while (dirty && editable.value) {
        dirty = false
        const content = pendingContent
        const revision = contentRevision.value
        saveStatus.value = '正在同步'
        try {
          const saved = await chapterApi.saveContent(projectId, chapterId, {
            expectedRevision: revision,
            content,
          })
          contentRevision.value = Number(saved.contentRevision ?? revision + 1)
          savedContent = content
          saveStatus.value = '已保存'
          lockReason.value = ''
          await options.onSaved?.(projectId, chapterId, content)
        } catch (error: unknown) {
          dirty = true
          const errorCode = String((error as AppErrorLike | null)?.errorCode || '')
          if (errorCode === 'CHAPTER_AI_EDITING') {
            blockSaving('AI', 'AI 正在编辑当前章节，本地草稿已保留', 'AI 正在编辑')
            await options.onConflict?.(projectId, chapterId, content)
          } else if (errorCode === 'CHAPTER_REVISION_CONFLICT') {
            blockSaving('', '章节已在其他页面更新，本地草稿已保留', '版本冲突')
            await options.onConflict?.(projectId, chapterId, content)
          } else {
            saveStatus.value = '保存失败'
            lockReason.value = getErrorMessage(error, '章节保存失败')
          }
          break
        }
      }
    })().finally(() => {
      savePromise = null
    })
    return savePromise
  }

  const scheduleSave = (content: string) => {
    pendingContent = content
    dirty = content !== savedContent
    if (!dirty) {
      clearSaveTimers()
      return
    }
    if (!editable.value) return
    if (!online) {
      clearSaveTimers()
      saveStatus.value = '离线'
      return
    }
    saveStatus.value = '本地暂存'
    if (debounceTimer) clearTimeout(debounceTimer)
    debounceTimer = setTimeout(() => void runSave(), SAVE_DELAY_MS)
    if (!maxTimer) maxTimer = setTimeout(() => void runSave(), MAX_SAVE_DELAY_MS)
  }

  const flush = async (content?: string) => {
    if (content !== undefined) {
      pendingContent = content
      dirty = content !== savedContent
    }
    await runSave()
  }

  const release = async () => {
    clearSaveTimers()
    if (editable.value) await runSave()
    editable.value = false
    leaseOwnerType.value = ''
  }

  const open = async (nextProjectId: string, nextChapterId: string) => {
    if (projectId && chapterId && (projectId !== nextProjectId || chapterId !== nextChapterId)) {
      await release()
    }
    projectId = nextProjectId
    chapterId = nextChapterId
    saveStatus.value = ''
    lockReason.value = ''
    const chapter = await chapterApi.getChapter(projectId, chapterId)
    const aiEditing = hasActiveAiLease(chapter)
    editable.value = !aiEditing
    leaseOwnerType.value = aiEditing ? 'AI' : ''
    contentRevision.value = Math.max(1, Number(chapter.contentRevision ?? 1))
    pendingContent = String(chapter.content ?? '')
    savedContent = pendingContent
    dirty = false
    if (aiEditing) lockReason.value = 'AI 正在编辑当前章节'
    return { content: pendingContent, editable: editable.value }
  }

  const lockForAi = (targetChapterId: string) => {
    if (!targetChapterId || targetChapterId !== chapterId) return
    blockSaving('AI', 'AI 正在编辑当前章节', 'AI 正在编辑')
  }

  const setOnline = (nextOnline: boolean) => {
    online = nextOnline
    if (!online) {
      clearSaveTimers()
      if (dirty) saveStatus.value = '离线'
      return
    }
    if (dirty && editable.value) void runSave()
  }

  const dispose = async () => release()

  return {
    editable,
    lockReason,
    leaseOwnerType,
    contentRevision,
    saveStatus,
    open,
    scheduleSave,
    flush,
    release,
    lockForAi,
    setOnline,
    dispose,
  }
}
