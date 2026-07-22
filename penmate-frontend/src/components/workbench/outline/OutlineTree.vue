<template>
  <a-dropdown :trigger="['contextmenu']" :disabled="busy">
    <div class="outline-tree">
      <OutlineVolumeNode
        v-for="volume in volumes"
        :key="volume.key"
        :volume="volume"
        :display-index="volumes.indexOf(volume)"
        :active-chapter-key="activeChapterKey"
        @select-chapter="emit('select-chapter', $event)"
        @rename-node="emit('rename-node', $event)"
        @move-node="emit('move-node', $event)"
        @add-chapter="emit('add-chapter', $event)"
        @delete-volume="emit('delete-volume', $event)"
        @delete-chapter="emit('delete-chapter', $event)"
      />
      <div v-if="!volumes.length" class="directory-empty" aria-label="作品目录为空"></div>
    </div>
    <template #overlay>
      <a-menu @click="({ key }: { key: string | number }) => key === 'add-volume' && emit('add-volume')">
        <a-menu-item key="add-volume"><FolderAddOutlined />新建卷</a-menu-item>
      </a-menu>
    </template>
  </a-dropdown>
</template>

<script setup lang="ts">
import { Dropdown as ADropdown, Menu as AMenu, MenuItem as AMenuItem } from 'ant-design-vue'
import type { OutlineChapterNode, OutlineVolumeNode as OutlineVolume } from '@/composables/workbench/workbenchOutline'
import type {
  DeleteChapterPayload,
  MoveNodePayload,
  RenameNodePayload,
} from '@/composables/workbench/useWorkbenchOutline'

import OutlineVolumeNodeItem from './OutlineVolumeNode.vue'
import { FolderAddOutlined } from '@ant-design/icons-vue'

defineOptions({
  components: {
    OutlineVolumeNode: OutlineVolumeNodeItem,
  },
})

defineProps<{
  volumes: OutlineVolume[]
  activeChapterKey: string
  busy: boolean
}>()

const emit = defineEmits<{
  (event: 'select-chapter', payload: OutlineChapterNode): void
  (event: 'rename-node', payload: RenameNodePayload): void
  (event: 'move-node', payload: MoveNodePayload): void
  (event: 'add-volume'): void
  (event: 'add-chapter', volume: OutlineVolume): void
  (event: 'delete-volume', nodeKey: string): void
  (event: 'delete-chapter', payload: DeleteChapterPayload): void
}>()
</script>

<style scoped>
.outline-tree {
  display: flex;
  width: 100%;
  min-width: 0;
  min-height: 100%;
  flex-direction: column;
  gap: 2px;
}
.directory-empty { min-height: 160px; }
</style>
