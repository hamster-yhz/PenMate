<template>
  <div class="tree-node">
    <div class="tree-item volume" :class="{ expanded: expanded }" @click="toggleExpanded">
      <span class="tree-arrow">{{ expanded ? '▾' : '▸' }}</span>
      <input
        v-if="isEditing"
        ref="renameInputRef"
        v-model="renameValue"
        class="tree-edit-input"
        :data-testid="`rename-input-${volume.key}`"
        @blur="submitRename"
        @keydown.enter.prevent="submitRename"
        @keydown.escape.prevent="cancelRename"
        @click.stop
      />
      <span v-else class="tree-label" :data-testid="`volume-label-${volume.key}`">{{ volume.title }}</span>
      <div class="tree-item-actions" @click.stop>
        <button class="tree-act-btn" :data-testid="`rename-node-${volume.key}`" title="重命名" @click="startRename">✏️</button>
        <button class="tree-act-btn" title="添加章节" @click="emit('add-chapter', volume)">+</button>
        <button class="tree-act-btn" :data-testid="`move-up-node-${volume.key}`" title="上移" @click="emitMove(-1)">↑</button>
        <button class="tree-act-btn" :data-testid="`move-down-node-${volume.key}`" title="下移" @click="emitMove(1)">↓</button>
        <button class="tree-act-btn danger" title="删除" @click="emit('delete-volume', volume.key)">✕</button>
      </div>
    </div>

    <div v-if="expanded" class="tree-children">
      <OutlineChapterNode
        v-for="chapter in volume.children"
        :key="chapter.key"
        :chapter="chapter"
        :parent-key="volume.key"
        :is-active="activeChapterKey === String(chapter.chapterId || chapter.key)"
        @select-chapter="emit('select-chapter', $event)"
        @rename-node="emit('rename-node', $event)"
        @move-node="emit('move-node', $event)"
        @delete-chapter="emit('delete-chapter', $event)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'

import type { OutlineVolumeNode } from '@/composables/workbench/workbenchOutline'
import type { DeleteChapterPayload, MoveNodePayload, RenameNodePayload } from '@/composables/workbench/useWorkbenchOutline'

import OutlineChapterNode from './OutlineChapterNode.vue'

const props = defineProps<{
  volume: OutlineVolumeNode
  activeChapterKey: string
}>()

const emit = defineEmits<{
  (event: 'select-chapter', payload: OutlineVolumeNode['children'][number]): void
  (event: 'rename-node', payload: RenameNodePayload): void
  (event: 'move-node', payload: MoveNodePayload): void
  (event: 'add-chapter', volume: OutlineVolumeNode): void
  (event: 'delete-volume', nodeKey: string): void
  (event: 'delete-chapter', payload: DeleteChapterPayload): void
}>()

const expanded = ref(props.volume.expanded)
const isEditing = ref(false)
const renameValue = ref('')
const renameInputRef = ref<HTMLInputElement | null>(null)

watch(
  () => props.volume.expanded,
  (value) => {
    expanded.value = value
  },
)

const toggleExpanded = () => {
  expanded.value = !expanded.value
}

const startRename = async () => {
  renameValue.value = props.volume.title
  isEditing.value = true
  await nextTick()
  renameInputRef.value?.focus()
}

const cancelRename = () => {
  isEditing.value = false
  renameValue.value = props.volume.title
}

const submitRename = () => {
  const title = renameValue.value.trim()
  isEditing.value = false
  if (!title) return
  emit('rename-node', {
    nodeKey: props.volume.key,
    title,
  })
}

const emitMove = (direction: -1 | 1) => {
  emit('move-node', {
    nodeKey: props.volume.key,
    direction,
  })
}
</script>

<style scoped lang="less">
.tree-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border-radius: 8px;
  color: var(--text-secondary);

  &:hover {
    background: rgba(201, 169, 110, 0.06);
    color: var(--text-primary);

    .tree-item-actions {
      opacity: 1;
    }
  }
}

.tree-children {
  padding-left: 12px;
}

.tree-arrow {
  font-size: 0.65rem;
  color: var(--text-muted);
  min-width: 12px;
}

.tree-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-edit-input {
  flex: 1;
  padding: 2px 6px;
  border: 1px solid rgba(201, 169, 110, 0.3);
  border-radius: 6px;
  outline: none;
  background: rgba(255, 255, 255, 0.95);
}

.tree-item-actions {
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.2s;
}

.tree-act-btn {
  padding: 1px 5px;
  border: 0;
  background: none;
  border-radius: 4px;
  cursor: pointer;

  &:hover {
    background: rgba(201, 169, 110, 0.12);
  }

  &.danger:hover {
    background: rgba(213, 95, 76, 0.12);
    color: #d55f4c;
  }
}
</style>
