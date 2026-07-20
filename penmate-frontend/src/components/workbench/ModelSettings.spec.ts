import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ModelSettings from './ModelSettings.vue'

const mocks = vi.hoisted(() => ({
  sessionUserId: '101' as string | number | null,
  messageSuccess: vi.fn(),
  messageWarning: vi.fn(),
  listProviders: vi.fn(),
  listKeys: vi.fn(),
  listOfficialKeys: vi.fn(),
  listUserModelConfigs: vi.fn(),
  getUserModelPreferences: vi.fn(),
  saveUserModelPreferences: vi.fn(),
  createUserModelConfig: vi.fn(),
  updateUserModelConfig: vi.fn(),
  probeEmbeddingDimensions: vi.fn(),
  deleteUserModelConfig: vi.fn(),
}))

vi.mock('ant-design-vue', () => ({
  message: {
    success: mocks.messageSuccess,
    warning: mocks.messageWarning,
  },
}))

vi.mock('@/stores/session', () => ({
  getSession: () => ({
    userId: mocks.sessionUserId,
  }),
}))

vi.mock('@/api/modules/model.api', () => ({
  modelApi: {
    listProviders: mocks.listProviders,
    listKeys: mocks.listKeys,
    listOfficialKeys: mocks.listOfficialKeys,
    listUserModelConfigs: mocks.listUserModelConfigs,
    getUserModelPreferences: mocks.getUserModelPreferences,
    saveUserModelPreferences: mocks.saveUserModelPreferences,
    createUserModelConfig: mocks.createUserModelConfig,
    updateUserModelConfig: mocks.updateUserModelConfig,
    probeEmbeddingDimensions: mocks.probeEmbeddingDimensions,
    deleteUserModelConfig: mocks.deleteUserModelConfig,
  },
}))

const providerFixture = [
  {
    providerId: 'provider-openai-900719925474099312345',
    providerName: 'OpenAI',
    displayName: 'OpenAI',
  },
]
const userKeyFixture = [
  {
    keyId: 'uk-501',
    providerId: 'provider-openai-900719925474099312345',
    name: 'OpenAI User Key',
    maskedApiKey: '****1234',
    status: 'active',
  },
]
const officialKeyFixture = [
  {
    keyId: 'ok-801',
    providerId: 'provider-openai-900719925474099312345',
    name: 'OpenAI Official Key',
    maskedApiKey: '****9999',
    status: 'active',
  },
]
const configFixture = [
  {
    modelConfigId: 'mcfg-1001',
    providerId: 'provider-openai-900719925474099312345',
    modelName: 'gpt-4o-mini',
    keySourceType: 'USER_KEY',
    userKeyId: 'uk-501',
    officialKeyId: null,
    keyName: 'OpenAI User Key',
    maskedApiKey: '****1234',
    status: 'active',
    baseUrl: '',
  },
  {
    modelConfigId: 'mcfg-1002',
    providerId: 'provider-openai-900719925474099312345',
    modelName: 'gpt-4.1',
    keySourceType: 'OFFICIAL_KEY',
    userKeyId: null,
    officialKeyId: 'ok-801',
    keyName: 'OpenAI Official Key',
    maskedApiKey: '****9999',
    status: 'active',
    baseUrl: 'https://example.com',
  },
]

const mountComponent = async () => {
  const wrapper = mount(ModelSettings, {
    props: {
      visible: true,
    },
  })
  await flushPromises()
  return wrapper
}

