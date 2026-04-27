<script setup lang="ts">
withDefaults(defineProps<{
  currentModelName?: string
  generationStatusText?: string
  isGenerating?: boolean
  generationPhase?: 'idle' | 'preparing' | 'streaming' | 'waiting_approval' | 'failed'
}>(), {
  currentModelName: '',
  generationStatusText: '就绪',
  isGenerating: false,
  generationPhase: 'idle',
})

defineEmits<{
  'toggle-history': []
}>()
</script>

<template>
  <div class="agent-header">
    <span class="agent-icon" aria-hidden="true">🤖</span>
    <span class="agent-title" data-testid="agent-title">AI会话</span>
    <button
      type="button"
      class="agent-history-btn"
      data-testid="toggle-history"
      @click="$emit('toggle-history')"
    >
      历史会话
    </button>
    <div
      class="agent-model"
      :class="{ empty: !currentModelName }"
      data-testid="current-model"
    >
      {{ currentModelName || '未选择模型' }}
    </div>
    <div
      class="agent-status"
      :class="{ busy: isGenerating, failed: generationPhase === 'failed' }"
      data-testid="agent-status"
    >
      <span class="status-dot"></span>
      <span>{{ generationStatusText }}</span>
    </div>
  </div>
</template>
