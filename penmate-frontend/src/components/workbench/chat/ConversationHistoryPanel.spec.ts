import { mount } from '@vue/test-utils'
import { defineComponent, type Component } from 'vue'
import { describe, expect, it } from 'vitest'

const MissingConversationHistoryPanel = defineComponent({
  name: 'MissingConversationHistoryPanel',
  template: '<div data-testid="missing-conversation-history-panel"></div>',
})

type ConversationItem = {
  conversationId: string
  title: string
  updatedAt: string
}

const loadConversationHistoryPanel = async (): Promise<Component> => {
  try {
    const componentPath = './ConversationHistoryPanel.vue'
    return (await import(/* @vite-ignore */ componentPath)).default
  } catch {
    return MissingConversationHistoryPanel
  }
}

const mountConversationHistoryPanel = async (
  overrides: Partial<{
    visible: boolean
    loading: boolean
    conversations: ConversationItem[]
    currentConversationId: string | null
  }> = {},
) => {
  const ConversationHistoryPanel = await loadConversationHistoryPanel()

  return mount(ConversationHistoryPanel, {
    props: {
      visible: true,
      loading: false,
      conversations: [],
      currentConversationId: null,
      ...overrides,
    },
  })
}

describe('ConversationHistoryPanel', () => {
  it('shows_loading_and_empty_states', async () => {
    const loadingWrapper = await mountConversationHistoryPanel({
      visible: true,
      loading: true,
      conversations: [],
    })

    expect(loadingWrapper.get('[data-testid="conversation-loading"]').text()).toContain('加载中')

    const emptyWrapper = await mountConversationHistoryPanel({
      visible: true,
      loading: false,
      conversations: [],
    })

    expect(emptyWrapper.get('[data-testid="conversation-empty"]').text()).toContain('暂无历史会话')
  })

  it('renders_conversations_and_emits_selection', async () => {
    const wrapper = await mountConversationHistoryPanel({
      conversations: [
        { conversationId: '81', title: '第一轮', updatedAt: '2026-04-26 20:00:00' },
        { conversationId: '82', title: '', updatedAt: '2026-04-26 20:02:00' },
      ],
      currentConversationId: '82',
    })

    const items = wrapper.findAll('[data-testid="conversation-item"]')
    expect(items).toHaveLength(2)
    expect(items[1].classes()).toContain('active')
    expect(wrapper.text()).toContain('会话#82')

    await items[0].trigger('click')

    expect(wrapper.emitted('select-conversation')).toEqual([['81']])
  })

  it('renders_back_button_and_emits_close_for_full_panel_exit', async () => {
    const wrapper = await mountConversationHistoryPanel({
      visible: true,
      conversations: [
        { conversationId: '81', title: '第一轮', updatedAt: '2026-04-26 20:00:00' },
      ],
    })

    expect(wrapper.get('[data-testid="conversation-back"]').text()).toContain('返回')

    await wrapper.get('[data-testid="conversation-back"]').trigger('click')

    expect(wrapper.emitted('close')).toEqual([[]])
  })
})
