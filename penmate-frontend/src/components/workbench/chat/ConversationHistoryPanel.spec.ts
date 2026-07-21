import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ConversationHistoryPanel from './ConversationHistoryPanel.vue'

describe('ConversationHistoryPanel', () => {
  it('searches, renames, deletes and restores sessions', async () => {
    const wrapper = mount(ConversationHistoryPanel, {
      props: {
        visible: true,
        conversations: [{ conversationId: '1', title: '第一章讨论', updatedAt: new Date().toISOString(), lastRunStatus: 'DONE' }],
        deletedConversations: [{ conversationId: '2', title: '旧会话', updatedAt: new Date().toISOString(), deletedAt: new Date().toISOString() }],
      },
    })

    await wrapper.get('[aria-label="重命名会话"]').trigger('click')
    await wrapper.get('.rename-row input').setValue('新的标题')
    await wrapper.get('.rename-row input').trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('rename')?.[0]).toEqual([{ conversationId: '1', title: '新的标题' }])

    await wrapper.get('[aria-label="删除会话"]').trigger('click')
    await wrapper.findAll('.delete-confirm button')[0]!.trigger('click')
    expect(wrapper.emitted('delete')?.[0]).toEqual(['1'])

    await wrapper.findAll('.history-tabs button')[1]!.trigger('click')
    expect(wrapper.emitted('load-deleted')).toBeTruthy()
    await wrapper.get('[aria-label="恢复会话"]').trigger('click')
    expect(wrapper.emitted('restore')?.[0]).toEqual(['2'])
  })
})
