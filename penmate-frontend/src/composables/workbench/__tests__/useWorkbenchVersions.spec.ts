import { describe, expect, it, vi } from 'vitest'

type UseWorkbenchVersionsFactory = (deps: any) => {
  chapterVersions: { value: Record<string, Array<Record<string, unknown>>> }
  selectedVersionNo: { value: string }
  selectedVersionContent: { value: string }
  versionDiffSummary: { value: string }
  versionBusy: { value: boolean }
  getCurrentChapterVersions: () => Array<Record<string, unknown>>
  loadChapterVersions: (projectId: number, chapterId: string) => Promise<void>
  viewSelectedVersion: () => Promise<void>
  restoreSelectedVersion: () => Promise<void>
  publishCurrentChapter: () => Promise<void>
  refreshEditorFromRemote: (projectId: number, chapterId: number, requestId: number, options?: { preferRemote?: boolean }) => Promise<boolean>
  uploadAndCommitContent: (projectId: number, chapterId: number, content: string, operatorId: number) => Promise<void>
}

const loadUseWorkbenchVersions = async (): Promise<UseWorkbenchVersionsFactory> => {
  try {
    const modulePath = '../useWorkbenchVersions'
    return (await import(/* @vite-ignore */ modulePath)).useWorkbenchVersions as UseWorkbenchVersionsFactory
  } catch {
    return ((deps: any) => ({
      chapterVersions: { value: {} },
      selectedVersionNo: { value: '' },
      selectedVersionContent: { value: '' },
      versionDiffSummary: { value: '' },
      versionBusy: { value: false },
      getCurrentChapterVersions: () => [],
      loadChapterVersions: async () => undefined,
      viewSelectedVersion: async () => undefined,
      restoreSelectedVersion: async () => undefined,
      publishCurrentChapter: async () => undefined,
      refreshEditorFromRemote: async () => false,
      uploadAndCommitContent: async () => undefined,
      __deps: deps,
    })) as unknown as UseWorkbenchVersionsFactory
  }
}

