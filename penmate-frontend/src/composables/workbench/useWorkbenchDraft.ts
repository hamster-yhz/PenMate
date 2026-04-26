import {
  clearChapterDraftLocal,
  getDraftStorageKey,
  readChapterDraftLocal,
  saveChapterDraftLocal,
} from './workbenchDraft'

export const createChapterLoadGuard = () => {
  let currentChapterId = ''
  let currentRequestId = 0

  const begin = (chapterId: string) => {
    currentChapterId = chapterId
    currentRequestId += 1
    return currentRequestId
  }

  const isCurrent = (chapterId: string, requestId: number) => currentChapterId === chapterId && currentRequestId === requestId

  return {
    begin,
    isCurrent,
  }
}

export const useWorkbenchDraft = () => {
  const readDraft = (projectId: number, chapterId: string | number) => readChapterDraftLocal(projectId, chapterId)

  const saveDraft = (projectId: number, chapterId: string | number, content: string) => {
    saveChapterDraftLocal(projectId, chapterId, content)
  }

  const clearDraft = (projectId: number, chapterId: string | number) => {
    clearChapterDraftLocal(projectId, chapterId)
  }

  const hasDraft = (projectId: number, chapterId: string | number) => {
    try {
      return window.localStorage.getItem(getDraftStorageKey(projectId, chapterId)) !== null
    } catch {
      return false
    }
  }

  const resolveStoredDraft = (projectId: number, chapterId: string | number) => {
    if (!hasDraft(projectId, chapterId)) return null
    return readDraft(projectId, chapterId)
  }

  const resolveEditorSeedContent = (chapterContent: string | undefined, storedDraft: string | null) => {
    if (chapterContent !== undefined) return chapterContent
    return storedDraft ?? ''
  }

  const resolveChapterContent = (
    projectId: number,
    chapterId: string | number,
    remoteContent: string,
    options?: { preferRemote?: boolean },
  ) => {
    if (options?.preferRemote) {
      clearDraft(projectId, chapterId)
      return remoteContent
    }
    const localDraft = readDraft(projectId, chapterId)
    if (hasDraft(projectId, chapterId)) return localDraft
    return remoteContent
  }

  return {
    readDraft,
    saveDraft,
    clearDraft,
    resolveStoredDraft,
    resolveEditorSeedContent,
    resolveChapterContent,
  }
}
