import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ProfileIndex from './index.vue'

const pushMock = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: pushMock,
  }),
}))

const currentDir = dirname(fileURLToPath(import.meta.url))
const readProfileSource = () => readFileSync(resolve(currentDir, 'index.vue'), 'utf-8')
const readProfileSettingsSource = () =>
  readFileSync(resolve(currentDir, '../../composables/profile/useProfileSettings.ts'), 'utf-8')

describe('Profile index refactor', () => {
  beforeEach(() => {
    pushMock.mockReset()
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

  it('removes dead parent edit forwarding state after profile split', () => {
    const pageSource = readProfileSource()
    const settingsSource = readProfileSettingsSource()

    expect(pageSource).not.toContain('@edit-profile="startProfileEdit"')
    expect(settingsSource).not.toContain('isEditingProfile')
    expect(settingsSource).not.toContain('const startProfileEdit')
  })
})
