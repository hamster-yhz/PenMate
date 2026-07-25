import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { describe, expect, it } from 'vitest'
import WorkbenchEditorPanel from './WorkbenchEditorPanel.vue'

const EditorToolbarStub = defineComponent({
  name: 'EditorToolbar',
  emits: ['save'],
  setup(_, { emit }) {
    return () => h('button', { 'data-testid': 'toolbar-save', onClick: () => emit('save') })
  },
})

const PlainTextEditorStub = defineComponent({
  name: 'PlainTextEditor',
  props: ['conflictPending', 'fontFamily', 'fontSize', 'lineHeight', 'paragraphSpacing', 'contentWidth', 'highlightCurrentParagraph'],
  emits: ['update:model-value', 'change', 'selection-change', 'use-latest', 'continue-local'],
  setup(_, { emit }) {
    return () => h('div', [
      h('button', { 'data-testid': 'editor-update', onClick: () => emit('update:model-value', '新正文') }),
      h('button', { 'data-testid': 'editor-change', onClick: () => emit('change', '新正文') }),
      h('button', { 'data-testid': 'editor-selection', onClick: () => emit('selection-change', { line: 2, column: 3, selectedText: '新正' }) }),
      h('button', { 'data-testid': 'editor-use-latest', onClick: () => emit('use-latest') }),
      h('button', { 'data-testid': 'editor-continue-local', onClick: () => emit('continue-local') }),
    ])
  },
})

const EditorStatusbarStub = defineComponent({
  name: 'EditorStatusbar',
  setup: () => () => h('div', { 'data-testid': 'editor-statusbar-stub' }),
})

describe('WorkbenchEditorPanel', () => {
  it('renders_the_plain_text_editor_and_forwards_content_events', async () => {
    const wrapper = mount(WorkbenchEditorPanel, {
      props: {
        currentChapterTitle: '第一章',
        activeChapter: '11',
        editorContent: '初始正文',
        selectedText: '',
        currentLine: 1,
        currentCol: 1,
        wordCount: 4,
        saveHint: '已保存',
        conflictPending: true,
      },
      global: { stubs: { EditorToolbar: EditorToolbarStub, PlainTextEditor: PlainTextEditorStub, EditorStatusbar: EditorStatusbarStub } },
    })

    expect(wrapper.get('.panel-center')).toBeTruthy()
    expect(wrapper.get('[data-testid="editor-statusbar-stub"]')).toBeTruthy()
    const editor = wrapper.findComponent(PlainTextEditorStub)
    expect(editor.props()).toMatchObject({
      fontFamily: 'SERIF',
      fontSize: 17,
      lineHeight: 1.9,
      paragraphSpacing: 0.35,
      contentWidth: 760,
      highlightCurrentParagraph: true,
      conflictPending: true,
    })

    await wrapper.get('[data-testid="toolbar-save"]').trigger('click')
    await wrapper.get('[data-testid="editor-update"]').trigger('click')
    await wrapper.get('[data-testid="editor-change"]').trigger('click')
    await wrapper.get('[data-testid="editor-selection"]').trigger('click')
    await wrapper.get('[data-testid="editor-use-latest"]').trigger('click')
    await wrapper.get('[data-testid="editor-continue-local"]').trigger('click')

    expect(wrapper.emitted('save')).toEqual([[]])
    expect(wrapper.emitted('update:editor-content')).toEqual([['新正文']])
    expect(wrapper.emitted('input')).toEqual([['新正文']])
    expect(wrapper.emitted('selection-change')).toEqual([[{ line: 2, column: 3, selectedText: '新正' }]])
    expect(wrapper.emitted('use-latest')).toEqual([[]])
    expect(wrapper.emitted('continue-local')).toEqual([[]])
  })
})
