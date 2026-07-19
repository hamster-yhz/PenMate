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

  const isCurrent = (chapterId: string, requestId: number) =>
    currentChapterId === chapterId && currentRequestId === requestId

  return {
    begin,
    isCurrent,
  }
}

export const useWorkbenchDraft = () => {
  const readDraft = (projectId: string, chapterId: string) => readChapterDraftLocal(projectId, chapterId)

  const saveDraft = (projectId: string, chapterId: string, content: string) => {
    saveChapterDraftLocal(projectId, chapterId, content)
  }

  const clearDraft = (projectId: string, chapterId: string) => {
    clearChapterDraftLocal(projectId, chapterId)
  }

  const hasDraft = (projectId: string, chapterId: string) => {
    try {
      return window.localStorage.getItem(getDraftStorageKey(projectId, chapterId)) !== null
    } catch {
      return false
    }
  }

  const resolveStoredDraft = (projectId: string, chapterId: string) => {
    if (!hasDraft(projectId, chapterId)) return null
    return readDraft(projectId, chapterId)
  }

  const resolveEditorSeedContent = (chapterContent: string | undefined, storedDraft: string | null) => {
    if (chapterContent !== undefined) return chapterContent
    return storedDraft ?? ''
  }

  const resolveChapterContent = (
    projectId: string,
    chapterId: string,
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
