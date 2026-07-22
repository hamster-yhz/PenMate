import { computed, ref } from 'vue'
import { novelApi, type NovelTxtImportPreview } from '@/api/modules/novel.api'
import { getErrorMessage } from '@/utils/errors'

const MAX_FILE_SIZE = 10 * 1024 * 1024

const copyPreview = (preview: NovelTxtImportPreview): NovelTxtImportPreview => ({
  projectTitle: String(preview.projectTitle || '').trim(),
  volumes: (preview.volumes || []).map((volume) => ({
    title: String(volume.title || '').trim(),
    chapters: (volume.chapters || []).map((chapter) => ({
      title: String(chapter.title || '').trim(),
      content: String(chapter.content || ''),
    })),
  })),
})

export const useNovelTxtImport = () => {
  const preview = ref<NovelTxtImportPreview | null>(null)
  const filename = ref('')
  const previewing = ref(false)
  const importing = ref(false)
  const error = ref('')

  const chapterCount = computed(() => preview.value?.volumes.reduce(
    (total, volume) => total + volume.chapters.length,
    0,
  ) || 0)
  const canConfirm = computed(() => Boolean(
    preview.value
      && preview.value.projectTitle.trim()
      && preview.value.volumes.length
      && preview.value.volumes.every((volume) =>
        volume.title.trim() && volume.chapters.length
        && volume.chapters.every((chapter) => chapter.title.trim()),
      )
      && !importing.value,
  ))

  const reset = () => {
    preview.value = null
    filename.value = ''
    previewing.value = false
    importing.value = false
    error.value = ''
  }

  const selectFile = async (file: File) => {
    error.value = ''
    if (!file.name.toLocaleLowerCase().endsWith('.txt')) {
      error.value = '请选择 TXT 文件'
      return false
    }
    if (file.size > MAX_FILE_SIZE) {
      error.value = 'TXT 文件不能超过 10 MB'
      return false
    }
    previewing.value = true
    try {
      preview.value = copyPreview(await novelApi.previewTxtImport(file))
      filename.value = file.name
      return true
    } catch (cause) {
      error.value = getErrorMessage(cause, '无法解析 TXT 文件')
      return false
    } finally {
      previewing.value = false
    }
  }

  const addVolume = () => {
    if (!preview.value || preview.value.volumes.length >= 100) return
    preview.value.volumes.push({ title: `第 ${preview.value.volumes.length + 1} 卷`, chapters: [] })
  }

  const removeVolume = (volumeIndex: number) => {
    if (!preview.value || preview.value.volumes.length <= 1) return
    const volume = preview.value.volumes[volumeIndex]
    if (!volume || volume.chapters.length) return
    preview.value.volumes.splice(volumeIndex, 1)
  }

  const moveChapter = (sourceVolumeIndex: number, chapterIndex: number, targetVolumeIndex: number) => {
    if (!preview.value || sourceVolumeIndex === targetVolumeIndex) return
    const source = preview.value.volumes[sourceVolumeIndex]
    const target = preview.value.volumes[targetVolumeIndex]
    const chapter = source?.chapters[chapterIndex]
    if (!source || !target || !chapter) return
    source.chapters.splice(chapterIndex, 1)
    target.chapters.push(chapter)
  }

  const confirmImport = async () => {
    if (!preview.value || !canConfirm.value) return null
    importing.value = true
    error.value = ''
    try {
      const created = await novelApi.importTxtProject(copyPreview(preview.value))
      return String(created.projectId ?? created.id ?? '').trim() || null
    } catch (cause) {
      error.value = getErrorMessage(cause, '导入作品失败')
      return null
    } finally {
      importing.value = false
    }
  }

  return {
    preview,
    filename,
    previewing,
    importing,
    error,
    chapterCount,
    canConfirm,
    reset,
    selectFile,
    addVolume,
    removeVolume,
    moveChapter,
    confirmImport,
  }
}
