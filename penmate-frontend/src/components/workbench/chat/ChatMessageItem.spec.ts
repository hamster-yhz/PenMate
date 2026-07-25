import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ChatMessageItem from './ChatMessageItem.vue'

type ChatMessage = {
  id: number
  role: 'user' | 'assistant' | 'system'
  text: string
  approval?: {
    id: string
    message: string
    time: string
    toolCode?: string
    toolDisplayName?: string
    riskLevel?: number
    operationCode?: string
    preview?: Record<string, string>
    resolved: boolean
    resolvedAction?: 'approved' | 'rejected'
  }
}

const mountChatMessageItem = async (
  overrides: Partial<{
    msg: ChatMessage
    isGenerating: boolean
    streamingAssistantMsgId: number | null
    approvalBusy: boolean
    canRetry: boolean
    isRetrying: boolean
  }> = {},
) => {
  return mount(ChatMessageItem, {
    props: {
      msg: {
        id: 1,
        role: 'assistant',
        text: '你好\n世界',
      },
      isGenerating: false,
      streamingAssistantMsgId: null,
      approvalBusy: false,
      ...overrides,
    },
  })
}

describe('ChatMessageItem', () => {
  it('renders compact assistant markdown without editor mutation actions', async () => {
    const wrapper = await mountChatMessageItem({
      msg: {
        id: 7,
        role: 'assistant',
        text: '**你好**\n世界',
      },
    })

    expect(wrapper.get('[data-testid="message-text"] strong').text()).toBe('你好')
    expect(wrapper.find('[data-testid="merge-to-editor"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="replace-selected"]').exists()).toBe(false)
  })

  it('renders_untrusted_message_content_as_text', async () => {
    const wrapper = await mountChatMessageItem({
      msg: {
        id: 9,
        role: 'assistant',
        text: '<img src=x onerror="window.__xss=true">',
      },
    })

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.get('[data-testid="message-text"]').text()).toContain('<img')
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

  it('confirms_before_reexecuting_a_failed_user_request', async () => {
    const wrapper = await mountChatMessageItem({
      msg: { id: 10, role: 'user', text: 'Rewrite chapter three' },
      canRetry: true,
    })

    const retry = wrapper.get('[data-testid="message-retry"]')
    expect(retry.attributes('title')).toBe('重新执行原请求')
    expect(wrapper.find('[data-testid="confirm-message-retry"]').exists()).toBe(false)

    await retry.trigger('click')
    expect(wrapper.get('[role="alertdialog"]').text()).toContain('不会继承上一次的步骤记录')
    await wrapper.get('[data-testid="confirm-message-retry"]').trigger('click')

    expect(wrapper.emitted('retry')).toEqual([[]])
  })
  it('passes_extended_approval_metadata_to_approval_card', async () => {
    const wrapper = await mountChatMessageItem({
      msg: {
        id: 12,
        role: 'assistant',
        text: '待审批',
        approval: {
          id: '42',
          message: '检测到待审批工具变更（书籍 CRUD）',
          time: '2026-05-05 12:00:00',
          toolCode: 'book_crud',
          toolDisplayName: '书籍 CRUD',
          riskLevel: 2,
          operationCode: 'delete',
          resolved: false,
        },
      },
    })

    const approvalCard = wrapper.getComponent({ name: 'ApprovalCard' })
    expect(approvalCard.props('card')).toMatchObject({
      toolCode: 'book_crud',
      toolDisplayName: '书籍 CRUD',
      riskLevel: 2,
      operationCode: 'delete',
    })
  })

  it('opens_the_story_bible_node_from_the_approval_preview', async () => {
    const wrapper = await mountChatMessageItem({
      msg: {
        id: 13,
        role: 'assistant',
        text: '',
        approval: {
          id: '43',
          message: 'pending',
          time: '2026-07-17 12:00:00',
          toolCode: 'story_bible_node_write',
          preview: { operation: 'update', nodeId: '71' },
          resolved: false,
        },
      },
    })

    await wrapper.get('.btn-open-bible').trigger('click')

    expect(wrapper.emitted('open-story-bible')).toEqual([['71']])
  })
})
