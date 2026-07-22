import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AdminOfficialModels from './AdminOfficialModels.vue'

const {
  listMock, providersMock, createMock, updateMock, deleteMock, testMock, probeMock,
  successMock, errorMock, confirmMock,
} = vi.hoisted(() => ({
  listMock: vi.fn(), providersMock: vi.fn(), createMock: vi.fn(), updateMock: vi.fn(), deleteMock: vi.fn(),
  testMock: vi.fn(), probeMock: vi.fn(), successMock: vi.fn(), errorMock: vi.fn(), confirmMock: vi.fn(),
}))

vi.mock('@/api/modules/model.api', async () => {
  const actual = await vi.importActual<typeof import('@/api/modules/model.api')>('@/api/modules/model.api')
  return {
    ...actual,
    modelApi: {
      ...actual.modelApi,
      listSystemModelConfigs: listMock,
      listProviders: providersMock,
      createSystemModelConfig: createMock,
      updateSystemModelConfig: updateMock,
      deleteSystemModelConfig: deleteMock,
      testSystemModelConnection: testMock,
      probeEmbeddingDimensions: probeMock,
    },
  }
})

vi.mock('ant-design-vue', () => ({
  message: { success: successMock, error: errorMock },
  Modal: { confirm: confirmMock },
}))

const officialModel = {
  modelConfigId: '501', scopeType: 'SYSTEM', providerId: '11', providerName: 'OpenAI',
  displayName: '官方长篇创作', modelType: 'CHAT', modelName: 'gpt-5', maskedApiKey: '****1234',
  credentialConfigured: true, status: 'ACTIVE',
} as const

describe('AdminOfficialModels', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    listMock.mockResolvedValue([officialModel])
    providersMock.mockResolvedValue([{
      providerId: '11', name: 'OpenAI', code: 'openai',
      capabilities: [{ capabilityCode: 'CHAT', protocolCode: 'OPENAI_CHAT_COMPLETIONS' }],
    }])
    testMock.mockResolvedValue({ success: true, latencyMs: 96, testedAt: '2026-07-22T03:00:00Z' })
    updateMock.mockResolvedValue(officialModel)
  })

  it('loads official configurations and uses the system connection-test endpoint', async () => {
    const wrapper = mount(AdminOfficialModels, { global: { stubs: { teleport: true } } })
    await flushPromises()

    expect(wrapper.get('[role="table"]').text()).toContain('官方长篇创作')
    await wrapper.findAll('.row-actions button')[0].trigger('click')
    await flushPromises()

    expect(testMock).toHaveBeenCalledWith('501')
    expect(wrapper.text()).toContain('成功 · 96 ms')
  })

  it('edits an official model without replacing a blank stored key', async () => {
    const wrapper = mount(AdminOfficialModels, { global: { stubs: { teleport: true } } })
    await flushPromises()

    await wrapper.get('button[aria-label="编辑官方模型"]').trigger('click')
    await wrapper.get('input[placeholder="例如：官方长篇创作"]').setValue('官方创作模型')
    await wrapper.get('.model-drawer form').trigger('submit')
    await flushPromises()

    expect(updateMock).toHaveBeenCalledWith('501', expect.objectContaining({
      displayName: '官方创作模型', apiKey: '', status: 'ACTIVE',
    }))
  })
})
