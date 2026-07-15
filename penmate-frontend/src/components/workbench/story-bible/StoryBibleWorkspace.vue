<template>
  <main class="story-bible-workspace">
    <div class="workspace-statusbar">
      <div>
        <BookOutlined />
        <strong>{{ story.root.value?.title || 'Story Bible' }}</strong>
        <span>修订 {{ story.root.value?.contentRevision || 0 }}</span>
      </div>
      <span v-if="story.errorMessage.value" class="error-text">{{ story.errorMessage.value }}</span>
      <ReloadOutlined v-if="story.loading.value" spin />
    </div>

    <div class="workspace-grid">
      <StoryBibleNavigator
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

      <section class="node-browser">
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
        @delete-relation="run(() => story.deleteRelation($event))"
        @create-progression="run(() => story.createProgression($event))"
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
import { BookOutlined, ReloadOutlined } from '@ant-design/icons-vue'
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
.workspace-grid { min-height: 0; flex: 1; display: grid; grid-template-columns: minmax(150px, 180px) minmax(210px, 260px) minmax(360px, 1fr); }
.node-browser { min-width: 0; min-height: 0; display: flex; flex-direction: column; border-right: 1px solid var(--border-subtle); background: rgba(17, 24, 39, 0.42); }
.node-browser :deep(.sb-node-list) { flex: 1; }
@media (max-width: 1080px) { .workspace-grid { grid-template-columns: 150px 220px minmax(340px, 1fr); } }
@media (max-width: 820px) {
  .story-bible-workspace { overflow: auto; }
  .workspace-grid { min-height: 900px; grid-template-columns: 150px minmax(220px, 1fr); grid-template-rows: 340px minmax(520px, 1fr); }
  .workspace-grid > :last-child { grid-column: 1 / -1; }
}
</style>
