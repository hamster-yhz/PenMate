import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { describe, expect, it } from 'vitest'
import DeleteBookDialog from './DeleteBookDialog.vue'

const ModalStub = defineComponent({
  template: '<section data-testid="modal-stub"><slot /></section>',
})

describe('DeleteBookDialog', () => {
  it('describes_recycle_bin_retention_and_disables_repeat_confirm_while_deleting', () => {
    const wrapper = mount(DeleteBookDialog, {
      props: {
        visible: true,
        deleting: true,
        book: {
          id: 'book-101',
          title: '天渊行',
          description: '',
          genre: '仙侠',
          tags: [],
          wordCount: 0,
          chapterCount: 1,
          updatedAt: '',
          updatedAtValue: 0,
          coverTone: 'forest',
        },
      },
      global: { stubs: { AModal: ModalStub } },
    })

    expect(wrapper.text()).toContain('30 天后永久删除')
    expect((wrapper.get('.danger-button').element as HTMLButtonElement).disabled).toBe(true)
  })
})
