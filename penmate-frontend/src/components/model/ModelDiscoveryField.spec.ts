import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ModelDiscoveryField from './ModelDiscoveryField.vue'

const { discoverMock } = vi.hoisted(() => ({ discoverMock: vi.fn() }))

vi.mock('@/api/modules/model.api', async () => {
  const actual = await vi.importActual<typeof import('@/api/modules/model.api')>('@/api/modules/model.api')
  return { ...actual, modelApi: { ...actual.modelApi, discoverModels: discoverMock } }
})

const mountField = (overrides: Record<string, unknown> = {}) => mount(ModelDiscoveryField, {
  props: {
    modelValue: '',
    providerId: '11',
    modelType: 'CHAT',
    baseUrl: 'https://models.example.test/v1',
    apiKey: 'secret',
    ...overrides,
  },
})

describe('ModelDiscoveryField', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    discoverMock.mockResolvedValue({ models: ['gpt-4.1', 'gpt-5', 'gpt-5-mini'], count: 3 })
  })

  it('discovers models and selects one without saving the form', async () => {
    const wrapper = mountField()

    await wrapper.get('.discover-button').trigger('click')
    await flushPromises()
    expect(discoverMock).toHaveBeenCalledWith(expect.objectContaining({
      providerId: '11', modelType: 'CHAT', baseUrl: 'https://models.example.test/v1', apiKey: 'secret',
    }), false)
    expect(wrapper.get('[role="listbox"]').text()).toContain('gpt-5-mini')

    await wrapper.findAll('[role="option"]').find((option) => option.text().includes('gpt-5-mini'))!.trigger('click')
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['gpt-5-mini'])
    expect(wrapper.text()).toContain('已从站点选择 gpt-5-mini')
  })

  it('shows an empty state when the site returns no models', async () => {
    discoverMock.mockResolvedValue({ models: [], count: 0 })
    const wrapper = mountField()

    await wrapper.get('.discover-button').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('站点没有返回可用模型')
  })

  it('keeps manual entry available and supports retry after a failure', async () => {
    discoverMock.mockRejectedValueOnce(new Error('站点鉴权失败'))
    const wrapper = mountField({ modelConfigId: '501', apiKey: '' })

    await wrapper.get('input[placeholder="例如：gpt-5"]').setValue('custom-model')
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['custom-model'])
    await wrapper.get('.discover-button').trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('站点鉴权失败')

    await wrapper.get('[role="alert"] button').trigger('click')
    await flushPromises()
    expect(discoverMock).toHaveBeenLastCalledWith(expect.objectContaining({
      modelConfigId: '501', apiKey: undefined,
    }), false)
    expect(wrapper.find('[role="listbox"]').exists()).toBe(true)
  })
})
