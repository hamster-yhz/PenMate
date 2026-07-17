<template>
  <div class="history-tab">
    <div v-for="change in history" :key="change.changesetId" class="history-row">
      <span class="actor" :class="change.actorType.toLowerCase()">{{ change.actorType }}</span>
      <div>
        <strong>{{ change.changeSummary }}</strong>
        <small>修订 {{ change.contentRevision }} · {{ formatTime(change.createdAt) }}</small>
      </div>
      <button v-if="change.sourceRunId" type="button" @click="emit('openRun', change.sourceRunId)">查看 Run</button>
    </div>
    <div v-if="!history.length" class="empty-state">暂无变更记录</div>
  </div>
</template>

<script setup lang="ts">
import type { StoryBibleChangeset } from '@/api/modules/storyBible.api'
defineProps<{ history: StoryBibleChangeset[] }>()
const emit = defineEmits<{ (event: 'openRun', runId: string): void }>()
const formatTime = (value: string) => value ? new Date(value).toLocaleString() : ''
</script>

<style scoped lang="less">
.history-tab { padding: 8px 16px; }
.history-row { min-height: 58px; display: grid; grid-template-columns: 52px minmax(0, 1fr) auto; align-items: center; gap: 10px; border-bottom: 1px solid var(--border-subtle); }
.actor { padding: 3px 5px; border: 1px solid var(--border-subtle); border-radius: 3px; color: var(--text-muted); font-size: 0.62rem; text-align: center; }
.actor.agent { color: var(--amber-gold); border-color: var(--border-gold); }
.history-row div { min-width: 0; display: grid; gap: 3px; }
.history-row strong { overflow: hidden; color: var(--text-primary); font-size: 0.78rem; text-overflow: ellipsis; white-space: nowrap; }
.history-row small { color: var(--text-muted); font-size: 0.66rem; }
.history-row button { height: 28px; border: 1px solid var(--border-subtle); border-radius: 3px; color: var(--text-secondary); background: transparent; cursor: pointer; }
.empty-state { padding: 28px; text-align: center; color: var(--text-muted); }
</style>
