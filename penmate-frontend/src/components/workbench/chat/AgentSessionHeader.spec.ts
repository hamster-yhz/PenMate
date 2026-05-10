import { mount } from '@vue/test-utils'
import { defineComponent, type Component } from 'vue'
import { describe, expect, it } from 'vitest'

const MissingAgentSessionHeader = defineComponent({
  name: 'MissingAgentSessionHeader',
  template: '<div data-testid="missing-agent-session-header"></div>',
})

const loadAgentSessionHeader = async (): Promise<Component> => {
  try {
    const componentPath = './AgentSessionHeader.vue'
    return (await import(/* @vite-ignore */ componentPath)).default
  } catch {
    return MissingAgentSessionHeader
  }
}

const mountAgentSessionHeader = async (
  overrides: Partial<{
    currentModelName: string
    generationStatusText: string
    agentStatusDetailText: string
    isGenerating: boolean
    generationPhase: 'idle' | 'preparing' | 'streaming' | 'waiting_approval' | 'failed'
    boundStyleName: string
  }> = {},
) => {
  const AgentSessionHeader = await loadAgentSessionHeader()

  return mount(AgentSessionHeader, {
    props: {
      currentModelName: '',
      generationStatusText: '就绪',
      agentStatusDetailText: '',
      isGenerating: false,
      generationPhase: 'idle',
      ...overrides,
    },
  })
}

describe('AgentSessionHeader', () => {
  it('shows_bound_style_from_session_recovery_snapshot', async () => {
    const wrapper = await mountAgentSessionHeader({
      currentModelName: 'DeepSeek-R1',
      generationStatusText: '等待审批',
      isGenerating: false,
      generationPhase: 'waiting_approval',
      boundStyleName: '冷峻悬疑',
    })

    expect(wrapper.text()).toContain('冷峻悬疑')
  })

  it('renders_new_session_entry_and_emits_create_session', async () => {
    const wrapper = await mountAgentSessionHeader({
      currentModelName: 'DeepSeek-R1',
      generationStatusText: '就绪',
      isGenerating: false,
      generationPhase: 'idle',
    })

    await wrapper.get('[data-testid="create-session"]').trigger('click')

    expect(wrapper.emitted('create-session')).toEqual([[]])
  })

  it('renders_model_fallback_status_and_emits_toggle_history', async () => {
    const wrapper = await mountAgentSessionHeader({
      currentModelName: '',
      generationStatusText: '等待审批',
      isGenerating: true,
      generationPhase: 'failed',
    })

    expect(wrapper.get('[data-testid="agent-title"]').text()).toContain('AI会话')
    expect(wrapper.get('[data-testid="current-model"]').text()).toContain('未选择模型')
    expect(wrapper.get('[data-testid="agent-status"]').text()).toContain('等待审批')
    expect(wrapper.get('[data-testid="agent-status"]').classes()).toContain('busy')
    expect(wrapper.get('[data-testid="agent-status"]').classes()).toContain('failed')

    await wrapper.get('[data-testid="toggle-history"]').trigger('click')

    expect(wrapper.emitted('toggle-history')).toEqual([[]])
  })

  it('keeps_waiting_approval_visible_without_busy_or_failed_state', async () => {
    const wrapper = await mountAgentSessionHeader({
      currentModelName: 'DeepSeek-R1',
      generationStatusText: '等待审批',
      isGenerating: false,
      generationPhase: 'waiting_approval',
    })

    expect(wrapper.get('[data-testid="current-model"]').text()).toContain('DeepSeek-R1')
    expect(wrapper.get('[data-testid="agent-status"]').text()).toContain('等待审批')
    expect(wrapper.get('[data-testid="agent-status"]').classes()).not.toContain('busy')
    expect(wrapper.get('[data-testid="agent-status"]').classes()).not.toContain('failed')
  })

  it('renders_agent_status_detail_under_primary_status', async () => {
    const wrapper = await mountAgentSessionHeader({
      currentModelName: 'DeepSeek-R1',
      generationStatusText: '生成中 · running',
      agentStatusDetailText: '正在调用 RAG 查询工具',
      isGenerating: true,
      generationPhase: 'streaming',
    })

    expect(wrapper.text()).toContain('生成中 · running')
    expect(wrapper.text()).toContain('正在调用 RAG 查询工具')
  })
})
