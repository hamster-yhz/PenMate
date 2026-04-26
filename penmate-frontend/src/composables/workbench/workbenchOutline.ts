export type OutlineChapterNode = { title: string; key: string; chapterId?: string }
export type OutlineVolumeNode = { title: string; key: string; expanded: boolean; children: OutlineChapterNode[] }

export const mapOutlineTree = (
  nodes: Array<Record<string, any>>,
  chapterByOutlineNodeId: Record<string, string> = {},
): OutlineVolumeNode[] => {
  const volumeMap = new Map<string, OutlineVolumeNode>()

  nodes.forEach((node) => {
    const key = String(node.outlineNodeId ?? '')
    if (!key) return
    const title = String(node.title ?? node.name ?? '未命名')
    const nodeType = String(node.nodeType ?? node.type ?? '').toUpperCase()
    if (nodeType.includes('VOLUME')) {
      volumeMap.set(key, { title, key, expanded: true, children: [] })
    }
  })

  nodes.forEach((node) => {
    const key = String(node.outlineNodeId ?? '')
    if (!key) return
    const title = String(node.title ?? node.name ?? '未命名章节')
    const parentId = node.parentId
    if (parentId != null) {
      const pKey = String(parentId)
      const parent = volumeMap.get(pKey)
      if (parent) {
        parent.children.push({ title, key, chapterId: chapterByOutlineNodeId[key] })
      }
    }
  })

  return Array.from(volumeMap.values())
}
