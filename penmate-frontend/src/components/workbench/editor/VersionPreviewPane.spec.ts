import { mount } from '@vue/test-utils'
import { defineComponent, type Component } from 'vue'
import { describe, expect, it } from 'vitest'

const MissingVersionPreviewPane = defineComponent({
  name: 'MissingVersionPreviewPane',
  template: '<div data-testid="missing-version-preview"></div>',
})

const loadVersionPreviewPane = async (): Promise<Component> => {
  try {
    const componentPath = './VersionPreviewPane.vue'
    return (await import(/* @vite-ignore */ componentPath)).default
  } catch {
    return MissingVersionPreviewPane
  }
}

const mountVersionPreviewPane = async (
  overrides: Partial<{
    currentContent: string
    selectedVersionContent: string
  }> = {},
) => {
  const VersionPreviewPane = await loadVersionPreviewPane()

  return mount(VersionPreviewPane, {
    props: {
      currentContent: '当前正文',
      selectedVersionContent: '历史版本正文',
      ...overrides,
    },
  })
}

describe('VersionPreviewPane', () => {
  it('renders_dual_readonly_preview_when_version_content_exists', async () => {
    const wrapper = await mountVersionPreviewPane()

    expect(wrapper.get('[data-testid="version-preview-root"]').text()).toContain('版本对比预览')
    expect((wrapper.get('[data-testid="version-preview-current"]').element as HTMLTextAreaElement).value).toBe('当前正文')
    expect((wrapper.get('[data-testid="version-preview-selected"]').element as HTMLTextAreaElement).value).toBe('历史版本正文')
    expect((wrapper.get('[data-testid="version-preview-current"]').element as HTMLTextAreaElement).readOnly).toBe(true)
    expect((wrapper.get('[data-testid="version-preview-selected"]').element as HTMLTextAreaElement).readOnly).toBe(true)
  })

  it('renders_nothing_when_selected_version_content_is_empty', async () => {
    const wrapper = await mountVersionPreviewPane({
      selectedVersionContent: '',
    })

    expect(wrapper.find('[data-testid="version-preview-root"]').exists()).toBe(false)
  })
})
