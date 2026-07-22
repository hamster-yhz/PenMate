import type { Ref } from 'vue'
import { novelApi } from '@/api/modules/novel.api'
import type { StoryBibleChapterOption } from '@/components/workbench/story-bible/storyBibleTypes'
import type { OutlineChapterNode, OutlineVolumeNode } from '@/composables/workbench/workbenchOutline'

type ChapterLoadGuard = {
  begin: (chapterId: string) => number
  isCurrent: (chapterId: string, requestId: number) => boolean
}

type WorkbenchProjectOptions = {
  novelTitle: Ref<string>
  storyBibleChapters: Ref<StoryBibleChapterOption[]>
  activeChapter: Ref<string>
  editorContent: Ref<string>
  chapterContents: Ref<Record<string, string>>
  getProjectId: () => string
  saveDraft: (projectId: string, chapterId: string, content: string) => void
  flushDraft: (projectId: string, chapterId: string) => Promise<void>
  selectChapter: (chapter: OutlineChapterNode) => void
  chapterLoadGuard: ChapterLoadGuard
  resolveStoredDraft: (projectId: string, chapterId: string) => Promise<string | null>
  resolveEditorSeedContent: (remoteContent: string | undefined, storedDraft: string | null) => string
  selectChapterDraft: (content: string) => void
  refreshEditorFromRemote: (projectId: string, chapterId: string, requestId: number) => Promise<boolean>
  loadOutline: (
    volumes: Array<Record<string, unknown>>,
    chapters?: Array<Record<string, unknown>>,
    structureRevision?: number,
  ) => OutlineVolumeNode[]
  loadActivePlugins: (projectId: string) => Promise<void>
  refreshActiveModelInfo: () => Promise<string | null>
}

const businessId = (value: unknown) => String(value ?? '').trim() || null

export const useWorkbenchProjectController = (options: WorkbenchProjectOptions) => {
  const loadRemoteContent = async (chapterIdInput: string, requestId: number) => {
    const projectId = options.getProjectId()
    const chapterId = businessId(chapterIdInput)
    if (!projectId || !chapterId) return
    try {
      const loaded = await options.refreshEditorFromRemote(projectId, chapterId, requestId)
      if (loaded || !options.chapterLoadGuard.isCurrent(chapterId, requestId)) return
    } catch {
      if (!options.chapterLoadGuard.isCurrent(chapterId, requestId)) return
    }
    const localDraft = await options.resolveStoredDraft(projectId, chapterId)
    if (localDraft !== null) {
      options.chapterContents.value[chapterId] = localDraft
      options.selectChapterDraft(localDraft)
    }
  }

  const updateTitle = (event: Event) => {
    const nextTitle = String((event.target as HTMLElement).textContent || '').trim() || '未命名小说'
    options.novelTitle.value = nextTitle
    const projectId = options.getProjectId()
    if (projectId) void novelApi.updateProject(projectId, { title: nextTitle }).catch(() => undefined)
  }

  const selectOutlineChapter = async (chapter: OutlineChapterNode) => {
    const chapterKey = String(chapter.chapterId || chapter.key)
    const previousProjectId = options.getProjectId()
    if (previousProjectId && options.activeChapter.value) {
      options.saveDraft(previousProjectId, options.activeChapter.value, options.editorContent.value)
      await options.flushDraft(previousProjectId, options.activeChapter.value)
    }
    options.activeChapter.value = chapterKey
    options.selectChapter(chapter)
    const requestId = options.chapterLoadGuard.begin(chapterKey)
    const projectId = options.getProjectId()
    if (projectId) {
      const localDraft = await options.resolveStoredDraft(projectId, chapterKey)
      const seed = localDraft ?? options.resolveEditorSeedContent(undefined, null)
      options.chapterContents.value[chapterKey] = seed
      options.selectChapterDraft(seed)
    }
    await loadRemoteContent(chapterKey, requestId)
  }

  const loadProject = async (projectId: string) => {
    if (!projectId) return
    const [project, directory] = await Promise.all([
      novelApi.getProject(projectId),
      novelApi.getDirectory(projectId),
    ])
    options.novelTitle.value = String(project.title ?? '').trim() || '未命名小说'
    const volumes = directory.volumes || []
    const chapters = directory.chapters || []
    options.storyBibleChapters.value = chapters.flatMap((chapter) => {
      const chapterId = businessId(chapter.chapterId)
      const displayNo = Number(chapter.displayNo)
      if (!chapterId || !Number.isInteger(displayNo) || displayNo < 1) return []
      return [{ chapterId, displayNo, title: String(chapter.title ?? '').trim() || '未命名章节' }]
    })
    const mapped = options.loadOutline(volumes, chapters, directory.structureRevision)
    if (!options.activeChapter.value) {
      const firstChapter = mapped.flatMap((volume) => volume.children).at(0)
      if (firstChapter) await selectOutlineChapter(firstChapter)
    }
    await Promise.all([options.loadActivePlugins(projectId), options.refreshActiveModelInfo()])
  }

  return { updateTitle, selectOutlineChapter, loadProject }
}
