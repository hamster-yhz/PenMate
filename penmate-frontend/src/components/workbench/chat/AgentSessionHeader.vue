<script setup lang="ts">
import {
  ArrowsAltOutlined,
  CompressOutlined,
  HistoryOutlined,
  PlusOutlined,
  RobotOutlined,
} from '@ant-design/icons-vue'

withDefaults(
  defineProps<{
    currentModelName?: string
    generationStatusText?: string
    agentStatusDetailText?: string
    isGenerating?: boolean
    generationPhase?: 'idle' | 'preparing' | 'streaming' | 'waiting_approval' | 'failed'
    boundStyleName?: string
    focused?: boolean
  }>(),
  {
    currentModelName: '', generationStatusText: '就绪', agentStatusDetailText: '',
    isGenerating: false, generationPhase: 'idle', boundStyleName: '', focused: false,
  },
)

defineEmits<{ 'toggle-history': []; 'create-session': []; 'toggle-focus': [] }>()
</script>

<template>
  <header class="agent-header">
    <div class="agent-title">
      <span class="agent-mark"><RobotOutlined /></span>
      <div><strong>写作 Agent</strong><span>{{ generationStatusText }}</span></div>
    </div>
    <div class="agent-meta">
      <span :class="['status-dot', { busy: isGenerating, failed: generationPhase === 'failed' }]"></span>
      <span class="model-name" :title="currentModelName || '未选择模型'">{{ currentModelName || '未选择模型' }}</span>
      <span v-if="boundStyleName" class="style-name">{{ boundStyleName }}</span>
      <span v-if="agentStatusDetailText" class="status-detail">{{ agentStatusDetailText }}</span>
    </div>
    <nav class="agent-actions" aria-label="对话工具">
      <button type="button" title="会话历史" aria-label="会话历史" data-testid="toggle-history" @click="$emit('toggle-history')"><HistoryOutlined /></button>
      <button type="button" title="新建会话" aria-label="新建会话" data-testid="create-session" @click="$emit('create-session')"><PlusOutlined /></button>
      <button type="button" :title="focused ? '退出专注模式' : '进入专注模式'" :aria-label="focused ? '退出专注模式' : '进入专注模式'" @click="$emit('toggle-focus')">
        <CompressOutlined v-if="focused" /><ArrowsAltOutlined v-else />
      </button>
    </nav>
  </header>
</template>

<style scoped lang="less">
.agent-header { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 14px; min-height: 64px; padding: 10px 14px; border-bottom: 1px solid var(--border-subtle); background: var(--bg-surface); }
.agent-title { display: flex; align-items: center; gap: 9px; min-width: 0; }
.agent-mark { width: 34px; height: 34px; display: grid; place-items: center; border: 1px solid var(--accent-border); border-radius: var(--radius-md); color: var(--accent); background: var(--accent-soft); }
.agent-title div { display: grid; gap: 2px; }
.agent-title strong { color: var(--text-primary); font-size: 13px; }
.agent-title span { color: var(--text-muted); font-size: 11px; }
.agent-meta { min-width: 0; display: flex; align-items: center; gap: 7px; overflow: hidden; color: var(--text-muted); font-size: 11px; }
.status-dot { flex: 0 0 auto; width: 7px; height: 7px; border-radius: 50%; background: var(--accent); }
.status-dot.busy { background: var(--warning); box-shadow: 0 0 0 4px color-mix(in srgb, var(--warning) 12%, transparent); }
.status-dot.failed { background: var(--danger); }
.model-name, .style-name { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.model-name { color: var(--text-secondary); }
.style-name { padding-left: 7px; border-left: 1px solid var(--border-subtle); }
.status-detail { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--warning); }
.agent-actions { display: flex; gap: 4px; }
.agent-actions button { width: 32px; height: 32px; display: grid; place-items: center; border: 1px solid transparent; background: transparent; color: var(--text-muted); cursor: pointer; }
.agent-actions button:hover, .agent-actions button:focus-visible { border-color: var(--accent-border); color: var(--accent); background: var(--accent-soft); outline: none; }
@media (max-width: 560px) { .agent-header { grid-template-columns: minmax(0, 1fr) auto; } .agent-meta { grid-column: 1 / -1; grid-row: 2; } }
</style>
