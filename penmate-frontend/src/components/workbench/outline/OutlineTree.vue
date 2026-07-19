<template>
  <div class="outline-tree">
    <div class="tree-actions">
      <button class="tree-btn" :disabled="busy" @click="emit('add-volume')">+ 新卷</button>
    </div>

    <div class="tree-root">
      <OutlineVolumeNode
        v-for="volume in volumes"
        :key="volume.key"
        :volume="volume"
        :active-chapter-key="activeChapterKey"
        @select-chapter="emit('select-chapter', $event)"
        @rename-node="emit('rename-node', $event)"
        @move-node="emit('move-node', $event)"
        @add-chapter="emit('add-chapter', $event)"
        @delete-volume="emit('delete-volume', $event)"
        @delete-chapter="emit('delete-chapter', $event)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import type { OutlineChapterNode, OutlineVolumeNode as OutlineVolume } from '@/composables/workbench/workbenchOutline'
import type {
  DeleteChapterPayload,
  MoveNodePayload,
  RenameNodePayload,
} from '@/composables/workbench/useWorkbenchOutline'

import OutlineVolumeNodeItem from './OutlineVolumeNode.vue'

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

<style scoped lang="less">
.tree-actions {
  padding: 4px 0 8px;
}

.tree-btn {
  padding: 4px 12px;
  font-size: 0.75rem;
  border: 1px solid rgba(201, 169, 110, 0.35);
  border-radius: 999px;
  background: rgba(201, 169, 110, 0.08);
  cursor: pointer;
}

.tree-root {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>
