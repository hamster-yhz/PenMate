import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import DeleteBookDialog from './DeleteBookDialog.vue'

const buildBook = () => ({
  id: 'book-101',
  title: '天渊行',
  description: '少年执笔入天渊。',
  genre: '仙侠',
  tags: ['热血'],
  wordCount: 128000,
  chapterCount: 36,
  updatedAt: '刚刚',
  coverGradient: 'linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%)',
})

const mountDialog = (deleting = false) =>
  mount(DeleteBookDialog, {
    props: {
      visible: true,
      deleting,
      book: buildBook(),
    },
  })

describe('DeleteBookDialog', () => {
  it('prevents_close_and_repeat_confirm_while_deleting', async () => {
    const wrapper = mountDialog(true)

    expect(wrapper.text()).toContain('删除中...')

    await wrapper.get('.modal-overlay').trigger('click')
    await wrapper.get('.btn-cancel').trigger('click')
    await wrapper.get('.btn-confirm').trigger('click')

    expect(wrapper.emitted('update:visible')).toBeUndefined()
    expect(wrapper.emitted('confirm')).toBeUndefined()
  })
})
