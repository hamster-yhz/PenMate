<template>
  <main class="story-bible-workspace">
    <div class="workspace-statusbar">
      <div>
        <BookOutlined />
        <strong>{{ story.root.value?.title || 'Story Bible' }}</strong>
        <span>修订 {{ story.root.value?.contentRevision || 0 }}</span>
      </div>
      <div class="status-actions">
        <span v-if="story.errorMessage.value" class="error-text">{{ story.errorMessage.value }}</span>
        <ReloadOutlined v-if="story.loading.value" spin />
        <div class="mobile-tools">
          <button type="button" title="打开 Story Bible 导航" @click="toggleMobilePanel('navigation')"><AppstoreOutlined /></button>
          <button type="button" title="打开节点列表" @click="toggleMobilePanel('nodes')"><UnorderedListOutlined /></button>
        </div>
      </div>
    </div>

    <button v-if="mobilePanel" type="button" class="mobile-backdrop" aria-label="关闭移动面板" @click="mobilePanel = ''"></button>

    <div class="workspace-grid">
      <StoryBibleNavigator
        class="story-navigator-panel"
        :class="{ 'is-mobile-open': mobilePanel === 'navigation' }"
        :node-types="story.visibleTypes.value"
        :categories="story.categories.value"
        :tags="story.tags.value"
        :selected-family="story.selectedFamily.value"
        :selected-type-id="story.selectedTypeId.value"
        :selected-category-id="story.selectedCategoryId.value"
        :selected-tag-id="story.selectedTagId.value"
        @update:selected-family="story.selectedFamily.value = $event"
        @update:selected-type-id="story.selectedTypeId.value = $event"
        @update:selected-category-id="story.selectedCategoryId.value = $event"
        @update:selected-tag-id="story.selectedTagId.value = $event"
        @manage-types="showTypeEditor = true"
      />

      <section class="node-browser" :class="{ 'is-mobile-open': mobilePanel === 'nodes' }">
        <StoryBibleSearchToolbar
          :query="story.searchQuery.value"
          :status="story.canonFilter.value"
          @update:query="story.searchQuery.value = $event"
          @update:status="story.canonFilter.value = $event as any"
          @create="story.createNodeDraft()"
        />
        <StoryBibleNodeList
          :nodes="story.filteredNodes.value"
          :node-types="story.nodeTypes.value"
          :selected-node-id="story.selectedNodeId.value"
          @select="selectNode"
        />
      </section>

      <StoryBibleNodeEditor
        :draft="story.draft.value"
        :saving="story.saving.value"
        :chapter-id="chapterId"
        :node-types="story.nodeTypes.value"
        :nodes="story.nodes.value"
        :categories="story.categories.value"
        :tags="story.tags.value"
        :relations="story.selectedRelations.value"
        :progressions="story.selectedProgressions.value"
        :history="story.history.value"
        :effective-state="story.effectiveState.value"
        @save="story.saveNode()"
        @delete="deleteNode"
        @open-routing="showRoutingSettings = true"
        @create-relation="run(() => story.createRelation($event))"
        @update-relation="run(() => story.updateRelation($event.relationId, $event.update))"
        @delete-relation="run(() => story.deleteRelation($event))"
        @create-progression="run(() => story.createProgression($event))"
        @update-progression="run(() => story.updateProgression($event.progressionId, $event.update))"
        @delete-progression="run(() => story.deleteProgression($event))"
        @open-run="emit('openRun', $event)"
      />
    </div>

    <StoryBibleTypeEditor
      :open="showTypeEditor"
      :node-types="story.nodeTypes.value"
      :categories="story.categories.value"
      :tags="story.tags.value"
      :views="story.views.value"
      @close="showTypeEditor = false"
      @save-type="run(() => story.saveNodeType($event as any))"
      @archive-type="run(() => story.archiveNodeType($event))"
      @save-category="run(() => story.saveCategory($event as any))"
      @delete-category="run(() => story.deleteCategory($event))"
      @save-tag="run(() => story.saveTag($event as any))"
      @delete-tag="run(() => story.deleteTag($event))"
      @save-view="run(() => story.saveViewPreference($event))"
    />
    <StoryBibleRoutingSettings
      v-if="showRoutingSettings"
      :user-preference="story.userRoutingPreference.value"
      :session-preference="story.sessionRoutingPreference.value"
      @close="showRoutingSettings = false"
      @save-user="run(() => story.saveUserRoutingPreference($event))"
      @save-session="run(() => story.saveSessionRoutingPreference($event))"
    />
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { AppstoreOutlined, BookOutlined, ReloadOutlined, UnorderedListOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { useStoryBible } from '@/composables/workbench/useStoryBible'
import StoryBibleNavigator from './StoryBibleNavigator.vue'
import StoryBibleNodeEditor from './StoryBibleNodeEditor.vue'
import StoryBibleNodeList from './StoryBibleNodeList.vue'
import StoryBibleRoutingSettings from './StoryBibleRoutingSettings.vue'
import StoryBibleSearchToolbar from './StoryBibleSearchToolbar.vue'
import StoryBibleTypeEditor from './StoryBibleTypeEditor.vue'

const props = defineProps<{
  projectId: string
  operatorId: string
  userId?: string
  sessionId?: string
  chapterId?: string
  projectTitle?: string
  initialNodeId?: string
}>()
const emit = defineEmits<{ (event: 'openRun', runId: string): void }>()
const showTypeEditor = ref(false)
const showRoutingSettings = ref(false)
const mobilePanel = ref<'' | 'navigation' | 'nodes'>('')
const story = useStoryBible({
  getContext: () => ({
    projectId: props.projectId,
    operatorId: props.operatorId,
    userId: props.userId,
    sessionId: props.sessionId,
    chapterId: props.chapterId,
    projectTitle: props.projectTitle,
  }),
  notify: (text) => message.error(text),
  notifySuccess: (text) => message.success(text),
})

const run = async (action: () => Promise<unknown>) => {
  try { await action() } catch (error) { message.error(String((error as Error)?.message || '操作失败')) }
}
const selectNode = async (nodeId: string) => {
  await story.selectNode(nodeId)
  mobilePanel.value = ''
}
const toggleMobilePanel = (panel: 'navigation' | 'nodes') => {
  mobilePanel.value = mobilePanel.value === panel ? '' : panel
}
const deleteNode = async () => {
  if (!window.confirm('确认删除当前 Story Bible 节点？')) return
  await story.deleteSelectedNode()
}
const reload = async () => {
  await story.loadWorkspace()
  if (props.initialNodeId) await story.selectNode(props.initialNodeId)
}

onMounted(reload)
watch(() => props.projectId, (next, previous) => { if (next && next !== previous) void reload() })
watch(() => props.chapterId, () => {
  if (story.selectedNodeId.value) void story.selectNode(story.selectedNodeId.value)
})
watch(() => props.initialNodeId, (nodeId) => { if (nodeId) void story.selectNode(nodeId) })

defineExpose({ reload })
</script>

<style scoped lang="less">
.story-bible-workspace { position: relative; flex: 1; min-width: 0; min-height: 0; display: flex; flex-direction: column; background: rgba(11, 17, 32, 0.58); box-shadow: var(--shadow-lg), var(--shadow-gold); }
.workspace-statusbar { height: 42px; flex: 0 0 42px; display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 0 12px; border-bottom: 1px solid var(--border-subtle); background: rgba(11, 17, 32, 0.88); }
.workspace-statusbar div { min-width: 0; display: flex; align-items: center; gap: 8px; }
.workspace-statusbar strong { color: var(--amber-gold); font-size: 0.82rem; }
.workspace-statusbar span { color: var(--text-muted); font-size: 0.68rem; }
.workspace-statusbar .error-text { overflow: hidden; color: #d19087; text-overflow: ellipsis; white-space: nowrap; }
.status-actions { min-width: 0; flex: 1 1 0; display: flex; align-items: center; justify-content: flex-end; gap: 8px; overflow: hidden; }
.status-actions .error-text { min-width: 0; flex: 1 1 auto; }
.mobile-tools { display: none; }
.mobile-tools button { width: 32px; height: 32px; display: grid; place-items: center; border: 1px solid var(--border-subtle); border-radius: 4px; color: var(--amber-gold); background: rgba(17, 24, 39, 0.9); cursor: pointer; }
.mobile-backdrop { display: none; }
.workspace-grid { min-height: 0; flex: 1; display: grid; grid-template-columns: minmax(150px, 180px) minmax(210px, 260px) minmax(360px, 1fr); }
.node-browser { min-width: 0; min-height: 0; display: flex; flex-direction: column; border-right: 1px solid var(--border-subtle); background: rgba(17, 24, 39, 0.42); }
.node-browser :deep(.sb-node-list) { flex: 1; }
@media (max-width: 1080px) { .workspace-grid { grid-template-columns: 150px 220px minmax(340px, 1fr); } }
@media (max-width: 1080px) {
  .story-bible-workspace { overflow: hidden; }
  .workspace-statusbar { height: 46px; flex-basis: 46px; }
  .workspace-statusbar > div:first-child span { display: none; }
  .status-actions { padding-right: 76px; }
  .mobile-tools { position: absolute; top: 7px; right: 8px; z-index: 2; display: flex; gap: 4px; }
  .mobile-backdrop { position: absolute; inset: 46px 0 0; z-index: 20; display: block; width: 100%; height: auto; border: 0; background: rgba(3, 7, 16, 0.62); }
  .workspace-grid { position: relative; display: block; min-height: 0; }
  .story-navigator-panel,
  .node-browser { position: absolute; inset: 0 auto 0 0; z-index: 21; width: min(84vw, 310px); border-right: 1px solid var(--border-gold); background: rgba(11, 17, 32, 0.99); box-shadow: var(--shadow-lg); transform: translateX(-105%); transition: transform 0.2s var(--ease-silk); }
  .story-navigator-panel.is-mobile-open,
  .node-browser.is-mobile-open { transform: translateX(0); }
  .workspace-grid > :last-child { width: 100%; height: 100%; }
}
</style>
