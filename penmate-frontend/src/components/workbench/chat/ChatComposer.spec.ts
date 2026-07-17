import { mount } from '@vue/test-utils'
import { defineComponent, type Component } from 'vue'
import { describe, expect, it } from 'vitest'

const MissingChatComposer = defineComponent({
  name: 'MissingChatComposer',
  template: '<div data-testid="missing-chat-composer"></div>',
})

const loadChatComposer = async (): Promise<Component> => {
  try {
    const componentPath = './ChatComposer.vue'
    return (await import(/* @vite-ignore */ componentPath)).default
  } catch {
    return MissingChatComposer
  }
}

const mountChatComposer = async (
  overrides: Partial<{
    modelValue: string
    isGenerating: boolean
    canCancelRun: boolean
    isCancelling: boolean
    currentModelName: string
    activePlugins: string[]
  }> = {},
) => {
  const ChatComposer = await loadChatComposer()

  return mount(ChatComposer, {
    props: {
      modelValue: '',
      isGenerating: false,
      currentModelName: 'DeepSeek-R1',
      activePlugins: [],
      ...overrides,
    },
  })
}

describe('ChatComposer', () => {
  it('disables_send_when_input_blank_or_generating', async () => {
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
    })

    expect((busyWrapper.get('[data-testid="chat-send"]').element as HTMLButtonElement).disabled).toBe(true)

    const readyWrapper = await mountChatComposer({
      modelValue: '继续写第三章',
      isGenerating: false,
    })

    expect((readyWrapper.get('[data-testid="chat-send"]').element as HTMLButtonElement).disabled).toBe(false)
  })

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

    expect(wrapper.emitted('update:modelValue')).toEqual([
      ['请补充主角与反派对话'],
    ])
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
})
