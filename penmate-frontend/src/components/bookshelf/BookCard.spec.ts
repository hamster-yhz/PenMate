import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import BookCard from './BookCard.vue'

interface BookCardModel {
  id: string
  title: string
  description: string
  genre: string
  tags: string[]
  wordCount: number
  chapterCount: number
  updatedAt: string
  coverGradient: string
}

const buildBook = (overrides: Partial<BookCardModel> = {}): BookCardModel => ({
  id: 'book-101',
  title: '天渊行',
  description: '少年执笔入天渊。',
  genre: '仙侠',
  tags: ['热血', '升级'],
  wordCount: 128000,
  chapterCount: 36,
  updatedAt: '刚刚',
  coverGradient: 'linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%)',
  ...overrides,
})

const mountBookCard = (overrides: Partial<BookCardModel> = {}) =>
  mount(BookCard, {
    props: {
      book: buildBook(overrides),
    },
  })

describe('BookCard', () => {
  it('emits_open_when_clicking_card_body', async () => {
    const wrapper = mountBookCard()
    const card = wrapper.find('[data-testid="book-card"]')

    expect(card.exists()).toBe(true)

    await card.trigger('click')

    expect(wrapper.emitted('open')).toEqual([
      [
        {
          id: 'book-101',
          title: '天渊行',
          description: '少年执笔入天渊。',
          genre: '仙侠',
          tags: ['热血', '升级'],
          wordCount: 128000,
          chapterCount: 36,
          updatedAt: '刚刚',
          coverGradient: 'linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%)',
        },
      ],
    ])
  })

  it('emits_edit_without_triggering_open', async () => {
    const wrapper = mountBookCard()
    const editButton = wrapper.find('[data-testid="book-card-edit"]')

    expect(editButton.exists()).toBe(true)

    await editButton.trigger('click')

    expect(wrapper.emitted('edit')).toEqual([
      [
        {
          id: 'book-101',
          title: '天渊行',
          description: '少年执笔入天渊。',
          genre: '仙侠',
          tags: ['热血', '升级'],
          wordCount: 128000,
          chapterCount: 36,
          updatedAt: '刚刚',
          coverGradient: 'linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%)',
        },
      ],
    ])
    expect(wrapper.emitted('open')).toBeUndefined()
  })

  it('emits_delete_without_triggering_open', async () => {
    const wrapper = mountBookCard()
    const deleteButton = wrapper.find('[data-testid="book-card-delete"]')

    expect(deleteButton.exists()).toBe(true)

    await deleteButton.trigger('click')

    expect(wrapper.emitted('delete')).toEqual([
      [
        {
          id: 'book-101',
          title: '天渊行',
          description: '少年执笔入天渊。',
          genre: '仙侠',
          tags: ['热血', '升级'],
          wordCount: 128000,
          chapterCount: 36,
          updatedAt: '刚刚',
          coverGradient: 'linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%)',
        },
      ],
    ])
    expect(wrapper.emitted('open')).toBeUndefined()
  })
})
