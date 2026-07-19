<script setup lang="ts">
withDefaults(
  defineProps<{
    currentModelName?: string
    generationStatusText?: string
    agentStatusDetailText?: string
    isGenerating?: boolean
    generationPhase?: 'idle' | 'preparing' | 'streaming' | 'waiting_approval' | 'failed'
    boundStyleName?: string
  }>(),
  {
    currentModelName: '',
    generationStatusText: '就绪',
    agentStatusDetailText: '',
    isGenerating: false,
    generationPhase: 'idle',
  },
)

defineEmits<{
  'toggle-history': []
  'create-session': []
}>()
</script>

<template>
  <div class="agent-header">
    <button type="button" class="agent-history-btn" data-testid="toggle-history" @click="$emit('toggle-history')">
      历史会话
    </button>
    <button type="button" class="agent-create-btn" data-testid="create-session" @click="$emit('create-session')">
      ＋
    </button>
    <div class="agent-model" :class="{ empty: !currentModelName }" data-testid="current-model">
      {{ currentModelName || '未选择模型' }}
    </div>
    <div v-if="boundStyleName" class="agent-style" data-testid="bound-style">
      {{ boundStyleName }}
    </div>
    <div
      class="agent-status"
      :class="{ busy: isGenerating, failed: generationPhase === 'failed' }"
      data-testid="agent-status"
    >
      <span class="status-dot"></span>
      <span class="agent-status-text-group">
        <span>{{ generationStatusText }}</span>
        <span v-if="agentStatusDetailText" class="agent-status-detail" data-testid="agent-status-detail">
          {{ agentStatusDetailText }}
        </span>
      </span>
    </div>
  </div>
</template>

<style scoped lang="less">
.agent-header {
  display: grid;
  grid-template-columns: auto auto minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border-subtle);
  background: linear-gradient(180deg, rgba(17, 24, 39, 0.9), rgba(11, 17, 32, 0.68));
}

.agent-history-btn,
.agent-create-btn,
.agent-model,
.agent-style,
.agent-status {
  min-height: 34px;
  border: 1px solid var(--border-subtle);
  border-radius: 999px;
  background: rgba(11, 17, 32, 0.56);
}

.agent-history-btn,
.agent-create-btn {
  padding: 0 12px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.25s var(--ease-silk);
}

.agent-create-btn {
  min-width: 34px;
  font-size: 1rem;
  line-height: 1;
}

.agent-history-btn:hover,
.agent-create-btn:hover {
  color: var(--amber-gold);
  border-color: var(--border-gold);
  box-shadow: var(--shadow-gold);
}

.agent-model,
.agent-style,
.agent-status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  color: var(--text-secondary);
}

.agent-status-text-group {
  display: inline-flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.15;
}

.agent-status-detail {
  font-size: 12px;
  color: var(--text-muted);
}

.agent-model {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.agent-model.empty {
  color: var(--text-muted);
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--jade-green);
  box-shadow: 0 0 10px rgba(90, 158, 111, 0.45);
}

.agent-status.busy .status-dot {
  background: var(--amber-gold);
  box-shadow: 0 0 12px rgba(201, 169, 110, 0.45);
}

.agent-status.failed {
  color: #f0b9a9;
  border-color: rgba(192, 60, 45, 0.3);
  background: rgba(192, 60, 45, 0.08);
}

.agent-status.failed .status-dot {
  background: var(--cinnabar);
  box-shadow: 0 0 12px rgba(192, 60, 45, 0.4);
}

@media (max-width: 1280px) {
  .agent-header {
    grid-template-columns: auto auto 1fr;
  }

  .agent-model,
  .agent-status {
    grid-column: span 3;
  }
}
</style>
