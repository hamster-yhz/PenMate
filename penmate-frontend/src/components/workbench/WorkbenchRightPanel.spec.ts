import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { describe, expect, it } from 'vitest'
import WorkbenchRightPanel from './WorkbenchRightPanel.vue'

const AgentSessionHeaderStub = defineComponent({
  name: 'AgentSessionHeader',
  emits: ['toggle-history'],
  setup(_, { emit }) {
    return () => h('button', { 'data-testid': 'toggle-history', onClick: () => emit('toggle-history') })
  },
})

const ConversationHistoryPanelStub = defineComponent({
  name: 'ConversationHistoryPanel',
  emits: ['select-conversation'],
  setup(_, { emit }) {
    return () => h('button', { 'data-testid': 'select-conversation', onClick: () => emit('select-conversation', 77) })
  },
})

const ChatMessageListStub = defineComponent({
  name: 'ChatMessageList',
  emits: ['merge-to-editor', 'approve'],
  setup(_, { emit }) {
    return () =>
      h('div', [
        h('button', { 'data-testid': 'merge-to-editor', onClick: () => emit('merge-to-editor', { messageId: 9 }) }),
        h('button', { 'data-testid': 'approve-message', onClick: () => emit('approve', 'approval-9') }),
      ])
  },
})

const ChatComposerStub = defineComponent({
  name: 'ChatComposer',
  emits: ['update:model-value', 'send', 'open-model-settings'],
  setup(_, { emit }) {
    return () =>
      h('div', [
        h('button', { 'data-testid': 'chat-input-update', onClick: () => emit('update:model-value', '继续生成') }),
        h('button', { 'data-testid': 'chat-send', onClick: () => emit('send') }),
        h('button', { 'data-testid': 'open-model-settings', onClick: () => emit('open-model-settings') }),
      ])
  },
})

describe('WorkbenchRightPanel', () => {
  it('forwards_chat_shell_events_and_accepts_nullable_chat_ref', async () => {
    const wrapper = mount(WorkbenchRightPanel, {
      props: {
        collapsed: false,
        currentModelName: 'DeepSeek-R1',
        generationStatusText: '等待审批',
        isGenerating: false,
        generationPhase: 'waiting_approval',
        showConversationPanel: true,
        conversationLoading: false,
        conversationList: [{ conversationId: 77, title: '会话1', updatedAt: '2026-04-27 10:00:00' }],
        currentConversationId: 77,
        bindChatContainer: () => undefined,
        messages: [],
        streamingAssistantMsgId: null,
        isApprovalBusy: () => false,
        chatInput: '',
        activePlugins: ['插件A'],
      },
      global: {
        stubs: {
          AgentSessionHeader: AgentSessionHeaderStub,
          ConversationHistoryPanel: ConversationHistoryPanelStub,
          ChatMessageList: ChatMessageListStub,
          ChatComposer: ChatComposerStub,
        },
      },
    })

    await wrapper.get('.panel-toggle').trigger('click')
    await wrapper.get('[data-testid="toggle-history"]').trigger('click')
    await wrapper.get('[data-testid="select-conversation"]').trigger('click')
    await wrapper.get('[data-testid="merge-to-editor"]').trigger('click')
    await wrapper.get('[data-testid="approve-message"]').trigger('click')
    await wrapper.get('[data-testid="chat-input-update"]').trigger('click')
    await wrapper.get('[data-testid="chat-send"]').trigger('click')
    await wrapper.get('[data-testid="open-model-settings"]').trigger('click')

    expect(wrapper.emitted('toggle-collapse')).toEqual([[]])
    expect(wrapper.emitted('toggle-history')).toEqual([[]])
    expect(wrapper.emitted('select-conversation')).toEqual([[77]])
    expect(wrapper.emitted('merge-to-editor')).toEqual([[{ messageId: 9 }]])
    expect(wrapper.emitted('approve')).toEqual([['approval-9']])
    expect(wrapper.emitted('update:chat-input')).toEqual([['继续生成']])
    expect(wrapper.emitted('send')).toEqual([[]])
    expect(wrapper.emitted('open-model-settings')).toEqual([[]])
  })
})
