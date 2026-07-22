<template>
  <div class="sb-node-list">
    <button
      v-for="node in nodes"
      :key="node.nodeId"
      type="button"
      class="node-row"
      :class="{ active: node.nodeId === selectedNodeId }"
      @click="emit('select', node.nodeId)"
    >
      <span class="canon-dot" :class="node.canonStatus.toLowerCase()"></span>
      <span class="node-copy">
        <strong>{{ node.title }}</strong>
        <small>{{ typeName(node.typeId) }} · {{ statusName(node.canonStatus) }}</small>
      </span>
    </button>
    <div v-if="!nodes.length" class="empty-state">暂无符合条件的节点</div>
  </div>
</template>

<script setup lang="ts">
import type { StoryBibleNode, StoryBibleNodeType } from '@/entities/story-bible/model'

const props = defineProps<{ nodes: StoryBibleNode[]; nodeTypes: StoryBibleNodeType[]; selectedNodeId: string }>()
const emit = defineEmits<{ (event: 'select', nodeId: string): void }>()
const typeName = (typeId: string) => props.nodeTypes.find((item) => item.typeId === typeId)?.displayName || '未分类'
const statusName = (status: StoryBibleNode['canonStatus']) => ({ DRAFT: '草稿', CANON: '已确认', ARCHIVED: '已归档' })[status]
</script>

<style scoped lang="less">
.sb-node-list {
  min-height: 0;
  overflow: auto;
}
.node-row {
  width: 100%;
  min-height: 54px;
  display: grid;
  grid-template-columns: 8px minmax(0, 1fr);
  gap: 8px;
  align-items: center;
  padding: 8px 10px;
  border: 0;
  border-bottom: 1px solid var(--border-subtle);
  color: var(--text-primary);
  background: transparent;
  text-align: left;
  cursor: pointer;
}
.node-row:hover,
.node-row.active {
  background: var(--accent-soft);
}
.node-row.active {
  box-shadow: inset 2px 0 var(--accent);
}
.canon-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #8b93a7;
}
.canon-dot.canon {
  background: var(--jade-green);
}
.canon-dot.archived {
  background: #a56c68;
}
.node-copy {
  min-width: 0;
  display: grid;
  gap: 3px;
}
.node-copy strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.84rem;
}
.node-copy small {
  color: var(--text-muted);
  font-size: 0.68rem;
}
.empty-state {
  padding: 24px 12px;
  text-align: center;
  color: var(--text-muted);
  font-size: 0.78rem;
}
</style>
