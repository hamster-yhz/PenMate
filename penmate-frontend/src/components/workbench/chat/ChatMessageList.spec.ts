import { mount } from '@vue/test-utils'
import { defineComponent, type Component, type PropType } from 'vue'
import { describe, expect, it } from 'vitest'

type ChatMessage = {
  id: number
  role: 'user' | 'assistant' | 'system'
  text: string
}

const MissingChatMessageList = defineComponent({
  name: 'MissingChatMessageList',
  template: '<div data-testid="missing-chat-message-list"></div>',
})

const ChatMessageItemStub = defineComponent({
  name: 'ChatMessageItem',
  props: {
    msg: {
      type: Object as PropType<ChatMessage>,
      required: true,
    },
  },
  emits: ['merge-to-editor', 'replace-selected'],
  template: `
    <div data-testid="chat-message-item-stub">
      <span data-testid="chat-message-item-id">{{ msg.id }}</span>
      <button data-testid="emit-merge" @click="$emit('merge-to-editor', msg)">merge</button>
      <button data-testid="emit-replace" @click="$emit('replace-selected', msg)">replace</button>
    </div>
  `,
})

const loadChatMessageList = async (): Promise<Component> => {
  try {
    const componentPath = './ChatMessageList.vue'
    return (await import(/* @vite-ignore */ componentPath)).default
  } catch {
    return MissingChatMessageList
  }
}

const mountChatMessageList = async (
  overrides: Partial<{
    messages: ChatMessage[]
    isGenerating: boolean
    streamingAssistantMsgId: number | null
  }> = {},
) => {
  const ChatMessageList = await loadChatMessageList()

  return mount(ChatMessageList, {
    props: {
      messages: [
        { id: 1, role: 'user', text: '继续写第三章' },
        { id: 2, role: 'assistant', text: '收到' },
      ],
      isGenerating: false,
      streamingAssistantMsgId: null,
      ...overrides,
    },
    global: {
      stubs: {
        ChatMessageItem: ChatMessageItemStub,
      },
    },
  })
}

describe('ChatMessageList', () => {
  it('renders_message_items_and_re_emits_item_actions', async () => {
    const wrapper = await mountChatMessageList()

    expect(wrapper.findAll('[data-testid="chat-message-item-stub"]')).toHaveLength(2)

    const mergeButtons = wrapper.findAll('[data-testid="emit-merge"]')
    const replaceButtons = wrapper.findAll('[data-testid="emit-replace"]')

    await mergeButtons[1].trigger('click')
    await replaceButtons[1].trigger('click')

    expect(wrapper.emitted('merge-to-editor')).toEqual([[{ id: 2, role: 'assistant', text: '收到' }]])
    expect(wrapper.emitted('replace-selected')).toEqual([[{ id: 2, role: 'assistant', text: '收到' }]])
  })
})
