import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { clearSession, setSession } from '@/stores/session'
import ProfileModelServicesPanel from './ProfileModelServicesPanel.vue'

const { listConfigsMock, listProvidersMock, updateMock, createMock, deleteMock, testMock, successMock, errorMock } = vi.hoisted(() => ({
  listConfigsMock: vi.fn(), listProvidersMock: vi.fn(), updateMock: vi.fn(), createMock: vi.fn(), deleteMock: vi.fn(), testMock: vi.fn(), successMock: vi.fn(), errorMock: vi.fn(),
}))

vi.mock('@/api/modules/model.api', async () => {
  const actual = await vi.importActual<typeof import('@/api/modules/model.api')>('@/api/modules/model.api')
  return { ...actual, modelApi: { ...actual.modelApi, listUserModelConfigs: listConfigsMock, listProviders: listProvidersMock, updateUserModelConfig: updateMock, createUserModelConfig: createMock, deleteUserModelConfig: deleteMock, testUserModelConnection: testMock } }
})
vi.mock('ant-design-vue', () => ({
  message: { success: successMock, error: errorMock },
  Modal: { confirm: vi.fn() },
}))

const userModel = {
  modelConfigId: '101', scopeType: 'USER', providerId: '1', providerName: 'OpenAI', displayName: '长篇创作',
  modelType: 'CHAT', modelName: 'gpt-5', maskedApiKey: '****1234', credentialConfigured: true, status: 'ACTIVE',
}

describe('ProfileModelServicesPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    clearSession(); setSession({ userId: '7' })
    listConfigsMock.mockResolvedValue([userModel, { ...userModel, modelConfigId: '202', scopeType: 'SYSTEM', displayName: '官方模型' }])
    listProvidersMock.mockResolvedValue([{ providerId: '1', name: 'OpenAI', code: 'openai', capabilities: [{ capabilityCode: 'CHAT', protocolCode: 'OPENAI_CHAT_COMPLETIONS' }] }])
    testMock.mockResolvedValue({ success: true, latencyMs: 128, testedAt: '2026-07-22T03:00:00Z' })
    updateMock.mockResolvedValue(userModel)
  })

  it('keeps system models out of personal settings and updates a tested row', async () => {
    const wrapper = mount(ProfileModelServicesPanel, { global: { stubs: { teleport: true } } })
    await flushPromises()

    expect(wrapper.get('.model-list').text()).toContain('长篇创作')
    expect(wrapper.get('.model-list').text()).not.toContain('官方模型')

    await wrapper.findAll('.row-actions button')[0].trigger('click')
    await flushPromises()

    expect(testMock).toHaveBeenCalledWith('101')
    expect(wrapper.text()).toContain('成功 · 128 ms')
  })

  it('edits in a drawer and leaves a blank API key for the backend to preserve', async () => {
    const wrapper = mount(ProfileModelServicesPanel, { global: { stubs: { teleport: true } } })
    await flushPromises()

    await wrapper.get('button[aria-label="编辑模型"]').trigger('click')
    await wrapper.get('input[placeholder="例如：长篇创作"]').setValue('长篇创作 2')
    await wrapper.get('.model-drawer form').trigger('submit')
    await flushPromises()

    expect(updateMock).toHaveBeenCalledWith('7', '101', '7', expect.objectContaining({
      displayName: '长篇创作 2',
      apiKey: '',
      status: 'ACTIVE',
    }))
  })
})
