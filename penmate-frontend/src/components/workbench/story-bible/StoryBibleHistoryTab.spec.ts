import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import StoryBibleHistoryTab from './StoryBibleHistoryTab.vue'

describe('StoryBibleHistoryTab', () => {
  it('keeps archived changes visible and disables undo', () => {
    const wrapper = mount(StoryBibleHistoryTab, {
      props: {
        projectId: '101',
        currentRevision: 50,
        history: [
          {
            changesetId: '499',
            storyBibleId: '11',
            contentRevision: 48,
            actorType: 'AGENT',
            sourceRunId: '799',
            changeSummary: 'Archived update',
            createdAt: new Date().toISOString(),
            archivedAt: new Date().toISOString(),
          },
        ],
      },
    })

    expect(wrapper.text()).toContain('Archived update')
    const undoButton = wrapper.find('.actions button[disabled]')
    expect(undoButton.exists()).toBe(true)
    expect(undoButton.attributes('title')).toBeTruthy()
  })
})
