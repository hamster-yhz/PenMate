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

describe('WorkbenchRightPanel runtime cards', () => {
  it('renders_runtime_cards_from_presenter_view_model_without_replacing_existing_chat_layout', () => {
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
        runtimeStatusCard: {
          title: '运行状态',
          badgeText: '正在整理待办',
          description: '正在整理待办',
          nextActionText: 'review_todo_plan',
          failureReasonText: '',
        },
        toolCallCard: {
          title: 'Todo 规划',
          toolCode: 'todo_planner',
          statusText: '进行中',
          argumentsPreview: '{"planningMode":"FOLLOW_UP_MODIFICATION"}',
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

    expect(wrapper.get('[data-testid="runtime-status-card"]').text()).toContain('正在整理待办')
    expect(wrapper.get('[data-testid="runtime-status-card"]').text()).toContain('review_todo_plan')
    expect(wrapper.get('[data-testid="tool-call-status-card"]').text()).toContain('Todo 规划')
    expect(wrapper.get('[data-testid="tool-call-status-card"]').text()).toContain('todo_planner')
    expect(wrapper.get('[data-testid="todo-plan-card"]').text()).toContain('第三章修订待办')
    expect(wrapper.get('[data-testid="todo-plan-card"]').text()).toContain('2 项待办')
    expect(wrapper.get('[data-testid="story-bible-approval-card"]').text()).toContain('故事圣经更新待确认')
    expect(wrapper.get('[data-testid="story-bible-approval-card"]').text()).toContain('maid.secret_order')
  })

  it('renders_failure_guidance_when_runtime_execution_fails', () => {
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
        runtimeStatusCard: {
          title: '运行状态',
          badgeText: '执行失败',
          description: '执行失败',
          nextActionText: 'retry_generation',
          failureReasonText: '质量审查超时',
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

    expect(wrapper.get('[data-testid="runtime-status-card"]').text()).toContain('执行失败')
    expect(wrapper.get('[data-testid="runtime-status-card"]').text()).toContain('质量审查超时')
    expect(wrapper.get('[data-testid="runtime-status-card"]').text()).toContain('retry_generation')
  })
})
