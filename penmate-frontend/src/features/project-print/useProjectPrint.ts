import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { novelApi, type AnyRecord } from '@/api/modules/novel.api'

export const useProjectPrint = () => {
  const route = useRoute()
  const router = useRouter()
  const projectId = String(route.params.projectId || '')
  const project = reactive<AnyRecord>({ title: '' })
  const volumes = ref<AnyRecord[]>([])
  const chapters = ref<AnyRecord[]>([])
  const loading = ref(true)
  const print = () => window.print()
  const chaptersByVolume = (volumeId: string) => chapters.value
    .filter((chapter) => String(chapter.volumeId || '') === volumeId)
  const paragraphs = (content: string) => content.replace(/\r\n?/g, '\n')
    .split(/\n{2,}/).map((value) => value.trim()).filter(Boolean)

  onMounted(async () => {
    try {
      const [projectValue, volumeValues, chapterValues] = await Promise.all([
        novelApi.getProject(projectId), novelApi.listVolumes(projectId), novelApi.listChapters(projectId),
      ])
      Object.assign(project, projectValue)
      volumes.value = volumeValues
      chapters.value = chapterValues
      document.title = `${String(project.title || '作品')} - 打印`
    } finally {
      loading.value = false
    }
  })

  return { router, project, volumes, loading, print, chaptersByVolume, paragraphs }
}