describe('ModelSettings', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.sessionUserId = '101'
    mocks.listProviders.mockResolvedValue(providerFixture)
    mocks.listKeys.mockResolvedValue(userKeyFixture)
    mocks.listOfficialKeys.mockResolvedValue(officialKeyFixture)
    mocks.listUserModelConfigs.mockResolvedValue(configFixture)
    mocks.getUserModelPreferences.mockResolvedValue({
      mainAgentModelConfigId: 'mcfg-1001',
      dirtyWorkAgentModelConfigId: 'mcfg-1002',
    })
    mocks.saveUserModelPreferences.mockResolvedValue({})
    mocks.createUserModelConfig.mockResolvedValue({})
    mocks.updateUserModelConfig.mockResolvedValue({})
    mocks.probeEmbeddingDimensions.mockResolvedValue({ dimensions: 1536 })
    mocks.deleteUserModelConfig.mockResolvedValue({})
  })

  it('默认展示模型池主视图时应初始化加载 providers、keys、configs 与 preferences，并移除旧角色偏好区域', async () => {
    const wrapper = await mountComponent()

    expect(mocks.listProviders).toHaveBeenCalledTimes(1)
    expect(mocks.listKeys).not.toHaveBeenCalled()
    expect(mocks.listOfficialKeys).not.toHaveBeenCalled()
    expect(mocks.listUserModelConfigs).toHaveBeenCalledWith('101')
    expect(mocks.getUserModelPreferences).toHaveBeenCalledWith('101')
    expect(wrapper.text()).toContain('模型池')
    expect(wrapper.find('.api-form').exists()).toBe(false)
    expect(wrapper.findAll('.config-card')).toHaveLength(2)
    expect(wrapper.text()).not.toContain('OpenAI User Key')
    expect(wrapper.text()).not.toContain('系统内部密钥')
    expect(wrapper.text()).not.toContain('Agent 角色偏好')
    expect(wrapper.text()).not.toContain('保存角色偏好')
    expect(wrapper.find('[data-role="main"]').exists()).toBe(false)
  })

  it('加载顶层 preferences 响应后应在对应模型卡片上显示主 Agent 与副 Agent 标记', async () => {
    const wrapper = await mountComponent()

    expect(wrapper.findAll('.config-card').at(0)!.text()).toContain('主 Agent')
    expect(wrapper.findAll('.config-card').at(0)!.text()).toContain('用户模型')
    expect(wrapper.findAll('.config-card').at(1)!.text()).toContain('Dirty Work Agent')
    expect(wrapper.findAll('.config-card').at(1)!.text()).toContain('官方模型')
  })

  it('加载嵌套 preferences 响应后应在对应模型卡片上显示主 Agent 与副 Agent 标记', async () => {
    mocks.getUserModelPreferences.mockResolvedValueOnce({
      preferences: {
        mainAgentModelConfigId: 'mcfg-1001',
        dirtyWorkAgentModelConfigId: 'mcfg-1002',
      },
    })

    const wrapper = await mountComponent()

    expect(wrapper.findAll('.config-card').at(0)!.text()).toContain('主 Agent')
    expect(wrapper.findAll('.config-card').at(1)!.text()).toContain('Dirty Work Agent')
  })

  it('当配置列表接口为空但偏好详情 candidateConfigs 非空时，仍应展示模型配置卡片', async () => {
    mocks.listUserModelConfigs.mockResolvedValueOnce([])
    mocks.getUserModelPreferences.mockResolvedValueOnce({
      data: {
        preferences: {
          mainAgentModelConfigId: 'mcfg-1001',
          dirtyWorkAgentModelConfigId: 'mcfg-1002',
        },
        candidateConfigs: configFixture,
      },
    })

    const wrapper = await mountComponent()

    expect(wrapper.findAll('.config-card')).toHaveLength(2)
    expect(wrapper.text()).not.toContain('还没有模型配置')
    expect(wrapper.findAll('.config-card').at(0)!.text()).toContain('主 Agent')
    expect(wrapper.findAll('.config-card').at(1)!.text()).toContain('Dirty Work Agent')
  })

  it('当模型配置列表响应包裹在 data 字段时，仍应展示模型池卡片而不是空态', async () => {
    mocks.listUserModelConfigs.mockResolvedValueOnce({
      data: configFixture,
    })

    const wrapper = await mountComponent()

    expect(wrapper.findAll('.config-card')).toHaveLength(2)
    expect(wrapper.text()).not.toContain('还没有模型配置')
    expect(wrapper.findAll('.config-card').at(0)!.text()).toContain('主 Agent')
    expect(wrapper.findAll('.config-card').at(1)!.text()).toContain('Dirty Work Agent')
  })

  it('当会话 userId 为数字时，仍应加载模型配置而不是误判为空会话', async () => {
    mocks.sessionUserId = 101

    const wrapper = await mountComponent()

    expect(mocks.listUserModelConfigs).toHaveBeenCalledWith('101')
    expect(mocks.getUserModelPreferences).toHaveBeenCalledWith('101')
    expect(wrapper.findAll('.config-card')).toHaveLength(2)
    expect(wrapper.text()).not.toContain('还没有模型配置')
    expect(mocks.messageWarning).not.toHaveBeenCalledWith('缺少用户会话')
  })

  it('当模型配置列表返回 modelConfigId 字段时，应根据偏好在卡片上显示主 Agent 与副 Agent 标记', async () => {
    mocks.listUserModelConfigs.mockResolvedValueOnce([
      {
        modelConfigId: 'mcfg-205172327654749798400000-main',
        providerId: 'provider-2-205172327654749798400000',
        modelName: 'grok-4.20-reasoning',
        keySourceType: 'USER_KEY',
        userKeyId: 'uk-205172327651394355200000',
        officialKeyId: null,
        keyName: 'a',
        maskedApiKey: '****8gNb',
        status: 'active',
        baseUrl: '',
      },
      {
        modelConfigId: 'mcfg-920024',
        providerId: 'provider-1-920024',
        modelName: 'gpt-4o-mini',
        keySourceType: 'USER_KEY',
        userKeyId: 'uk-920013',
        officialKeyId: null,
        keyName: 'DBCASE Admin OpenAI Key',
        maskedApiKey: '****92013',
        status: 'disabled',
        baseUrl: '',
      },
    ])
    mocks.listProviders.mockResolvedValueOnce([
      { providerId: 'provider-1-920024', displayName: 'provider-1' },
      { providerId: 'provider-2-205172327654749798400000', displayName: 'provider-2' },
    ])
    mocks.getUserModelPreferences.mockResolvedValueOnce({
      mainAgentModelConfigId: 'mcfg-205172327654749798400000-main',
      dirtyWorkAgentModelConfigId: 'mcfg-205172327654749798400000-main',
    })

    const wrapper = await mountComponent()

    expect(wrapper.findAll('.config-card').at(0)!.text()).toContain('主 Agent')
    expect(wrapper.findAll('.config-card').at(0)!.text()).toContain('Dirty Work Agent')
  })

  it('点击新增模型后展示 key 输入表单而不是 key 下拉，并直接提交 key 值', async () => {
    const wrapper = await mountComponent()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === '新增模型')!
      .trigger('click')
    expect(wrapper.find('.api-form').exists()).toBe(true)
    expect(wrapper.text()).toContain('模型类别')
    expect(wrapper.text()).not.toContain('密钥来源')
    expect(wrapper.text()).toContain('Key')
    expect(wrapper.text()).not.toContain('选择 Key')
    expect(wrapper.find('select').exists()).toBe(true)
    expect(wrapper.find('input[placeholder="例如：sk-xxx"]').exists()).toBe(true)

    const selects = wrapper.findAll('.api-form select')
    await selects[0].setValue('provider-openai-900719925474099312345')
    await wrapper.find('input[placeholder="例如：gpt-4o-mini"]').setValue('claude-3-7-sonnet')
    await wrapper.find('input[placeholder="留空则使用提供商默认地址"]').setValue('https://api.example.com')
    await selects[1].setValue('USER_KEY')
    await wrapper.find('input[placeholder="例如：sk-xxx"]').setValue('sk-direct-user-key')

    await wrapper
      .findAll('button')
      .find((button) => button.text() === '保存模型配置')!
      .trigger('click')
    await flushPromises()

    expect(mocks.createUserModelConfig).toHaveBeenCalledWith(
      '101',
      '101',
      expect.objectContaining({
        providerId: 'provider-openai-900719925474099312345',
        modelName: 'claude-3-7-sonnet',
        baseUrl: 'https://api.example.com',
        modelCategory: 'USER_MODEL',
        apiKey: 'sk-direct-user-key',
      }),
    )
    expect(mocks.createUserModelConfig.mock.calls[0][2]).not.toHaveProperty('keySourceType')
    expect(mocks.createUserModelConfig.mock.calls[0][2]).not.toHaveProperty('userKeyId')
    expect(mocks.createUserModelConfig.mock.calls[0][2]).not.toHaveProperty('officialKeyId')
    expect(mocks.createUserModelConfig.mock.calls[0][2]).not.toHaveProperty('selectedKeyId')
    expect(wrapper.emitted('saved')).toBeTruthy()
  })

  it('点击模型池中的设为主 Agent 按钮后应立即发请求并更新主 Agent 标签', async () => {
    const wrapper = await mountComponent()

    const secondCardButtons = wrapper.findAll('.config-card').at(1)!.findAll('.card-action-btn')
    await secondCardButtons[0].trigger('click')
    await flushPromises()

    expect(mocks.saveUserModelPreferences).toHaveBeenCalledWith('101', '101', {
      mainAgentModelConfigId: 'mcfg-1002',
      dirtyWorkAgentModelConfigId: 'mcfg-1002',
    })
    expect(wrapper.findAll('.config-card').at(1)!.text()).toContain('主 Agent')
    expect(wrapper.emitted('saved')).toBeTruthy()
  })

  it('点击模型池中的设为副 Agent 按钮后应保留主 Agent 并立即发请求', async () => {
    const wrapper = await mountComponent()

    const firstCardButtons = wrapper.findAll('.config-card').at(0)!.findAll('.card-action-btn')
    await firstCardButtons[1].trigger('click')
    await flushPromises()

    expect(mocks.saveUserModelPreferences).toHaveBeenCalledWith('101', '101', {
      mainAgentModelConfigId: 'mcfg-1001',
      dirtyWorkAgentModelConfigId: 'mcfg-1001',
    })
    expect(wrapper.findAll('.config-card').at(0)!.text()).toContain('Dirty Work Agent')
    expect(wrapper.emitted('saved')).toBeTruthy()
  })

  it('当用户已有 key 但还没有模型配置时，仍可创建第一条模型配置并直接填写 key', async () => {
    mocks.listUserModelConfigs.mockResolvedValueOnce([])

    const wrapper = await mountComponent()

    expect(wrapper.text()).toContain('创建第一条配置')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '创建第一条配置')!
      .trigger('click')

    const selects = wrapper.findAll('.api-form select')
    await selects[0].setValue('provider-openai-900719925474099312345')
    await wrapper.find('input[placeholder="例如：gpt-4o-mini"]').setValue('gpt-4.1-mini')
    await selects[1].setValue('USER_KEY')
    await wrapper.find('input[placeholder="例如：sk-xxx"]').setValue('sk-first-config-key')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '保存模型配置')!
      .trigger('click')
    await flushPromises()

    expect(mocks.createUserModelConfig).toHaveBeenCalledWith(
      '101',
      '101',
      expect.objectContaining({
        providerId: 'provider-openai-900719925474099312345',
        modelName: 'gpt-4.1-mini',
        modelCategory: 'USER_MODEL',
        apiKey: 'sk-first-config-key',
      }),
    )
    expect(mocks.createUserModelConfig.mock.calls[0][2]).not.toHaveProperty('keySourceType')
  })

  it('当保存失败时应给出提示而不是直接吞掉异常', async () => {
    mocks.saveUserModelPreferences.mockRejectedValueOnce(new Error('network error'))
    const wrapper = await mountComponent()

    const secondCardButtons = wrapper.findAll('.config-card').at(1)!.findAll('.card-action-btn')
    await secondCardButtons[0].trigger('click')
    await flushPromises()

    expect(mocks.messageWarning).toHaveBeenCalled()
    expect(wrapper.emitted('saved')).toBeFalsy()
  })

  it('编辑配置时切换为官方密钥后保存，应直接提交 official key 值而不是 officialKeyId', async () => {
    const wrapper = await mountComponent()

    const firstCardButtons = wrapper.findAll('.config-card').at(0)!.findAll('.card-action-btn')
    await firstCardButtons[2].trigger('click')
    await flushPromises()

    await wrapper.find('input[placeholder="例如：sk-xxx"]').setValue('sk-direct-official-key')

    await wrapper
      .findAll('button')
      .find((button) => button.text() === '保存模型配置')!
      .trigger('click')
    await flushPromises()

    expect(mocks.updateUserModelConfig).toHaveBeenCalledWith(
      '101',
      'mcfg-1001',
      '101',
      expect.objectContaining({
        apiKey: 'sk-direct-official-key',
      }),
    )
    expect(mocks.updateUserModelConfig.mock.calls[0][3]).not.toHaveProperty('keySourceType')
    expect(mocks.updateUserModelConfig.mock.calls[0][3]).not.toHaveProperty('userKeyId')
    expect(mocks.updateUserModelConfig.mock.calls[0][3]).not.toHaveProperty('officialKeyId')
  })

  it('编辑配置时若未修改 provider 与模型类别，不应强制要求重新填写 key', async () => {
    const wrapper = await mountComponent()

    const firstCardButtons = wrapper.findAll('.config-card').at(0)!.findAll('.card-action-btn')
    await firstCardButtons[2].trigger('click')
    await flushPromises()

    await wrapper.find('input[placeholder="例如：gpt-4o-mini"]').setValue('gpt-4.1')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '保存模型配置')!
      .trigger('click')
    await flushPromises()

    expect(mocks.messageWarning).not.toHaveBeenCalledWith('请填写 Key')
    expect(mocks.updateUserModelConfig).toHaveBeenCalledWith(
      '101',
      'mcfg-1001',
      '101',
      expect.objectContaining({
        modelName: 'gpt-4.1',
      }),
    )
    expect(mocks.updateUserModelConfig.mock.calls[0][3]).not.toHaveProperty('apiKey')
  })

  it('编辑配置时若仅修改模型名，应只提交变更字段而不是整张表单', async () => {
    const wrapper = await mountComponent()

    const firstCardButtons = wrapper.findAll('.config-card').at(0)!.findAll('.card-action-btn')
    await firstCardButtons[2].trigger('click')
    await flushPromises()

    await wrapper.find('input[placeholder="例如：gpt-4o-mini"]').setValue('gpt-4.1')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '保存模型配置')!
      .trigger('click')
    await flushPromises()

    expect(mocks.updateUserModelConfig).toHaveBeenCalledWith('101', 'mcfg-1001', '101', {
      modelName: 'gpt-4.1',
    })
  })

  it('加载失败时应提示模型设置加载失败', async () => {
    mocks.listUserModelConfigs.mockRejectedValueOnce(new Error('load fail'))

    await mountComponent()

    expect(mocks.messageWarning).toHaveBeenCalledWith('模型设置加载失败，请稍后重试')
  })

  it('保存模型配置失败时应提示错误且不发送 saved 事件', async () => {
    mocks.createUserModelConfig.mockRejectedValueOnce(new Error('save fail'))
    const wrapper = await mountComponent()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === '新增模型')!
      .trigger('click')
    const selects = wrapper.findAll('.api-form select')
    await selects[0].setValue('provider-openai-900719925474099312345')
    await wrapper.find('input[placeholder="例如：gpt-4o-mini"]').setValue('gpt-4.1-mini')
    await selects[1].setValue('USER_KEY')
    await wrapper.find('input[placeholder="例如：sk-xxx"]').setValue('sk-save-fail-key')

    await wrapper
      .findAll('button')
      .find((button) => button.text() === '保存模型配置')!
      .trigger('click')
    await flushPromises()

    expect(mocks.messageWarning).toHaveBeenCalledWith('模型配置保存失败，请稍后重试')
    expect(wrapper.emitted('saved')).toBeFalsy()
  })

  it('删除配置后应调用删除接口并刷新列表', async () => {
    const wrapper = await mountComponent()

    await wrapper.findAll('.config-card').at(0)!.find('.card-delete-btn').trigger('click')
    await flushPromises()

    expect(mocks.deleteUserModelConfig).toHaveBeenCalledWith('101', 'mcfg-1001', '101')
    expect(mocks.listUserModelConfigs).toHaveBeenCalledTimes(2)
  })

  it('providers 响应缺少有效 providerId 时，不应出现在供应商选项中', async () => {
    mocks.listProviders.mockResolvedValueOnce([
      { id: 1, displayName: 'OpenAI Legacy' },
      { providerId: '', displayName: 'Invalid Empty' },
      { providerId: '   ', displayName: 'Invalid Blank' },
    ])

    const wrapper = await mountComponent()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === '新增模型')!
      .trigger('click')
    await flushPromises()

    const providerOptions = wrapper.findAll('.api-form select').at(0)!.findAll('option')
    expect(providerOptions).toHaveLength(1)
    expect(providerOptions[0].text()).toBe('请选择')
    expect(wrapper.text()).not.toContain('OpenAI Legacy')
    expect(wrapper.text()).not.toContain('Invalid Empty')
    expect(wrapper.text()).not.toContain('Invalid Blank')
  })

  it('providers 加载失败时，仍应展示已存在的模型配置卡片', async () => {
    mocks.listProviders.mockRejectedValueOnce(new Error('Invalid provider contract'))

    const wrapper = await mountComponent()

    expect(wrapper.findAll('.config-card')).toHaveLength(2)
    expect(wrapper.text()).not.toContain('还没有模型配置')
    expect(wrapper.text()).toContain('gpt-4o-mini')
  })

  it('providers 响应仍包裹在 data 字段时，新增表单中仍应展示供应商选项', async () => {
    mocks.listProviders.mockResolvedValueOnce({
      data: providerFixture,
    })

    const wrapper = await mountComponent()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === '新增模型')!
      .trigger('click')
    await flushPromises()

    const providerOptions = wrapper.findAll('.api-form select').at(0)!.findAll('option')
    expect(providerOptions).toHaveLength(2)
    expect(providerOptions[1].text()).toBe('OpenAI')
  })

  it('supports probing and saving a custom Embedding dimension', async () => {
    mocks.listUserModelConfigs.mockResolvedValueOnce([])
    mocks.probeEmbeddingDimensions.mockResolvedValueOnce({ dimensions: 1024 })
    const wrapper = await mountComponent()

    await wrapper.findAll('.btn-save-api').at(0)!.trigger('click')
    const selects = wrapper.findAll('.api-form select')
    await selects[0].setValue('provider-openai-900719925474099312345')
    await wrapper.findAll('.api-form input').at(0)!.setValue('text-embedding-model')
    await selects[1].setValue('EMBEDDING')
    await wrapper.findAll('.api-form input').at(-1)!.setValue('sk-embedding-key')
    await wrapper.findAll('.dimension-mode button').at(1)!.trigger('click')
    await wrapper.find('input[aria-label="自定义向量维度"]').setValue('1024')
    await wrapper.find('.dimension-actions button').trigger('click')
    await flushPromises()

    expect(mocks.probeEmbeddingDimensions).toHaveBeenCalledWith(
      expect.objectContaining({ embeddingDimensions: 1024, modelName: 'text-embedding-model' }),
    )
    expect(wrapper.find('.dimension-result').text()).toContain('1024')

    await wrapper.find('.api-actions .btn-save-api').trigger('click')
    await flushPromises()
    expect(mocks.createUserModelConfig).toHaveBeenCalledWith(
      '101',
      '101',
      expect.objectContaining({ modelType: 'EMBEDDING', embeddingDimensions: 1024 }),
    )
  })
})
