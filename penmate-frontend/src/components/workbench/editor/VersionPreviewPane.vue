<script setup lang="ts">
withDefaults(defineProps<{
  currentContent: string
  selectedVersionContent: string
}>(), {
  currentContent: '',
  selectedVersionContent: '',
})
</script>

<template>
  <div v-if="selectedVersionContent" data-testid="version-preview-root" class="version-preview">
    <div class="vp-title">版本对比预览（左：当前内容 / 右：所选版本）</div>
    <div class="vp-grid">
      <textarea
        data-testid="version-preview-current"
        class="vp-box"
        :value="currentContent"
        readonly
      ></textarea>
      <textarea
        data-testid="version-preview-selected"
        class="vp-box"
        :value="selectedVersionContent"
        readonly
      ></textarea>
    </div>
  </div>
</template>
