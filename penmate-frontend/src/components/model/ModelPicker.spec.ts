import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ModelPicker, { type ModelPickerOption } from './ModelPicker.vue'

const options: ModelPickerOption[] = [
  {
    id: 'official-1',
    displayName: '官方创作模型',
    modelName: 'gpt-5',
    providerName: 'OpenAI',
    type: 'CHAT',
    official: true,
  },
  {
    id: 'personal-1',
    displayName: '我的长篇模型',
    modelName: 'writer-pro',
    providerName: '自定义服务',
    type: 'CHAT',
    official: false,
  },
]

describe('ModelPicker', () => {
  it('groups official and personal models and emits the selected model', async () => {
    const wrapper = mount(ModelPicker, {
      props: { modelValue: 'personal-1', label: '创作模型', options },
      global: { stubs: { teleport: true } },
    })

    await wrapper.get('.model-picker-trigger').trigger('click')

    expect(wrapper.text()).toContain('官方模型')
    expect(wrapper.text()).toContain('个人模型')
    expect(wrapper.findAll('.official-badge').length).toBeGreaterThan(0)
    await wrapper.get('[data-model-id="official-1"]').trigger('click')
    expect(wrapper.emitted('update:modelValue')).toEqual([['official-1']])
  })

  it('searches model metadata and supports clearing the selection', async () => {
    const wrapper = mount(ModelPicker, {
      props: { modelValue: 'official-1', label: '创作模型', options },
      global: { stubs: { teleport: true } },
    })

    await wrapper.get('.model-picker-trigger').trigger('click')
    await wrapper.get('input[type="search"]').setValue('writer-pro')
    expect(wrapper.find('[data-model-id="official-1"]').exists()).toBe(false)
    expect(wrapper.find('[data-model-id="personal-1"]').exists()).toBe(true)
    await wrapper.get('.model-picker-clear').trigger('click')
    expect(wrapper.emitted('update:modelValue')).toEqual([[null]])
  })

  it('keeps unavailable official models visible but prevents selecting them', async () => {
    const restrictedOptions: ModelPickerOption[] = [
      {
        ...options[0],
        usable: false,
        unavailableReason: 'OFFICIAL_MODEL_PERMISSION_REQUIRED',
      },
      options[1],
    ]
    const wrapper = mount(ModelPicker, {
      props: { modelValue: 'personal-1', label: 'Model', options: restrictedOptions },
      global: { stubs: { teleport: true } },
    })

    await wrapper.get('.model-picker-trigger').trigger('click')

    const officialOption = wrapper.get('[data-model-id="official-1"]')
    expect(officialOption.attributes('disabled')).toBeDefined()
    expect(officialOption.find('.model-option-lock').exists()).toBe(true)
    await officialOption.trigger('click')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(wrapper.find('[data-model-id="official-1"]').exists()).toBe(true)
  })
})
