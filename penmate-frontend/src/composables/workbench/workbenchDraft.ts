export const getDraftStorageKey = (projectId: string, chapterId: string) =>
  `penmate.chapterDraft.${projectId}.${chapterId}`

export const saveChapterDraftLocal = (projectId: string, chapterId: string, content: string) => {
  try {
    localStorage.setItem(getDraftStorageKey(projectId, chapterId), content)
  } catch {
    // 忽略浏览器存储异常
  }
}

export const clearChapterDraftLocal = (projectId: string, chapterId: string) => {
  try {
    localStorage.removeItem(getDraftStorageKey(projectId, chapterId))
  } catch {
    // 忽略浏览器存储异常
  }
}

export const readChapterDraftLocal = (projectId: string, chapterId: string) => {
  try {
    return localStorage.getItem(getDraftStorageKey(projectId, chapterId)) || ''
  } catch {
    return ''
  }
}
