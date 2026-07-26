import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AiEditActivity from './AiEditActivity.vue'

const operations = [
  {
    operationId: 'undo-2',
    runId: 'run-2',
    chapterId: 'chapter-1',
    chapterTitle: '第一章',
    status: 'AVAILABLE',
    sequenceNo: 2,
  },
  {
    operationId: 'undo-1',
    runId: 'run-1',
    chapterId: 'chapter-1',
    chapterTitle: '第一章',
    status: 'AVAILABLE',
    sequenceNo: 1,
  },
]

describe('AiEditActivity', () => {
  it('only enables the newest undo in each chapter stack', () => {
    const wrapper = mount(AiEditActivity, { props: { operations } })
    const undoButtons = wrapper.findAll('[aria-label="撤回 第一章 的 AI 修改"]')

    expect(undoButtons[0]?.attributes('disabled')).toBeUndefined()
    expect(undoButtons[1]?.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('等待上一条')
  })

  it('emits single and dismiss-all actions from the close buttons', async () => {
    const wrapper = mount(AiEditActivity, { props: { operations } })

    await wrapper.findAll('[aria-label="放弃 第一章 的撤回记录"]')[0]!.trigger('click')
    await wrapper.get('[aria-label="放弃全部 AI 撤回记录"]').trigger('click')

    expect(wrapper.emitted('dismiss')?.[0]).toEqual(['undo-2'])
    expect(wrapper.emitted('dismiss-all')).toHaveLength(1)
  })
})
