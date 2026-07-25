<template>
  <div class="editor-frame" :class="{ locked: readOnly, 'ai-editing': aiEditing, 'typewriter-mode': typewriterMode }" :style="editorStyle">
    <div ref="host" class="editor-host" data-testid="editor-textarea" aria-label="章节正文编辑器"></div>
    <div v-if="readOnly" class="lock-overlay" role="status">
      <RobotOutlined v-if="aiEditing" />
      <LockOutlined v-else />
      <div>
        <span>{{ lockReason || 'AI 正在编辑当前章节' }}</span>
        <div v-if="conflictPending && !aiEditing" class="conflict-actions">
          <button type="button" @click="$emit('use-latest')">使用最新版本</button>
          <button type="button" class="primary" @click="$emit('continue-local')">继续本地草稿</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Annotation, EditorState, Compartment, Prec, Transaction } from '@codemirror/state'
import { EditorView, drawSelection, highlightActiveLine, keymap, placeholder as editorPlaceholder } from '@codemirror/view'
import { defaultKeymap, history, historyKeymap, indentWithTab, redo, undo } from '@codemirror/commands'
import { openSearchPanel, searchKeymap } from '@codemirror/search'
import { RobotOutlined } from '@ant-design/icons-vue'
import { LockOutlined } from '@ant-design/icons-vue'
import type { EditorFontFamily } from '@/entities/auth/model'
import { chinesePunctuationExtension } from './chinesePunctuation'

export interface EditorSelectionState {
  line: number
  column: number
  selectedText: string
}

export interface PlainTextEditorApi {
  undo: () => boolean
  redo: () => boolean
  find: () => boolean
  focus: () => void
}

const props = withDefaults(defineProps<{
  modelValue: string
  placeholder?: string
  readOnly?: boolean
  lockReason?: string
  typewriterMode?: boolean
  aiEditing?: boolean
  conflictPending?: boolean
  fontFamily?: EditorFontFamily
  fontSize?: number
  lineHeight?: number
  paragraphSpacing?: number
  contentWidth?: number
  highlightCurrentParagraph?: boolean
}>(), {
  modelValue: '',
  placeholder: '',
  readOnly: false,
  lockReason: '',
  typewriterMode: false,
  aiEditing: false,
  conflictPending: false,
  fontFamily: 'SERIF',
  fontSize: 17,
  lineHeight: 1.9,
  paragraphSpacing: 0.35,
  contentWidth: 760,
  highlightCurrentParagraph: true,
})

const fontFamilies: Record<EditorFontFamily, string> = {
  SERIF: 'var(--font-writing)',
  SANS: 'var(--font-ui)',
  SYSTEM: 'system-ui, sans-serif',
}
const editorStyle = computed(() => ({
  '--editor-font-family': fontFamilies[props.fontFamily],
  '--editor-font-size': `${props.fontSize}px`,
  '--editor-line-height': String(props.lineHeight),
  '--editor-paragraph-spacing': `${props.paragraphSpacing}em`,
  '--editor-content-width': `${props.contentWidth}px`,
  '--editor-active-line': props.highlightCurrentParagraph
    ? 'color-mix(in srgb, var(--accent-soft) 46%, transparent)'
    : 'transparent',
}))

const emit = defineEmits<{
  'update:modelValue': [string]
  change: [string]
  selectionChange: [EditorSelectionState]
  save: []
  'use-latest': []
  'continue-local': []
}>()

const host = ref<HTMLElement | null>(null)
const editable = new Compartment()
let view: EditorView | null = null
let syncingExternalValue = false
const remoteChange = Annotation.define<boolean>()

const emitSelection = (state: EditorState) => {
  const selection = state.selection.main
  const line = state.doc.lineAt(selection.head)
  emit('selectionChange', {
    line: line.number,
    column: selection.head - line.from + 1,
    selectedText: state.sliceDoc(selection.from, selection.to),
  })
}

const writingTheme = EditorView.theme({
  '&': { height: '100%', backgroundColor: 'transparent', color: 'var(--text-primary)' },
  '.cm-scroller': { overflow: 'auto', fontFamily: 'var(--editor-font-family)', lineHeight: 'var(--editor-line-height)' },
  '.cm-content': { maxWidth: 'var(--editor-content-width)', minHeight: '100%', margin: '0 auto', padding: '42px 48px 120px', fontSize: 'var(--editor-font-size)', caretColor: 'var(--accent)' },
  '.cm-line': { padding: '0 2px var(--editor-paragraph-spacing)' },
  '.cm-activeLine': { backgroundColor: 'var(--editor-active-line)' },
  '.cm-selectionBackground, &.cm-focused .cm-selectionBackground': { backgroundColor: 'var(--accent-soft) !important' },
  '.cm-cursor': { borderLeftColor: 'var(--accent)', borderLeftWidth: '2px' },
  '.cm-gutters': { display: 'none' },
  '.cm-panels': { color: 'var(--text-primary)', backgroundColor: 'var(--bg-surface)' },
  '.cm-search': { display: 'flex', flexWrap: 'wrap', gap: '6px', alignItems: 'center', padding: '8px', borderBottom: '1px solid var(--border-subtle)' },
  '.cm-search input, .cm-search button, .cm-search select': { color: 'var(--text-primary)', backgroundColor: 'var(--bg-surface)', border: '1px solid var(--border-strong)', borderRadius: '4px', minHeight: '30px' },
  '.cm-tooltip': { color: 'var(--text-primary)', backgroundColor: 'var(--bg-elevated)', border: '1px solid var(--border-subtle)', boxShadow: 'var(--shadow-md)' },
})

