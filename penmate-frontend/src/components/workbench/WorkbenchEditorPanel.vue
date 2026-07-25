<template>
  <main class="panel-center" :class="{ focused: focusMode }">
    <EditorToolbar
      :current-chapter-title="currentChapterTitle"
      :active-chapter="activeChapter"
      :read-only="readOnly"
      :typewriter-mode="typewriterMode"
      @save="$emit('save')"
      @undo="editorApi?.undo()"
      @redo="editorApi?.redo()"
      @find="editorApi?.find()"
      @toggle-typewriter="toggleTypewriterMode"
      @focus-mode="focusMode = !focusMode"
    />
    <div class="editor-area">
      <PlainTextEditor
        ref="editorApi"
        :model-value="aiEditing ? (aiPreviewContent || editorContent) : editorContent"
        :read-only="readOnly"
        :lock-reason="lockReason"
        :ai-editing="aiEditing"
        :conflict-pending="conflictPending"
        :typewriter-mode="typewriterMode"
        :font-family="uiPreferences.editorFontFamily"
        :font-size="uiPreferences.editorFontSize"
        :line-height="uiPreferences.editorLineHeight"
        :paragraph-spacing="uiPreferences.editorParagraphSpacing"
        :content-width="uiPreferences.editorContentWidth"
        :highlight-current-paragraph="uiPreferences.highlightCurrentParagraph"
        placeholder="开始写作"
        @update:model-value="$emit('update:editor-content', $event)"
        @change="$emit('input', $event)"
        @selection-change="$emit('selection-change', $event)"
        @save="$emit('save')"
        @use-latest="$emit('use-latest')"
        @continue-local="$emit('continue-local')"
      />
    </div>
    <EditorStatusbar
      :selected-text="selectedText"
      :current-line="currentLine"
      :current-col="currentCol"
      :word-count="wordCount"
      :save-hint="saveHint"
      :ai-undo-available="aiUndoAvailable"
      :ai-undo-busy="aiUndoBusy"
      @undo-ai="$emit('undo-ai')"
    />
  </main>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import EditorToolbar from '@/components/workbench/editor/EditorToolbar.vue'
import EditorStatusbar from '@/components/workbench/editor/EditorStatusbar.vue'
import PlainTextEditor, { type EditorSelectionState, type PlainTextEditorApi } from '@/components/workbench/editor/PlainTextEditor.vue'
import { useUserUiPreferences } from '@/composables/useUserUiPreferences'

defineProps<{
  currentChapterTitle: string
  activeChapter: string
  editorContent: string
  selectedText: string
  currentLine: number
  currentCol: number
  wordCount: number
  saveHint: string
  readOnly?: boolean
  lockReason?: string
  aiEditing?: boolean
  conflictPending?: boolean
  aiPreviewContent?: string
  aiUndoAvailable?: boolean
  aiUndoBusy?: boolean
}>()

defineEmits<{
  save: []
  'update:editor-content': [string]
  input: [string]
  'selection-change': [EditorSelectionState]
  'undo-ai': []
  'use-latest': []
  'continue-local': []
}>()

const editorApi = ref<PlainTextEditorApi | null>(null)
const { uiPreferences } = useUserUiPreferences()
const typewriterMode = ref(uiPreferences.typewriterMode)
const typewriterOverridden = ref(false)
const focusMode = ref(false)
watch(() => uiPreferences.typewriterMode, (value) => {
  if (!typewriterOverridden.value) typewriterMode.value = value
})
const toggleTypewriterMode = () => {
  typewriterOverridden.value = true
  typewriterMode.value = !typewriterMode.value
}
</script>

<style scoped>
.panel-center {
  position: relative;
  z-index: 1;
  display: flex;
  flex: 1;
  min-width: 520px;
  min-height: 0;
  flex-direction: column;
  background: var(--bg-editor);
  border-inline: 1px solid var(--border-subtle);
}
.editor-area { flex: 1; min-height: 0; overflow: hidden; }
.panel-center.focused { position: fixed; inset: var(--app-header-height) 0 0; z-index: 300; }
@media (max-width: 900px) { .panel-center { min-width: 0; border: 0; } }
</style>
