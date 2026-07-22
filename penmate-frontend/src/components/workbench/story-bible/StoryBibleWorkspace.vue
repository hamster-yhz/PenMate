<template>
  <main class="story-bible-workspace">
    <div class="workspace-statusbar">
      <div>
        <BookOutlined />
        <strong>{{ story.root.value?.title || 'Story Bible' }}</strong>
        <span>{{ story.nodes.value.length }} 项设定</span>
      </div>
      <div class="status-actions">
        <span v-if="story.errorMessage.value" class="error-text">{{ story.errorMessage.value }}</span>
        <ReloadOutlined v-if="story.loading.value" spin />
        <div class="mobile-tools" role="tablist" aria-label="Story Bible 视图">
          <button type="button" :class="{ active: mobilePane === 'browser' }" @click="mobilePane = 'browser'"><AppstoreOutlined />导航</button>
          <button type="button" :class="{ active: mobilePane === 'detail' }" @click="mobilePane = 'detail'"><UnorderedListOutlined />详情</button>
        </div>
      </div>
    </div>

    <div class="workspace-grid">
      <aside class="story-browser" :class="{ 'mobile-hidden': mobilePane !== 'browser' }">
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
      </aside>

      <StoryBibleNodeEditor
        class="story-detail"
        :class="{ 'mobile-hidden': mobilePane !== 'detail' }"
        :draft="story.draft.value"
        :saving="story.saving.value"
        :chapter-id="chapterId"
        :chapters="chapters"
        :node-types="story.nodeTypes.value"
        :nodes="story.nodes.value"
        :categories="story.categories.value"
        :tags="story.tags.value"
        :relations="story.selectedRelations.value"
        :progressions="story.selectedProgressions.value"
        :history="story.nodeHistory.value"
        :effective-state="story.effectiveState.value"
        @save="story.saveNode()"
        @delete="deleteNode"
        @create-relation="run(() => story.createRelation($event))"
        @update-relation="run(() => story.updateRelation($event.relationId, $event.update))"
        @delete-relation="run(() => story.deleteRelation($event))"
        @select-node="selectNode"
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
  </main>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { AppstoreOutlined, BookOutlined, ReloadOutlined, UnorderedListOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { useStoryBible } from '@/composables/workbench/useStoryBible'
import StoryBibleNavigator from './StoryBibleNavigator.vue'
import StoryBibleNodeEditor from './StoryBibleNodeEditor.vue'
import StoryBibleNodeList from './StoryBibleNodeList.vue'
import StoryBibleSearchToolbar from './StoryBibleSearchToolbar.vue'
import StoryBibleTypeEditor from './StoryBibleTypeEditor.vue'
import type { StoryBibleChapterOption } from './storyBibleTypes'

const props = defineProps<{
  projectId: string
  operatorId: string
  userId?: string
  sessionId?: string
  chapterId?: string
  chapters: StoryBibleChapterOption[]
  projectTitle?: string
  initialNodeId?: string
}>()
const emit = defineEmits<{ (event: 'openRun', runId: string): void }>()
const showTypeEditor = ref(false)
const mobilePane = ref<'browser' | 'detail'>('detail')
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
  try {
    await action()
  } catch (error) {
    message.error(String((error as Error)?.message || '操作失败'))
  }
}
const selectNode = async (nodeId: string) => {
  await story.selectNode(nodeId)
  mobilePane.value = 'detail'
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
watch(
  () => props.projectId,
  (next, previous) => {
    if (next && next !== previous) void reload()
  },
)
watch(
  () => props.chapterId,
  () => {
    if (story.selectedNodeId.value) void story.selectNode(story.selectedNodeId.value)
  },
)
watch(
  () => props.initialNodeId,
  (nodeId) => {
    if (nodeId) void story.selectNode(nodeId)
  },
)
let filterTimer: ReturnType<typeof setTimeout> | undefined
watch(
  [
    () => story.selectedTypeId.value,
    () => story.selectedCategoryId.value,
    () => story.selectedTagId.value,
    () => story.searchQuery.value,
    () => story.canonFilter.value,
  ],
  () => {
    if (filterTimer) clearTimeout(filterTimer)
    filterTimer = setTimeout(() => {
      void story.refreshNodes()
    }, 180)
  },
)
onBeforeUnmount(() => {
  if (filterTimer) clearTimeout(filterTimer)
})

defineExpose({ reload })
</script>

<style scoped lang="less">
.story-bible-workspace {
  position: relative;
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--bg-editor);
}
.workspace-statusbar {
  height: 42px;
  flex: 0 0 42px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 12px;
  border-bottom: 1px solid var(--border-subtle);
  background: var(--bg-surface);
}
.workspace-statusbar div {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}
.workspace-statusbar strong {
  color: var(--accent);
  font-size: 0.82rem;
}
.workspace-statusbar span {
  color: var(--text-muted);
  font-size: 0.68rem;
}
.workspace-statusbar .error-text {
  overflow: hidden;
  color: var(--danger);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.status-actions {
  min-width: 0;
  flex: 1 1 0;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  overflow: hidden;
}
.status-actions .error-text {
  min-width: 0;
  flex: 1 1 auto;
}
.mobile-tools {
  display: none;
}
.mobile-tools button {
  min-width: 58px;
  height: 32px;
  display: grid;
  place-items: center;
  border: 1px solid var(--border-subtle);
  border-radius: 4px;
  color: var(--text-muted);
  background: var(--bg-surface);
  cursor: pointer;
}
.workspace-grid {
  min-height: 0;
  flex: 1;
  display: grid;
  grid-template-columns: minmax(260px, 320px) minmax(420px, 1fr);
}
.story-browser {
  min-width: 0;
  min-height: 0;
  display: grid;
  grid-template-rows: auto minmax(180px, 1fr);
  overflow: auto;
  border-right: 1px solid var(--border-subtle);
  background: var(--bg-surface);
}
.node-browser {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border-top: 1px solid var(--border-subtle);
  background: var(--bg-surface);
}
.node-browser :deep(.sb-node-list) {
  flex: 1;
}
@media (max-width: 900px) {
  .story-bible-workspace {
    overflow: hidden;
  }
  .workspace-statusbar {
    height: 46px;
    flex-basis: 46px;
  }
  .workspace-statusbar > div:first-child span {
    display: none;
  }
  .status-actions { padding-right: 128px; }
  .mobile-tools {
    position: absolute;
    top: 7px;
    right: 8px;
    z-index: 2;
    display: flex;
    gap: 2px;
  }
  .workspace-grid {
    position: relative;
    display: block;
    min-height: 0;
  }
  .story-browser,
  .story-detail {
    width: 100%;
    height: 100%;
  }
  .mobile-hidden { display: none !important; }
  .mobile-tools button.active { color: var(--accent); background: var(--accent-soft); }
}
</style>
