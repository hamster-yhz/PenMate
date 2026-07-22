import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ProfileAppearancePanel from './ProfileAppearancePanel.vue'

const { preferences, loadMock, saveMock } = vi.hoisted(() => ({
  preferences: {
    themeMode: 'SYSTEM' as const,
    editorFontFamily: 'SERIF' as const,
    editorFontSize: 17,
    editorLineHeight: 1.9,
    editorParagraphSpacing: 0.35,
    editorContentWidth: 760,
    typewriterMode: false,
    highlightCurrentParagraph: true,
  },
  loadMock: vi.fn(),
  saveMock: vi.fn(),
}))

vi.mock('@/composables/useUserUiPreferences', () => ({
  useUserUiPreferences: () => ({
    uiPreferences: preferences,
    loadUserUiPreferences: loadMock,
    saveUserUiPreferences: saveMock,
  }),
}))

describe('ProfileAppearancePanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    loadMock.mockResolvedValue({ ...preferences })
    saveMock.mockImplementation(async (value) => value)
  })

  it('loads the server preferences and saves the complete section explicitly', async () => {
    const wrapper = mount(ProfileAppearancePanel)
    await flushPromises()

    expect(loadMock).toHaveBeenCalledOnce()
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()

    await wrapper.get('input[aria-label="正文字号"]').setValue('19')
    expect(wrapper.text()).toContain('有未保存修改')
    await wrapper.get('button[type="submit"]').trigger('submit')
    await flushPromises()

    expect(saveMock).toHaveBeenCalledWith(expect.objectContaining({
      themeMode: 'SYSTEM',
      editorFontFamily: 'SERIF',
      editorFontSize: 19,
      editorLineHeight: 1.9,
      editorParagraphSpacing: 0.35,
      editorContentWidth: 760,
      typewriterMode: false,
      highlightCurrentParagraph: true,
    }))
    expect(wrapper.text()).toContain('已保存')
  })

  it('keeps a failed save visible in the form action area', async () => {
    saveMock.mockRejectedValueOnce(new Error('网络不可用'))
    const wrapper = mount(ProfileAppearancePanel)
    await flushPromises()

    await wrapper.get('input[aria-label="正文宽度"]').setValue('800')
    await wrapper.get('button[type="submit"]').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toBe('网络不可用')
    expect(wrapper.text()).toContain('保存失败')
  })
})
