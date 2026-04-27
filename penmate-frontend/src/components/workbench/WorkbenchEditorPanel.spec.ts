import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { describe, expect, it } from 'vitest'
import WorkbenchEditorPanel from './WorkbenchEditorPanel.vue'

const EditorToolbarStub = defineComponent({
  name: 'EditorToolbar',
  emits: ['save', 'wrap-selection', 'update:selected-version-no'],
  setup(_, { emit }) {
    return () =>
      h('div', [
        h('button', { 'data-testid': 'toolbar-save', onClick: () => emit('save') }),
        h('button', { 'data-testid': 'toolbar-wrap', onClick: () => emit('wrap-selection', '**', '**') }),
        h('button', { 'data-testid': 'toolbar-version', onClick: () => emit('update:selected-version-no', '7') }),
      ])
  },
})

const EditorTextareaStub = defineComponent({
  name: 'EditorTextarea',
  emits: ['update:model-value', 'input', 'cursor-activity'],
  setup(_, { emit }) {
    return () =>
      h('div', [
        h('button', { 'data-testid': 'textarea-update', onClick: () => emit('update:model-value', '新正文') }),
        h('button', { 'data-testid': 'textarea-input', onClick: () => emit('input') }),
        h('button', { 'data-testid': 'textarea-cursor', onClick: () => emit('cursor-activity') }),
      ])
  },
})

const EditorStatusbarStub = defineComponent({ name: 'EditorStatusbar', setup: () => () => h('div', { 'data-testid': 'editor-statusbar-stub' }) })
const VersionPreviewPaneStub = defineComponent({ name: 'VersionPreviewPane', setup: () => () => h('div', { 'data-testid': 'version-preview-pane-stub' }) })

describe('WorkbenchEditorPanel', () => {
  it('renders_editor_shell_and_forwards_toolbar_and_textarea_events', async () => {
    const wrapper = mount(WorkbenchEditorPanel, {
      props: {
        currentChapterTitle: '第一章',
        selectedVersionNo: '',
        versionBusy: false,
        activeChapter: '11',
        versions: [],
        editorTextareaRef: () => undefined,
        editorContent: '初始正文',
        selectedText: '选中文本',
        versionDiffSummary: '无差异',
        currentLine: 3,
        currentCol: 8,
        selectedVersionContent: '',
      },
      global: {
        stubs: {
          EditorToolbar: EditorToolbarStub,
          EditorTextarea: EditorTextareaStub,
          EditorStatusbar: EditorStatusbarStub,
          VersionPreviewPane: VersionPreviewPaneStub,
        },
      },
    })

    expect(wrapper.get('.panel-center').classes()).toContain('glass-panel')

    expect(wrapper.get('[data-testid="editor-statusbar-stub"]')).toBeTruthy()
    expect(wrapper.get('[data-testid="version-preview-pane-stub"]')).toBeTruthy()

    await wrapper.get('[data-testid="toolbar-save"]').trigger('click')
    await wrapper.get('[data-testid="toolbar-wrap"]').trigger('click')
    await wrapper.get('[data-testid="toolbar-version"]').trigger('click')
    await wrapper.get('[data-testid="textarea-update"]').trigger('click')
    await wrapper.get('[data-testid="textarea-input"]').trigger('click')
    await wrapper.get('[data-testid="textarea-cursor"]').trigger('click')

    expect(wrapper.emitted('save')).toEqual([[]])
    const wrapSelectionEvents = wrapper.emitted('wrap-selection')
    expect(wrapSelectionEvents).toHaveLength(1)
    expect(wrapSelectionEvents?.[0]).toHaveLength(2)
    expect(wrapSelectionEvents?.[0]?.every((part) => typeof part === 'string')).toBe(true)
    expect(wrapper.emitted('update:selected-version-no')).toEqual([['7']])
    expect(wrapper.emitted('update:editor-content')).toEqual([['新正文']])
    expect(wrapper.emitted('input')).toEqual([[]])
    expect(wrapper.emitted('cursor-activity')).toEqual([[]])
  })
})
