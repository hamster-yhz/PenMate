<template>
  <a-dropdown :trigger="['contextmenu']">
    <div
      class="tree-item chapter"
      :class="{ active: isActive }"
      :data-testid="`chapter-node-${chapter.key}`"
      role="button"
      tabindex="0"
      :draggable="!isEditing"
      @click="emit('select-chapter', chapter)"
      @keydown.enter="emit('select-chapter', chapter)"
      @keydown.space.prevent="emit('select-chapter', chapter)"
      @dragstart.stop="handleDragStart"
      @dragover.prevent
      @drop.prevent.stop="handleDrop"
      @contextmenu.stop
    >
    <span class="chapter-no">{{ displayNo }}</span>
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
    </div>
    <template #overlay>
      <a-menu @click="handleMenuClick">
        <a-menu-item key="add"><FileAddOutlined />在本卷新建章节</a-menu-item>
        <a-menu-divider />
        <a-menu-item key="rename"><EditOutlined />重命名</a-menu-item>
        <a-menu-item key="up"><ArrowUpOutlined />上移</a-menu-item>
        <a-menu-item key="down"><ArrowDownOutlined />下移</a-menu-item>
        <a-menu-divider />
        <a-menu-item key="delete" danger><DeleteOutlined />删除章节</a-menu-item>
      </a-menu>
    </template>
  </a-dropdown>
</template>

<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { ArrowDownOutlined, ArrowUpOutlined, DeleteOutlined, EditOutlined, FileAddOutlined } from '@ant-design/icons-vue'
import { Dropdown as ADropdown, Menu as AMenu, MenuDivider as AMenuDivider, MenuItem as AMenuItem, Modal } from 'ant-design-vue'

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
  displayNo: number
}>()

const emit = defineEmits<{
  (event: 'select-chapter', chapter: OutlineChapterNode): void
  (event: 'rename-node', payload: RenameNodePayload): void
  (event: 'move-node', payload: MoveNodePayload): void
  (event: 'add-chapter'): void
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

type DraggedDirectoryNode = {
  nodeKey: string
  parentKey?: string
  nodeType: 'VOLUME' | 'CHAPTER'
}

const handleDragStart = (event: DragEvent) => {
  event.dataTransfer?.setData('application/x-penmate-directory-node', JSON.stringify({
    nodeKey: props.chapter.chapterId || props.chapter.key,
    parentKey: props.parentKey,
    nodeType: 'CHAPTER',
  } satisfies DraggedDirectoryNode))
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
}

const handleDrop = (event: DragEvent) => {
  const raw = event.dataTransfer?.getData('application/x-penmate-directory-node')
  if (!raw) return
  try {
    const dragged = JSON.parse(raw) as DraggedDirectoryNode
    if (dragged.nodeType !== 'CHAPTER' || !dragged.parentKey) return
    const target = event.currentTarget as HTMLElement
    const rect = target.getBoundingClientRect()
    const after = event.clientY >= rect.top + rect.height / 2
    emit('move-node', {
      nodeKey: dragged.nodeKey,
      parentKey: dragged.parentKey,
      targetParentKey: props.parentKey,
      targetIndex: props.displayNo - 1 + (after ? 1 : 0),
      drop: true,
    })
  } catch {
    // Ignore drag payloads from outside the directory.
  }
}

const confirmDelete = () => {
  Modal.confirm({
    title: `删除“${props.chapter.title}”？`,
    content: '章节正文会一并删除，此操作无法撤销。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: () => emit('delete-chapter', { nodeKey: props.chapter.key, parentKey: props.parentKey }),
  })
}

const handleMenuClick = ({ key, domEvent }: { key: string | number; domEvent: Event }) => {
  domEvent.stopPropagation()
  if (key === 'add') emit('add-chapter')
  if (key === 'rename') void startRename()
  if (key === 'up') emitMove(-1)
  if (key === 'down') emitMove(1)
  if (key === 'delete') confirmDelete()
}
</script>

<style scoped>
.tree-item {
  display: flex;
  width: 100%;
  min-width: 0;
  max-width: 100%;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
}
.tree-item:hover { background: var(--bg-subtle); color: var(--text-primary); }
.tree-item.active { background: var(--accent-soft); color: var(--text-primary); }

.chapter-no {
  flex: 0 0 auto;
  width: 18px;
  overflow: hidden;
  color: var(--text-muted);
  font-size: 10px;
  text-align: right;
}

.tree-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-edit-input {
  flex: 1;
  padding: 2px 6px;
  border: 1px solid var(--accent-border);
  border-radius: var(--radius-sm);
  outline: none;
  background: var(--bg-surface);
}
</style>
