import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import StoryBibleRoutingSettings from './StoryBibleRoutingSettings.vue'

describe('StoryBibleRoutingSettings', () => {
  it('preserves the approved routing option wording', () => {
    const wrapper = mount(StoryBibleRoutingSettings, {
      props: {
        projectPreference: { mode: 'RETRIEVAL_THEN_LLM' },
      },
    })
    expect(wrapper.text()).toContain('规则匹配 + Embedding')
    expect(wrapper.text()).toContain('直接使用 LLM')
    expect(wrapper.text()).toContain('规则匹配 + Embedding，LLM 兜底')
    expect(wrapper.text()).toContain('当前项目')
    expect(wrapper.text()).not.toContain('Session')
  })
})
