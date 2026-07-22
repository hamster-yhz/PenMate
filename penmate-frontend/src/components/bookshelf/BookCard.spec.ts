import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import BookCard from './BookCard.vue'
import type { BookshelfBook } from '@/composables/bookshelf/useBookshelf'

const buildBook = (): BookshelfBook => ({
  id: 'book-101',
  title: '天渊行',
  description: '少年执笔入天渊。',
  genre: '仙侠',
  tags: ['热血', '升级'],
  wordCount: 128000,
  chapterCount: 36,
  updatedAt: '2026-07-21T12:00:00Z',
  updatedAtValue: Date.parse('2026-07-21T12:00:00Z'),
  coverTone: 'forest',
})

describe('BookCard', () => {
  it('renders_a_stable_text_cover_and_emits_open_from_the_card', async () => {
    const book = buildBook()
    const wrapper = mount(BookCard, { props: { book, viewMode: 'grid' } })

    expect(wrapper.get('.book-cover').classes()).toContain('tone-forest')
    expect(wrapper.text()).toContain('天渊行')
    expect(wrapper.text()).toContain('128,000 字')

    await wrapper.get('[data-testid="book-card"]').trigger('click')
    expect(wrapper.emitted('open')).toEqual([[book]])
  })
})
