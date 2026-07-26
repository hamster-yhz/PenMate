import { computed, getCurrentScope, onScopeDispose, reactive, ref, watch } from 'vue'
import { modelApi } from '@/api/modules/model.api'
import { novelApi, type AnyRecord, type NovelCoverState, type NovelExportFormat } from '@/api/modules/novel.api'
import type { NovelCoverCrop } from '@/entities/novel/model'
import { ragApi } from '@/api/modules/rag.api'
import { getSession } from '@/stores/session'
import { getErrorMessage } from '@/utils/errors'
import { saveDownload } from '@/utils/download'

export type ProjectSettingsSection = 'general' | 'ai' | 'index' | 'data' | 'danger'
export type RoutingMode = 'LLM_SELECTOR' | 'RETRIEVAL' | 'RETRIEVAL_THEN_LLM'

export interface ModelOption {
  id: string
  label: string
  modelName: string
  type: 'CHAT' | 'EMBEDDING'
  scope: 'SYSTEM' | 'USER'
  providerName?: string
  usable?: boolean
  unavailableReason?: string | null
}

export interface ProjectGeneralSettings {
  title: string
  summary: string
  genre: string
  customGenre: string
  tagsText: string
  coverUrl: string
  coverOriginalUrl: string
}

export interface ProjectAiSettings {
  creativeModelConfigId: string
  routerModelConfigId: string
  embeddingModelConfigId: string
  storyBibleRoutingMode: RoutingMode
}

export interface ProjectIndexState {
  status: string
  lastCompletedAt: string
  lastErrorMessage: string
  activeIndexBuildId: string
  rebuildJobId: string
  progressCurrent: number
  progressTotal: number
  progressMessage: string
}

const text = (value: unknown) => String(value ?? '').trim()
const id = (value: unknown) => text(value) || ''
const number = (value: unknown) => (Number.isFinite(Number(value)) ? Number(value) : 0)
const ACTIVE_INDEX_STATUSES = new Set(['QUEUED', 'BUILDING', 'CANCELLING'])
const INDEX_POLL_INTERVAL_MS = 1500

