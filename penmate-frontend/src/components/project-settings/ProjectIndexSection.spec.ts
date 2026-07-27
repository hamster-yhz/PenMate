import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ProjectIndexSection from './ProjectIndexSection.vue'

const props = {
  ai: {
    creativeModelConfigId: '',
    routerModelConfigId: '',
    embeddingModelConfigId: '13',
    ragEnabled: true,
    storyBibleRoutingMode: 'LLM_SELECTOR' as const,
  },
  index: {
    status: 'BUILDING',
    lastCompletedAt: '',
    lastErrorMessage: '',
    activeIndexBuildId: '',
    rebuildJobId: '501',
    progressCurrent: 3,
    progressTotal: 10,
    progressMessage: '正在向量化正文',
  },
  embeddingModels: [
    { id: '13', label: '中文向量', modelName: 'embed', type: 'EMBEDDING' as const, scope: 'USER' as const },
  ],
  canRebuild: true,
  rebuilding: true,
  cancelling: false,
  error: '',
  success: '',
}

describe('ProjectIndexSection', () => {
  it('shows determinate progress and exposes the stop action while rebuilding', async () => {
    const wrapper = mount(ProjectIndexSection, { props })

    const progress = wrapper.get('progress')
    expect(progress.attributes('value')).toBe('3')
    expect(progress.attributes('max')).toBe('10')
    expect(wrapper.text()).toContain('30%')
    expect(wrapper.text()).toContain('停止重建')

    await wrapper.get('.stop-rebuild-button').trigger('click')
    expect(wrapper.emitted('stop')).toHaveLength(1)
  })

  it('shows indeterminate progress before the total is known', () => {
    const wrapper = mount(ProjectIndexSection, {
      props: {
        ...props,
        index: { ...props.index, status: 'QUEUED', progressCurrent: 0, progressTotal: 0 },
      },
    })

    expect(wrapper.get('progress').attributes('value')).toBeUndefined()
    expect(wrapper.text()).toContain('等待任务开始')
  })
})