onMounted(() => {
  if (!host.value) return
  view = new EditorView({
    parent: host.value,
    state: EditorState.create({
      doc: props.modelValue,
      extensions: [
        history(),
        drawSelection(),
        highlightActiveLine(),
        editorPlaceholder(props.placeholder),
        editable.of(EditorView.editable.of(!props.readOnly)),
        Prec.highest(keymap.of([
          { key: 'Mod-s', preventDefault: true, run: () => { emit('save'); return true } },
          { key: 'Mod-z', preventDefault: true, run: undo, shift: redo },
          { key: 'Mod-h', preventDefault: true, run: openSearchPanel },
        ])),
        keymap.of([
          indentWithTab,
          ...defaultKeymap,
          ...historyKeymap,
          ...searchKeymap,
        ]),
        chinesePunctuationExtension(),
        writingTheme,
        EditorView.updateListener.of((update) => {
          if (update.docChanged && !syncingExternalValue) {
            const content = update.state.doc.toString()
            emit('update:modelValue', content)
            emit('change', content)
          }
          if (update.docChanged || update.selectionSet) {
            emitSelection(update.state)
            if (props.typewriterMode && update.view.hasFocus) {
              update.view.dispatch({ effects: EditorView.scrollIntoView(update.state.selection.main.head, { y: 'center' }) })
            }
          }
        }),
        EditorState.transactionFilter.of((transaction) => {
          if (!props.readOnly || !transaction.docChanged) return transaction
          return transaction.annotation(remoteChange) ? transaction : []
        }),
      ],
    }),
  })
  emitSelection(view.state)
})

watch(() => props.modelValue, (content) => {
  if (!view || content === view.state.doc.toString()) return
  syncingExternalValue = true
  view.dispatch({
    changes: { from: 0, to: view.state.doc.length, insert: content },
    annotations: [remoteChange.of(true), Transaction.addToHistory.of(false)],
  })
  syncingExternalValue = false
})

watch(() => props.readOnly, (value) => {
  view?.dispatch({ effects: editable.reconfigure(EditorView.editable.of(!value)) })
})

const api: PlainTextEditorApi = {
  undo: () => view ? undo(view) : false,
  redo: () => view ? redo(view) : false,
  find: () => view ? openSearchPanel(view) : false,
  focus: () => view?.focus(),
}

defineExpose(api)
onBeforeUnmount(() => view?.destroy())
</script>

<style scoped>
.editor-frame,
.editor-host {
  width: 100%;
  height: 100%;
  min-height: 0;
}

.editor-frame {
  position: relative;
  background: var(--bg-editor);
  border: 2px solid transparent;
  transition: border-color 160ms ease, filter 160ms ease;
}

.editor-frame.locked {
  border-color: var(--border-strong);
  filter: saturate(.8) brightness(.96);
}

.editor-frame.ai-editing {
  border-color: var(--info);
  filter: saturate(.62) brightness(.9);
}

.lock-overlay {
  position: absolute;
  top: 14px;
  left: 50%;
  z-index: 4;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 32px;
  padding: 0 10px;
  color: var(--info);
  background: var(--info-soft);
  border: 1px solid color-mix(in srgb, var(--info) 45%, transparent);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
  font-size: 12px;
  transform: translateX(-50%);
  max-width: calc(100% - 32px);
  text-align: center;
}

.conflict-actions {
  display: flex;
  justify-content: center;
  gap: 7px;
  margin-top: 7px;
}

.conflict-actions button {
  min-height: 28px;
  padding: 3px 9px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  background: var(--bg-surface);
  color: var(--text-secondary);
  cursor: pointer;
}

.conflict-actions button.primary {
  border-color: var(--accent);
  background: var(--accent);
  color: var(--text-inverse);
}

@media (max-width: 720px) {
  .lock-overlay { top: 10px; width: max-content; max-width: calc(100% - 24px); font-size: 11px; }
  .editor-host :deep(.cm-content) {
    padding: 28px 22px 100px;
  }
}
</style>
