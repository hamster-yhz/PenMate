export const getDraftStorageKey = (projectId: number, chapterId: string | number) => `penmate.chapterDraft.${projectId}.${chapterId}`

export const saveChapterDraftLocal = (projectId: number, chapterId: string | number, content: string) => {
  try {
    localStorage.setItem(getDraftStorageKey(projectId, chapterId), content)
  } catch {
    // 忽略浏览器存储异常
  }
}

export const clearChapterDraftLocal = (projectId: number, chapterId: string | number) => {
  try {
    localStorage.removeItem(getDraftStorageKey(projectId, chapterId))
  } catch {
    // 忽略浏览器存储异常
  }
}

export const readChapterDraftLocal = (projectId: number, chapterId: string | number) => {
  try {
    return localStorage.getItem(getDraftStorageKey(projectId, chapterId)) || ''
  } catch {
    return ''
  }
}
