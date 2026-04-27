<script setup lang="ts">
withDefaults(defineProps<{
  selectedText: string
  versionDiffSummary: string
  currentLine: number
  currentCol: number
}>(), {
  selectedText: '',
  versionDiffSummary: '',
  currentLine: 1,
  currentCol: 1,
})
</script>

<template>
  <div class="editor-statusbar">
    <span v-if="selectedText" data-testid="status-selection">已选 {{ selectedText.length }} 字</span>
    <span v-if="versionDiffSummary" data-testid="status-diff" class="diff-summary">{{ versionDiffSummary }}</span>
    <span data-testid="status-position">行 {{ currentLine }} · 列 {{ currentCol }}</span>
  </div>
</template>

<style scoped lang="less">
.editor-statusbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 10px 18px;
  border-top: 1px solid var(--border-subtle);
  background: rgba(11, 17, 32, 0.72);
  color: var(--text-secondary);
  font-size: 0.78rem;
}

.editor-statusbar span {
  padding: 4px 10px;
  border: 1px solid rgba(201, 169, 110, 0.08);
  border-radius: 999px;
  background: rgba(17, 24, 39, 0.54);
}

.diff-summary {
  color: var(--amber-gold);
  border-color: rgba(201, 169, 110, 0.18);
  background: rgba(201, 169, 110, 0.08);
}
</style>
