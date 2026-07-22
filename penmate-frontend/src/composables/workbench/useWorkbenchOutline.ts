import { ref } from 'vue'

import type { MoveNovelDirectoryItemPayload, NovelDirectoryState } from '@/api/modules/novel.api'
import { mapNovelDirectory, type OutlineChapterNode, type OutlineVolumeNode } from './workbenchOutline'

type ContextProfile = {
  projectId?: string | null
  operatorId?: string | null
}

type OutlineApiPayload = Record<string, unknown>

type UseWorkbenchOutlineDeps = {
  getContext: () => ContextProfile
  reloadOutline: () => Promise<void>
  createVolume: (projectId: string, operatorId: string, payload: OutlineApiPayload) => Promise<unknown>
  createChapter: (projectId: string, operatorId: string, payload: OutlineApiPayload) => Promise<unknown>
  deleteVolume: (projectId: string, volumeId: string, operatorId: string) => Promise<unknown>
  deleteChapter: (projectId: string, chapterId: string, operatorId: string) => Promise<unknown>
  updateVolume: (
    projectId: string,
    volumeId: string,
    operatorId: string,
    payload: OutlineApiPayload,
  ) => Promise<unknown>
  updateChapter: (
    projectId: string,
    chapterId: string,
    operatorId: string,
    payload: OutlineApiPayload,
  ) => Promise<unknown>
  moveDirectoryItem: (projectId: string, payload: MoveNovelDirectoryItemPayload) => Promise<NovelDirectoryState>
  notify?: (message: string) => void
  notifySuccess?: (message: string) => void
}

export type RenameNodePayload = {
  nodeKey: string
  title: string
}

export type MoveNodePayload = {
  nodeKey: string
  direction?: -1 | 1
  parentKey?: string
  targetParentKey?: string
  targetIndex?: number
  drop?: boolean
}

export type DeleteChapterPayload = {
  nodeKey: string
  parentKey: string
}

type OutlineTreeNode = (OutlineVolumeNode | OutlineChapterNode) & {
  chapterId?: string
}

const VOLUME_NUMERALS = ['一', '二', '三', '四', '五', '六', '七', '八', '九', '十']

const toBusinessId = (value: unknown) => {
  if (typeof value !== 'string') {
    return ''
  }
  const normalized = value.trim()
  return normalized || ''
}

const findVolumeIndexByKey = (volumes: OutlineVolumeNode[], nodeKey: string) =>
  volumes.findIndex((item) => item.key === nodeKey)

const findVolumeByKey = (volumes: OutlineVolumeNode[], nodeKey: string) => volumes.find((item) => item.key === nodeKey)

const findNodeByKey = (volumes: OutlineVolumeNode[], nodeKey: string): OutlineTreeNode | null => {
  const volume = findVolumeByKey(volumes, nodeKey)
  if (volume) return volume

  for (const item of volumes) {
    const chapter = item.children.find((child) => child.key === nodeKey)
    if (chapter) return chapter
  }

  return null
}

const clearSelection = (activeChapter: { value: string }, currentChapterTitle: { value: string }) => {
  activeChapter.value = ''
  currentChapterTitle.value = ''
}

