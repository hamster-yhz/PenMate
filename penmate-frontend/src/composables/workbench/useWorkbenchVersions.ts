import { ref } from 'vue'

type VersionRecord = Record<string, unknown>

type UploadResult = {
  ok: boolean
  status?: number
  etag?: string
  checksum?: string
}

type UseWorkbenchVersionsDeps = {
  getProjectId: () => number
  getActiveChapterKey: () => string
  getOperatorId: () => number
  getEditorContent: () => string
  setEditorContent: (content: string) => void
  setWordCount: (count: number) => void
  setLastSnapshot: (content: string) => void
  resolveChapterContent: (
    projectId: number,
    chapterId: string | number,
    remoteContent: string,
    options?: { preferRemote?: boolean },
  ) => string
  resolveStoredDraft: (projectId: number, chapterId: string | number) => string | null
  clearDraft: (projectId: number, chapterId: string | number) => void
  beginChapterRequest: (chapterId: string) => number
  isChapterRequestCurrent: (chapterId: string, requestId: number) => boolean
  listVersions: (projectId: number, chapterId: number) => Promise<unknown>
  getVersionSnapshotUrl: (projectId: number, chapterId: number, versionNo: number) => Promise<unknown>
  getContentUrl: (projectId: number, chapterId: number) => Promise<unknown>
  restoreVersion: (projectId: number, chapterId: number, versionNo: number, operatorId: number) => Promise<void>
  publishChapter: (projectId: number, chapterId: number, operatorId: number) => Promise<void>
  getContentUploadUrl: (projectId: number, chapterId: number) => Promise<unknown>
  commitContent: (projectId: number, chapterId: number, operatorId: number, payload: Record<string, unknown>) => Promise<void>
  createVersion: (projectId: number, chapterId: number, payload: Record<string, unknown>) => Promise<void>
  resolveUploadTarget: (payload: VersionRecord) => {
    uploadUrl: string
    objectKey: string
    storageProvider: string
  }
  normalizeStorageUrl: (url: string) => string
  hasObjectKeyInStorageUrl: (url: string, marker: '/read/' | '/upload/') => boolean
  fetchText: (url: string) => Promise<string>
  uploadText: (url: string, content: string) => Promise<UploadResult>
  notify: (message: string) => void
  notifySuccess: (message: string) => void
}

const countWords = (content: string) => String(content || '').replace(/\s/g, '').length
const toRecord = (value: unknown) => (value && typeof value === 'object' ? (value as VersionRecord) : {})
const pickString = (obj: VersionRecord, keys: string[]) => {
  for (const key of keys) {
    const value = obj[key]
    if (typeof value === 'string' && value.trim()) return value
  }
  return ''
}

