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
