import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useWorkbenchIntegrations } from './useWorkbenchIntegrations'

const mocks = vi.hoisted(() => ({
  getUserModelPreferences: vi.fn(),
  listUserModelConfigs: vi.fn(),
  listProjectPlugins: vi.fn(),
  getProjectConfiguration: vi.fn(),
}))

vi.mock('@/api/modules/model.api', () => ({
  modelApi: {
    getUserModelPreferences: mocks.getUserModelPreferences,
    listUserModelConfigs: mocks.listUserModelConfigs,
  },
}))

vi.mock('@/api/modules/plugin.api', () => ({
  pluginApi: {
    listProjectPlugins: mocks.listProjectPlugins,
  },
}))

vi.mock('@/api/modules/rag.api', () => ({
  ragApi: {
    getConfiguration: mocks.getProjectConfiguration,
  },
}))

const activeChat = (modelConfigId: string, modelName = 'gpt-test') => ({
  modelConfigId,
  modelName,
  modelType: 'CHAT',
  status: 'ACTIVE',
})

describe('useWorkbenchIntegrations model selection', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getProjectConfiguration.mockResolvedValue({})
  })

  it('resolves the explicitly preferred active chat model from the accessible configuration list', async () => {
    mocks.getUserModelPreferences.mockResolvedValue({ defaultCreativeModelConfigId: '2' })
    mocks.listUserModelConfigs.mockResolvedValue([activeChat('1'), activeChat('2', 'gpt-preferred')])
    const integrations = useWorkbenchIntegrations({ getUserId: () => '101', getProjectId: () => '201' })

    await expect(integrations.refreshActiveModelInfo()).resolves.toBe('2')

    expect(mocks.getUserModelPreferences).toHaveBeenCalledWith('101')
    expect(mocks.listUserModelConfigs).toHaveBeenCalledWith('101')
    expect(integrations.currentModelName.value).toBe('gpt-preferred')
  })

  it('prefers the project creative model over the account default', async () => {
    mocks.getUserModelPreferences.mockResolvedValue({ defaultCreativeModelConfigId: '1' })
    mocks.getProjectConfiguration.mockResolvedValue({ creativeModelConfigId: '2' })
    mocks.listUserModelConfigs.mockResolvedValue([activeChat('1'), activeChat('2', 'project-model')])
    const integrations = useWorkbenchIntegrations({ getUserId: () => '101', getProjectId: () => '201' })

    await expect(integrations.refreshActiveModelInfo()).resolves.toBe('2')
    expect(integrations.currentModelName.value).toBe('project-model')
  })

  it.each([
    ['missing preference', {}, [activeChat('1')]],
    ['unknown preference', { defaultCreativeModelConfigId: '9' }, [activeChat('1')]],
    [
      'inactive preference',
      { defaultCreativeModelConfigId: '2' },
      [activeChat('1'), { ...activeChat('2'), status: 'INACTIVE' }],
    ],
    [
      'non-chat preference',
      { defaultCreativeModelConfigId: '2' },
      [activeChat('1'), { ...activeChat('2'), modelType: 'EMBEDDING' }],
    ],
  ])('returns no model without falling back for %s', async (_case, preferences, configurations) => {
    mocks.getUserModelPreferences.mockResolvedValue(preferences)
    mocks.listUserModelConfigs.mockResolvedValue(configurations)
    const integrations = useWorkbenchIntegrations({ getUserId: () => '101', getProjectId: () => '201' })

    await expect(integrations.ensureModelConfigId()).resolves.toBe('')

    expect(integrations.currentModelName.value).toBe('')
  })
})
