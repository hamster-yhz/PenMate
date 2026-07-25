import { mount } from '@vue/test-utils'
import { defineComponent, type PropType } from 'vue'
import { describe, expect, it } from 'vitest'
import ChatMessageList from './ChatMessageList.vue'
import type { AgentRunAttempt } from '@/components/workbench/workbenchTypes'

type ChatMessage = {
  id: number
  role: 'user' | 'assistant' | 'system'
  text: string
  turnId?: string
  runId?: string
}

const ChatMessageItemStub = defineComponent({
  name: 'ChatMessageItem',
  emits: ['retry'],
  props: {
    msg: {
      type: Object as PropType<ChatMessage>,
      required: true,
    },
    canRetry: Boolean,
  },
  template: `
    <div data-testid="chat-message-item-stub">
      <span data-testid="chat-message-item-id">{{ msg.id }}</span>
      <button v-if="canRetry" data-testid="message-retry-stub" @click="$emit('retry')"></button>
    </div>
  `,
})

const mountChatMessageList = async (
  overrides: Partial<{
    messages: ChatMessage[]
    isGenerating: boolean
    streamingAssistantMsgId: number | null
    runAttempts: AgentRunAttempt[]
    canRetryRun: boolean
    isRetrying: boolean
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
  it('renders message items without editor mutation actions', async () => {
    const wrapper = await mountChatMessageList()

    expect(wrapper.find('.chat-messages').exists()).toBe(true)
    expect(wrapper.findAll('[data-testid="chat-message-item-stub"]')).toHaveLength(2)

    expect(wrapper.find('[data-testid="emit-merge"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="emit-replace"]').exists()).toBe(false)
  })

  it('renders retry outputs through one versioned attempt group', async () => {
    const wrapper = await mountChatMessageList({
      messages: [
        { id: 1, role: 'user', text: 'question', turnId: '10' },
        { id: 2, role: 'assistant', text: 'failed output', turnId: '10', runId: '20' },
        { id: 3, role: 'assistant', text: 'successful output', turnId: '10', runId: '21' },
      ],
    })
    await wrapper.setProps({
      runAttempts: [
        { runId: '20', turnId: '10', runStatus: 'FAILED', runPhase: 'failed', attemptCount: 1, latestSequence: 1, connectionState: 'closed', events: [] },
        { runId: '21', turnId: '10', runStatus: 'DONE', runPhase: 'completed', attemptCount: 2, latestSequence: 2, connectionState: 'closed', events: [] },
      ],
    })

    expect(wrapper.findAll('[data-testid="chat-message-item-stub"]')).toHaveLength(2)
    expect(wrapper.find('[data-testid="run-attempt-group"]').exists()).toBe(true)
  })

  it('offers_retry_only_on_the_user_message_for_the_latest_failed_turn', async () => {
    const wrapper = await mountChatMessageList({
      messages: [
        { id: 1, role: 'user', text: 'first', turnId: '10' },
        { id: 2, role: 'user', text: 'second', turnId: '11' },
      ],
      runAttempts: [
        { runId: '20', turnId: '10', runStatus: 'FAILED', runPhase: 'failed', attemptCount: 1, latestSequence: 1, connectionState: 'closed', events: [] },
        { runId: '21', turnId: '11', runStatus: 'FAILED', runPhase: 'failed', attemptCount: 1, latestSequence: 2, connectionState: 'closed', events: [] },
      ],
      canRetryRun: true,
    })

    expect(wrapper.findAll('[data-testid="message-retry-stub"]')).toHaveLength(1)
    await wrapper.get('[data-testid="message-retry-stub"]').trigger('click')
    expect(wrapper.emitted('retry')).toEqual([[]])
  })
})
