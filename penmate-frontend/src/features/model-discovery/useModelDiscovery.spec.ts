import { nextTick, reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useModelDiscovery } from './useModelDiscovery'

const { discoverMock } = vi.hoisted(() => ({ discoverMock: vi.fn() }))

vi.mock('@/api/modules/model.api', () => ({
  modelApi: { discoverModels: discoverMock },
}))

describe('useModelDiscovery', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    discoverMock.mockResolvedValue({ models: ['gpt-5', 'gpt-5-mini'], count: 2 })
  })

  it('orchestrates model discovery and selection outside the presentation component', async () => {
    const source = reactive({
      providerId: '11',
      modelType: 'CHAT' as const,
      baseUrl: 'https://models.example.test/v1',
      apiKey: 'secret',
      modelConfigId: '',
      systemScope: false,
    })
    const onSelect = vi.fn()
    const discovery = useModelDiscovery({
      providerId: () => source.providerId,
      modelType: () => source.modelType,
      baseUrl: () => source.baseUrl,
      apiKey: () => source.apiKey,
      modelConfigId: () => source.modelConfigId,
      systemScope: () => source.systemScope,
    }, onSelect)

    await discovery.discover()

    expect(discoverMock).toHaveBeenCalledWith({
      modelConfigId: undefined,
      providerId: '11',
      modelType: 'CHAT',
      baseUrl: 'https://models.example.test/v1',
      apiKey: 'secret',
    }, false)
    expect(discovery.models.value).toEqual(['gpt-5', 'gpt-5-mini'])
    expect(discovery.resultsOpen.value).toBe(true)

    discovery.selectModel('gpt-5-mini')
    expect(onSelect).toHaveBeenCalledWith('gpt-5-mini')
    expect(discovery.selectedFromDiscovery.value).toBe('gpt-5-mini')
    expect(discovery.resultsOpen.value).toBe(false)
  })

  it('clears stale discovery state when connection details change', async () => {
    const source = reactive({ providerId: '11', baseUrl: 'https://one.example/v1' })
    const discovery = useModelDiscovery({
      providerId: () => source.providerId,
      modelType: 'CHAT',
      baseUrl: () => source.baseUrl,
      apiKey: '',
    }, vi.fn())

    await discovery.discover()
    discovery.query.value = 'mini'
    source.baseUrl = 'https://two.example/v1'
    await nextTick()

    expect(discovery.models.value).toEqual([])
    expect(discovery.query.value).toBe('')
    expect(discovery.resultsOpen.value).toBe(false)
    expect(discovery.discoveryError.value).toBe('')
  })
})
