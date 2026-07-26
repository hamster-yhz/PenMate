<template>
  <div class="tree-node" @contextmenu.stop>
    <a-dropdown :trigger="['contextmenu']">
      <div
        class="tree-item volume"
        :class="{ expanded }"
        role="button"
        tabindex="0"
        draggable="true"
        @click="toggleExpanded"
        @keydown.enter="toggleExpanded"
        @keydown.space.prevent="toggleExpanded"
        @dragstart.stop="handleDragStart"
        @dragover.prevent
        @drop.prevent.stop="handleDrop"
      >
      <CaretDownOutlined v-if="expanded" class="tree-arrow" />
      <CaretRightOutlined v-else class="tree-arrow" />
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
      </div>
      <template #overlay>
        <a-menu @click="handleMenuClick">
          <a-menu-item key="add"><FileAddOutlined />在本卷新建章节</a-menu-item>
          <a-menu-item key="rename"><EditOutlined />重命名</a-menu-item>
          <a-menu-item key="up"><ArrowUpOutlined />上移</a-menu-item>
          <a-menu-item key="down"><ArrowDownOutlined />下移</a-menu-item>
          <a-menu-divider />
          <a-menu-item key="delete" danger><DeleteOutlined />删除卷</a-menu-item>
        </a-menu>
      </template>
    </a-dropdown>

    <div v-if="expanded" class="tree-children">
      <OutlineChapterNode
        v-for="(chapter, chapterIndex) in volume.children"
        :key="chapter.key"
        :chapter="chapter"
        :parent-key="volume.key"
        :is-active="activeChapterKey === String(chapter.chapterId || chapter.key)"
        :display-no="chapterIndex + 1"
        @select-chapter="emit('select-chapter', $event)"
        @rename-node="emit('rename-node', $event)"
        @move-node="emit('move-node', $event)"
        @add-chapter="emit('add-chapter', volume)"
        @delete-chapter="emit('delete-chapter', $event)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { ArrowDownOutlined, ArrowUpOutlined, CaretDownOutlined, CaretRightOutlined, DeleteOutlined, EditOutlined, FileAddOutlined } from '@ant-design/icons-vue'
import { Dropdown as ADropdown, Menu as AMenu, MenuDivider as AMenuDivider, MenuItem as AMenuItem, Modal } from 'ant-design-vue'

import type { OutlineVolumeNode } from '@/composables/workbench/workbenchOutline'
import type {
  DeleteChapterPayload,
  MoveNodePayload,
  RenameNodePayload,
} from '@/composables/workbench/useWorkbenchOutline'

import OutlineChapterNode from './OutlineChapterNode.vue'

const props = defineProps<{
  volume: OutlineVolumeNode
  displayIndex: number
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

type DraggedDirectoryNode = {
  nodeKey: string
  parentKey?: string
  nodeType: 'VOLUME' | 'CHAPTER'
}

const handleDragStart = (event: DragEvent) => {
  event.dataTransfer?.setData('application/x-penmate-directory-node', JSON.stringify({
    nodeKey: props.volume.key,
    nodeType: 'VOLUME',
  } satisfies DraggedDirectoryNode))
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
}

const handleDrop = (event: DragEvent) => {
  const raw = event.dataTransfer?.getData('application/x-penmate-directory-node')
  if (!raw) return
  try {
    const dragged = JSON.parse(raw) as DraggedDirectoryNode
    if (dragged.nodeType === 'CHAPTER' && dragged.parentKey) {
      emit('move-node', {
        nodeKey: dragged.nodeKey,
        parentKey: dragged.parentKey,
        targetParentKey: props.volume.key,
        targetIndex: props.volume.children.length,
        drop: true,
      })
      return
    }
    if (dragged.nodeType === 'VOLUME') {
      const target = event.currentTarget as HTMLElement
      const rect = target.getBoundingClientRect()
      const after = event.clientY >= rect.top + rect.height / 2
      emit('move-node', {
        nodeKey: dragged.nodeKey,
        targetIndex: props.displayIndex + (after ? 1 : 0),
        drop: true,
      })
    }
  } catch {
    // Ignore drag payloads from outside the directory.
  }
}

const confirmDelete = () => {
  const chapterCount = props.volume.children.length
  Modal.confirm({
    title: `删除“${props.volume.title}”？`,
    content: chapterCount
      ? `卷内 ${chapterCount} 个章节及其正文会一并删除，此操作无法撤销。`
      : '此操作无法撤销。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: () => emit('delete-volume', props.volume.key),
  })
}

const handleMenuClick = ({ key, domEvent }: { key: string | number; domEvent: Event }) => {
  domEvent.stopPropagation()
  if (key === 'add') emit('add-chapter', props.volume)
  if (key === 'rename') void startRename()
  if (key === 'up') emitMove(-1)
  if (key === 'down') emitMove(1)
  if (key === 'delete') confirmDelete()
}
</script>

<style scoped>
.tree-node,
.tree-children,
.tree-item {
  width: 100%;
  min-width: 0;
  max-width: 100%;
}

.tree-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
}
.tree-item:hover { background: var(--bg-subtle); color: var(--text-primary); }

.tree-children {
  padding-left: 12px;
}

.tree-arrow {
  font-size: 10px;
  color: var(--text-muted);
  min-width: 12px;
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
