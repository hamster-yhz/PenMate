import { EditorState } from '@codemirror/state'
import { describe, expect, it } from 'vitest'
import { resolveChinesePunctuationEdit } from './chinesePunctuation'

const transaction = (userEvent: string) => EditorState.create().update({ userEvent })

describe('Chinese punctuation editing', () => {
  it('pairs an opening mark and keeps the cursor between the pair', () => {
    expect(resolveChinesePunctuationEdit({
      text: '“', from: 3, to: 3, nextText: '', selectedText: '', composing: false,
      transaction: transaction('input.type'),
    })).toEqual({ kind: 'pair', insert: '“”', anchor: 4, head: 4 })
  })

  it('wraps selected Chinese text', () => {
    expect(resolveChinesePunctuationEdit({
      text: '《', from: 2, to: 4, nextText: '', selectedText: '长夜', composing: false,
      transaction: transaction('input.type'),
    })).toEqual({ kind: 'pair', insert: '《长夜》', anchor: 3, head: 5 })
  })

  it('skips an existing closing mark', () => {
    expect(resolveChinesePunctuationEdit({
      text: '）', from: 8, to: 8, nextText: '）', selectedText: '', composing: false,
      transaction: transaction('input.type'),
    })).toEqual({ kind: 'skip', anchor: 9 })
  })

  it('does not rewrite IME composition updates or pasted text', () => {
    expect(resolveChinesePunctuationEdit({
      text: '“', from: 0, to: 0, nextText: '', selectedText: '', composing: true,
      transaction: transaction('input.type.compose'),
    })).toBeNull()
    expect(resolveChinesePunctuationEdit({
      text: '《粘贴内容》', from: 0, to: 0, nextText: '', selectedText: '', composing: false,
      transaction: transaction('input.paste'),
    })).toBeNull()
  })

  it('keeps long chapter edits incremental', () => {
    const content = `${'长篇正文。'.repeat(40_000)}\n尾声`
    const state = EditorState.create({ doc: content })
    const update = state.update({ changes: { from: content.length - 2, to: content.length, insert: '终章' } })

    expect(update.changes.iterChangedRanges((fromA, toA, fromB, toB) => {
      expect([fromA, toA, fromB, toB]).toEqual([content.length - 2, content.length, content.length - 2, content.length])
    })).toBeUndefined()
    expect(update.newDoc.length).toBe(content.length)
    expect(update.newDoc.sliceString(update.newDoc.length - 2)).toBe('终章')
  })
})
