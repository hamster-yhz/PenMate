import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { clearSession, setSession } from '@/stores/session'
import ProfileIndex from './index.vue'

const { pushMock, getUserModelPreferencesMock, saveUserModelPreferencesMock } = vi.hoisted(() => ({
  pushMock: vi.fn(),
  getUserModelPreferencesMock: vi.fn(),
  saveUserModelPreferencesMock: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: pushMock,
  }),
}))

vi.mock('@/api/modules/model.api', async () => {
  const actual = await vi.importActual<typeof import('@/api/modules/model.api')>('@/api/modules/model.api')
  return {
    ...actual,
    modelApi: {
      ...actual.modelApi,
      getUserModelPreferences: getUserModelPreferencesMock,
      saveUserModelPreferences: saveUserModelPreferencesMock,
    },
  }
})

describe('Profile index refactor', () => {
  beforeEach(() => {
    pushMock.mockReset()
    getUserModelPreferencesMock.mockReset()
    saveUserModelPreferencesMock.mockReset()
    clearSession()
    setSession({ userId: '1001' })
    getUserModelPreferencesMock.mockResolvedValue({
      mainAgentModelConfigId: 'mcfg-9001',
      dirtyWorkAgentModelConfigId: 'mcfg-9002',
      candidateConfigs: [
        { modelConfigId: 'mcfg-9001', modelName: 'gpt-4o-mini', providerName: 'OpenAI', keySourceType: 'USER_KEY' },
        { modelConfigId: 'mcfg-9002', modelName: 'deepseek-chat', providerName: 'DeepSeek', keySourceType: 'OFFICIAL_KEY' },
      ],
    })
    saveUserModelPreferencesMock.mockResolvedValue('updated')
  })

  it('renders split profile sections and wires hero, security, and navigation behaviors', async () => {
    const wrapper = mount(ProfileIndex)

    expect(wrapper.find('[data-testid="profile-hero-card"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('账号安全')
    expect(wrapper.text()).toContain('API密钥管理')

    await wrapper.find('[data-testid="profile-hero-edit"]').trigger('click')
    await wrapper.find('[data-testid="profile-hero-name-input"]').setValue(' 新笔名 ')
    await wrapper.find('[data-testid="profile-hero-bio-input"]').setValue(' 新简介 ')
    await wrapper.find('[data-testid="profile-hero-save"]').trigger('click')
    await nextTick()

    expect(wrapper.text()).toContain('新笔名')
    expect(wrapper.text()).toContain('新简介')

    await wrapper.find('[data-testid="profile-security-email-toggle"]').trigger('click')
    await wrapper.find('input[placeholder="新邮箱地址"]').setValue(' @ ')
    await wrapper.find('[data-testid="profile-security-email-save"]').trigger('click')

    expect(wrapper.text()).toContain('请输入有效邮箱地址')
    expect(wrapper.find('[data-testid="profile-security-email-form"]').exists()).toBe(true)

    const openWorkbenchButton = wrapper
      .findAll('button')
      .find((button) => button.text() === '前往设置')

    expect(openWorkbenchButton?.exists()).toBe(true)

    await openWorkbenchButton!.trigger('click')

    expect(pushMock).toHaveBeenCalledWith('/workbench')

    await wrapper.find('.nav-btn').trigger('click')

    expect(pushMock).toHaveBeenCalledWith('/mybooks')
  })

  it('renders_model_preference_editor_with_loading_save_and_error_feedback', async () => {
    const wrapper = mount(ProfileIndex)

    expect(getUserModelPreferencesMock).toHaveBeenCalled()
    await nextTick()
    await Promise.resolve()
    await nextTick()
    expect(wrapper.text()).toContain('模型偏好')
    expect(wrapper.text()).toContain('主 Agent')
    expect(wrapper.text()).toContain('脏活 Agent')

    const mainSelect = wrapper.find('[data-testid="model-preference-main-select"]')
    const dirtySelect = wrapper.find('[data-testid="model-preference-dirty-select"]')

    expect(mainSelect.exists()).toBe(true)
    expect(dirtySelect.exists()).toBe(true)

    await mainSelect.setValue('mcfg-9002')
    await dirtySelect.setValue('mcfg-9001')
    await wrapper.find('[data-testid="model-preference-save"]').trigger('click')

    expect(saveUserModelPreferencesMock).toHaveBeenCalledWith('1001', '1001', {
      mainAgentModelConfigId: 'mcfg-9002',
      dirtyWorkAgentModelConfigId: 'mcfg-9001',
    })
    expect(wrapper.text()).toContain('模型偏好已保存')

    saveUserModelPreferencesMock.mockRejectedValueOnce(new Error('save failed'))
    await wrapper.find('[data-testid="model-preference-save"]').trigger('click')
    await nextTick()

    expect(wrapper.text()).toContain('保存模型偏好失败')
  })

})
