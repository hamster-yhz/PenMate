<script setup lang="ts">
import { computed } from 'vue'
import { CloseOutlined, RobotOutlined, UndoOutlined } from '@ant-design/icons-vue'
import type { ChapterAiUndoOperation } from '@/entities/chapter/model'

const props = withDefaults(
  defineProps<{
    operations?: ChapterAiUndoOperation[]
    busyOperationId?: string
    busyRunId?: string
    dismissBusyOperationId?: string
    dismissAllBusy?: boolean
  }>(),
  {
    operations: () => [],
    busyOperationId: '',
    busyRunId: '',
    dismissBusyOperationId: '',
    dismissAllBusy: false,
  },
)

const emit = defineEmits<{
  undo: [operationId: string]
  'undo-run': [runId: string]
  dismiss: [operationId: string]
  'dismiss-all': []
}>()

const latestSequenceByChapter = computed(() => {
  const latest = new Map<string, number>()
  for (const operation of props.operations) {
    latest.set(operation.chapterId, Math.max(latest.get(operation.chapterId) ?? 0, operation.sequenceNo))
  }
  return latest
})

const canUndo = (operation: ChapterAiUndoOperation) =>
  operation.sequenceNo === latestSequenceByChapter.value.get(operation.chapterId)

const canUndoRun = (operations: ChapterAiUndoOperation[]) => operations.every(canUndo)

const runGroups = computed(() => {
  const grouped = new Map<string, ChapterAiUndoOperation[]>()
  for (const operation of props.operations) {
    const entries = grouped.get(operation.runId) || []
    entries.push(operation)
    grouped.set(operation.runId, entries)
  }
  return [...grouped.entries()].map(([runId, operations]) => ({ runId, operations }))
})
</script>

<template>
  <section v-if="operations.length" class="ai-edit-activity" aria-label="可撤回的 AI 修改">
    <div class="activity-title">
      <RobotOutlined /><span>AI 已修改正文</span>
      <button
        type="button"
        class="dismiss-all"
        :disabled="dismissAllBusy"
        aria-label="放弃全部 AI 撤回记录"
        title="放弃全部撤回记录"
        @click="emit('dismiss-all')"
      >
        <CloseOutlined />
      </button>
    </div>
    <div v-for="group in runGroups" :key="group.runId" class="activity-run">
      <button
        v-if="group.operations.length > 1"
        type="button"
        class="undo-all"
        :disabled="busyRunId === group.runId || !canUndoRun(group.operations)"
        :title="canUndoRun(group.operations) ? '撤回本次全部' : '需先处理更新的 AI 修改'"
        @click="emit('undo-run', group.runId)"
      >
        <UndoOutlined />撤回本次全部
      </button>
      <div v-for="operation in group.operations" :key="operation.operationId" class="activity-row">
        <span :title="operation.chapterTitle">{{ operation.chapterTitle || '未命名章节' }}</span>
        <small v-if="!canUndo(operation)">等待上一条</small>
        <div class="activity-actions">
          <button
            type="button"
            :disabled="dismissBusyOperationId === operation.operationId || dismissAllBusy"
            :aria-label="`放弃 ${operation.chapterTitle || '章节'} 的撤回记录`"
            title="放弃此条及更早的撤回记录"
            @click="emit('dismiss', operation.operationId)"
          >
            <CloseOutlined />
          </button>
          <button
            type="button"
            :disabled="!canUndo(operation) || busyOperationId === operation.operationId || Boolean(busyRunId)"
            :aria-label="`撤回 ${operation.chapterTitle || '章节'} 的 AI 修改`"
            :title="canUndo(operation) ? '撤回 AI 修改' : '需先撤回更新的 AI 修改'"
            @click="emit('undo', operation.operationId)"
          >
            <UndoOutlined />
          </button>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.ai-edit-activity {
  flex: 0 0 auto;
  max-height: 164px;
  overflow: auto;
  padding: 8px 12px;
  border-top: 1px solid var(--border-subtle);
  background: var(--info-soft);
}
.activity-title,
.activity-row {
  display: flex;
  align-items: center;
}
.activity-title {
  gap: 6px;
  margin-bottom: 5px;
  color: var(--info);
  font-size: 12px;
  font-weight: 650;
}
.activity-title span {
  flex: 1;
}
.activity-run {
  position: relative;
}
.activity-row {
  justify-content: space-between;
  gap: 8px;
  min-height: 28px;
  color: var(--text-secondary);
  font-size: 12px;
}
.activity-row span {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.activity-row small {
  flex: 0 0 auto;
  color: var(--text-tertiary);
  font-size: 11px;
}
.activity-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
}
.activity-row button,
.undo-all,
.dismiss-all {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 5px;
  color: var(--info);
  background: transparent;
  border: 0;
  cursor: pointer;
}
.activity-row button,
.dismiss-all {
  flex: 0 0 auto;
  width: 26px;
  height: 26px;
  justify-content: center;
}
.undo-all {
  position: absolute;
  top: -25px;
  right: 30px;
  font-size: 11px;
}
.activity-row button:hover,
.activity-row button:focus-visible,
.undo-all:hover,
.undo-all:focus-visible,
.dismiss-all:hover,
.dismiss-all:focus-visible {
  color: var(--text-primary);
  outline: 1px solid var(--info);
}
button:disabled {
  cursor: wait;
  opacity: 0.55;
}
</style>
