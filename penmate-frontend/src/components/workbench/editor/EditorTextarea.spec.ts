import { mount } from '@vue/test-utils'
import { defineComponent, type Component } from 'vue'
import { describe, expect, it } from 'vitest'

const MissingEditorTextarea = defineComponent({
  name: 'MissingEditorTextarea',
  template: '<div data-testid="missing-editor-textarea"></div>',
})

const loadEditorTextarea = async (): Promise<Component> => {
  try {
    const componentPath = './EditorTextarea.vue'
    return (await import(/* @vite-ignore */ componentPath)).default
  } catch {
    return MissingEditorTextarea
  }
}

const mountEditorTextarea = async (
  overrides: Partial<{
    modelValue: string
    placeholder: string
  }> = {},
) => {
  const EditorTextarea = await loadEditorTextarea()

  return mount(EditorTextarea, {
    props: {
      modelValue: '初稿正文',
      placeholder: '在此处开始创作，或让AI为你执笔...',
      ...overrides,
    },
  })
}

describe('EditorTextarea', () => {
  it('renders_model_value_and_emits_input_cursor_and_shortcut_commands', async () => {
    const wrapper = await mountEditorTextarea()
    const textarea = wrapper.get('[data-testid="editor-textarea"]')

    expect((textarea.element as HTMLTextAreaElement).value).toBe('初稿正文')

    await textarea.setValue('更新后的正文')
    await textarea.trigger('keyup')
    await textarea.trigger('click')
    await textarea.trigger('keydown', { key: 's', ctrlKey: true })
    await textarea.trigger('keydown', { key: 'z', ctrlKey: true })
    await textarea.trigger('keydown', { key: 'y', ctrlKey: true })
    await textarea.trigger('keydown', { key: 'b', ctrlKey: true })
    await textarea.trigger('keydown', { key: 'i', ctrlKey: true })

    expect(wrapper.emitted('update:modelValue')).toEqual([['更新后的正文']])
    expect(wrapper.emitted('input')).toEqual([[]])
    expect(wrapper.emitted('cursor-activity')).toEqual([[], []])
    expect(wrapper.emitted('save')).toEqual([[]])
    expect(wrapper.emitted('undo')).toEqual([[]])
    expect(wrapper.emitted('redo')).toEqual([[]])
    expect(wrapper.emitted('wrap-selection')).toEqual([
      ['**', '**'],
      ['*', '*'],
    ])
  })
})
