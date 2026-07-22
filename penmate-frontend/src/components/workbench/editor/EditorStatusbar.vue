<template>
  <footer class="editor-statusbar">
    <div class="status-left">
      <span>{{ wordCount.toLocaleString('zh-CN') }} 字</span>
      <span v-if="selectedText" data-testid="status-selection">已选 {{ selectedText.length }} 字</span>
      <span data-testid="status-position">第 {{ currentLine }} 行，第 {{ currentCol }} 列</span>
    </div>
    <div class="status-actions">
      <button v-if="aiUndoAvailable" type="button" class="undo-ai" :disabled="aiUndoBusy" @click="$emit('undo-ai')">
        <UndoOutlined />{{ aiUndoBusy ? '正在撤回' : '撤回 AI 修改' }}
      </button>
      <span class="save-state" :class="saveStateClass" role="status">{{ saveHint || '已保存' }}</span>
    </div>
  </footer>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { UndoOutlined } from '@ant-design/icons-vue'
const props = defineProps<{ selectedText: string; currentLine: number; currentCol: number; wordCount: number; saveHint: string; aiUndoAvailable?: boolean; aiUndoBusy?: boolean }>()
defineEmits<{ 'undo-ai': [] }>()
const saveStateClass = computed(() => props.saveHint.includes('失败')
  ? 'error'
  : props.saveHint.includes('离线')
    ? 'offline'
    : props.saveHint.includes('同步')
      ? 'saving'
      : 'saved')
</script>

<style scoped>
.editor-statusbar,
.status-left {
  display: flex;
  align-items: center;
}
.editor-statusbar {
  justify-content: space-between;
  gap: 12px;
  min-height: 34px;
  padding: 0 16px;
  color: var(--text-muted);
  background: var(--bg-surface);
  border-top: 1px solid var(--border-subtle);
  font-size: 11px;
}
.status-left { flex-wrap: wrap; gap: 14px; }
.status-actions { display: flex; align-items: center; gap: 12px; }
.undo-ai { display: inline-flex; align-items: center; gap: 5px; padding: 3px 7px; color: var(--info); background: transparent; border: 0; cursor: pointer; font-size: 11px; }
.undo-ai:hover, .undo-ai:focus-visible { color: var(--text-primary); outline: 1px solid var(--info); outline-offset: 2px; }
.undo-ai:disabled { cursor: wait; opacity: .6; }
.save-state { color: var(--accent); }
.save-state.saving { color: var(--info); }
.save-state.offline { color: var(--warning); }
.save-state.error { color: var(--danger); }
</style>
