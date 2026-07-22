import { EditorSelection, type Extension, type Transaction } from '@codemirror/state'
import { EditorView, keymap } from '@codemirror/view'

export const CHINESE_PUNCTUATION_PAIRS = new Map([
  ['“', '”'],
  ['‘', '’'],
  ['（', '）'],
  ['【', '】'],
  ['《', '》'],
  ['〈', '〉'],
  ['「', '」'],
  ['『', '』'],
  ['〔', '〕'],
])

const closingPunctuation = new Set(CHINESE_PUNCTUATION_PAIRS.values())

export interface ChinesePunctuationInput {
  text: string
  from: number
  to: number
  nextText: string
  selectedText: string
  composing: boolean
  transaction: Transaction
}

export type ChinesePunctuationEdit =
  | { kind: 'pair'; insert: string; anchor: number; head: number }
  | { kind: 'skip'; anchor: number }

export const resolveChinesePunctuationEdit = ({
  text,
  from,
  to,
  nextText,
  selectedText,
  composing,
  transaction,
}: ChinesePunctuationInput): ChinesePunctuationEdit | null => {
  if (composing || text.length !== 1 || transaction.isUserEvent('input.paste')) return null
  const closing = CHINESE_PUNCTUATION_PAIRS.get(text)
  if (closing) {
    return {
      kind: 'pair',
      insert: `${text}${selectedText}${closing}`,
      anchor: from + 1,
      head: from + 1 + selectedText.length,
    }
  }
  if (from === to && closingPunctuation.has(text) && nextText === text) {
    return { kind: 'skip', anchor: from + 1 }
  }
  return null
}

const deleteEmptyPair = (view: EditorView) => {
  const selection = view.state.selection.main
  if (!selection.empty || selection.from === 0 || selection.from >= view.state.doc.length) return false
  const opening = view.state.sliceDoc(selection.from - 1, selection.from)
  const closing = view.state.sliceDoc(selection.from, selection.from + 1)
  if (CHINESE_PUNCTUATION_PAIRS.get(opening) !== closing) return false
  view.dispatch({
    changes: { from: selection.from - 1, to: selection.from + 1 },
    selection: EditorSelection.cursor(selection.from - 1),
    userEvent: 'input.delete.backward',
  })
  return true
}

export const chinesePunctuationExtension = (): Extension => [
  EditorView.inputHandler.of((view, from, to, text, defaultInsert) => {
    const transaction = defaultInsert()
    const edit = resolveChinesePunctuationEdit({
      text,
      from,
      to,
      nextText: view.state.sliceDoc(from, from + text.length),
      selectedText: view.state.sliceDoc(from, to),
      composing: view.composing,
      transaction,
    })
    if (!edit) return false
    if (edit.kind === 'skip') {
      view.dispatch({ selection: EditorSelection.cursor(edit.anchor), userEvent: 'input.type' })
      return true
    }
    view.dispatch({
      changes: { from, to, insert: edit.insert },
      selection: EditorSelection.range(edit.anchor, edit.head),
      userEvent: 'input.type',
    })
    return true
  }),
  keymap.of([{ key: 'Backspace', run: deleteEmptyPair }]),
]