export const useProjectSettings = (projectId: string) => {
  const session = getSession()
  const activeSection = ref<ProjectSettingsSection>('general')
  const loading = ref(true)
  const loadError = ref('')
  const savingSection = ref<ProjectSettingsSection | null>(null)
  const saveError = ref('')
  const saveSuccess = ref('')
  const rebuilding = ref(false)
  const cancellingRebuild = ref(false)
  const exportingFormat = ref<NovelExportFormat | null>(null)
  const coverStatus = ref('EMPTY')
  const coverUploadId = ref('')
  const coverError = ref('')
  const coverCrop = ref<NovelCoverCrop | null>(null)
  const coverBusy = computed(() => ['UPLOADING', 'PROCESSING'].includes(coverStatus.value))
  let coverPollTimer: ReturnType<typeof setTimeout> | null = null
  let indexPollTimer: ReturnType<typeof setTimeout> | null = null
  let indexPollFailures = 0
  let localPreviewUrl = ''
  let previousCoverUrl = ''
  let lastCoverFile: File | null = null
  let lastCoverCrop: NovelCoverCrop | null = null

  const project = reactive<ProjectGeneralSettings>({
    title: '',
    summary: '',
    genre: '玄幻',
    customGenre: '',
    tagsText: '',
    coverUrl: '',
    coverOriginalUrl: '',
  })

  const ai = reactive<ProjectAiSettings>({
    creativeModelConfigId: '',
    routerModelConfigId: '',
    embeddingModelConfigId: '',
    storyBibleRoutingMode: 'LLM_SELECTOR' as RoutingMode,
  })

  const index = reactive<ProjectIndexState>({
    status: 'UNBOUND',
    lastCompletedAt: '',
    lastErrorMessage: '',
    activeIndexBuildId: '',
    rebuildJobId: '',
    progressCurrent: 0,
    progressTotal: 0,
    progressMessage: '',
  })

  const modelOptions = ref<ModelOption[]>([])
  const savedGeneral = ref({ title: '', summary: '', genre: '', customGenre: '', tagsText: '' })
  const savedAi = ref<ProjectAiSettings>({
    creativeModelConfigId: '',
    routerModelConfigId: '',
    embeddingModelConfigId: '',
    storyBibleRoutingMode: 'LLM_SELECTOR',
  })
  const chatModels = computed(() => modelOptions.value.filter((option) => option.type === 'CHAT'))
  const embeddingModels = computed(() => modelOptions.value.filter((option) => option.type === 'EMBEDDING'))
  const retrievalAvailable = computed(() => index.status === 'READY')
  const canRebuildIndex = computed(() => Boolean(ai.embeddingModelConfigId))
  const generalSnapshot = () => ({
    title: project.title,
    summary: project.summary,
    genre: project.genre,
    customGenre: project.customGenre,
    tagsText: project.tagsText,
  })
  const aiSnapshot = (): ProjectAiSettings => ({ ...ai })
  const generalDirty = computed(() => JSON.stringify(generalSnapshot()) !== JSON.stringify(savedGeneral.value))
  const aiDirty = computed(() => JSON.stringify(aiSnapshot()) !== JSON.stringify(savedAi.value))
  const embeddingSelectionChanged = computed(() => ai.embeddingModelConfigId !== savedAi.value.embeddingModelConfigId)
  const hasUnsavedChanges = computed(() => generalDirty.value || aiDirty.value)
  const isSectionDirty = (section: ProjectSettingsSection) =>
    section === 'general' ? generalDirty.value : section === 'ai' ? aiDirty.value : false
  const discardSectionChanges = (section: ProjectSettingsSection) => {
    if (section === 'general') Object.assign(project, savedGeneral.value)
    if (section === 'ai') Object.assign(ai, savedAi.value)
  }

  const mapModels = (items: AnyRecord[]) =>
    items
      .filter((item) => text(item.status || 'ACTIVE').toUpperCase() === 'ACTIVE')
      .map((item): ModelOption | null => {
        const modelConfigId = id(item.modelConfigId)
        const modelType = text(item.modelType).toUpperCase()
        if (!modelConfigId || (modelType !== 'CHAT' && modelType !== 'EMBEDDING')) return null
        const modelName = text(item.modelName)
        return {
          id: modelConfigId,
          label: text(item.displayName) || modelName,
          modelName,
          type: modelType,
          scope: text(item.scopeType).toUpperCase() === 'SYSTEM' ? 'SYSTEM' : 'USER',
          providerName: text(item.providerName || item.providerCode),
          usable: item.usable !== false,
          unavailableReason: text(item.unavailableReason) || null,
        }
      })
      .filter((item): item is ModelOption => item !== null)

  const load = async () => {
    loading.value = true
    loadError.value = ''
    try {
      const [projectResult, configurationResult, modelsResult, coverResult] = await Promise.all([
        novelApi.getProject(projectId),
        ragApi.getConfiguration(projectId),
        modelApi.listUserModelConfigs(session.userId || ''),
        novelApi.getCover(projectId),
      ])
      project.title = text(projectResult.title)
      project.summary = text(projectResult.summary ?? projectResult.description)
      project.genre = text(projectResult.genre) || '玄幻'
      project.customGenre = text(projectResult.customGenre)
      project.tagsText = Array.isArray(projectResult.tags)
        ? projectResult.tags.map(String).join(', ')
        : text(projectResult.tags)
      project.coverUrl = text(projectResult.coverUrl)
      applyCoverState(coverResult)
      if (coverStatus.value === 'PROCESSING') pollCover()

      ai.creativeModelConfigId = id(configurationResult.creativeModelConfigId)
      ai.routerModelConfigId = id(configurationResult.routerModelConfigId)
      ai.embeddingModelConfigId = id(configurationResult.embeddingModelConfigId)
      applyIndexState(configurationResult)
      const routing = text(configurationResult.storyBibleRoutingMode)
      ai.storyBibleRoutingMode =
        retrievalAvailable.value && (routing === 'RETRIEVAL' || routing === 'RETRIEVAL_THEN_LLM')
          ? routing
          : 'LLM_SELECTOR'
      modelOptions.value = mapModels(Array.isArray(modelsResult) ? modelsResult : [])
      savedGeneral.value = generalSnapshot()
      savedAi.value = aiSnapshot()
      if (ACTIVE_INDEX_STATUSES.has(index.status)) pollIndexStatus(false)
    } catch (error: unknown) {
      loadError.value = getErrorMessage(error, '加载作品设置失败')
    } finally {
      loading.value = false
    }
  }

  const beginSave = (section: ProjectSettingsSection) => {
    savingSection.value = section
    saveError.value = ''
    saveSuccess.value = ''
  }

  const finishSave = (message: string) => {
    savingSection.value = null
    saveSuccess.value = message
  }

  const failSave = (error: unknown, fallback: string) => {
    savingSection.value = null
    saveError.value = getErrorMessage(error, fallback)
  }

  const saveGeneral = async () => {
    if (!project.title.trim()) {
      saveError.value = '作品名不能为空'
      return false
    }
    beginSave('general')
    try {
      const tags = [
        ...new Set(
          project.tagsText
            .split(/[,，]/)
            .map((tag) => tag.trim())
            .filter(Boolean),
        ),
      ].slice(0, 10)
      await novelApi.updateProject(projectId, {
        title: project.title.trim(),
        summary: project.summary.trim(),
        genre: project.genre,
        customGenre: project.genre === '其他' ? project.customGenre.trim() : null,
        tags,
      })
      savedGeneral.value = generalSnapshot()
      finishSave('基本信息已保存')
      return true
    } catch (error: unknown) {
      failSave(error, '保存基本信息失败')
      return false
    }
  }

  const saveAi = async () => {
    if (ai.storyBibleRoutingMode !== 'LLM_SELECTOR' && !retrievalAvailable.value) {
      saveError.value = '当前没有可用索引，只能选择智能筛选'
      return false
    }
    beginSave('ai')
    try {
      const result = await ragApi.updateConfiguration(projectId, {
        creativeModelConfigId: ai.creativeModelConfigId || null,
        routerModelConfigId: ai.routerModelConfigId || null,
        embeddingModelConfigId: ai.embeddingModelConfigId || null,
        storyBibleRoutingMode: ai.storyBibleRoutingMode,
      })
      applyIndexState(result)
      ai.creativeModelConfigId = id(result.creativeModelConfigId)
      ai.routerModelConfigId = id(result.routerModelConfigId)
      ai.embeddingModelConfigId = id(result.embeddingModelConfigId)
      ai.storyBibleRoutingMode = (text(result.storyBibleRoutingMode) as RoutingMode) || 'LLM_SELECTOR'
      savedAi.value = aiSnapshot()
      finishSave('AI 与上下文设置已保存')
      return true
    } catch (error: unknown) {
      failSave(error, '保存 AI 与上下文设置失败')
      return false
    }
  }

  const rebuildIndex = async () => {
    rebuilding.value = true
    cancellingRebuild.value = false
    saveError.value = ''
    saveSuccess.value = ''
    index.rebuildJobId = ''
    index.progressCurrent = 0
    index.progressTotal = 0
    index.progressMessage = ''
    try {
      const result = await ragApi.rebuild(projectId)
      index.status = text(result.status) || 'QUEUED'
      index.rebuildJobId = id(result.jobId)
      saveSuccess.value = '索引重建已开始'
      pollIndexStatus(true)
    } catch (error: unknown) {
      rebuilding.value = false
      saveError.value = getErrorMessage(error, '启动索引重建失败')
    }
  }

  const stopRebuild = async () => {
    if (!rebuilding.value || !index.rebuildJobId || cancellingRebuild.value) return
    cancellingRebuild.value = true
    saveError.value = ''
    saveSuccess.value = ''
    try {
      const result = await ragApi.cancelRebuild(projectId, index.rebuildJobId)
      index.status = text(result.status).toUpperCase() || 'CANCELLING'
      saveSuccess.value = index.status === 'CANCELLED' ? '索引重建已停止' : '正在停止索引重建'
      pollIndexStatus(false)
    } catch (error: unknown) {
      cancellingRebuild.value = false
      saveError.value = getErrorMessage(error, '停止索引重建失败')
    }
  }

  const applyIndexState = (result: AnyRecord) => {
    index.status = text(result.indexStatus).toUpperCase() || 'UNBOUND'
    cancellingRebuild.value = index.status === 'CANCELLING'
    index.lastCompletedAt = text(result.lastIndexCompletedAt)
    index.lastErrorMessage = text(result.lastErrorMessage)
    index.activeIndexBuildId = id(result.activeIndexBuildId)
    index.rebuildJobId = id(result.rebuildJobId)
    index.progressCurrent = number(result.rebuildProgressCurrent)
    index.progressTotal = number(result.rebuildProgressTotal)
    index.progressMessage = text(result.rebuildProgressMessage)
  }

  const clearIndexPoll = () => {
    if (indexPollTimer) clearTimeout(indexPollTimer)
    indexPollTimer = null
  }

  function pollIndexStatus(announceCompletion: boolean) {
    clearIndexPoll()
    rebuilding.value = true
    indexPollTimer = setTimeout(async () => {
      try {
        const result = await ragApi.getConfiguration(projectId)
        applyIndexState(result)
        indexPollFailures = 0
        if (ACTIVE_INDEX_STATUSES.has(index.status)) {
          pollIndexStatus(announceCompletion)
          return
        }
        rebuilding.value = false
        cancellingRebuild.value = false
        if (index.status === 'READY') {
          if (announceCompletion) saveSuccess.value = '索引重建完成'
        } else if (index.status === 'CANCELLED') {
          saveError.value = ''
          saveSuccess.value = '索引重建已停止'
        } else if (index.status === 'FAILED') {
          saveSuccess.value = ''
          saveError.value = index.lastErrorMessage || '索引重建失败'
        }
      } catch (error: unknown) {
        indexPollFailures += 1
        if (indexPollFailures < 3) {
          pollIndexStatus(announceCompletion)
          return
        }
        rebuilding.value = false
        cancellingRebuild.value = false
        saveSuccess.value = ''
        saveError.value = getErrorMessage(error, '获取索引重建状态失败')
      }
    }, INDEX_POLL_INTERVAL_MS)
  }

  const moveToTrash = () => novelApi.deleteProject(projectId, session.userId || '')

  const clearCoverPoll = () => {
    if (coverPollTimer) clearTimeout(coverPollTimer)
    coverPollTimer = null
  }

  const releaseLocalPreview = () => {
    if (localPreviewUrl) URL.revokeObjectURL(localPreviewUrl)
    localPreviewUrl = ''
  }

  const applyCoverState = (state: NovelCoverState, preserveLocalPreview = false) => {
    const status = text(state.status).toUpperCase() || 'EMPTY'
    coverStatus.value = status
    coverUploadId.value = id(state.uploadId)
    coverError.value = text(state.errorMessage)
    coverCrop.value = state.crop && typeof state.crop === 'object' ? state.crop : null
    lastCoverCrop = coverCrop.value
    project.coverOriginalUrl = text(state.originalUrl)
    if (!(preserveLocalPreview && localPreviewUrl && status === 'PROCESSING')) {
      project.coverUrl = text(state.coverUrl)
    }
  }

  const pollCover = () => {
    clearCoverPoll()
    coverPollTimer = setTimeout(async () => {
      try {
        const state = await novelApi.getCover(projectId)
        applyCoverState(state, true)
        if (coverStatus.value === 'PROCESSING') {
          pollCover()
          return
        }
        if (coverStatus.value === 'READY') {
          releaseLocalPreview()
          project.coverUrl = text(state.coverUrl)
          previousCoverUrl = project.coverUrl
          coverError.value = ''
        } else if (coverStatus.value === 'FAILED') {
          releaseLocalPreview()
          project.coverUrl = text(state.coverUrl) || previousCoverUrl
        }
      } catch (error: unknown) {
        coverError.value = getErrorMessage(error, '查询封面处理状态失败')
        pollCover()
      }
    }, 1500)
  }

  const changeCover = async (payload: { file: File | null; crop: NovelCoverCrop; previewUrl: string }) => {
    if (coverBusy.value) return false
    coverError.value = ''
    previousCoverUrl = project.coverUrl
    lastCoverFile = payload.file
    lastCoverCrop = payload.crop
    if (payload.previewUrl) {
      releaseLocalPreview()
      localPreviewUrl = payload.previewUrl
      project.coverUrl = localPreviewUrl
    }
    coverStatus.value = 'UPLOADING'
    try {
      const state = payload.file
        ? await novelApi.uploadCover(projectId, payload.file, payload.crop)
        : await novelApi.recropCover(projectId, payload.crop)
      applyCoverState(state, true)
      pollCover()
      return true
    } catch (error: unknown) {
      coverStatus.value = 'FAILED'
      coverError.value = getErrorMessage(error, '封面上传失败')
      releaseLocalPreview()
      project.coverUrl = previousCoverUrl
      return false
    }
  }

  const retryCover = async () => {
    if (coverBusy.value || !lastCoverCrop) return false
    coverError.value = ''
    coverStatus.value = 'PROCESSING'
    try {
      const state = coverUploadId.value
        ? await novelApi.retryCover(projectId, coverUploadId.value)
        : lastCoverFile
          ? await novelApi.uploadCover(projectId, lastCoverFile, lastCoverCrop)
          : await novelApi.recropCover(projectId, lastCoverCrop)
      applyCoverState(state, true)
      pollCover()
      return true
    } catch (error: unknown) {
      coverStatus.value = 'FAILED'
      coverError.value = getErrorMessage(error, '重试封面处理失败')
      return false
    }
  }

  const removeCover = async () => {
    if (coverBusy.value) return false
    coverError.value = ''
    try {
      await novelApi.removeCover(projectId)
      clearCoverPoll()
      releaseLocalPreview()
      project.coverUrl = ''
      project.coverOriginalUrl = ''
      coverStatus.value = 'EMPTY'
      coverUploadId.value = ''
      coverCrop.value = null
      lastCoverFile = null
      lastCoverCrop = null
      return true
    } catch (error: unknown) {
      coverError.value = getErrorMessage(error, '移除封面失败')
      return false
    }
  }

  const exportProject = async (format: NovelExportFormat) => {
    if (exportingFormat.value) return false
    exportingFormat.value = format
    saveError.value = ''
    saveSuccess.value = ''
    try {
      const result = await novelApi.exportProject(projectId, format)
      const safeTitle = project.title.trim().replace(/[\\/:*?"<>|]/g, '_') || 'novel'
      saveDownload(result.blob, result.contentDisposition, `${safeTitle}.${format === 'markdown' ? 'md' : format}`)
      saveSuccess.value = `${format.toUpperCase()} 导出已开始下载`
      return true
    } catch (error: unknown) {
      saveError.value = getErrorMessage(error, `导出 ${format.toUpperCase()} 失败`)
      return false
    } finally {
      exportingFormat.value = null
    }
  }

  watch(activeSection, () => {
    saveError.value = ''
    saveSuccess.value = ''
  })

  if (getCurrentScope()) {
    onScopeDispose(() => {
      clearCoverPoll()
      clearIndexPoll()
      releaseLocalPreview()
    })
  }

  return {
    activeSection,
    loading,
    loadError,
    savingSection,
    saveError,
    saveSuccess,
    rebuilding,
    cancellingRebuild,
    exportingFormat,
    coverStatus,
    coverUploadId,
    coverError,
    coverCrop,
    coverBusy,
    project,
    ai,
    index,
    chatModels,
    embeddingModels,
    canRebuildIndex,
    retrievalAvailable,
    generalDirty,
    aiDirty,
    embeddingSelectionChanged,
    hasUnsavedChanges,
    isSectionDirty,
    discardSectionChanges,
    load,
    saveGeneral,
    saveAi,
    rebuildIndex,
    stopRebuild,
    exportProject,
    changeCover,
    retryCover,
    removeCover,
    moveToTrash,
  }
}
