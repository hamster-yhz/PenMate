export type OutlineChapterNode = { title: string; key: string; chapterId?: string }
export type OutlineVolumeNode = { title: string; key: string; expanded: boolean; children: OutlineChapterNode[] }

const businessId = (value: unknown) => String(value ?? '').trim()
const sortOrder = (item: Record<string, unknown>) => Number(item.sortOrder ?? 0)

export const mapNovelDirectory = (
  volumes: Array<Record<string, unknown>>,
  chapters: Array<Record<string, unknown>>,
): OutlineVolumeNode[] => {
  const sortedVolumes = [...volumes].sort((left, right) => sortOrder(left) - sortOrder(right))
  const sortedChapters = [...chapters].sort((left, right) => sortOrder(left) - sortOrder(right))
  const volumeMap = new Map<string, OutlineVolumeNode>()

  for (const volume of sortedVolumes) {
    const volumeId = businessId(volume.volumeId)
    if (!volumeId) continue
    volumeMap.set(volumeId, {
      key: volumeId,
      title: String(volume.title ?? '未命名卷'),
      expanded: true,
      children: [],
    })
  }

  for (const chapter of sortedChapters) {
    const chapterId = businessId(chapter.chapterId)
    const volumeId = businessId(chapter.volumeId)
    if (!chapterId || !volumeId) continue
    volumeMap.get(volumeId)?.children.push({
      key: chapterId,
      chapterId,
      title: String(chapter.title ?? '未命名章节'),
    })
  }

  return Array.from(volumeMap.values())
}
