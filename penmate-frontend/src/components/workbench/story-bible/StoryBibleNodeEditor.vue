<template>
  <section class="sb-node-editor">
    <template v-if="draft">
      <header class="editor-header">
        <div class="title-block">
          <span>{{ draft.nodeId ? '编辑节点' : '新建节点' }}</span>
          <strong>{{ draft.title || '未命名节点' }}</strong>
        </div>
        <div class="editor-actions">
          <button type="button" class="icon-button" title="路由设置" @click="emit('openRouting')"><SettingOutlined /></button>
          <button v-if="draft.nodeId" type="button" class="icon-button danger" title="删除节点" @click="emit('delete')"><DeleteOutlined /></button>
          <button type="button" class="save-button" :disabled="saving" @click="emit('save')"><SaveOutlined /> {{ saving ? '保存中' : '保存' }}</button>
        </div>
      </header>

      <div class="editor-tabs" role="tablist">
        <button v-for="tab in tabs" :key="tab.key" type="button" :class="{ active: activeTab === tab.key }" @click="activeTab = tab.key">
          <component :is="tab.icon" />{{ tab.label }}
        </button>
      </div>

      <div class="editor-body">
        <StoryBibleBaseTab v-if="activeTab === 'base'" :draft="draft" :node-types="nodeTypes" :categories="categories" :tags="tags" />
        <StoryBibleRelationsTab
          v-else-if="activeTab === 'relations' && draft.nodeId"
          :node-id="draft.nodeId"
          :nodes="nodes"
          :relations="relations"
          @create="emit('createRelation', $event)"
          @delete="emit('deleteRelation', $event)"
        />
        <StoryBibleProgressionsTab
          v-else-if="activeTab === 'progressions' && draft.nodeId"
          :chapter-id="chapterId"
          :progressions="progressions"
          :effective-state="effectiveState"
          @create="emit('createProgression', $event)"
          @delete="emit('deleteProgression', $event)"
        />
        <StoryBibleHistoryTab v-else-if="activeTab === 'history'" :history="history" @open-run="emit('openRun', $event)" />
        <div v-else class="empty-state">保存节点后可编辑此内容</div>
      </div>
    </template>
    <div v-else class="editor-empty">
      <BookOutlined />
      <strong>选择或新建 Story Bible 节点</strong>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ApartmentOutlined, BookOutlined, ClockCircleOutlined, DeleteOutlined, HistoryOutlined, SaveOutlined, SettingOutlined } from '@ant-design/icons-vue'
import type { StoryBibleCategory, StoryBibleChangeset, StoryBibleNode, StoryBibleNodeType, StoryBibleProgression, StoryBibleRelation, StoryBibleTag } from '@/api/modules/storyBible.api'
import type { StoryBibleNodeDraft } from '@/composables/workbench/useStoryBible'
import StoryBibleBaseTab from './StoryBibleBaseTab.vue'
import StoryBibleHistoryTab from './StoryBibleHistoryTab.vue'
import StoryBibleProgressionsTab from './StoryBibleProgressionsTab.vue'
import StoryBibleRelationsTab from './StoryBibleRelationsTab.vue'

defineProps<{
  draft: StoryBibleNodeDraft | null
  saving: boolean
  chapterId?: string
  nodeTypes: StoryBibleNodeType[]
  nodes: StoryBibleNode[]
  categories: StoryBibleCategory[]
  tags: StoryBibleTag[]
  relations: StoryBibleRelation[]
  progressions: StoryBibleProgression[]
  history: StoryBibleChangeset[]
  effectiveState: Record<string, unknown> | null
}>()
const emit = defineEmits<{
  (event: 'save'): void
  (event: 'delete'): void
  (event: 'openRouting'): void
  (event: 'createRelation', payload: Omit<StoryBibleRelation, 'relationId' | 'storyBibleId' | 'revision'>): void
  (event: 'deleteRelation', payload: StoryBibleRelation): void
  (event: 'createProgression', payload: Omit<StoryBibleProgression, 'progressionId' | 'storyBibleId' | 'nodeId' | 'revision'>): void
  (event: 'deleteProgression', payload: StoryBibleProgression): void
  (event: 'openRun', runId: string): void
}>()
const activeTab = ref('base')
const tabs = [
  { key: 'base', label: '基础设定', icon: BookOutlined },
  { key: 'relations', label: '关系', icon: ApartmentOutlined },
  { key: 'progressions', label: '状态演进', icon: ClockCircleOutlined },
  { key: 'history', label: '最近变更', icon: HistoryOutlined },
]
</script>

<style scoped lang="less">
.sb-node-editor { position: relative; min-width: 0; min-height: 0; display: flex; flex-direction: column; background: rgba(17, 24, 39, 0.5); }
.editor-header { height: 58px; flex: 0 0 58px; display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 0 14px; border-bottom: 1px solid var(--border-subtle); }
.title-block { min-width: 0; display: grid; gap: 2px; }
.title-block span { color: var(--text-muted); font-size: 0.65rem; }
.title-block strong { overflow: hidden; color: var(--text-primary); font-size: 0.9rem; text-overflow: ellipsis; white-space: nowrap; }
.editor-actions { display: flex; gap: 6px; }
.icon-button, .save-button { height: 32px; border: 1px solid var(--border-subtle); border-radius: 4px; color: var(--text-secondary); background: rgba(11, 17, 32, 0.7); cursor: pointer; }
.icon-button { width: 32px; }
.icon-button.danger { color: #c9827b; }
.save-button { padding: 0 12px; color: var(--amber-gold); border-color: var(--border-gold); }
.save-button:disabled { opacity: 0.5; cursor: wait; }
.editor-tabs { height: 42px; flex: 0 0 42px; display: flex; align-items: end; gap: 2px; padding: 0 12px; border-bottom: 1px solid var(--border-subtle); }
.editor-tabs button { height: 34px; display: flex; align-items: center; gap: 5px; padding: 0 11px; border: 0; border-bottom: 2px solid transparent; color: var(--text-secondary); background: transparent; cursor: pointer; }
.editor-tabs button.active { color: var(--amber-gold); border-bottom-color: var(--amber-gold); }
.editor-body { min-height: 0; flex: 1; overflow: auto; }
.editor-empty { height: 100%; display: grid; place-content: center; justify-items: center; gap: 12px; color: var(--text-muted); }
.editor-empty :deep(svg) { font-size: 34px; color: var(--border-gold); }
.empty-state { padding: 40px; text-align: center; color: var(--text-muted); }
@media (max-width: 680px) { .editor-tabs { overflow-x: auto; } .editor-tabs button { flex: 0 0 auto; } }
</style>
