import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readMyBooksSource = () => readFileSync(resolve(currentDir, 'index.vue'), 'utf-8')

describe('MyBooks index refactor', () => {
  it('reduces the page to bookshelf shell components', () => {
    const source = readMyBooksSource()

    expect(source).toContain("import BookStatsBar from '@/components/bookshelf/BookStatsBar.vue'")
    expect(source).toContain("import BookActionBar from '@/components/bookshelf/BookActionBar.vue'")
    expect(source).toContain("import BookCard from '@/components/bookshelf/BookCard.vue'")
    expect(source).toContain("import BookEditorModal from '@/components/bookshelf/BookEditorModal.vue'")
    expect(source).toContain("import DeleteBookDialog from '@/components/bookshelf/DeleteBookDialog.vue'")
    expect(source).toContain('useBookshelf()')

    expect(source).toContain('<BookStatsBar')
    expect(source).toContain('<BookActionBar')
    expect(source).toContain('<BookCard')
    expect(source).toContain('<BookEditorModal')
    expect(source).toContain('<DeleteBookDialog')

    expect(source).not.toContain('<div class="stats-bar">')
    expect(source).not.toContain('<div class="action-bar">')
    expect(source).not.toContain('<div\n          v-for="book in books"')
    expect(source).not.toContain('<div class="modal-overlay" v-if="showEditorModal"')
    expect(source).not.toContain('<div class="modal-overlay" v-if="showDeleteDialog"')
    expect(source).not.toMatch(/const\s+loadBooks\s*=\s*async\s*\(/)
    expect(source).not.toMatch(/const\s+confirmBook\s*=\s*async\s*\(/)
    expect(source).not.toMatch(/const\s+confirmDelete\s*=\s*async\s*\(/)
    expect(source).not.toMatch(/const\s+editBook\s*=\s*\(/)
    expect(source).not.toMatch(/const\s+deleteBook\s*=\s*\(/)
  })
})
