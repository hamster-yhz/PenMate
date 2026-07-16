<template>
  <div class="relations-tab">
    <form class="relation-form" @submit.prevent="submit">
      <select v-model="targetNodeId" required aria-label="目标节点">
        <option value="" disabled>选择目标节点</option>
        <option v-for="node in availableTargets" :key="node.nodeId" :value="node.nodeId">{{ node.title }}</option>
      </select>
      <input v-model="relationType" required placeholder="关系类型，例如 ALLY_OF" />
      <input v-model="description" placeholder="关系说明" />
      <button type="submit"><PlusOutlined /> 添加关系</button>
    </form>

    <div class="relation-list">
      <div v-for="relation in relations" :key="relation.relationId" class="relation-row">
        <div v-if="editingRelationId === relation.relationId" class="relation-edit">
          <select v-model="editTargetNodeId" required aria-label="编辑目标节点">
            <option v-for="node in availableTargets" :key="node.nodeId" :value="node.nodeId">{{ node.title }}</option>
          </select>
          <input v-model="editRelationType" required aria-label="编辑关系类型" />
          <input v-model="editDescription" aria-label="编辑关系说明" />
        </div>
        <div v-else class="relation-copy">
          <strong>{{ nodeName(relation.sourceNodeId) }} → {{ nodeName(relation.targetNodeId) }}</strong>
          <span>{{ relation.relationType }}</span>
          <small>{{ relation.description || '无说明' }}</small>
        </div>
        <div class="row-actions">
          <template v-if="editingRelationId === relation.relationId">
            <button type="button" title="保存关系" @click="saveEdit(relation)"><SaveOutlined /></button>
            <button type="button" title="取消编辑" @click="cancelEdit"><CloseOutlined /></button>
          </template>
          <template v-else>
            <button type="button" title="编辑关系" @click="startEdit(relation)"><EditOutlined /></button>
            <button type="button" title="删除关系" class="danger" @click="emit('delete', relation)"><DeleteOutlined /></button>
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
import type { StoryBibleNode, StoryBibleRelation, StoryBibleRelationUpdatePayload } from '@/api/modules/storyBible.api'

const props = defineProps<{ nodeId: string; nodes: StoryBibleNode[]; relations: StoryBibleRelation[] }>()
const emit = defineEmits<{
  (event: 'create', payload: Omit<StoryBibleRelation, 'relationId' | 'storyBibleId' | 'revision'>): void
  (event: 'update', payload: { relationId: string; update: StoryBibleRelationUpdatePayload }): void
  (event: 'delete', payload: StoryBibleRelation): void
}>()
const targetNodeId = ref('')
const relationType = ref('')
const description = ref('')
const editingRelationId = ref('')
const editTargetNodeId = ref('')
const editRelationType = ref('')
const editDescription = ref('')
const availableTargets = computed(() => props.nodes.filter((node) => node.nodeId !== props.nodeId))
const nodeName = (id: string) => props.nodes.find((node) => node.nodeId === id)?.title || id
const submit = () => {
  emit('create', {
    sourceNodeId: props.nodeId,
    targetNodeId: targetNodeId.value,
    relationType: relationType.value.trim().toUpperCase(),
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
      relationType: editRelationType.value.trim().toUpperCase(),
      description: editDescription.value,
      attributesJson: relation.attributesJson || '{}',
    },
  })
  cancelEdit()
}
</script>

<style scoped lang="less">
.relations-tab { padding: 16px; }
.relation-form { display: grid; grid-template-columns: minmax(130px, 0.8fr) minmax(130px, 0.7fr) minmax(160px, 1fr) auto; gap: 8px; padding-bottom: 14px; border-bottom: 1px solid var(--border-subtle); }
input, select, button { min-width: 0; height: 34px; border: 1px solid var(--border-subtle); border-radius: 4px; color: var(--text-primary); background: rgba(11, 17, 32, 0.7); }
input, select { padding: 0 8px; }
button { padding: 0 10px; color: var(--amber-gold); border-color: var(--border-gold); cursor: pointer; }
.relation-row { min-height: 66px; display: flex; align-items: center; justify-content: space-between; gap: 12px; border-bottom: 1px solid var(--border-subtle); }
.relation-copy { min-width: 0; display: grid; flex: 1; gap: 2px; }
.relation-edit { min-width: 0; display: grid; flex: 1; grid-template-columns: minmax(120px, 0.8fr) minmax(120px, 0.7fr) minmax(140px, 1fr); gap: 6px; }
.relation-row strong { color: var(--text-primary); font-size: 0.8rem; }
.relation-row span { color: var(--amber-gold); font-size: 0.7rem; }
.relation-row small { color: var(--text-muted); }
.row-actions { display: flex; flex: 0 0 auto; gap: 2px; }
.relation-row button { width: 32px; padding: 0; color: var(--amber-gold); border-color: transparent; background: transparent; }
.relation-row button.danger { color: #c9827b; }
.empty-state { padding: 28px; text-align: center; color: var(--text-muted); }
@media (max-width: 820px) { .relation-form, .relation-edit { grid-template-columns: 1fr 1fr; } }
</style>
