<template>
  <div class="relationship-graph" role="img" :aria-label="`${currentNode?.title || '当前设定'}的关系图`">
    <svg viewBox="0 0 600 320" aria-hidden="true">
      <g v-for="edge in edges" :key="edge.relation.relationId">
        <line :x1="edge.from.x" :y1="edge.from.y" :x2="edge.to.x" :y2="edge.to.y" />
        <rect :x="edge.labelX - 28" :y="edge.labelY - 10" width="56" height="20" rx="3" />
        <text :x="edge.labelX" :y="edge.labelY + 4">{{ relationName(edge.relation.relationType) }}</text>
      </g>
    </svg>
    <button
      v-for="position in positions"
      :key="position.node.nodeId"
      type="button"
      :class="{ current: position.node.nodeId === nodeId }"
      :style="{ left: `${position.x / 6}%`, top: `${position.y / 3.2}%` }"
      @click="emit('select', position.node.nodeId)"
    >{{ position.node.title }}</button>
    <div v-if="!edges.length" class="empty-state">建立关系后将在这里显示</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { StoryBibleNode, StoryBibleRelation } from '@/entities/story-bible/model'

const props = defineProps<{ nodeId: string; nodes: StoryBibleNode[]; relations: StoryBibleRelation[] }>()
const emit = defineEmits<{ select: [nodeId: string] }>()
const currentNode = computed(() => props.nodes.find((node) => node.nodeId === props.nodeId))
const relatedNodes = computed(() => {
  const ids = new Set<string>([props.nodeId])
  props.relations.forEach((relation) => { ids.add(relation.sourceNodeId); ids.add(relation.targetNodeId) })
  return props.nodes.filter((node) => ids.has(node.nodeId))
})
const positions = computed(() => relatedNodes.value.map((node, _index, all) => {
  if (node.nodeId === props.nodeId) return { node, x: 300, y: 160 }
  const peers = all.filter((item) => item.nodeId !== props.nodeId)
  const peerIndex = peers.findIndex((item) => item.nodeId === node.nodeId)
  const angle = (Math.PI * 2 * peerIndex) / Math.max(1, peers.length) - Math.PI / 2
  return { node, x: 300 + Math.cos(angle) * 210, y: 160 + Math.sin(angle) * 108 }
}))
const byId = computed(() => new Map(positions.value.map((item) => [item.node.nodeId, item])))
const edges = computed(() => props.relations.flatMap((relation) => {
  const from = byId.value.get(relation.sourceNodeId)
  const to = byId.value.get(relation.targetNodeId)
  return from && to ? [{ relation, from, to, labelX: (from.x + to.x) / 2, labelY: (from.y + to.y) / 2 }] : []
}))
const labels: Record<string, string> = { ALLY_OF: '盟友', ENEMY_OF: '敌对', MEMBER_OF: '隶属', LOCATED_IN: '位于', OWNS: '拥有', FAMILY_OF: '亲属', KNOWS: '相识', CONNECTED_TO: '关联' }
const relationName = (value: string) => labels[value] || '其他关系'
</script>

<style scoped>
.relationship-graph { position: relative; min-height: 320px; overflow: hidden; background: var(--bg-subtle); border: 1px solid var(--border-subtle); }
svg { position: absolute; inset: 0; width: 100%; height: 100%; }line { stroke: var(--border-strong); stroke-width: 1.5; }rect { fill: var(--bg-surface); stroke: var(--border-subtle); }text { fill: var(--text-muted); font-size: 11px; text-anchor: middle; }
.relationship-graph button { position: absolute; z-index: 1; width: 104px; min-height: 38px; padding: 5px 7px; overflow: hidden; color: var(--text-primary); background: var(--bg-surface); border: 1px solid var(--border-strong); border-radius: 5px; box-shadow: var(--shadow-xs); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; cursor: pointer; transform: translate(-50%, -50%); }.relationship-graph button.current { color: var(--accent); border-color: var(--accent); font-weight: 600; }.empty-state { position: absolute; inset: 0; display: grid; place-items: center; color: var(--text-muted); font-size: 12px; }
@media (max-width: 620px) { .relationship-graph { min-height: 280px; }.relationship-graph button { width: 88px; font-size: 11px; } }
</style>
