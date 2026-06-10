import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { describe, expect, it } from 'vitest'
import WorkbenchRightPanel from './WorkbenchRightPanel.vue'

const AgentSessionHeaderStub = defineComponent({
  name: 'AgentSessionHeader',
  setup() {
    return () => h('div', { 'data-testid': 'agent-session-header-stub' })
  },
})

const ConversationHistoryPanelStub = defineComponent({
  name: 'ConversationHistoryPanel',
  setup() {
    return () => h('div', { 'data-testid': 'conversation-history-panel-stub' })
  },
})

const ChatMessageListStub = defineComponent({
  name: 'ChatMessageList',
  setup() {
    return () => h('div', { 'data-testid': 'chat-message-list-stub' })
  },
})

const ChatComposerStub = defineComponent({
  name: 'ChatComposer',
  setup() {
    return () => h('div', { 'data-testid': 'chat-composer-stub' })
  },
})

describe('WorkbenchRightPanel no-standalone-runtime-cards contract', () => {
  it('does_not_render_runtime_cards_even_when_runtime_card_props_are_present', () => {
    const wrapper = mount(WorkbenchRightPanel, {
      props: {
        collapsed: false,
        currentModelName: 'DeepSeek-R1',
        generationStatusText: '正在整理待办',
        agentStatusDetailText: '等待确认 Todo 规划',
        isGenerating: false,
        generationPhase: 'waiting_approval',
        boundStyleName: '冷峻悬疑',
        showConversationPanel: false,
        conversationLoading: false,
        conversationList: [],
        currentConversationId: '77',
        bindChatContainer: () => undefined,
        messages: [],
        streamingAssistantMsgId: null,
        isApprovalBusy: () => false,
        chatInput: '',
        activePlugins: ['outline.search'],
        toolCallCard: {
          title: 'Todo 规划',
          toolCode: 'todo_crud',
          statusText: '进行中',
          argumentsPreview: '{"operation":"create"}',
          outputPreview: '{"planTitle":"第三章修订待办"}',
          errorMessage: '',
        },
        todoPlanCard: {
          title: '第三章修订待办',
          itemCountText: '2 项待办',
          nextActionText: 'apply_todo_plan',
          items: [
            { title: '修正文脉络跳跃', statusText: 'pending', priorityText: 'HIGH' },
            { title: '补充侍从知晓密令的设定', statusText: 'pending', priorityText: 'MEDIUM' },
          ],
        },
        storyBibleApprovalCard: {
          title: '故事圣经更新待确认',
          proposalSummary: '建议补充侍从知晓密令的设定',
          entryKeys: ['maid.secret_order'],
          nextActionText: 'await_approval',
        },
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

    expect(wrapper.find('[data-testid="agent-session-header-stub"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="chat-message-list-stub"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="chat-composer-stub"]').exists()).toBe(true)

    expect(wrapper.find('[data-testid="runtime-status-card"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="tool-call-status-card"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="todo-plan-card"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="story-bible-approval-card"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('Todo 规划')
    expect(wrapper.text()).not.toContain('第三章修订待办')
    expect(wrapper.text()).not.toContain('故事圣经更新待确认')
  })

  it('keeps_failure_runtime_status_out_of_standalone_card_rendering', () => {
    const wrapper = mount(WorkbenchRightPanel, {
      props: {
        collapsed: false,
        currentModelName: 'DeepSeek-R1',
        generationStatusText: '执行失败',
        agentStatusDetailText: '质量审查超时',
        isGenerating: false,
        generationPhase: 'failed',
        boundStyleName: '冷峻悬疑',
        showConversationPanel: false,
        conversationLoading: false,
        conversationList: [],
        currentConversationId: '77',
        bindChatContainer: () => undefined,
        messages: [],
        streamingAssistantMsgId: null,
        isApprovalBusy: () => false,
        chatInput: '',
        activePlugins: ['quality_review'],
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

    expect(wrapper.find('[data-testid="runtime-status-card"]').exists()).toBe(false)
  })
})
