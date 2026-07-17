<template>
  <aside class="panel panel-left glass-panel" :class="{ collapsed }">
    <button class="panel-toggle" type="button" :title="collapsed ? '展开大纲' : '收起大纲'" @click="emit('toggle-collapse')">
      {{ collapsed ? '›' : '‹' }}
    </button>
    <div v-show="!collapsed" class="panel-content">
      <div class="left-tabs" role="tablist" aria-label="写作导航">
        <button
          v-for="tab in leftTabs"
          :key="tab.key"
          type="button"
          class="ltab"
          :class="{ active: activeLeftTab === tab.key }"
          role="tab"
          :aria-selected="activeLeftTab === tab.key"
          @click="emit('update:active-left-tab', tab.key)"
        >
          <img :src="tab.icon" alt="" class="ltab-icon" />
          <span>{{ tab.label }}</span>
        </button>
      </div>
      <div class="tab-content" role="tabpanel">
        <OutlineTree
          :volumes="outlineData"
          :active-chapter-key="activeChapter"
          :busy="outlineOpBusy"
          @select-chapter="emit('select-chapter', $event)"
          @rename-node="emit('rename-node', $event)"
          @move-node="emit('move-node', $event)"
          @add-volume="emit('add-volume')"
          @add-chapter="emit('add-chapter', $event)"
          @delete-volume="emit('delete-volume', $event)"
          @delete-chapter="emit('delete-chapter', $event)"
        />
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import OutlineTree from '@/components/workbench/outline/OutlineTree.vue'
import type { WorkbenchOutlineData } from '@/components/workbench/workbenchTypes'
import type { OutlineChapterNode } from '@/composables/workbench/workbenchOutline'

defineProps<{
  collapsed: boolean
  leftTabs: Array<{ key: string; label: string; icon: string }>
  activeLeftTab: string
  outlineData: WorkbenchOutlineData
  activeChapter: string
  outlineOpBusy: boolean
}>()
const emit = defineEmits<{
  (event: 'toggle-collapse'): void
  (event: 'update:active-left-tab', payload: string): void
  (event: 'select-chapter', payload: OutlineChapterNode): void
  (event: 'rename-node', payload: unknown): void
  (event: 'move-node', payload: unknown): void
  (event: 'add-volume'): void
  (event: 'add-chapter', payload: unknown): void
  (event: 'delete-volume', payload: unknown): void
  (event: 'delete-chapter', payload: unknown): void
}>()
</script>

<style scoped lang="less">
.panel { position: relative; display: flex; flex-direction: column; transition: width 0.25s ease; }
.panel-left { width: clamp(248px, 20vw, 320px); min-width: 0; min-height: 0; border-right: 1px solid var(--border-subtle); background: rgba(17, 24, 39, 0.72); box-shadow: var(--shadow-lg), var(--shadow-gold); }
.panel-left.collapsed { width: 0; border-right: 0; }
.panel-content { min-height: 0; flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.panel-toggle { position: absolute; top: 50%; right: -16px; z-index: 10; width: 16px; height: 40px; border: 1px solid var(--border-subtle); border-left: 0; border-radius: 0 4px 4px 0; color: var(--text-muted); background: rgba(17, 24, 39, 0.94); cursor: pointer; }
.left-tabs { display: flex; gap: 8px; padding: 10px 8px 8px; border-bottom: 1px solid var(--border-subtle); }
.ltab {
  flex: 1;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  background: rgba(17, 24, 39, 0.72);
  border: 1px solid var(--border-subtle);
  border-radius: 4px;
  color: var(--text-primary);
  cursor: pointer;

  &:hover {
    background: rgba(201, 169, 110, 0.06);
    color: var(--text-primary);
    border-color: var(--border-gold);
  }

  &.active {
    background: rgba(201, 169, 110, 0.12);
    color: var(--amber-gold);
    border-color: var(--border-gold);
    box-shadow: 0 0 8px rgba(201, 169, 110, 0.1);
  }
}
.ltab-icon { width: 16px; height: 16px; object-fit: cover; }
.tab-content { min-height: 0; flex: 1; overflow-y: auto; padding: 8px; }
@media (max-width: 1360px) { .panel-left { width: 248px; } }
</style>
