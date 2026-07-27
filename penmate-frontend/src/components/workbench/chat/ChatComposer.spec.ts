import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ChatComposer from './ChatComposer.vue'

const mountChatComposer = (
  overrides: Partial<{
    modelValue: string
    isGenerating: boolean
    canCancelRun: boolean
    isCancelling: boolean
    currentModelName: string
    activePlugins: string[]
    skillCatalog: Array<{ name: string; description: string }>
    activeSkills: string[]
    safetyMode: 'STRICT' | 'STANDARD' | 'AUTONOMOUS' | 'FULL_AUTHORITY'
    contextUsage: { usedTokens: number; maxContextTokens: number | null; usageRatio: number | null }
    queuedRequest: { requestId: string; type: 'MESSAGE' | 'COMPRESS'; status: 'PENDING' | 'EXECUTING' }
    attachedChapterRanges: Array<{ key: string; label: string; chapterIds: string[] }>
  }> = {},
) =>
  mount(ChatComposer, {
    props: {
      modelValue: '',
      isGenerating: false,
      currentModelName: 'DeepSeek-R1',
      activePlugins: [],
      ...overrides,
    },
  })

describe('ChatComposer', () => {
  it('disables blank sends and registers input while a run is active', async () => {
    const blankWrapper = await mountChatComposer({
      modelValue: '   ',
      isGenerating: false,
    })

    expect(blankWrapper.get('[data-testid="chat-composer"]').classes()).toContain('workbench-composer')
    expect(blankWrapper.get('[data-testid="chat-input"]').classes()).toContain('composer-textarea')
    expect(blankWrapper.get('[data-testid="chat-send"]').classes()).toContain('btn-send')

    expect((blankWrapper.get('[data-testid="chat-send"]').element as HTMLButtonElement).disabled).toBe(true)

    const busyWrapper = await mountChatComposer({
      modelValue: '继续写第三章',
      isGenerating: true,
      canCancelRun: true,
    })

    expect(busyWrapper.find('[data-testid="chat-send"]').exists()).toBe(false)
    expect(busyWrapper.find('[data-testid="chat-cancel"]').exists()).toBe(true)
    await busyWrapper.get('[data-testid="chat-input"]').trigger('keydown', { key: 'Enter' })
    expect(busyWrapper.emitted('send')).toEqual([[]])

    const readyWrapper = await mountChatComposer({
      modelValue: '继续写第三章',
      isGenerating: false,
    })

    expect((readyWrapper.get('[data-testid="chat-send"]').element as HTMLButtonElement).disabled).toBe(false)
  }, 10_000)

  it('allows_followup_send_after_waiting_approval_when_generation_has_stopped', async () => {
    const wrapper = await mountChatComposer({
      modelValue: '审批意见已补充，请继续生成正文',
      isGenerating: false,
      currentModelName: 'DeepSeek-R1',
    })

    const sendButton = wrapper.get('[data-testid="chat-send"]')

    expect((sendButton.element as HTMLButtonElement).disabled).toBe(false)

    await sendButton.trigger('click')

    expect(wrapper.emitted('send')).toEqual([[]])
  })

  it('emits_input_updates_and_send_on_ctrl_enter', async () => {
    const wrapper = await mountChatComposer({
      modelValue: '请重写结尾冲突',
      isGenerating: false,
    })

    const textarea = wrapper.get('[data-testid="chat-input"]')

    await textarea.setValue('请补充主角与反派对话')
    await textarea.trigger('keydown', { key: 'Enter', ctrlKey: true })

    expect(wrapper.emitted('update:modelValue')).toEqual([['请补充主角与反派对话']])
    expect(wrapper.emitted('send')).toEqual([[]])
  }, 10_000)

  it('offers_an_icon_stop_action_while_a_run_is_cancellable', async () => {
    const wrapper = await mountChatComposer({
      modelValue: '继续写',
      isGenerating: true,
      canCancelRun: true,
    })

    const stopButton = wrapper.get('[data-testid="chat-cancel"]')
    expect(stopButton.attributes('title')).toBe('停止运行')
    expect((stopButton.element as HTMLButtonElement).disabled).toBe(false)

    await stopButton.trigger('click')

    expect(wrapper.emitted('cancel')).toEqual([[]])
  }, 10_000)

  it('does_not_place_terminal_run_retry_in_the_composer', async () => {
    const wrapper = await mountChatComposer()

    expect(wrapper.find('[data-testid="chat-retry"]').exists()).toBe(false)
  })

  it('shows_model_warning_and_emits_open_model_settings_when_model_missing', async () => {
    const wrapper = await mountChatComposer({
      modelValue: '开始写第三卷第二章',
      currentModelName: '',
    })

    expect(wrapper.get('[data-testid="model-warning"]').text()).toContain('当前未选择模型')
    expect(wrapper.get('[data-testid="model-warning"]').classes()).toContain('theme-warning')
    expect(wrapper.get('[data-testid="current-model-value"]').text()).toContain('未选择模型')

    await wrapper.get('[data-testid="open-model-settings"]').trigger('click')

    expect(wrapper.emitted('open-model-settings')).toEqual([[]])
  })

  it('registers context compression without leaving the slash token in user text', async () => {
    const wrapper = await mountChatComposer({
      modelValue: '请用 /wri',
      skillCatalog: [
        { name: 'writer', description: '创作正文' },
        { name: 'checker', description: '检查一致性' },
      ],
      activeSkills: [],
    })
    const textarea = wrapper.get('[data-testid="chat-input"]')
    ;(textarea.element as HTMLTextAreaElement).setSelectionRange(7, 7)
    await textarea.trigger('click')

    expect(wrapper.find('[role="listbox"]').exists()).toBe(true)
    expect(wrapper.findAll('[role="option"]')).toHaveLength(1)
    expect(wrapper.get('[role="option"]').text()).toContain('压缩上下文')
    await wrapper.get('[role="option"]').trigger('mousedown')

    expect(wrapper.emitted('compress-context')).toEqual([[]])
    expect(String(wrapper.emitted('update:modelValue')?.at(-1)?.[0])).not.toContain('/')
  })

  it('shows live usage and emits persistent safety mode changes', async () => {
    const wrapper = await mountChatComposer({
      safetyMode: 'STANDARD',
      contextUsage: { usedTokens: 95_000, maxContextTokens: 100_000, usageRatio: 0.95 },
      queuedRequest: { requestId: '7', type: 'COMPRESS', status: 'PENDING' },
    })

    expect(wrapper.text()).toContain('上下文占用 95%')
    expect(wrapper.text()).toContain('压缩上下文')
    await wrapper.get('[aria-label="Agent 安全模式"]').setValue('AUTONOMOUS')

    expect(wrapper.emitted('update:safety-mode')).toEqual([['AUTONOMOUS']])
  })

  it('renders_active_skills_as_removable_tags', async () => {
    const wrapper = await mountChatComposer({
      activeSkills: ['writer'],
      skillCatalog: [{ name: 'writer', description: '创作正文' }],
    })

    const remove = wrapper.get('[aria-label="移除 writer"]')
    await remove.trigger('click')

    expect(wrapper.emitted('remove-skill')).toEqual([['writer']])
  })

  it('removes chapter ranges and accepts multi-chapter outline drops', async () => {
    const wrapper = await mountChatComposer({
      attachedChapterRanges: [{ key: '301:302:303', label: '第3章到第5章', chapterIds: ['301', '302', '303'] }],
    })

    await wrapper.get('[aria-label="移除 第3章到第5章"]').trigger('click')
    expect(wrapper.emitted('remove-chapters')).toEqual([[['301', '302', '303']]])

    const dataTransfer = {
      types: ['application/x-penmate-chat-chapters'],
      dropEffect: 'none',
      getData: () => JSON.stringify({ chapterIds: ['303', '304', '303'] }),
    }
    await wrapper.get('[data-testid="chat-composer"]').trigger('dragover', { dataTransfer })
    expect(wrapper.get('[data-testid="chat-composer"]').classes()).toContain('chapter-drag-over')
    await wrapper.get('[data-testid="chat-composer"]').trigger('drop', { dataTransfer })

    expect(wrapper.emitted('drop-chapters')).toEqual([[['303', '304']]])
  })
})
