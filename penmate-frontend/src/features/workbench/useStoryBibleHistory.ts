import { ref } from 'vue'
import { storyBibleApi } from '@/api/modules/storyBible.api'
import type { StoryBibleChangeset, StoryBibleChangesetDetails } from '@/entities/story-bible/model'

type StoryBibleHistoryOptions = {
  getProjectId: () => string
  getCurrentRevision: () => number | undefined
  getHistory: () => StoryBibleChangeset[]
  onUndone: () => void
}

export const useStoryBibleHistory = (options: StoryBibleHistoryOptions) => {
  const expandedId = ref('')
  const detailById = ref<Record<string, StoryBibleChangesetDetails>>({})
  const loadingId = ref('')
  const undoingId = ref('')

  const runChanges = (change: StoryBibleChangeset) => change.sourceRunId
    ? options.getHistory().filter((item) => item.sourceRunId === change.sourceRunId)
    : [change]
  const isRunHead = (change: StoryBibleChangeset) => !change.sourceRunId
    || change.contentRevision === Math.max(...runChanges(change).map((item) => item.contentRevision))
  const canUndo = (change: StoryBibleChangeset) => {
    const changes = runChanges(change)
    if (!isRunHead(change) || changes.some((item) => item.archivedAt || item.undoneAt)) return false
    const latestRevision = Math.max(...changes.map((item) => item.contentRevision))
    const currentRevision = options.getCurrentRevision()
    if (currentRevision != null && latestRevision !== currentRevision) return false
    return changes.every((item) => {
      const created = new Date(item.createdAt).getTime()
      return Number.isFinite(created) && Date.now() - created <= 7 * 24 * 60 * 60 * 1000
    })
  }
  const undoReason = (change: StoryBibleChangeset) => {
    const changes = runChanges(change)
    if (!isRunHead(change)) return '请从该 AI 任务的最新记录整体撤回'
    if (changes.some((item) => item.archivedAt)) return '已归档，只能查看'
    if (changes.some((item) => item.undoneAt)) return '已经撤回'
    if (changes.some((item) => Date.now() - new Date(item.createdAt).getTime() > 7 * 24 * 60 * 60 * 1000)) return '已超过 7 天'
    const latestRevision = Math.max(...changes.map((item) => item.contentRevision))
    const currentRevision = options.getCurrentRevision()
    if (currentRevision != null && latestRevision !== currentRevision) return '已有后续变更'
    return change.sourceRunId ? '撤回本次 AI 任务的全部故事圣经变更' : '撤回整组变更'
  }
  const toggle = async (change: StoryBibleChangeset) => {
    if (expandedId.value === change.changesetId) {
      expandedId.value = ''
      return
    }
    expandedId.value = change.changesetId
    if (detailById.value[change.changesetId]) return
    loadingId.value = change.changesetId
    try {
      detailById.value[change.changesetId] = await storyBibleApi.getChangeset(
        options.getProjectId(),
        change.changesetId,
      )
    } finally {
      loadingId.value = ''
    }
  }
  const undo = async (change: StoryBibleChangeset) => {
    if (!canUndo(change) || undoingId.value) return
    undoingId.value = change.changesetId
    try {
      if (change.sourceRunId) await storyBibleApi.undoRun(options.getProjectId(), change.sourceRunId)
      else await storyBibleApi.undoChangeset(options.getProjectId(), change.changesetId)
      expandedId.value = ''
      options.onUndone()
    } finally {
      undoingId.value = ''
    }
  }
  const formatTime = (value: string) => (value ? new Date(value).toLocaleString() : '')
  const actorName = (type: StoryBibleChangeset['actorType']) => ({ USER: '作者', AGENT: 'AI', SYSTEM: '系统' })[type]
  const operationLabel = (value: string) => ({ CREATE: '创建', UPDATE: '更新', DELETE: '删除', ARCHIVE: '归档', RESTORE: '恢复' }[value] || value)

  return {
    expandedId, detailById, loadingId, undoingId, canUndo, undoReason, toggle, undo, formatTime, actorName,
    operationLabel,
  }
}