describe('useWorkbenchVersions', () => {
  it('loads_versions_for_active_chapter_and_selects_first_version', async () => {
    const useWorkbenchVersions = await loadUseWorkbenchVersions()
    const listVersions = vi.fn(async () => [
      { chapterVersionId: 501, versionNo: 9, changeReason: '修正文风' },
      { chapterVersionId: 500, versionNo: 8, changeType: 'MANUAL_SAVE' },
    ])

    const versions = useWorkbenchVersions({
      getProjectId: () => 101,
      getActiveChapterKey: () => '301',
      getOperatorId: () => 201,
      getEditorContent: () => '当前正文',
      setEditorContent: vi.fn(),
      setWordCount: vi.fn(),
      setLastSnapshot: vi.fn(),
      resolveChapterContent: vi.fn((_projectId, _chapterId, remoteContent) => remoteContent),
      resolveStoredDraft: vi.fn(() => null),
      clearDraft: vi.fn(),
      beginChapterRequest: vi.fn(() => 1),
      isChapterRequestCurrent: vi.fn(() => true),
      listVersions,
      getVersionSnapshotUrl: vi.fn(async () => ({ downloadUrl: 'https://oss.example/read/version-9.txt' })),
      getContentUrl: vi.fn(async () => ({ downloadUrl: 'https://oss.example/read/chapter-301.txt' })),
      restoreVersion: vi.fn(async () => undefined),
      publishChapter: vi.fn(async () => undefined),
      getContentUploadUrl: vi.fn(async () => ({ uploadUrl: 'https://oss.example/upload', objectKey: 'chapter-301.txt', storageProvider: 'OSS' })),
      commitContent: vi.fn(async () => undefined),
      createVersion: vi.fn(async () => undefined),
      resolveUploadTarget: (payload: any) => payload,
      normalizeStorageUrl: (url: string) => url,
      hasObjectKeyInStorageUrl: () => true,
      fetchText: vi.fn(async () => '版本正文'),
      uploadText: vi.fn(async () => ({ ok: true, status: 200, etag: 'etag-1', checksum: 'crc32-1' })),
      notify: vi.fn(),
      notifySuccess: vi.fn(),
    })

    await versions.loadChapterVersions(101, '301')

    expect(listVersions).toHaveBeenCalledWith(101, 301)
    expect(versions.chapterVersions.value['301']).toEqual([
      { chapterVersionId: 501, versionNo: 9, changeReason: '修正文风' },
      { chapterVersionId: 500, versionNo: 8, changeType: 'MANUAL_SAVE' },
    ])
    expect(versions.selectedVersionNo.value).toBe('9')
    expect(versions.getCurrentChapterVersions()).toHaveLength(2)
  })

  it('views_selected_version_and_generates_diff_summary', async () => {
    const useWorkbenchVersions = await loadUseWorkbenchVersions()
    const fetchText = vi.fn(async () => '版本中的正文片段')

    const versions = useWorkbenchVersions({
      getProjectId: () => 101,
      getActiveChapterKey: () => '301',
      getOperatorId: () => 201,
      getEditorContent: () => '当前正文',
      setEditorContent: vi.fn(),
      setWordCount: vi.fn(),
      setLastSnapshot: vi.fn(),
      resolveChapterContent: vi.fn((_projectId, _chapterId, remoteContent) => remoteContent),
      resolveStoredDraft: vi.fn(() => null),
      clearDraft: vi.fn(),
      beginChapterRequest: vi.fn(() => 1),
      isChapterRequestCurrent: vi.fn(() => true),
      listVersions: vi.fn(async () => []),
      getVersionSnapshotUrl: vi.fn(async () => ({ downloadUrl: 'https://oss.example/read/version-5.txt' })),
      getContentUrl: vi.fn(async () => ({ downloadUrl: 'https://oss.example/read/chapter-301.txt' })),
      restoreVersion: vi.fn(async () => undefined),
      publishChapter: vi.fn(async () => undefined),
      getContentUploadUrl: vi.fn(async () => ({ uploadUrl: 'https://oss.example/upload', objectKey: 'chapter-301.txt', storageProvider: 'OSS' })),
      commitContent: vi.fn(async () => undefined),
      createVersion: vi.fn(async () => undefined),
      resolveUploadTarget: (payload: any) => payload,
      normalizeStorageUrl: (url: string) => url,
      hasObjectKeyInStorageUrl: () => true,
      fetchText,
      uploadText: vi.fn(async () => ({ ok: true, status: 200, etag: 'etag-1', checksum: 'crc32-1' })),
      notify: vi.fn(),
      notifySuccess: vi.fn(),
    })

    versions.selectedVersionNo.value = '5'
    await versions.viewSelectedVersion()

    expect(fetchText).toHaveBeenCalledWith('https://oss.example/read/version-5.txt')
    expect(versions.selectedVersionContent.value).toBe('版本中的正文片段')
    expect(versions.versionDiffSummary.value).toBe('当前 4 字 / 版本 8 字 / 差值 +4')
  })

  it('restores_selected_version_then_refreshes_editor_from_remote_content', async () => {
    const useWorkbenchVersions = await loadUseWorkbenchVersions()
    const setEditorContent = vi.fn()
    const setWordCount = vi.fn()
    const setLastSnapshot = vi.fn()
    const restoreVersion = vi.fn(async () => undefined)
    const beginChapterRequest = vi.fn(() => 77)
    const listVersions = vi.fn(async () => [{ versionNo: 7, chapterVersionId: 501 }])
    const fetchText = vi.fn(async () => '恢复后的远端正文')
    const notifySuccess = vi.fn()

    const versions = useWorkbenchVersions({
      getProjectId: () => 101,
      getActiveChapterKey: () => '301',
      getOperatorId: () => 201,
      getEditorContent: () => '当前正文',
      setEditorContent,
      setWordCount,
      setLastSnapshot,
      resolveChapterContent: vi.fn((_projectId, _chapterId, remoteContent) => remoteContent),
      resolveStoredDraft: vi.fn(() => null),
      clearDraft: vi.fn(),
      beginChapterRequest,
      isChapterRequestCurrent: vi.fn((chapterId: string, requestId: number) => chapterId === '301' && requestId === 77),
      listVersions,
      getVersionSnapshotUrl: vi.fn(async () => ({ downloadUrl: 'https://oss.example/read/version-7.txt' })),
      getContentUrl: vi.fn(async () => ({ downloadUrl: 'https://oss.example/read/chapter-301.txt' })),
      restoreVersion,
      publishChapter: vi.fn(async () => undefined),
      getContentUploadUrl: vi.fn(async () => ({ uploadUrl: 'https://oss.example/upload', objectKey: 'chapter-301.txt', storageProvider: 'OSS' })),
      commitContent: vi.fn(async () => undefined),
      createVersion: vi.fn(async () => undefined),
      resolveUploadTarget: (payload: any) => payload,
      normalizeStorageUrl: (url: string) => url,
      hasObjectKeyInStorageUrl: () => true,
      fetchText,
      uploadText: vi.fn(async () => ({ ok: true, status: 200, etag: 'etag-1', checksum: 'crc32-1' })),
      notify: vi.fn(),
      notifySuccess,
    })

    versions.selectedVersionNo.value = '7'
    await versions.restoreSelectedVersion()

    expect(restoreVersion).toHaveBeenCalledWith(101, 301, 7, 201)
    expect(beginChapterRequest).toHaveBeenCalledWith('301')
    expect(fetchText).toHaveBeenCalledWith('https://oss.example/read/chapter-301.txt')
    expect(setEditorContent).toHaveBeenCalledWith('恢复后的远端正文')
    expect(setWordCount).toHaveBeenCalledWith(8)
    expect(setLastSnapshot).toHaveBeenCalledWith('恢复后的远端正文')
    expect(listVersions).toHaveBeenCalledWith(101, 301)
    expect(versions.selectedVersionContent.value).toBe('')
    expect(versions.versionDiffSummary.value).toBe('')
    expect(notifySuccess).toHaveBeenCalledWith('已恢复到版本 v7')
  })

  it('uploads_and_commits_content_before_publishing_current_chapter', async () => {
    const useWorkbenchVersions = await loadUseWorkbenchVersions()
    const publishChapter = vi.fn(async () => undefined)
    const getContentUploadUrl = vi.fn(async () => ({
      uploadUrl: 'https://oss.example/upload/chapter-301.txt',
      objectKey: 'chapter-301.txt',
      storageProvider: 'OSS',
    }))
    const uploadText = vi.fn(async () => ({
      ok: true,
      status: 200,
      etag: 'etag-301',
      checksum: 'crc32-301',
    }))
    const commitContent = vi.fn(async () => undefined)
    const createVersion = vi.fn(async () => undefined)
    const listVersions = vi.fn(async () => [{ versionNo: 11, chapterVersionId: 700 }])
    const notifySuccess = vi.fn()

    const versions = useWorkbenchVersions({
      getProjectId: () => 101,
      getActiveChapterKey: () => '301',
      getOperatorId: () => 201,
      getEditorContent: () => '待发布正文',
      setEditorContent: vi.fn(),
      setWordCount: vi.fn(),
      setLastSnapshot: vi.fn(),
      resolveChapterContent: vi.fn((_projectId, _chapterId, remoteContent) => remoteContent),
      resolveStoredDraft: vi.fn(() => null),
      clearDraft: vi.fn(),
      beginChapterRequest: vi.fn(() => 1),
      isChapterRequestCurrent: vi.fn(() => true),
      listVersions,
      getVersionSnapshotUrl: vi.fn(async () => ({ downloadUrl: 'https://oss.example/read/version-11.txt' })),
      getContentUrl: vi.fn(async () => ({ downloadUrl: 'https://oss.example/read/chapter-301.txt' })),
      restoreVersion: vi.fn(async () => undefined),
      publishChapter,
      getContentUploadUrl,
      commitContent,
      createVersion,
      resolveUploadTarget: (payload: any) => payload,
      normalizeStorageUrl: (url: string) => url,
      hasObjectKeyInStorageUrl: () => true,
      fetchText: vi.fn(async () => '远端正文'),
      uploadText,
      notify: vi.fn(),
      notifySuccess,
    })

    await versions.publishCurrentChapter()

    expect(getContentUploadUrl).toHaveBeenCalledWith(101, 301)
    expect(uploadText).toHaveBeenCalledWith('https://oss.example/upload/chapter-301.txt', '待发布正文')
    expect(commitContent).toHaveBeenCalledWith(101, 301, 201, {
      objectKey: 'chapter-301.txt',
      etag: 'etag-301',
      size: 15,
      checksum: 'crc32-301',
      storageProvider: 'OSS',
    })
    expect(createVersion).toHaveBeenCalledWith(101, 301, {
      changeType: 'MANUAL_SAVE',
      changeReason: '前端手动保存',
      createdBy: 201,
    })
    expect(publishChapter).toHaveBeenCalledWith(101, 301, 201)
    expect(listVersions).toHaveBeenCalledWith(101, 301)
    expect(notifySuccess).toHaveBeenCalledWith('章节已发布')
  })
})
