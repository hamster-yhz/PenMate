<script setup lang="ts">
import { DownOutlined, UndoOutlined, UpOutlined } from '@ant-design/icons-vue'
import type { StoryBibleChangeset } from '@/entities/story-bible/model'
import { useStoryBibleHistory } from '@/features/workbench/useStoryBibleHistory'

const props = defineProps<{
  projectId: string
  currentRevision?: number
  history: StoryBibleChangeset[]
  hasMore?: boolean
  loadingMore?: boolean
}>()
const emit = defineEmits<{
  (event: 'openRun', runId: string): void
  (event: 'undone'): void
  (event: 'loadMore'): void
}>()

const {
  expandedId, detailById, loadingId, undoingId, canUndo, undoReason, toggle, undo, formatTime, actorName,
  operationLabel,
} = useStoryBibleHistory({
  getProjectId: () => props.projectId,
  getCurrentRevision: () => props.currentRevision,
  getHistory: () => props.history,
  onUndone: () => emit('undone'),
})
</script>

<template>
  <div class="history-tab">
    <article v-for="change in history" :key="change.changesetId" class="history-entry">
      <div class="history-row">
        <span class="actor" :class="change.actorType.toLowerCase()">{{ actorName(change.actorType) }}</span>
        <button class="summary" type="button" @click="toggle(change)">
          <strong>{{ change.changeSummary }}</strong>
          <small>
            {{ formatTime(change.createdAt) }}
            <span v-if="change.archivedAt"> · 已归档</span>
            <span v-if="change.undoneAt"> · 已撤回</span>
          </small>
        </button>
        <div class="actions">
          <button v-if="change.sourceRunId" type="button" @click="emit('openRun', change.sourceRunId)">AI 任务</button>
          <button type="button" :disabled="!canUndo(change) || !!undoingId" :title="undoReason(change)" :aria-label="undoReason(change)" @click="undo(change)"><UndoOutlined /></button>
          <button type="button" :aria-label="expandedId === change.changesetId ? '收起详情' : '展开详情'" @click="toggle(change)"><UpOutlined v-if="expandedId === change.changesetId" /><DownOutlined v-else /></button>
        </div>
      </div>
      <div v-if="expandedId === change.changesetId" class="change-details">
        <span v-if="loadingId === change.changesetId">加载中</span>
        <div v-for="item in detailById[change.changesetId]?.items || []" :key="item.changeItemId" class="change-item">
          <b>{{ operationLabel(item.operation) }}</b>
          <span>{{ item.entityType }} · {{ item.entityId }} · {{ item.fieldPath }}</span>
          <details v-if="item.beforeJson != null || item.afterJson != null">
            <summary>值变化</summary>
            <pre>{{ item.beforeJson ?? '∅' }}
→
{{ item.afterJson ?? '∅' }}</pre>
          </details>
        </div>
      </div>
    </article>
    <div v-if="!history.length" class="empty-state">暂无变更记录</div>
    <button v-if="hasMore" type="button" class="load-more" :disabled="loadingMore" @click="emit('loadMore')">
      {{ loadingMore ? '加载中' : '加载更早记录' }}
    </button>
  </div>
</template>

<style scoped lang="less">
.history-tab { padding: 8px 16px; }
.history-entry { border-bottom: 1px solid var(--border-subtle); }
.history-row { min-height: 58px; display: grid; grid-template-columns: 52px minmax(0, 1fr) auto; align-items: center; gap: 10px; }
.actor { padding: 3px 5px; border: 1px solid var(--border-subtle); border-radius: 3px; color: var(--text-muted); font-size: 0.62rem; text-align: center; }
.actor.agent { color: var(--accent); border-color: var(--accent-border); }
.summary { min-width: 0; display: grid; gap: 3px; padding: 0; border: 0; background: transparent; text-align: left; cursor: pointer; }
.summary strong { overflow: hidden; color: var(--text-primary); font-size: 0.78rem; text-overflow: ellipsis; white-space: nowrap; }
.summary small { color: var(--text-muted); font-size: 0.66rem; }
.actions { display: flex; gap: 4px; }
.actions button { min-width: 28px; height: 28px; display: grid; place-items: center; padding: 0 7px; border: 1px solid var(--border-subtle); border-radius: 3px; color: var(--text-secondary); background: transparent; cursor: pointer; }
.actions button:disabled { cursor: not-allowed; opacity: 0.38; }
.change-details { display: grid; gap: 6px; padding: 4px 0 12px 62px; }
.change-item { display: grid; grid-template-columns: 42px minmax(0, 1fr); gap: 5px 8px; color: var(--text-secondary); font-size: 11px; }
.change-item b { color: var(--accent); }
.change-item details { grid-column: 2; }
.change-item summary { cursor: pointer; color: var(--text-muted); }
.change-item pre { max-height: 180px; margin: 5px 0 0; padding: 7px; overflow: auto; border: 1px solid var(--border-subtle); background: var(--bg-subtle); color: var(--text-secondary); font: 10px/1.45 ui-monospace, monospace; white-space: pre-wrap; overflow-wrap: anywhere; }
.empty-state { padding: 28px; text-align: center; color: var(--text-muted); }
.load-more { width: 100%; min-height: 36px; border: 0; border-top: 1px solid var(--border-subtle); color: var(--accent); background: transparent; cursor: pointer; }
.load-more:disabled { color: var(--text-muted); cursor: wait; }
</style>