export const useWorkbenchOutline = (deps: UseWorkbenchOutlineDeps) => {
  const outlineData = ref<OutlineVolumeNode[]>([])
  const activeChapter = ref('')
  const currentChapterTitle = ref('')
  const outlineOpBusy = ref(false)
  const structureRevision = ref(0)
  const pendingMoveUndo = ref<{
    label: string
    payload: Omit<MoveNovelDirectoryItemPayload, 'expectedStructureRevision'>
  } | null>(null)
  let undoTimer: ReturnType<typeof setTimeout> | null = null

  const loadOutline = (
    volumes: Array<Record<string, unknown>>,
    chapters: Array<Record<string, unknown>> = [],
    revision = structureRevision.value,
  ) => {
    const mapped = mapNovelDirectory(volumes, chapters)
    outlineData.value = mapped
    structureRevision.value = Number(revision) || 0
    return mapped
  }

  const applyDirectory = (directory: NovelDirectoryState) =>
    loadOutline(directory.volumes || [], directory.chapters || [], directory.structureRevision)

  const offerMoveUndo = (
    label: string,
    payload: Omit<MoveNovelDirectoryItemPayload, 'expectedStructureRevision'>,
  ) => {
    if (undoTimer) clearTimeout(undoTimer)
    pendingMoveUndo.value = { label, payload }
    undoTimer = setTimeout(() => {
      pendingMoveUndo.value = null
      undoTimer = null
    }, 8000)
  }

  const selectChapter = (chapter: OutlineChapterNode) => {
    activeChapter.value = chapter.chapterId || chapter.key
    currentChapterTitle.value = chapter.title
  }

  const addVolume = async () => {
    if (outlineOpBusy.value) return

    const { projectId, operatorId } = deps.getContext()
    if (!projectId || !operatorId) {
      deps.notify?.('缺少 projectId/operatorId，无法新建分卷')
      return
    }

    const idx = outlineData.value.length
    const title = `第${VOLUME_NUMERALS[idx] || idx + 1}卷：新的篇章`

    outlineOpBusy.value = true
    try {
      await deps.createVolume(projectId, operatorId, {
        title,
        sortOrder: idx + 1,
        description: '',
      })
      await deps.reloadOutline()
    } catch (error: unknown) {
      deps.notify?.(error instanceof Error ? error.message : '新建分卷失败')
    } finally {
      outlineOpBusy.value = false
    }
  }

  const addChapter = async (volume: OutlineVolumeNode) => {
    if (outlineOpBusy.value) return

    const { projectId, operatorId } = deps.getContext()
    if (!projectId || !operatorId) {
      deps.notify?.('缺少 projectId/operatorId，无法新建章节')
      return
    }

    const volumeId = toBusinessId(volume.key)
    if (!volumeId) {
      deps.notify?.('分卷ID异常，无法创建章节')
      return
    }

    const idx = volume.children.length
    const title = `第${idx + 1}章：未命名`

    outlineOpBusy.value = true
    try {
      await deps.createChapter(projectId, operatorId, {
        volumeId,
        title,
        sortOrder: idx + 1,
      })

      await deps.reloadOutline()
      deps.notifySuccess?.('章节已创建')
    } catch (error: unknown) {
      deps.notify?.(error instanceof Error ? error.message : '新建章节失败')
    } finally {
      outlineOpBusy.value = false
    }
  }

  const deleteVolume = async (nodeKey: string) => {
    const { projectId, operatorId } = deps.getContext()
    const volumeIndex = findVolumeIndexByKey(outlineData.value, nodeKey)
    if (volumeIndex < 0) return
    const volume = outlineData.value[volumeIndex]

    if (projectId && operatorId && nodeKey) {
      try {
        await deps.deleteVolume(projectId, nodeKey, operatorId)
        outlineData.value.splice(volumeIndex, 1)
        const removedActive = volume.children.some(
          (chapter) => activeChapter.value === (chapter.chapterId || chapter.key),
        )
        if (removedActive) {
          clearSelection(activeChapter, currentChapterTitle)
        }
      } catch (error: unknown) {
        deps.notify?.(error instanceof Error ? error.message : '删除分卷失败')
      }
    }
  }

  const deleteChapter = async ({ nodeKey, parentKey }: DeleteChapterPayload) => {
    const { projectId, operatorId } = deps.getContext()
    const volume = findVolumeByKey(outlineData.value, parentKey)
    const chapterIndex = volume?.children.findIndex((item) => item.key === nodeKey) ?? -1
    const chapter = chapterIndex >= 0 && volume ? volume.children[chapterIndex] : null

    if (!volume || !chapter) return

    if (projectId && operatorId && nodeKey) {
      try {
        await deps.deleteChapter(projectId, chapter.chapterId || nodeKey, operatorId)
        volume.children.splice(chapterIndex, 1)
        if (activeChapter.value === (chapter.chapterId || chapter.key)) {
          clearSelection(activeChapter, currentChapterTitle)
        }
      } catch (error: unknown) {
        deps.notify?.(error instanceof Error ? error.message : '删除章节失败')
      }
    }
  }

  const renameNode = async ({ nodeKey, title }: RenameNodePayload) => {
    const nextTitle = title.trim()
    if (!nextTitle) return

    const target = findNodeByKey(outlineData.value, nodeKey)
    if (!target) return

    const previousTitle = target.title
    const previousCurrentChapterTitle = currentChapterTitle.value
    target.title = nextTitle
    if (activeChapter.value === (target.chapterId || target.key)) {
      currentChapterTitle.value = nextTitle
    }

    const { projectId, operatorId } = deps.getContext()
    if (projectId && operatorId && nodeKey) {
      try {
        if ('children' in target) {
          await deps.updateVolume(projectId, nodeKey, operatorId, {
            title: nextTitle,
            sortOrder: outlineData.value.indexOf(target as OutlineVolumeNode) + 1,
            description: '',
          })
        } else {
          await deps.updateChapter(projectId, target.chapterId || nodeKey, operatorId, {
            title: nextTitle,
          })
        }
      } catch (error: unknown) {
        target.title = previousTitle
        currentChapterTitle.value = previousCurrentChapterTitle
        deps.notify?.(error instanceof Error ? error.message : '重命名失败')
      }
    }
  }

  const moveNode = async ({ nodeKey, parentKey, direction, targetParentKey, targetIndex, drop }: MoveNodePayload) => {
    const { projectId, operatorId } = deps.getContext()
    if (!projectId || !operatorId || outlineOpBusy.value || structureRevision.value < 1) return

    if (!parentKey) {
      const currentIdx = findVolumeIndexByKey(outlineData.value, nodeKey)
      let resolvedTargetIndex = targetIndex ?? currentIdx + (direction ?? 0)
      if (drop && currentIdx < resolvedTargetIndex) resolvedTargetIndex -= 1
      if (currentIdx < 0 || resolvedTargetIndex < 0 || resolvedTargetIndex >= outlineData.value.length) return
      if (currentIdx === resolvedTargetIndex) return

      outlineOpBusy.value = true
      try {
        const directory = await deps.moveDirectoryItem(projectId, {
          nodeType: 'VOLUME',
          nodeId: nodeKey,
          sortOrder: resolvedTargetIndex + 1,
          expectedStructureRevision: structureRevision.value,
        })
        applyDirectory(directory)
        offerMoveUndo('分卷已移动', {
          nodeType: 'VOLUME',
          nodeId: nodeKey,
          sortOrder: currentIdx + 1,
        })
      } catch (error: unknown) {
        deps.notify?.(error instanceof Error ? error.message : '移动分卷失败')
      } finally {
        outlineOpBusy.value = false
      }
      return
    }

    const source = findVolumeByKey(outlineData.value, parentKey)
    if (!source) return
    const currentIdx = source.children.findIndex((item) => item.key === nodeKey)
    if (currentIdx < 0) return

    const destinationKey = targetParentKey || parentKey
    const destination = findVolumeByKey(outlineData.value, destinationKey)
    if (!destination) return
    let resolvedTargetIndex = targetIndex ?? currentIdx + (direction ?? 0)
    if (drop && targetParentKey === parentKey && currentIdx < resolvedTargetIndex) {
      resolvedTargetIndex -= 1
    }
    const maxIndex = destinationKey === parentKey ? destination.children.length - 1 : destination.children.length
    if (resolvedTargetIndex < 0 || resolvedTargetIndex > maxIndex) return
    if (destinationKey === parentKey && currentIdx === resolvedTargetIndex) return

    const chapter = source.children[currentIdx]
    outlineOpBusy.value = true
    try {
      const directory = await deps.moveDirectoryItem(projectId, {
        nodeType: 'CHAPTER',
        nodeId: chapter.chapterId || chapter.key,
        targetVolumeId: destinationKey,
        sortOrder: resolvedTargetIndex + 1,
        expectedStructureRevision: structureRevision.value,
      })
      applyDirectory(directory)
      offerMoveUndo('章节已移动', {
        nodeType: 'CHAPTER',
        nodeId: chapter.chapterId || chapter.key,
        targetVolumeId: parentKey,
        sortOrder: currentIdx + 1,
      })
    } catch (error: unknown) {
      deps.notify?.(error instanceof Error ? error.message : '移动章节失败')
    } finally {
      outlineOpBusy.value = false
    }
  }

  const undoLastMove = async () => {
    const pending = pendingMoveUndo.value
    const { projectId } = deps.getContext()
    if (!pending || !projectId || outlineOpBusy.value) return

    outlineOpBusy.value = true
    try {
      const directory = await deps.moveDirectoryItem(projectId, {
        ...pending.payload,
        expectedStructureRevision: structureRevision.value,
      })
      applyDirectory(directory)
      pendingMoveUndo.value = null
      if (undoTimer) clearTimeout(undoTimer)
      undoTimer = null
      deps.notifySuccess?.('已撤销移动')
    } catch (error: unknown) {
      deps.notify?.(error instanceof Error ? error.message : '撤销移动失败')
    } finally {
      outlineOpBusy.value = false
    }
  }

  return {
    outlineData,
    activeChapter,
    currentChapterTitle,
    outlineOpBusy,
    structureRevision,
    pendingMoveUndo,
    loadOutline,
    selectChapter,
    addVolume,
    addChapter,
    deleteVolume,
    deleteChapter,
    renameNode,
    moveNode,
    undoLastMove,
  }
}
