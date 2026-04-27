<template>
  <main class="panel panel-center">
    <EditorToolbar
      :current-chapter-title="currentChapterTitle"
      :selected-version-no="selectedVersionNo"
      :version-busy="versionBusy"
      :active-chapter="activeChapter"
      :versions="versions"
      @save="emit('save')"
      @undo="emit('undo')"
      @redo="emit('redo')"
      @wrap-selection="emit('wrap-selection', $event[0], $event[1])"
      @insert-prefix="emit('insert-prefix', $event)"
      @update:selected-version-no="emit('update:selected-version-no', $event)"
      @restore-version="emit('restore-version')"
      @view-version="emit('view-version')"
      @publish-chapter="emit('publish-chapter')"
    />

    <div class="editor-area">
      <EditorTextarea
        :ref="editorTextareaRef"
        :model-value="editorContent"
        placeholder="在此处开始创作，或让AI为你执笔..."
        @update:model-value="emit('update:editor-content', $event)"
        @input="emit('input')"
        @cursor-activity="emit('cursor-activity')"
        @save="emit('save')"
        @undo="emit('undo')"
        @redo="emit('redo')"
        @wrap-selection="emit('wrap-selection', $event[0], $event[1])"
      />
    </div>

    <EditorStatusbar
      :selected-text="selectedText"
      :version-diff-summary="versionDiffSummary"
      :current-line="currentLine"
      :current-col="currentCol"
    />

    <VersionPreviewPane
      :current-content="editorContent"
      :selected-version-content="selectedVersionContent"
    />
  </main>
</template>

<script setup lang="ts">
import EditorToolbar from '@/components/workbench/editor/EditorToolbar.vue'
import EditorTextarea from '@/components/workbench/editor/EditorTextarea.vue'
import EditorStatusbar from '@/components/workbench/editor/EditorStatusbar.vue'
import VersionPreviewPane from '@/components/workbench/editor/VersionPreviewPane.vue'

defineProps<{
  currentChapterTitle: string
  selectedVersionNo: string
  versionBusy: boolean
  activeChapter: string
  versions: Array<Record<string, unknown>>
  editorTextareaRef: (instance: Element | import('vue').ComponentPublicInstance | null) => void
  editorContent: string
  selectedText: string
  versionDiffSummary: string
  currentLine: number
  currentCol: number
  selectedVersionContent: string
}>()

const emit = defineEmits<{
  (event: 'save'): void
  (event: 'undo'): void
  (event: 'redo'): void
  (event: 'wrap-selection', before: string, after: string): void
  (event: 'insert-prefix', payload: string): void
  (event: 'update:selected-version-no', payload: string): void
  (event: 'restore-version'): void
  (event: 'view-version'): void
  (event: 'publish-chapter'): void
  (event: 'update:editor-content', payload: string): void
  (event: 'input'): void
  (event: 'cursor-activity'): void
}>()
</script>

<style lang="less" scoped>
.panel {
  position: relative;
  display: flex;
  flex-direction: column;
  transition: width 0.3s var(--ease-silk);
}

.panel-center {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: rgba(11, 17, 32, 0.3);
}

.editor-area {
  flex: 1;
  overflow: hidden;
  padding: 0;
}
</style>