export const useWorkbenchVersions = (deps: UseWorkbenchVersionsDeps) => {
  const chapterVersions = ref<Record<string, VersionRecord[]>>({})
  const selectedVersionNo = ref('')
  const selectedVersionContent = ref('')
  const versionDiffSummary = ref('')
  const versionBusy = ref(false)

  const getCurrentChapterVersions = () => chapterVersions.value[deps.getActiveChapterKey()] || []

  const loadChapterVersions = async (projectId: number, chapterId: string) => {
    const numericChapterId = Number(chapterId)
    if (!projectId || !numericChapterId) return
    try {
      const versions = await deps.listVersions(projectId, numericChapterId)
      const versionList = Array.isArray(versions) ? (versions as VersionRecord[]) : []
      chapterVersions.value[chapterId] = versionList
      if (chapterId === deps.getActiveChapterKey()) {
        const firstVersionNo = versionList[0]?.versionNo
        selectedVersionNo.value = firstVersionNo != null ? String(firstVersionNo) : ''
      }
    } catch {
      chapterVersions.value[chapterId] = []
      if (chapterId === deps.getActiveChapterKey()) {
        selectedVersionNo.value = ''
      }
    }
  }

  const viewSelectedVersion = async () => {
    const projectId = deps.getProjectId()
    const chapterId = Number(deps.getActiveChapterKey())
    const versionNo = Number(selectedVersionNo.value)
    if (!projectId || !chapterId || !versionNo) {
      deps.notify('请选择有效版本后再查看')
      return
    }

    versionBusy.value = true
    try {
      const snapshotResp = toRecord(await deps.getVersionSnapshotUrl(projectId, chapterId, versionNo))
      const url = deps.normalizeStorageUrl(pickString(snapshotResp, ['downloadUrl', 'url', 'getUrl']))
      if (!url || !deps.hasObjectKeyInStorageUrl(url, '/read/')) throw new Error('版本快照地址为空')
      const text = await deps.fetchText(url)
      selectedVersionContent.value = text
      const currentLen = deps.getEditorContent().length
      const versionLen = text.length
      const delta = versionLen - currentLen
      versionDiffSummary.value = `当前 ${currentLen} 字 / 版本 ${versionLen} 字 / 差值 ${delta >= 0 ? '+' : ''}${delta}`
    } catch (error: any) {
      selectedVersionContent.value = ''
      versionDiffSummary.value = ''
      deps.notify(error?.message || '查看版本失败')
    } finally {
      versionBusy.value = false
    }
  }

  const refreshEditorFromRemote = async (
    projectId: number,
    chapterId: number,
    requestId: number,
    options?: { preferRemote?: boolean },
  ) => {
    const contentResp = toRecord(await deps.getContentUrl(projectId, chapterId))
    const downloadUrl = deps.normalizeStorageUrl(pickString(contentResp, ['downloadUrl', 'url', 'getUrl']))
    if (!downloadUrl || !deps.hasObjectKeyInStorageUrl(downloadUrl, '/read/')) return false
    const text = await deps.fetchText(downloadUrl)
    if (!deps.isChapterRequestCurrent(String(chapterId), requestId)) return false
    const resolvedContent = deps.resolveChapterContent(projectId, chapterId, text, options)
    deps.setEditorContent(resolvedContent)
    deps.setWordCount(countWords(resolvedContent))
    deps.setLastSnapshot(resolvedContent)
    return true
  }

  const restoreSelectedVersion = async () => {
    const projectId = deps.getProjectId()
    const chapterId = Number(deps.getActiveChapterKey())
    const operatorId = deps.getOperatorId()
    const versionNo = Number(selectedVersionNo.value)
    if (!projectId || !chapterId || !operatorId || !versionNo) {
      deps.notify('缺少 projectId/chapterId/operatorId/versionNo，无法恢复版本')
      return
    }

    versionBusy.value = true
    try {
      await deps.restoreVersion(projectId, chapterId, versionNo, operatorId)
      const requestId = deps.beginChapterRequest(String(chapterId))
      await refreshEditorFromRemote(projectId, chapterId, requestId, { preferRemote: true })
      await loadChapterVersions(projectId, String(chapterId))
      selectedVersionContent.value = ''
      versionDiffSummary.value = ''
      deps.notifySuccess(`已恢复到版本 v${versionNo}`)
    } catch (error: any) {
      deps.notify(error?.message || '恢复版本失败')
    } finally {
      versionBusy.value = false
    }
  }

  const uploadAndCommitContent = async (projectId: number, chapterId: number, content: string, operatorId: number) => {
    const uploadResp = toRecord(await deps.getContentUploadUrl(projectId, chapterId))
    const { uploadUrl, objectKey, storageProvider } = deps.resolveUploadTarget(uploadResp)

    const uploadResult = await deps.uploadText(uploadUrl, content)
    if (!uploadResult.ok) {
      throw new Error(`直传正文失败(${uploadResult.status || 0})`)
    }

    await deps.commitContent(projectId, chapterId, operatorId, {
      objectKey,
      etag: String(uploadResult.etag || ''),
      size: new Blob([content]).size,
      checksum: String(uploadResult.checksum || ''),
      storageProvider,
    })

    await deps.createVersion(projectId, chapterId, {
      changeType: 'MANUAL_SAVE',
      changeReason: '前端手动保存',
      createdBy: operatorId,
    })
  }

  const publishCurrentChapter = async () => {
    const projectId = deps.getProjectId()
    const chapterId = Number(deps.getActiveChapterKey())
    const operatorId = deps.getOperatorId()
    if (!projectId || !chapterId || !operatorId) {
      deps.notify('缺少 projectId/chapterId/operatorId，无法发布章节')
      return
    }

    versionBusy.value = true
    try {
      await uploadAndCommitContent(projectId, chapterId, deps.getEditorContent(), operatorId)
      await loadChapterVersions(projectId, String(chapterId))
      await deps.publishChapter(projectId, chapterId, operatorId)
      deps.notifySuccess('章节已发布')
    } catch (error: any) {
      deps.notify(error?.message || '发布章节失败')
    } finally {
      versionBusy.value = false
    }
  }

  return {
    chapterVersions,
    selectedVersionNo,
    selectedVersionContent,
    versionDiffSummary,
    versionBusy,
    getCurrentChapterVersions,
    loadChapterVersions,
    viewSelectedVersion,
    restoreSelectedVersion,
    publishCurrentChapter,
    refreshEditorFromRemote,
    uploadAndCommitContent,
  }
}
