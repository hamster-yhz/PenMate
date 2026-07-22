import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { describe, expect, it } from 'vitest'
import PermanentlyDeleteBookDialog from './PermanentlyDeleteBookDialog.vue'
import type { BookshelfBook } from '@/composables/bookshelf/useBookshelf'

const ModalStub = defineComponent({ template: '<section><slot /></section>' })
const book: BookshelfBook = {
  id: 'book-101', title: '天渊行', description: '', genre: '仙侠', tags: [], wordCount: 0,
  chapterCount: 1, updatedAt: '', updatedAtValue: 0, coverTone: 'forest',
}

describe('PermanentlyDeleteBookDialog', () => {
  it('requires_the_complete_project_title_before_emitting_confirmation', async () => {
    const wrapper = mount(PermanentlyDeleteBookDialog, {
      props: { visible: true, deleting: false, book },
      global: { stubs: { AModal: ModalStub } },
    })
    const button = wrapper.get('.danger-button')
    expect((button.element as HTMLButtonElement).disabled).toBe(true)

    await wrapper.get('input').setValue('天渊')
    expect((button.element as HTMLButtonElement).disabled).toBe(true)
    await wrapper.get('input').setValue('天渊行')
    await button.trigger('click')

    expect(wrapper.emitted('confirm')).toEqual([['天渊行']])
    expect(wrapper.text()).toContain('正文、目录、Story Bible、封面和关联 AI 数据')
  })
})
