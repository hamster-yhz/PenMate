import { mount } from '@vue/test-utils'
import { defineComponent, type Component } from 'vue'
import { describe, expect, it } from 'vitest'

const MissingChatMessageItem = defineComponent({
  name: 'MissingChatMessageItem',
  template: '<div data-testid="missing-chat-message-item"></div>',
})

type ChatMessage = {
  id: number
  role: 'user' | 'assistant' | 'system'
  text: string
  approval?: {
    id: string
    message: string
    time: string
    resolved: boolean
    resolvedAction?: 'approved' | 'rejected'
  }
}

const loadChatMessageItem = async (): Promise<Component> => {
  try {
    const componentPath = './ChatMessageItem.vue'
    return (await import(/* @vite-ignore */ componentPath)).default
  } catch {
    return MissingChatMessageItem
  }
}

const mountChatMessageItem = async (
  overrides: Partial<{
    msg: ChatMessage
    isGenerating: boolean
    streamingAssistantMsgId: number | null
    approvalBusy: boolean
  }> = {},
) => {
  const ChatMessageItem = await loadChatMessageItem()

  return mount(ChatMessageItem, {
    props: {
      msg: {
        id: 1,
        role: 'assistant',
        text: '你好<br/>世界',
      },
      isGenerating: false,
      streamingAssistantMsgId: null,
      approvalBusy: false,
      ...overrides,
    },
  })
}

describe('ChatMessageItem', () => {
  it('renders_assistant_actions_and_emits_editor_commands', async () => {
    const wrapper = await mountChatMessageItem({
      msg: {
        id: 7,
        role: 'assistant',
        text: '你好<br/>世界',
      },
    })

    expect(wrapper.get('[data-testid="message-html"]').html()).toContain('你好<br>世界')

    await wrapper.get('[data-testid="merge-to-editor"]').trigger('click')
    await wrapper.get('[data-testid="replace-selected"]').trigger('click')

    expect(wrapper.emitted('merge-to-editor')).toEqual([[{ id: 7, role: 'assistant', text: '你好<br/>世界' }]])
    expect(wrapper.emitted('replace-selected')).toEqual([[{ id: 7, role: 'assistant', text: '你好<br/>世界' }]])
  })

  it('shows_inline_typing_when_streaming_assistant_message_is_empty', async () => {
    const wrapper = await mountChatMessageItem({
      msg: {
        id: 8,
        role: 'assistant',
        text: '',
      },
      isGenerating: true,
      streamingAssistantMsgId: 8,
    })

    expect(wrapper.get('[data-testid="inline-typing"]').text()).toContain('AI正在创作中')
  })
})
