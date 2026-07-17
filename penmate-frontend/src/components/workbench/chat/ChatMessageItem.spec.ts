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
  }> = {},
) => {
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
          toolCode: 'story_bible_update',
          preview: { kind: 'update_node', nodeId: '71' },
          resolved: false,
        },
      },
    })

    await wrapper.get('.btn-open-bible').trigger('click')

    expect(wrapper.emitted('open-story-bible')).toEqual([['71']])
  })
})
