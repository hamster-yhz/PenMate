<template>
  <div
    class="tree-item chapter"
    :class="{ active: isActive }"
    :data-testid="`chapter-node-${chapter.key}`"
    role="button"
    tabindex="0"
    @click="emit('select-chapter', chapter)"
    @keydown.enter="emit('select-chapter', chapter)"
    @keydown.space.prevent="emit('select-chapter', chapter)"
  >
    <span class="tree-dot">◇</span>
    <input
      v-if="isEditing"
      ref="renameInputRef"
      v-model="renameValue"
      class="tree-edit-input"
      :data-testid="`rename-input-${chapter.key}`"
      @blur="submitRename"
      @keydown.enter.prevent="submitRename"
      @keydown.escape.prevent="cancelRename"
      @click.stop
    />
    <span v-else class="tree-label" :data-testid="`chapter-label-${chapter.key}`">{{ chapter.title }}</span>
    <div class="tree-item-actions" @click.stop>
      <button class="tree-act-btn" :data-testid="`rename-node-${chapter.key}`" title="重命名" @click="startRename">
        ✏️
      </button>
      <button class="tree-act-btn" :data-testid="`move-up-node-${chapter.key}`" title="上移" @click="emitMove(-1)">
        ↑
      </button>
      <button class="tree-act-btn" :data-testid="`move-down-node-${chapter.key}`" title="下移" @click="emitMove(1)">
        ↓
      </button>
      <button
        class="tree-act-btn danger"
        title="删除"
        @click="emit('delete-chapter', { nodeKey: chapter.key, parentKey })"
      >
        ✕
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref } from 'vue'

import type { OutlineChapterNode } from '@/composables/workbench/workbenchOutline'
import type {
  DeleteChapterPayload,
  MoveNodePayload,
  RenameNodePayload,
} from '@/composables/workbench/useWorkbenchOutline'

const props = defineProps<{
  chapter: OutlineChapterNode
  parentKey: string
  isActive: boolean
}>()

const emit = defineEmits<{
  (event: 'select-chapter', chapter: OutlineChapterNode): void
  (event: 'rename-node', payload: RenameNodePayload): void
  (event: 'move-node', payload: MoveNodePayload): void
  (event: 'delete-chapter', payload: DeleteChapterPayload): void
}>()

const isEditing = ref(false)
const renameValue = ref('')
const renameInputRef = ref<HTMLInputElement | null>(null)

const startRename = async () => {
  renameValue.value = props.chapter.title
  isEditing.value = true
  await nextTick()
  renameInputRef.value?.focus()
}

const cancelRename = () => {
  isEditing.value = false
  renameValue.value = props.chapter.title
}

const submitRename = () => {
  const title = renameValue.value.trim()
  isEditing.value = false
  if (!title) return
  emit('rename-node', {
    nodeKey: props.chapter.key,
    title,
  })
}

const emitMove = (direction: -1 | 1) => {
  emit('move-node', {
    nodeKey: props.chapter.key,
    parentKey: props.parentKey,
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

  &.active {
    background: rgba(201, 169, 110, 0.14);
    color: var(--text-primary);
  }
}

.tree-dot {
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
