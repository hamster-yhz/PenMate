import {
  clearChapterDraft,
  getDraftStorageKey,
  readChapterDraft,
  saveChapterDraft,
} from './workbenchDraft'

const DRAFT_SAVE_DELAY_MS = 250

export const createChapterLoadGuard = () => {
  let currentChapterId = ''
  let currentRequestId = 0

  const begin = (chapterId: string) => {
    currentChapterId = chapterId
    currentRequestId += 1
    return currentRequestId
  }

  const isCurrent = (chapterId: string, requestId: number) =>
    currentChapterId === chapterId && currentRequestId === requestId

  return { begin, isCurrent }
}

export const useWorkbenchDraft = () => {
  const pendingContent = new Map<string, string>()
  const timers = new Map<string, ReturnType<typeof setTimeout>>()

  const writePendingDraft = async (projectId: string, chapterId: string) => {
    const key = getDraftStorageKey(projectId, chapterId)
    const content = pendingContent.get(key)
    const timer = timers.get(key)
    if (timer) clearTimeout(timer)
    timers.delete(key)
    if (content === undefined) return
    await saveChapterDraft(projectId, chapterId, content)
  }

  const readDraft = async (projectId: string, chapterId: string) => {
    const key = getDraftStorageKey(projectId, chapterId)
    if (pendingContent.has(key)) return pendingContent.get(key) ?? ''
    return (await readChapterDraft(projectId, chapterId))?.content ?? ''
  }

  const saveDraft = (projectId: string, chapterId: string, content: string) => {
    const key = getDraftStorageKey(projectId, chapterId)
    pendingContent.set(key, content)
    const currentTimer = timers.get(key)
    if (currentTimer) clearTimeout(currentTimer)
    timers.set(key, setTimeout(() => void writePendingDraft(projectId, chapterId), DRAFT_SAVE_DELAY_MS))
  }

  const flushDraft = (projectId: string, chapterId: string) => writePendingDraft(projectId, chapterId)

  const clearDraft = async (projectId: string, chapterId: string) => {
    const key = getDraftStorageKey(projectId, chapterId)
    const timer = timers.get(key)
    if (timer) clearTimeout(timer)
    timers.delete(key)
    pendingContent.delete(key)
    await clearChapterDraft(projectId, chapterId)
  }

  const markDraftSynced = async (projectId: string, chapterId: string, savedContent: string) => {
    const key = getDraftStorageKey(projectId, chapterId)
    const latestPending = pendingContent.get(key)
    if (latestPending !== undefined && latestPending !== savedContent) return
    if (latestPending === undefined) {
      const stored = await readChapterDraft(projectId, chapterId)
      if (stored && stored.content !== savedContent) return
    }
    await clearDraft(projectId, chapterId)
  }

  const resolveStoredDraft = async (projectId: string, chapterId: string) => {
    const key = getDraftStorageKey(projectId, chapterId)
    if (pendingContent.has(key)) return pendingContent.get(key) ?? ''
    return (await readChapterDraft(projectId, chapterId))?.content ?? null
  }

  const resolveEditorSeedContent = (chapterContent: string | undefined, storedDraft: string | null) =>
    chapterContent !== undefined ? chapterContent : storedDraft ?? ''

  return {
    readDraft,
    saveDraft,
    flushDraft,
    clearDraft,
    markDraftSynced,
    resolveStoredDraft,
    resolveEditorSeedContent,
  }
}
