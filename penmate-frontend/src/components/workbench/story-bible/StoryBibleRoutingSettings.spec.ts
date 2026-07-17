import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import StoryBibleRoutingSettings from './StoryBibleRoutingSettings.vue'

describe('StoryBibleRoutingSettings', () => {
  it('preserves the approved routing option wording', () => {
    const wrapper = mount(StoryBibleRoutingSettings, {
      props: {
        userPreference: { mode: 'RETRIEVAL_THEN_LLM', routerModelConfigRevision: 0, inherited: false },
        sessionPreference: { mode: 'RETRIEVAL_THEN_LLM', routerModelConfigRevision: 0, inherited: true },
      },
    })
    expect(wrapper.text()).toContain('规则匹配 + Embedding')
    expect(wrapper.text()).toContain('直接使用 LLM')
    expect(wrapper.text()).toContain('规则匹配 + Embedding，LLM 兜底')
  })
})
