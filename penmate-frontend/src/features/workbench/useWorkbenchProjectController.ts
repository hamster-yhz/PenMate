import type { Ref } from 'vue'
import { novelApi } from '@/api/modules/novel.api'
import { outlineApi } from '@/api/modules/outline.api'
import type { StoryBibleChapterOption } from '@/components/workbench/story-bible/storyBibleTypes'
import type { OutlineChapterNode } from '@/composables/workbench/workbenchOutline'
import { pickBusinessArray } from '@/utils/apiPayload'

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
  selectChapter: (chapter: OutlineChapterNode) => void
  chapterLoadGuard: ChapterLoadGuard
  resolveStoredDraft: (projectId: string, chapterId: string) => string | null
  resolveEditorSeedContent: (remoteContent: string | undefined, storedDraft: string | null) => string
  selectChapterDraft: (content: string) => void
  refreshEditorFromRemote: (projectId: string, chapterId: string, requestId: number) => Promise<boolean>
  loadChapterVersions: (projectId: string, chapterId: string) => Promise<void>
  loadOutline: (nodes: Array<Record<string, unknown>>, chapterMap?: Record<string, string>) => void
  loadActivePlugins: (projectId: string) => Promise<void>
  refreshActiveModelInfo: () => Promise<string | null>
}

const businessId = (value: unknown) => String(value ?? '').trim() || null

const fallbackOutline = (chapters: Array<Record<string, unknown>>) => {
  const volumeNodeId = 'virtual-volume-root'
  const chapterNodes = chapters.flatMap((chapter) => {
    const chapterId = businessId(chapter.chapterId)
    const outlineNodeId = businessId(chapter.outlineNodeId)
    if (!outlineNodeId || !chapterId) return []
    return [
      {
        outlineNodeId,
        chapterId,
        title: String(chapter.title ?? chapter.chapterTitle ?? '未命名章节'),
        nodeType: 'CHAPTER',
        parentId: volumeNodeId,
      },
    ]
  })
  return chapterNodes.length
    ? [{ outlineNodeId: volumeNodeId, title: '未分卷', nodeType: 'VOLUME' }, ...chapterNodes]
    : []
}

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
    const localDraft = options.resolveStoredDraft(projectId, chapterId)
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
    }
    options.activeChapter.value = chapterKey
    options.selectChapter(chapter)
    const requestId = options.chapterLoadGuard.begin(chapterKey)
    const projectId = options.getProjectId()
    if (projectId) {
      const localDraft = options.resolveStoredDraft(projectId, chapterKey)
      const seed = localDraft ?? options.resolveEditorSeedContent(undefined, null)
      options.chapterContents.value[chapterKey] = seed
      options.selectChapterDraft(seed)
    }
    await loadRemoteContent(chapterKey, requestId)
    if (projectId) await options.loadChapterVersions(projectId, chapterKey)
  }

  const loadProject = async (projectId: string) => {
    if (!projectId) return
    const outlineResponse = pickBusinessArray<Record<string, unknown>>(await outlineApi.listOutlineTree(projectId))
    const chapters = pickBusinessArray<Record<string, unknown>>(await novelApi.listChapters(projectId))
    options.storyBibleChapters.value = chapters.flatMap((chapter) => {
      const chapterId = businessId(chapter.chapterId)
      const displayNo = Number(chapter.displayNo)
      if (!chapterId || !Number.isInteger(displayNo) || displayNo < 1) return []
      return [{ chapterId, displayNo, title: String(chapter.title ?? '').trim() || '未命名章节' }]
    })
    const chapterMap = Object.fromEntries(
      chapters
        .map((chapter) => {
          const outlineNodeId = businessId(chapter.outlineNodeId)
          const chapterId = businessId(chapter.chapterId)
          return outlineNodeId && chapterId ? [outlineNodeId, chapterId] : null
        })
        .filter((entry): entry is [string, string] => Array.isArray(entry)),
    )
    const hasVolume = outlineResponse.some((node) =>
      String(node.nodeType ?? node.type ?? '')
        .toUpperCase()
        .includes('VOLUME'),
    )
    options.loadOutline(outlineResponse.length && hasVolume ? outlineResponse : fallbackOutline(chapters), chapterMap)
    await Promise.all([options.loadActivePlugins(projectId), options.refreshActiveModelInfo()])
  }

  return { updateTitle, selectOutlineChapter, loadProject }
}
