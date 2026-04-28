import { mount } from '@vue/test-utils'
import { defineComponent, type Component } from 'vue'
import { describe, expect, it } from 'vitest'

type ChapterVersionItem = {
  chapterVersionId?: number | string
  versionNo?: number | string
  changeReason?: string
  changeType?: string
}

const MissingEditorToolbar = defineComponent({
  name: 'MissingEditorToolbar',
  template: '<div data-testid="missing-editor-toolbar"></div>',
})

const loadEditorToolbar = async (): Promise<Component> => {
  try {
    const componentPath = './EditorToolbar.vue'
    return (await import(/* @vite-ignore */ componentPath)).default
  } catch {
    return MissingEditorToolbar
  }
}

const mountEditorToolbar = async (
  overrides: Partial<{
    currentChapterTitle: string
    selectedVersionNo: string
    versionBusy: boolean
    activeChapter: string
    versions: ChapterVersionItem[]
  }> = {},
) => {
  const EditorToolbar = await loadEditorToolbar()

  return mount(EditorToolbar, {
    props: {
      currentChapterTitle: '第一章：风起',
      selectedVersionNo: '',
      versionBusy: false,
      activeChapter: '301',
      versions: [],
      ...overrides,
    },
  })
}

describe('EditorToolbar', () => {
  it('emits_toolbar_commands_when_clicking_left_actions', async () => {
    const wrapper = await mountEditorToolbar()

    expect(wrapper.find('.editor-toolbar').exists()).toBe(true)
    expect(wrapper.get('[data-testid="toolbar-save"]').classes()).toContain('toolbar-btn')
    expect(wrapper.get('[data-testid="toolbar-undo"]').classes()).toContain('toolbar-btn')
    expect(wrapper.get('[data-testid="toolbar-redo"]').classes()).toContain('toolbar-btn')
    expect(wrapper.get('[data-testid="toolbar-bold"]').classes()).toContain('toolbar-btn')
    expect(wrapper.get('[data-testid="toolbar-italic"]').classes()).toContain('toolbar-btn')
    expect(wrapper.get('[data-testid="toolbar-quote"]').classes()).toContain('toolbar-btn')

    await wrapper.get('[data-testid="toolbar-save"]').trigger('click')
    await wrapper.get('[data-testid="toolbar-undo"]').trigger('click')
    await wrapper.get('[data-testid="toolbar-redo"]').trigger('click')
    await wrapper.get('[data-testid="toolbar-bold"]').trigger('click')
    await wrapper.get('[data-testid="toolbar-italic"]').trigger('click')
    await wrapper.get('[data-testid="toolbar-quote"]').trigger('click')

    expect(wrapper.emitted('save')).toEqual([[]])
    expect(wrapper.emitted('undo')).toEqual([[]])
    expect(wrapper.emitted('redo')).toEqual([[]])
    expect(wrapper.emitted('wrap-selection')).toEqual([
      ['**', '**'],
      ['*', '*'],
    ])
    expect(wrapper.emitted('insert-prefix')).toEqual([
      ['> '],
    ])
  })

  it('renders_versions_and_emits_version_actions', async () => {
    const wrapper = await mountEditorToolbar({
      selectedVersionNo: '7',
      versions: [
        { chapterVersionId: 9001, versionNo: 7, changeReason: '修正文风' },
        { chapterVersionId: 9002, versionNo: 6, changeType: 'MANUAL_SAVE' },
      ],
    })

    const versionSelect = wrapper.get('[data-testid="version-select"]')
    const options = versionSelect.findAll('option')

    expect(wrapper.get('[data-testid="chapter-label"]').text()).toBe('第一章：风起')
    expect(wrapper.get('[data-testid="chapter-label"]').classes()).toContain('toolbar-chapter')
    expect(versionSelect.classes()).toContain('toolbar-select')
    expect(options).toHaveLength(3)
    expect(options[1].text()).toContain('v7 · 修正文风')
    expect(options[2].text()).toContain('v6 · MANUAL_SAVE')

    await versionSelect.setValue('6')
    await wrapper.get('[data-testid="toolbar-restore-version"]').trigger('click')
    await wrapper.get('[data-testid="toolbar-view-version"]').trigger('click')
    await wrapper.get('[data-testid="toolbar-publish-chapter"]').trigger('click')

    expect(wrapper.emitted('update:selectedVersionNo')).toEqual([['6']])
    expect(wrapper.emitted('restore-version')).toEqual([[]])
    expect(wrapper.emitted('view-version')).toEqual([[]])
    expect(wrapper.emitted('publish-chapter')).toEqual([[]])
  })

  it('disables_version_actions_when_busy_or_selection_missing', async () => {
    const withoutSelection = await mountEditorToolbar({
      selectedVersionNo: '',
      activeChapter: '',
      versions: [],
    })

    expect((withoutSelection.get('[data-testid="version-select"]').element as HTMLSelectElement).disabled).toBe(true)
    expect((withoutSelection.get('[data-testid="toolbar-restore-version"]').element as HTMLButtonElement).disabled).toBe(true)
    expect((withoutSelection.get('[data-testid="toolbar-view-version"]').element as HTMLButtonElement).disabled).toBe(true)
    expect((withoutSelection.get('[data-testid="toolbar-publish-chapter"]').element as HTMLButtonElement).disabled).toBe(true)

    const busyWrapper = await mountEditorToolbar({
      selectedVersionNo: '3',
      versionBusy: true,
      activeChapter: '301',
      versions: [{ versionNo: 3, changeReason: '发布前保存' }],
    })

    expect((busyWrapper.get('[data-testid="version-select"]').element as HTMLSelectElement).disabled).toBe(true)
    expect((busyWrapper.get('[data-testid="toolbar-restore-version"]').element as HTMLButtonElement).disabled).toBe(true)
    expect((busyWrapper.get('[data-testid="toolbar-view-version"]').element as HTMLButtonElement).disabled).toBe(true)
    expect((busyWrapper.get('[data-testid="toolbar-publish-chapter"]').element as HTMLButtonElement).disabled).toBe(true)
  })
})
