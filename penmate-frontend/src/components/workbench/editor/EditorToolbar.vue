<template>
  <div class="editor-toolbar">
    <div class="chapter-heading">
      <span>正文</span>
      <strong data-testid="chapter-label">{{ currentChapterTitle || '请选择章节' }}</strong>
    </div>
    <div class="toolbar-actions">
      <button type="button" data-testid="toolbar-undo" title="撤销" :disabled="!activeChapter || readOnly" @click="$emit('undo')"><UndoOutlined /></button>
      <button type="button" data-testid="toolbar-redo" title="重做" :disabled="!activeChapter || readOnly" @click="$emit('redo')"><RedoOutlined /></button>
      <span class="toolbar-divider" aria-hidden="true"></span>
      <button type="button" title="查找与替换" :disabled="!activeChapter" @click="$emit('find')"><SearchOutlined /></button>
      <button type="button" data-testid="toolbar-save" title="立即保存" :disabled="!activeChapter || readOnly" @click="$emit('save')"><SaveOutlined /></button>
      <button type="button" :class="{ active: typewriterMode }" title="打字机模式" :disabled="!activeChapter" @click="$emit('toggle-typewriter')"><AimOutlined /></button>
      <button type="button" title="专注模式" :disabled="!activeChapter" @click="$emit('focus-mode')"><FullscreenOutlined /></button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { AimOutlined, FullscreenOutlined, RedoOutlined, SaveOutlined, SearchOutlined, UndoOutlined } from '@ant-design/icons-vue'

defineProps<{ currentChapterTitle: string; activeChapter: string; readOnly?: boolean; typewriterMode?: boolean }>()
defineEmits<{ save: []; undo: []; redo: []; find: []; 'toggle-typewriter': []; 'focus-mode': [] }>()
</script>

<style scoped>
.editor-toolbar,
.chapter-heading,
.toolbar-actions {
  display: flex;
  align-items: center;
}

.editor-toolbar {
  justify-content: space-between;
  gap: 16px;
  min-height: 50px;
  padding: 0 14px 0 18px;
  background: var(--bg-surface);
  border-bottom: 1px solid var(--border-subtle);
}

.chapter-heading {
  min-width: 0;
  gap: 10px;
}

.chapter-heading span {
  color: var(--text-muted);
  font-size: 11px;
}

.chapter-heading strong {
  overflow: hidden;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.toolbar-actions { gap: 3px; }
.toolbar-actions button {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  color: var(--text-secondary);
  background: transparent;
  border: 0;
  border-radius: var(--radius-md);
  cursor: pointer;
}
.toolbar-actions button:hover:not(:disabled), .toolbar-actions button.active { color: var(--accent); background: var(--accent-soft); }
.toolbar-actions button:disabled { cursor: not-allowed; opacity: .4; }
.toolbar-divider { width: 1px; height: 20px; margin: 0 4px; background: var(--border-subtle); }
</style>
