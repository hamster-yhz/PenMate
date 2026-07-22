<script setup lang="ts">
import { computed } from 'vue'
import { RobotOutlined, UndoOutlined } from '@ant-design/icons-vue'
import type { ChapterAiUndoOperation } from '@/entities/chapter/model'

const props = withDefaults(defineProps<{
  operations?: ChapterAiUndoOperation[]
  busyOperationId?: string
  busyRunId?: string
}>(), {
  operations: () => [],
  busyOperationId: '',
  busyRunId: '',
})

const emit = defineEmits<{
  undo: [operationId: string]
  'undo-run': [runId: string]
}>()

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
    <div class="activity-title"><RobotOutlined /><span>AI 已修改正文</span></div>
    <div v-for="group in runGroups" :key="group.runId" class="activity-run">
      <button
        v-if="group.operations.length > 1"
        type="button"
        class="undo-all"
        :disabled="busyRunId === group.runId"
        @click="emit('undo-run', group.runId)"
      >
        <UndoOutlined />撤回本次全部
      </button>
      <div v-for="operation in group.operations" :key="operation.operationId" class="activity-row">
        <span :title="operation.chapterTitle">{{ operation.chapterTitle || '未命名章节' }}</span>
        <button
          type="button"
          :disabled="busyOperationId === operation.operationId || Boolean(busyRunId)"
          :aria-label="`撤回 ${operation.chapterTitle || '章节'} 的 AI 修改`"
          title="撤回 AI 修改"
          @click="emit('undo', operation.operationId)"
        ><UndoOutlined /></button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.ai-edit-activity { flex: 0 0 auto; max-height: 164px; overflow: auto; padding: 8px 12px; border-top: 1px solid var(--border-subtle); background: var(--info-soft); }
.activity-title, .activity-row { display: flex; align-items: center; }
.activity-title { gap: 6px; margin-bottom: 5px; color: var(--info); font-size: 12px; font-weight: 650; }
.activity-run { position: relative; }
.activity-row { justify-content: space-between; gap: 8px; min-height: 28px; color: var(--text-secondary); font-size: 12px; }
.activity-row span { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.activity-row button, .undo-all { display: inline-flex; align-items: center; gap: 4px; padding: 3px 5px; color: var(--info); background: transparent; border: 0; cursor: pointer; }
.activity-row button { flex: 0 0 auto; width: 26px; height: 26px; justify-content: center; }
.undo-all { position: absolute; top: -25px; right: 0; font-size: 11px; }
.activity-row button:hover, .activity-row button:focus-visible, .undo-all:hover, .undo-all:focus-visible { color: var(--text-primary); outline: 1px solid var(--info); }
button:disabled { cursor: wait; opacity: .55; }
</style>
