import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { clearSession, setSession } from '@/stores/session'
import ProfileIndex from './index.vue'

const { pushMock, replaceMock, preferencesMock, configurationsMock, savePreferencesMock, listKeysMock, providersMock, meMock, listSessionsMock, revokeSessionMock } = vi.hoisted(() => ({
  pushMock: vi.fn(), replaceMock: vi.fn(), preferencesMock: vi.fn(), configurationsMock: vi.fn(),
  savePreferencesMock: vi.fn(), listKeysMock: vi.fn(), providersMock: vi.fn(), meMock: vi.fn(), listSessionsMock: vi.fn(), revokeSessionMock: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock, replace: replaceMock }),
  useRoute: () => ({ query: {} }),
}))
vi.mock('@/api/modules/model.api', async () => {
  const actual = await vi.importActual<typeof import('@/api/modules/model.api')>('@/api/modules/model.api')
  return { ...actual, modelApi: { ...actual.modelApi, getUserModelPreferences: preferencesMock, listUserModelConfigs: configurationsMock, saveUserModelPreferences: savePreferencesMock, listKeys: listKeysMock, listProviders: providersMock } }
})
vi.mock('@/api/modules/auth.api', async () => {
  const actual = await vi.importActual<typeof import('@/api/modules/auth.api')>('@/api/modules/auth.api')
  return { ...actual, authApi: { ...actual.authApi, me: meMock, listSessions: listSessionsMock, revokeSession: revokeSessionMock } }
})
vi.mock('@/api/modules/novel.api', async () => {
  const actual = await vi.importActual<typeof import('@/api/modules/novel.api')>('@/api/modules/novel.api')
  return { ...actual, novelApi: { ...actual.novelApi, listProjects: vi.fn().mockResolvedValue([]) } }
})

describe('Profile settings architecture', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    clearSession()
    setSession({ userId: '1001' })
    meMock.mockResolvedValue({ id: '1001', displayName: '原笔名', email: 'writer@example.com', bio: '原简介' })
    listKeysMock.mockResolvedValue([])
    providersMock.mockResolvedValue([])
    preferencesMock.mockResolvedValue({ defaultCreativeModelConfigId: '9001', defaultContextSelectorModelConfigId: '9002', defaultEmbeddingModelConfigId: '9003' })
    configurationsMock.mockResolvedValue([
      { modelConfigId: '9001', displayName: '创作', modelName: 'gpt-5', modelType: 'CHAT', scopeType: 'SYSTEM' },
      { modelConfigId: '9002', displayName: '筛选', modelName: 'gpt-5-mini', modelType: 'CHAT', scopeType: 'USER' },
      { modelConfigId: '9003', displayName: '向量', modelName: 'embedding-3', modelType: 'EMBEDDING', scopeType: 'SYSTEM' },
    ])
    savePreferencesMock.mockResolvedValue({})
    listSessionsMock.mockResolvedValue([])
    revokeSessionMock.mockResolvedValue('ok')
  })

  it('uses six focused settings sections instead of one long page', async () => {
    const wrapper = mount(ProfileIndex, { global: { stubs: { teleport: true } } })
    await flushPromises()

    expect(wrapper.find('[data-testid="profile-hero-card"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('外观与编辑器')
    expect(wrapper.text()).toContain('默认模型与 Agent')

    await wrapper.findAll('.settings-nav nav button').find((button) => button.text().includes('安全'))!.trigger('click')
    expect(wrapper.text()).toContain('账号安全')
    expect(wrapper.text()).toContain('已登录设备')

    await wrapper.findAll('.settings-nav nav button').find((button) => button.text().includes('模型服务'))!.trigger('click')
    expect(wrapper.text()).toContain('个人模型服务')
  })

  it('edits the three approved model roles without Worker fields', async () => {
    const wrapper = mount(ProfileIndex, { global: { stubs: { teleport: true } } })
    await flushPromises()
    await wrapper.findAll('.settings-nav nav button').find((button) => button.text().includes('默认模型'))!.trigger('click')

    expect(wrapper.text()).toContain('创作模型')
    expect(wrapper.text()).toContain('上下文筛选模型')
    expect(wrapper.text()).toContain('Embedding 模型')
    expect(wrapper.text()).not.toContain('脏活 Agent')

    await wrapper.get('[data-testid="model-preference-creative-select"]').trigger('click')
    expect(wrapper.text()).toContain('官方')
    await wrapper.get('[data-model-id="9002"]').trigger('click')
    await wrapper.find('[data-testid="model-preference-save"]').trigger('click')
    await flushPromises()

    expect(savePreferencesMock).toHaveBeenCalledWith('1001', '1001', {
      creativeModelConfigId: '9002',
      contextSelectorModelConfigId: '9002',
      defaultEmbeddingModelConfigId: '9003',
    })
    expect(wrapper.text()).toContain('默认模型已保存')
  })

  it('keeps_profile_load_failures_in_place_and_retries_without_leaving_the_page', async () => {
    meMock.mockRejectedValueOnce(new Error('服务暂时不可用'))
      .mockResolvedValueOnce({ id: '1001', displayName: '恢复后的笔名', email: 'writer@example.com', bio: '' })
    const wrapper = mount(ProfileIndex)
    await flushPromises()

    expect(wrapper.text()).toContain('个人资料加载失败')
    expect(wrapper.text()).toContain('服务暂时不可用')
    await wrapper.get('.load-error button').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="profile-hero-card"]').text()).toContain('恢复后的笔名')
  })

  it('offers_an_in_place_retry_when_default_models_fail_to_load', async () => {
    preferencesMock.mockRejectedValueOnce(new Error('偏好加载失败'))
    const wrapper = mount(ProfileIndex)
    await flushPromises()
    await wrapper.findAll('.settings-nav nav button').find((button) => button.text().includes('默认模型'))!.trigger('click')

    expect(wrapper.text()).toContain('加载模型偏好失败')
    await wrapper.get('[data-testid="model-preference-retry"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="model-preference-creative-select"]').exists()).toBe(true)
  })
})
