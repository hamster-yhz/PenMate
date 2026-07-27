<template>
  <aside class="panel-left" :class="{ collapsed }" :style="panelStyle">
    <button
      v-if="collapsed"
      class="panel-reopen"
      type="button"
      title="展开作品目录"
      aria-label="展开作品目录"
      @click="$emit('toggle-collapse')"
    >
      <MenuUnfoldOutlined aria-hidden="true" />
    </button>
    <div v-if="!collapsed" class="panel-content">
      <header class="directory-header">
        <div class="directory-heading">
          <strong>作品目录</strong>
          <button class="panel-toggle" type="button" title="收起作品目录" aria-label="收起作品目录" @click="$emit('toggle-collapse')">
            <MenuFoldOutlined aria-hidden="true" />
          </button>
        </div>
        <label class="directory-search">
          <SearchOutlined />
          <input v-model="query" type="search" aria-label="搜索章节" placeholder="搜索" />
        </label>
      </header>
      <div class="tab-content">
        <OutlineTree
          :volumes="filteredOutline"
          :active-chapter-key="activeChapter"
          :selected-chapter-ids="selectedChapterIds"
          :busy="outlineOpBusy"
          @select-chapter="$emit('select-chapter', $event)"
          @toggle-chapter-selection="$emit('toggle-chapter-selection', $event)"
          @rename-node="$emit('rename-node', $event)"
          @move-node="$emit('move-node', $event)"
          @add-volume="$emit('add-volume')"
          @add-chapter="$emit('add-chapter', $event)"
          @delete-volume="$emit('delete-volume', $event)"
          @delete-chapter="$emit('delete-chapter', $event)"
        />
      </div>
      <div v-if="pendingMoveUndo" class="move-undo" role="status">
        <span>{{ pendingMoveUndo.label }}</span>
        <button type="button" :disabled="outlineOpBusy" @click="$emit('undo-move')">撤销</button>
      </div>
      <button
        class="resize-handle"
        type="button"
        aria-label="调整作品目录宽度"
        title="拖拽调整作品目录宽度"
        @pointerdown="startResize"
        @dblclick="$emit('reset-panel-width')"
        @keydown.left.prevent="$emit('update:panel-width', Math.max(160, panelWidth - 16))"
        @keydown.right.prevent="$emit('update:panel-width', Math.min(360, panelWidth + 16))"
      ></button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { MenuFoldOutlined, MenuUnfoldOutlined, SearchOutlined } from '@ant-design/icons-vue'
import OutlineTree from '@/components/workbench/outline/OutlineTree.vue'
import type { WorkbenchOutlineData } from '@/components/workbench/workbenchTypes'
import type { OutlineChapterNode } from '@/composables/workbench/workbenchOutline'

const props = withDefaults(defineProps<{
  collapsed: boolean
  panelWidth: number
  outlineData: WorkbenchOutlineData
  activeChapter: string
  selectedChapterIds?: string[]
  outlineOpBusy: boolean
  pendingMoveUndo?: { label: string } | null
}>(), { selectedChapterIds: () => [] })
const emit = defineEmits<{
  'toggle-collapse': []
  'update:panel-width': [number]
  'reset-panel-width': []
  'select-chapter': [OutlineChapterNode]
  'toggle-chapter-selection': [OutlineChapterNode]
  'rename-node': [unknown]
  'move-node': [unknown]
  'undo-move': []
  'add-volume': []
  'add-chapter': [unknown]
  'delete-volume': [unknown]
  'delete-chapter': [unknown]
}>()

const query = ref('')
const panelStyle = computed(() => ({ width: props.collapsed ? '0px' : `${props.panelWidth}px` }))
const filteredOutline = computed(() => {
  const keyword = query.value.trim().toLocaleLowerCase('zh-CN')
  if (!keyword) return props.outlineData
  return props.outlineData.flatMap((volume) => {
    const children = volume.children.filter((chapter) => chapter.title.toLocaleLowerCase('zh-CN').includes(keyword))
    return volume.title.toLocaleLowerCase('zh-CN').includes(keyword) || children.length ? [{ ...volume, children }] : []
  })
})

let stopResize: (() => void) | null = null
const startResize = (event: PointerEvent) => {
  event.preventDefault()
  const startX = event.clientX
  const startWidth = props.panelWidth
  const move = (next: PointerEvent) => emit('update:panel-width', Math.min(360, Math.max(160, startWidth + next.clientX - startX)))
  const stop = () => {
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', stop)
    stopResize = null
  }
  stopResize = stop
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', stop)
}
onUnmounted(() => stopResize?.())
</script>

<style scoped>
.panel-left { position: relative; flex: 0 0 auto; min-width: 0; min-height: 0; background: var(--bg-surface); border-right: 1px solid var(--border-subtle); transition: width 160ms ease; }
.panel-left.collapsed { border-right: 0; }
.panel-content { position: relative; display: flex; height: 100%; min-height: 0; flex-direction: column; }
.directory-header { display: grid; gap: 10px; padding: 13px 10px 10px; border-bottom: 1px solid var(--border-subtle); }
.directory-heading { display: flex; min-height: 28px; align-items: center; justify-content: space-between; gap: 8px; }
.directory-header strong { padding-left: 3px; font-size: 13px; }
.directory-search { display: flex; align-items: center; gap: 7px; height: 30px; padding: 0 8px; color: var(--text-muted); background: var(--bg-subtle); border: 1px solid transparent; border-radius: var(--radius-md); }
.directory-search:focus-within { background: var(--bg-surface); border-color: var(--accent-border); }
.directory-search input { width: 100%; min-width: 0; color: var(--text-primary); background: transparent; border: 0; outline: 0; font-size: 12px; }
.tab-content { flex: 1; min-height: 0; overflow-y: auto; padding: 7px 5px 16px; }
.move-undo { display: flex; min-height: 40px; align-items: center; justify-content: space-between; gap: 8px; padding: 7px 10px; color: var(--text-secondary); background: var(--bg-elevated); border-top: 1px solid var(--border-subtle); font-size: 12px; }
.move-undo button { padding: 4px 6px; color: var(--accent-strong); background: transparent; border: 0; cursor: pointer; font-weight: 650; }
.move-undo button:disabled { opacity: 0.45; cursor: wait; }
.panel-toggle { display: grid; width: 28px; height: 28px; flex: 0 0 auto; padding: 0; place-items: center; color: var(--text-secondary); background: transparent; border: 1px solid transparent; border-radius: var(--radius-md); cursor: pointer; }
.panel-toggle:hover, .panel-toggle:focus-visible { color: var(--accent); background: var(--accent-soft); border-color: var(--accent-border); outline: 0; }
.panel-reopen { position: absolute; top: 50%; left: 0; z-index: 30; display: grid; width: 32px; height: 48px; padding: 0; place-items: center; color: var(--text-secondary); background: var(--bg-surface); border: 1px solid var(--border-strong); border-left: 0; border-radius: 0 var(--radius-md) var(--radius-md) 0; box-shadow: var(--shadow-sm); cursor: pointer; transform: translateY(-50%); }
.panel-reopen:hover, .panel-reopen:focus-visible { color: var(--accent); background: var(--accent-soft); border-color: var(--accent-border); outline: 0; }
.resize-handle { position: absolute; inset: 0 -4px 0 auto; z-index: 20; width: 8px; padding: 0; background: transparent; border: 0; cursor: col-resize; }
.resize-handle:hover, .resize-handle:focus-visible { background: var(--accent-border); outline: 0; }
@media (max-width: 900px) { .resize-handle, .panel-reopen { display: none; } }
</style>
