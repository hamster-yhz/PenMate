<template>
  <div class="relations-tab">
    <div class="view-switch" role="tablist" aria-label="关系视图">
      <button type="button" :class="{ active: viewMode === 'graph' }" @click="viewMode = 'graph'">关系图</button>
      <button type="button" :class="{ active: viewMode === 'list' }" @click="viewMode = 'list'">列表</button>
    </div>
    <StoryBibleRelationshipGraph v-if="viewMode === 'graph'" :node-id="nodeId" :nodes="nodes" :relations="relations" @select="emit('selectNode', $event)" />
    <form class="relation-form" @submit.prevent="submit">
      <select v-model="targetNodeId" required aria-label="目标节点">
        <option value="" disabled>选择目标节点</option>
        <option v-for="node in availableTargets" :key="node.nodeId" :value="node.nodeId">{{ node.title }}</option>
      </select>
      <select v-model="relationType" required aria-label="关系类型">
        <option value="" disabled>选择关系</option>
        <option v-for="option in relationOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
      </select>
      <input v-model="description" placeholder="关系说明" />
      <button type="submit"><PlusOutlined /> 添加关系</button>
    </form>

    <div v-if="viewMode === 'list'" class="relation-list">
      <div v-for="relation in relations" :key="relation.relationId" class="relation-row">
        <div v-if="editingRelationId === relation.relationId" class="relation-edit">
          <select v-model="editTargetNodeId" required aria-label="编辑目标节点">
            <option v-for="node in availableTargets" :key="node.nodeId" :value="node.nodeId">{{ node.title }}</option>
          </select>
          <select v-model="editRelationType" required aria-label="编辑关系类型">
            <option v-if="!knownRelation(editRelationType)" :value="editRelationType">其他关系</option>
            <option v-for="option in relationOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
          <input v-model="editDescription" aria-label="编辑关系说明" />
        </div>
        <div v-else class="relation-copy">
          <strong>{{ nodeName(relation.sourceNodeId) }} → {{ nodeName(relation.targetNodeId) }}</strong>
          <span>{{ relationName(relation.relationType) }}</span>
          <small>{{ relation.description || '无说明' }}</small>
        </div>
        <div class="row-actions">
          <template v-if="editingRelationId === relation.relationId">
            <button type="button" title="保存关系" @click="saveEdit(relation)"><SaveOutlined /></button>
            <button type="button" title="取消编辑" @click="cancelEdit"><CloseOutlined /></button>
          </template>
          <template v-else>
            <button type="button" title="编辑关系" @click="startEdit(relation)"><EditOutlined /></button>
            <button type="button" title="删除关系" class="danger" @click="emit('delete', relation)">
              <DeleteOutlined />
            </button>
          </template>
        </div>
      </div>
      <div v-if="!relations.length" class="empty-state">尚未建立关系</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { CloseOutlined, DeleteOutlined, EditOutlined, PlusOutlined, SaveOutlined } from '@ant-design/icons-vue'
import type { StoryBibleNode, StoryBibleRelation, StoryBibleRelationUpdatePayload } from '@/entities/story-bible/model'
import StoryBibleRelationshipGraph from './StoryBibleRelationshipGraph.vue'

