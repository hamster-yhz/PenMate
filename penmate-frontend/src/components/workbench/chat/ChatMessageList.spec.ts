import { mount } from '@vue/test-utils'
import { defineComponent, type PropType } from 'vue'
import { describe, expect, it } from 'vitest'
import ChatMessageList from './ChatMessageList.vue'

type ChatMessage = {
  id: number
  role: 'user' | 'assistant' | 'system'
  text: string
}

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

const mountChatMessageList = async (
  overrides: Partial<{
    messages: ChatMessage[]
    isGenerating: boolean
    streamingAssistantMsgId: number | null
  }> = {},
) => {
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

    expect(wrapper.find('.chat-messages').exists()).toBe(true)
    expect(wrapper.findAll('[data-testid="chat-message-item-stub"]')).toHaveLength(2)

    const mergeButtons = wrapper.findAll('[data-testid="emit-merge"]')
    const replaceButtons = wrapper.findAll('[data-testid="emit-replace"]')

    await mergeButtons[1].trigger('click')
    await replaceButtons[1].trigger('click')

    expect(wrapper.emitted('merge-to-editor')).toEqual([[{ id: 2, role: 'assistant', text: '收到' }]])
    expect(wrapper.emitted('replace-selected')).toEqual([[{ id: 2, role: 'assistant', text: '收到' }]])
  })
})
