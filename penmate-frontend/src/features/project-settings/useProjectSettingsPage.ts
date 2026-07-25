import { computed, onBeforeUnmount, onMounted } from 'vue'
import { Modal } from 'ant-design-vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { bookshelfGenres } from '@/composables/bookshelf/useBookshelf'
import { getErrorMessage } from '@/utils/errors'
import { useProjectSettings } from './useProjectSettings'

export const useProjectSettingsPage = () => {
  const route = useRoute()
  const router = useRouter()
  const projectId = String(route.params.projectId || '')
  const settings = useProjectSettings(projectId)

  const goBackToWorkbench = () => router.push(`/workbench?projectId=${encodeURIComponent(projectId)}`)
  const printProject = () => window.open(router.resolve(`/projects/${encodeURIComponent(projectId)}/print`).href, '_blank', 'noopener')
  const currentTitle = computed(() => settings.project.title || '作品设置')

  const requestSectionChange = (next: typeof settings.activeSection.value) => {
    if (next === settings.activeSection.value) return
    if (!settings.isSectionDirty(settings.activeSection.value)) {
      settings.activeSection.value = next
      return
    }
    const current = settings.activeSection.value
    Modal.confirm({
      title: '放弃未保存的修改？',
      content: '当前分区的修改尚未保存，离开后会恢复为上次保存的内容。',
      okText: '放弃修改',
      okType: 'danger',
      cancelText: '继续编辑',
      onOk() {
        settings.discardSectionChanges(current)
        settings.activeSection.value = next
      },
    })
  }

  const saveAi = () => {
    if (!settings.embeddingSelectionChanged.value) return settings.saveAi()
    Modal.confirm({
      title: '更改 Embedding 模型？',
      content: '当前索引将立即失效，路由会切回智能筛选。保存后需要由你手动重建索引。',
      okText: '确认保存',
      cancelText: '取消',
      async onOk() {
        const saved = await settings.saveAi()
        if (!saved) throw new Error(settings.saveError.value || '保存 AI 与上下文设置失败')
      },
    })
  }

  const confirmTrash = () => {
    Modal.confirm({
      title: '将作品移入回收站？',
      content: '作品会从书架隐藏，并可在回收站中保留 30 天。',
      okText: '移入回收站',
      okType: 'danger',
      cancelText: '取消',
      async onOk() {
        try {
          await settings.moveToTrash()
          await router.replace('/mybooks')
        } catch (error: unknown) {
          settings.saveError.value = getErrorMessage(error, '移入回收站失败')
          throw error
        }
      },
    })
  }

  const confirmRemoveCover = () => {
    Modal.confirm({
      title: '移除作品封面？',
      content: '移除后书架会恢复为文字封面，原图和已生成图片将被清理。',
      okText: '移除封面',
      okType: 'danger',
      cancelText: '取消',
      async onOk() {
        const removed = await settings.removeCover()
        if (!removed) throw new Error(settings.coverError.value || '移除封面失败')
      },
    })
  }

  const handleBeforeUnload = (event: BeforeUnloadEvent) => {
    if (!settings.hasUnsavedChanges.value) return
    event.preventDefault()
    event.returnValue = ''
  }

  onBeforeRouteLeave((_to, _from, next) => {
    if (!settings.hasUnsavedChanges.value) {
      next()
      return
    }
    Modal.confirm({
      title: '离开作品设置？',
      content: '尚有未保存的修改，离开后这些修改会丢失。',
      okText: '离开',
      okType: 'danger',
      cancelText: '继续编辑',
      onOk: () => next(),
      onCancel: () => next(false),
    })
  })

  onMounted(() => {
    void settings.load()
    window.addEventListener('beforeunload', handleBeforeUnload)
  })
  onBeforeUnmount(() => window.removeEventListener('beforeunload', handleBeforeUnload))

  return {
    ...settings,
    currentTitle,
    genres: bookshelfGenres,
    goBackToWorkbench,
    printProject,
    saveAi,
    requestSectionChange,
    confirmTrash,
    confirmRemoveCover,
  }
}
