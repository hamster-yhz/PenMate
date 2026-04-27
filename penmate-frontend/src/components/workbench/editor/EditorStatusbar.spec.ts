import { mount } from '@vue/test-utils'
import { defineComponent, type Component } from 'vue'
import { describe, expect, it } from 'vitest'

const MissingEditorStatusbar = defineComponent({
  name: 'MissingEditorStatusbar',
  template: '<div data-testid="missing-editor-statusbar"></div>',
})

const loadEditorStatusbar = async (): Promise<Component> => {
  try {
    const componentPath = './EditorStatusbar.vue'
    return (await import(/* @vite-ignore */ componentPath)).default
  } catch {
    return MissingEditorStatusbar
  }
}

const mountEditorStatusbar = async (
  overrides: Partial<{
    selectedText: string
    versionDiffSummary: string
    currentLine: number
    currentCol: number
  }> = {},
) => {
  const EditorStatusbar = await loadEditorStatusbar()

  return mount(EditorStatusbar, {
    props: {
      selectedText: '山河',
      versionDiffSummary: '新增 12 字 / 删除 3 字',
      currentLine: 3,
      currentCol: 8,
      ...overrides,
    },
  })
}

describe('EditorStatusbar', () => {
  it('renders_selection_diff_and_cursor_position', async () => {
    const wrapper = await mountEditorStatusbar()

    expect(wrapper.get('[data-testid="status-selection"]').text()).toBe('已选 2 字')
    expect(wrapper.get('[data-testid="status-diff"]').text()).toBe('新增 12 字 / 删除 3 字')
    expect(wrapper.get('[data-testid="status-position"]').text()).toBe('行 3 · 列 8')
  })

  it('hides_optional_sections_when_selection_and_diff_are_empty', async () => {
    const wrapper = await mountEditorStatusbar({
      selectedText: '',
      versionDiffSummary: '',
      currentLine: 1,
      currentCol: 1,
    })

    expect(wrapper.find('[data-testid="status-selection"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="status-diff"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="status-position"]').text()).toBe('行 1 · 列 1')
  })
})