const props = defineProps<{ nodeId: string; nodes: StoryBibleNode[]; relations: StoryBibleRelation[] }>()
const emit = defineEmits<{
  (event: 'create', payload: Omit<StoryBibleRelation, 'relationId' | 'storyBibleId' | 'revision'>): void
  (event: 'update', payload: { relationId: string; update: StoryBibleRelationUpdatePayload }): void
  (event: 'delete', payload: StoryBibleRelation): void
  (event: 'selectNode', nodeId: string): void
}>()
const viewMode = ref<'graph' | 'list'>('graph')
const targetNodeId = ref('')
const relationType = ref('')
const description = ref('')
const editingRelationId = ref('')
const editTargetNodeId = ref('')
const editRelationType = ref('')
const editDescription = ref('')
const relationOptions = [
  { value: 'ALLY_OF', label: '盟友' },
  { value: 'ENEMY_OF', label: '敌对' },
  { value: 'MEMBER_OF', label: '隶属' },
  { value: 'LOCATED_IN', label: '位于' },
  { value: 'OWNS', label: '拥有' },
  { value: 'FAMILY_OF', label: '亲属' },
  { value: 'KNOWS', label: '相识' },
  { value: 'CONNECTED_TO', label: '关联' },
]
const knownRelation = (value: string) => relationOptions.some((option) => option.value === value)
const relationName = (value: string) => relationOptions.find((option) => option.value === value)?.label || '其他关系'
const availableTargets = computed(() => props.nodes.filter((node) => node.nodeId !== props.nodeId))
const nodeName = (id: string) => props.nodes.find((node) => node.nodeId === id)?.title || id
const submit = () => {
  emit('create', {
    sourceNodeId: props.nodeId,
    targetNodeId: targetNodeId.value,
    relationType: relationType.value,
    description: description.value,
    attributesJson: '{}',
  })
  targetNodeId.value = ''
  relationType.value = ''
  description.value = ''
}
const startEdit = (relation: StoryBibleRelation) => {
  editingRelationId.value = relation.relationId
  editTargetNodeId.value = relation.targetNodeId
  editRelationType.value = relation.relationType
  editDescription.value = relation.description || ''
}
const cancelEdit = () => {
  editingRelationId.value = ''
}
const saveEdit = (relation: StoryBibleRelation) => {
  emit('update', {
    relationId: relation.relationId,
    update: {
      expectedRevision: relation.revision,
      targetNodeId: editTargetNodeId.value,
      relationType: editRelationType.value,
      description: editDescription.value,
      attributesJson: relation.attributesJson || '{}',
    },
  })
  cancelEdit()
}
</script>

<style scoped lang="less">
.relations-tab {
  display: grid;
  gap: 12px;
  padding: 16px;
}
.view-switch { display: flex; width: max-content; padding: 3px; background: var(--bg-subtle); border-radius: 5px; }.view-switch button { min-width: 56px; height: 28px; padding: 0 8px; color: var(--text-muted); background: transparent; border: 0; }.view-switch button.active { color: var(--text-primary); background: var(--bg-surface); box-shadow: var(--shadow-xs); }
.relation-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--border-subtle);
}
input,
select,
button {
  min-width: 0;
  height: 34px;
  border: 1px solid var(--border-subtle);
  border-radius: 4px;
  color: var(--text-primary);
  background: var(--bg-surface);
}
input,
select {
  padding: 0 8px;
}
button {
  padding: 0 10px;
  color: var(--accent);
  border-color: var(--accent-border);
  cursor: pointer;
  white-space: nowrap;
}
.relation-row {
  min-height: 66px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid var(--border-subtle);
}
.relation-copy {
  min-width: 0;
  display: grid;
  flex: 1;
  gap: 2px;
}
.relation-edit {
  min-width: 0;
  display: grid;
  flex: 1;
  grid-template-columns: minmax(120px, 0.8fr) minmax(120px, 0.7fr) minmax(140px, 1fr);
  gap: 6px;
}
.relation-row strong {
  color: var(--text-primary);
  font-size: 0.8rem;
}
.relation-row span {
  color: var(--accent);
  font-size: 0.7rem;
}
.relation-row small {
  color: var(--text-muted);
}
.row-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 2px;
}
.relation-row button {
  width: 32px;
  padding: 0;
  color: var(--accent);
  border-color: transparent;
  background: transparent;
}
.relation-row button.danger {
  color: var(--danger);
}
.empty-state {
  padding: 28px;
  text-align: center;
  color: var(--text-muted);
}
@media (max-width: 820px) {
  .relation-edit {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
