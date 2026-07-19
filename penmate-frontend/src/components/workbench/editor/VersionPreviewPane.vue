<script setup lang="ts">
withDefaults(
  defineProps<{
    currentContent: string
    selectedVersionContent: string
  }>(),
  {
    currentContent: '',
    selectedVersionContent: '',
  },
)
</script>

<template>
  <div v-if="selectedVersionContent" data-testid="version-preview-root" class="version-preview">
    <div class="vp-title">版本对比预览（左：当前内容 / 右：所选版本）</div>
    <div class="vp-grid">
      <textarea
        aria-label="当前章节内容"
        data-testid="version-preview-current"
        class="vp-box workbench-editor-textarea"
        :value="currentContent"
        readonly
      ></textarea>
      <textarea
        aria-label="历史版本内容"
        data-testid="version-preview-selected"
        class="vp-box workbench-editor-textarea"
        :value="selectedVersionContent"
        readonly
      ></textarea>
    </div>
  </div>
</template>

<style scoped lang="less">
.version-preview {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 18px 18px;
  border-top: 1px solid var(--border-subtle);
  background: rgba(11, 17, 32, 0.4);
}

.vp-title {
  font-size: 0.82rem;
  color: var(--amber-gold);
  letter-spacing: 0.08em;
}

.vp-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.vp-box {
  min-height: 180px;
  padding: 14px 16px;
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  background: rgba(11, 17, 32, 0.6);
  color: var(--text-primary);
  resize: none;
  line-height: 1.7;
}

.workbench-editor-textarea {
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.02);
}

@media (max-width: 1100px) {
  .vp-grid {
    grid-template-columns: 1fr;
  }
}
</style>
