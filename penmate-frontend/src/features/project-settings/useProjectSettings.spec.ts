import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { useProjectSettings } from './useProjectSettings'
import { modelApi } from '@/api/modules/model.api'
import { novelApi } from '@/api/modules/novel.api'
import { ragApi } from '@/api/modules/rag.api'
import { saveDownload } from '@/utils/download'

vi.mock('@/api/modules/model.api', () => ({
  modelApi: {
    listUserModelConfigs: vi.fn(),
    getUserModelPreferences: vi.fn(),
  },
}))
vi.mock('@/api/modules/novel.api', () => ({
  novelApi: {
    getProject: vi.fn(),
    updateProject: vi.fn(),
    deleteProject: vi.fn(),
    exportProject: vi.fn(),
    getCover: vi.fn(),
  },
}))
vi.mock('@/api/modules/rag.api', () => ({ ragApi: { getConfiguration: vi.fn(), updateConfiguration: vi.fn(), rebuild: vi.fn() } }))
vi.mock('@/stores/session', () => ({ getSession: () => ({ userId: '1001' }) }))
vi.mock('@/utils/download', () => ({ saveDownload: vi.fn() }))

describe('useProjectSettings', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(novelApi.getProject).mockResolvedValue({ title: '旧城夜话', genre: '悬疑' })
    vi.mocked(modelApi.listUserModelConfigs).mockResolvedValue([])
    vi.mocked(modelApi.getUserModelPreferences).mockResolvedValue({})
    vi.mocked(novelApi.getCover).mockResolvedValue({ status: 'EMPTY' })
    vi.mocked(ragApi.getConfiguration).mockResolvedValue({
      storyBibleRoutingMode: 'LLM_SELECTOR',
      indexStatus: 'UNBOUND',
    })
  })

  it('falls back to LLM selection when the retrieval index is unavailable', async () => {
    vi.mocked(ragApi.getConfiguration).mockResolvedValue({
      storyBibleRoutingMode: 'RETRIEVAL_THEN_LLM',
      indexStatus: 'REINDEX_REQUIRED',
    })

    const settings = useProjectSettings('2001')
    await settings.load()

    expect(settings.retrievalAvailable.value).toBe(false)
    expect(settings.ai.storyBibleRoutingMode).toBe('LLM_SELECTOR')
  })

  it('clears section-specific feedback when navigating to another section', async () => {
    const settings = useProjectSettings('2001')
    settings.saveError.value = '保存失败'
    settings.saveSuccess.value = '已保存'

    settings.activeSection.value = 'ai'
    await nextTick()

    expect(settings.saveError.value).toBe('')
    expect(settings.saveSuccess.value).toBe('')
  })

  it('shows concrete inherited models and can discard unsaved AI changes', async () => {
    vi.mocked(modelApi.getUserModelPreferences).mockResolvedValue({
      defaultCreativeModelConfigId: '11',
      defaultContextSelectorModelConfigId: '12',
      defaultEmbeddingModelConfigId: '13',
    })
    vi.mocked(modelApi.listUserModelConfigs).mockResolvedValue([
      { modelConfigId: '11', scopeType: 'USER', providerId: '1', displayName: '写作主力', modelName: 'writer', modelType: 'CHAT', status: 'ACTIVE' },
      { modelConfigId: '12', scopeType: 'USER', providerId: '1', displayName: '设定筛选', modelName: 'router', modelType: 'CHAT', status: 'ACTIVE' },
      { modelConfigId: '13', scopeType: 'USER', providerId: '1', displayName: '中文向量', modelName: 'embed', modelType: 'EMBEDDING', status: 'ACTIVE' },
    ])
    const settings = useProjectSettings('2001')

    await settings.load()

    expect(settings.inheritedCreativeLabel.value).toContain('写作主力')
    expect(settings.inheritedRouterLabel.value).toContain('设定筛选')
    expect(settings.inheritedEmbeddingLabel.value).toContain('中文向量')
    expect(settings.aiDirty.value).toBe(false)
    settings.ai.creativeModelConfigId = '11'
    expect(settings.aiDirty.value).toBe(true)
    settings.discardSectionChanges('ai')
    expect(settings.ai.creativeModelConfigId).toBe('')
    expect(settings.aiDirty.value).toBe(false)
  })

  it('saves all project AI overrides and routing in one request', async () => {
    vi.mocked(ragApi.updateConfiguration).mockResolvedValue({
      creativeModelConfigId: '11',
      routerModelConfigId: '12',
      embeddingModelConfigId: null,
      storyBibleRoutingMode: 'LLM_SELECTOR',
      indexStatus: 'UNBOUND',
    })
    const settings = useProjectSettings('2001')
    await settings.load()
    settings.ai.creativeModelConfigId = '11'
    settings.ai.routerModelConfigId = '12'

    await settings.saveAi()

    expect(ragApi.updateConfiguration).toHaveBeenCalledTimes(1)
    expect(ragApi.updateConfiguration).toHaveBeenCalledWith('2001', {
      creativeModelConfigId: '11',
      routerModelConfigId: '12',
      embeddingModelConfigId: null,
      storyBibleRoutingMode: 'LLM_SELECTOR',
    })
    expect(settings.aiDirty.value).toBe(false)
  })

  it('downloads a real export and exposes per-format progress', async () => {
    const blob = new Blob(['novel'])
    vi.mocked(novelApi.exportProject).mockResolvedValue({
      blob,
      contentDisposition: "attachment; filename*=UTF-8''Night%20Story.docx",
    })
    const settings = useProjectSettings('2001')
    settings.project.title = 'Night Story'

    const pending = settings.exportProject('docx')
    expect(settings.exportingFormat.value).toBe('docx')
    await pending

    expect(novelApi.exportProject).toHaveBeenCalledWith('2001', 'docx')
    expect(saveDownload).toHaveBeenCalledWith(
      blob,
      "attachment; filename*=UTF-8''Night%20Story.docx",
      'Night Story.docx',
    )
    expect(settings.exportingFormat.value).toBeNull()
    expect(settings.saveSuccess.value).toContain('DOCX')
  })
})
