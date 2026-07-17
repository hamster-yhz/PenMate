import { ref } from 'vue'

import {
  mapOutlineTree,
  type OutlineChapterNode,
  type OutlineVolumeNode,
} from './workbenchOutline'

type ContextProfile = {
  projectId?: string | null
  operatorId?: string | null
}

type OutlineApiPayload = Record<string, unknown>

type UseWorkbenchOutlineDeps = {
  getContext: () => ContextProfile
  reloadOutline: () => Promise<void>
  createOutlineNode: (projectId: string, operatorId: string, payload: OutlineApiPayload) => Promise<unknown>
  createChapter: (projectId: string, operatorId: string, payload: OutlineApiPayload) => Promise<unknown>
  deleteOutlineNode: (projectId: string, nodeId: string, operatorId: string) => Promise<unknown>
  deleteChapter: (projectId: string, chapterId: string, operatorId: string) => Promise<unknown>
  updateOutlineNode: (projectId: string, nodeId: string, operatorId: string, payload: OutlineApiPayload) => Promise<unknown>
  moveOutlineNode: (projectId: string, nodeId: string, operatorId: string, payload: OutlineApiPayload) => Promise<unknown>
  moveChapter?: (projectId: string, chapterId: string, operatorId: string, payload: OutlineApiPayload) => Promise<unknown>
  notify?: (message: string) => void
  notifySuccess?: (message: string) => void
}

export type RenameNodePayload = {
  nodeKey: string
  title: string
}

export type MoveNodePayload = {
  nodeKey: string
  direction: -1 | 1
  parentKey?: string
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

const pickOutlineNodeId = (item: Record<string, unknown>) => toBusinessId(item.outlineNodeId)

const findVolumeIndexByKey = (volumes: OutlineVolumeNode[], nodeKey: string) => volumes.findIndex((item) => item.key === nodeKey)

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

const clearSelection = (
  activeChapter: { value: string },
  currentChapterTitle: { value: string },
) => {
  activeChapter.value = ''
  currentChapterTitle.value = ''
}

export const useWorkbenchOutline = (deps: UseWorkbenchOutlineDeps) => {
  const outlineData = ref<OutlineVolumeNode[]>([])
  const activeChapter = ref('')
  const currentChapterTitle = ref('')
  const outlineOpBusy = ref(false)

  const loadOutline = (
    nodes: Array<Record<string, unknown>>,
    chapterByOutlineNodeId: Record<string, string> = {},
  ) => {
    const mapped = mapOutlineTree(nodes, chapterByOutlineNodeId)
    outlineData.value = mapped
    return mapped
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
      await deps.createOutlineNode(projectId, operatorId, {
        parentId: null,
        title,
        nodeType: 'VOLUME',
        sortOrder: idx + 1,
        content: '',
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

    const volumeNodeId = toBusinessId(volume.key)
    if (!volumeNodeId) {
      deps.notify?.('分卷节点ID异常，无法创建章节')
      return
    }

    const idx = volume.children.length
    const title = `第${idx + 1}章：未命名`

    outlineOpBusy.value = true
    let createdOutlineNodeId = ''
    try {
      const createdOutline = await deps.createOutlineNode(projectId, operatorId, {
        parentId: volumeNodeId,
        title,
        nodeType: 'CHAPTER',
        sortOrder: idx + 1,
        content: '',
      }) as Record<string, unknown>
      createdOutlineNodeId = pickOutlineNodeId(createdOutline)

      await deps.createChapter(projectId, operatorId, {
        volumeId: null,
        outlineNodeId: createdOutlineNodeId || null,
        title,
        sortOrder: idx + 1,
        status: 1,
        wordCount: 0,
        excerpt: '',
      })

      await deps.reloadOutline()
      deps.notifySuccess?.('章节已创建')
    } catch (error: unknown) {
      if (projectId && operatorId && createdOutlineNodeId) {
        await deps.deleteOutlineNode(projectId, createdOutlineNodeId, operatorId).catch(() => undefined)
      }
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
        await deps.deleteOutlineNode(projectId, nodeKey, operatorId)
        outlineData.value.splice(volumeIndex, 1)
        const removedActive = volume.children.some((chapter) => activeChapter.value === (chapter.chapterId || chapter.key))
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
        if (chapter.chapterId) {
          await deps.deleteChapter(projectId, chapter.chapterId, operatorId)
        }
        await deps.deleteOutlineNode(projectId, nodeKey, operatorId)
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
        await deps.updateOutlineNode(projectId, nodeKey, operatorId, { title: nextTitle })
      } catch (error: unknown) {
        target.title = previousTitle
        currentChapterTitle.value = previousCurrentChapterTitle
        deps.notify?.(error instanceof Error ? error.message : '重命名失败')
      }
    }
  }

  const moveNode = async ({ nodeKey, parentKey, direction }: MoveNodePayload) => {
    const { projectId, operatorId } = deps.getContext()

    if (!parentKey) {
      const currentIdx = findVolumeIndexByKey(outlineData.value, nodeKey)
      const targetIdx = currentIdx + direction
      if (currentIdx < 0 || targetIdx < 0 || targetIdx >= outlineData.value.length) return

      if (projectId && operatorId && nodeKey) {
        try {
          await deps.moveOutlineNode(projectId, nodeKey, operatorId, {
            parentId: null,
            sortOrder: targetIdx + 1,
          })
        } catch (error: unknown) {
          deps.notify?.(error instanceof Error ? error.message : '移动分卷失败')
          return
        }
      }

      const [item] = outlineData.value.splice(currentIdx, 1)
      outlineData.value.splice(targetIdx, 0, item)
      return
    }

    const volume = findVolumeByKey(outlineData.value, parentKey)
    if (!volume) return

    const currentIdx = volume.children.findIndex((item) => item.key === nodeKey)
    const targetIdx = currentIdx + direction
    if (currentIdx < 0 || targetIdx < 0 || targetIdx >= volume.children.length) return

    if (projectId && operatorId && nodeKey) {
      try {
        await deps.moveOutlineNode(projectId, nodeKey, operatorId, {
          parentId: parentKey || null,
          sortOrder: targetIdx + 1,
        })
        const chapter = volume.children[currentIdx]
        if (chapter.chapterId && deps.moveChapter) {
          await deps.moveChapter(projectId, chapter.chapterId, operatorId, {
            volumeId: null,
            sortOrder: targetIdx + 1,
          })
        }
      } catch (error: unknown) {
        deps.notify?.(error instanceof Error ? error.message : '移动章节失败')
        return
      }
    }

    const [item] = volume.children.splice(currentIdx, 1)
    volume.children.splice(targetIdx, 0, item)
  }

  return {
    outlineData,
    activeChapter,
    currentChapterTitle,
    outlineOpBusy,
    loadOutline,
    selectChapter,
    addVolume,
    addChapter,
    deleteVolume,
    deleteChapter,
    renameNode,
    moveNode,
  }
}
