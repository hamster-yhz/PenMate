<template>
  <div class="progressions-tab">
    <section class="effective-preview">
      <header>
        <strong>当前章节有效状态</strong>
        <span v-if="chapterId">{{ chapterLabel(chapterId) }}</span>
      </header>
      <pre>{{ formattedEffectiveState }}</pre>
    </section>

    <form class="progression-form" @submit.prevent="submit">
      <label>
        <span>起始章节</span>
        <select v-model="anchorChapterId" required>
          <option value="" disabled>选择起始章节</option>
          <option v-if="anchorChapterId && !hasChapter(anchorChapterId)" :value="anchorChapterId">
            {{ chapterLabel(anchorChapterId) }}
          </option>
          <option v-for="chapter in chapters" :key="chapter.chapterId" :value="chapter.chapterId">
            {{ chapterLabel(chapter.chapterId) }}
          </option>
        </select>
      </label>
      <label>
        <span>结束章节</span>
        <select v-model="endChapterId">
          <option value="">持续生效</option>
          <option v-if="endChapterId && !hasChapter(endChapterId)" :value="endChapterId">
            {{ chapterLabel(endChapterId) }}
          </option>
          <option v-for="chapter in chapters" :key="chapter.chapterId" :value="chapter.chapterId">
            {{ chapterLabel(chapter.chapterId) }}
          </option>
        </select>
      </label>
      <label class="wide"><span>变化摘要</span><input v-model="summary" /></label>
      <label class="wide"><span>RFC 6902 Patch</span><textarea v-model="patchJson" rows="5" required></textarea></label>
      <button type="submit"><PlusOutlined /> 添加状态演进</button>
    </form>

    <div class="progression-list">
      <div v-for="item in progressions" :key="item.progressionId" class="progression-row">
        <template v-if="editingProgressionId === item.progressionId">
          <div class="progression-edit">
            <select v-model="editAnchorChapterId" required aria-label="编辑起始章节">
              <option v-if="editAnchorChapterId && !hasChapter(editAnchorChapterId)" :value="editAnchorChapterId">
                {{ chapterLabel(editAnchorChapterId) }}
              </option>
              <option v-for="chapter in chapters" :key="chapter.chapterId" :value="chapter.chapterId">
                {{ chapterLabel(chapter.chapterId) }}
              </option>
            </select>
            <select v-model="editEndChapterId" aria-label="编辑结束章节">
              <option value="">持续生效</option>
              <option v-if="editEndChapterId && !hasChapter(editEndChapterId)" :value="editEndChapterId">
                {{ chapterLabel(editEndChapterId) }}
              </option>
              <option v-for="chapter in chapters" :key="chapter.chapterId" :value="chapter.chapterId">
                {{ chapterLabel(chapter.chapterId) }}
              </option>
            </select>
            <input v-model="editSummary" aria-label="编辑变化摘要" />
            <textarea v-model="editPatchJson" rows="4" required aria-label="编辑 RFC 6902 Patch"></textarea>
          </div>
          <div class="row-actions">
            <button type="button" title="保存状态演进" @click="saveEdit(item)"><SaveOutlined /></button>
            <button type="button" title="取消编辑" @click="cancelEdit"><CloseOutlined /></button>
          </div>
        </template>
        <template v-else>
          <div class="anchor">
            <span>{{ chapterLabel(item.anchorChapterId) }}</span
            ><ArrowRightOutlined /><span>{{ item.endChapterId ? chapterLabel(item.endChapterId) : '持续生效' }}</span>
          </div>
          <div class="progression-copy">
            <strong>{{ item.summary || '状态演进' }}</strong
            ><code>{{ item.patchJson }}</code>
          </div>
          <div class="row-actions">
            <button type="button" title="编辑状态演进" @click="startEdit(item)"><EditOutlined /></button>
            <button type="button" title="删除状态演进" class="danger" @click="emit('delete', item)">
              <DeleteOutlined />
            </button>
          </div>
        </template>
      </div>
      <div v-if="!progressions.length" class="empty-state">当前节点尚无状态演进</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  ArrowRightOutlined,
  CloseOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  SaveOutlined,
} from '@ant-design/icons-vue'
import type { StoryBibleProgression, StoryBibleProgressionUpdatePayload } from '@/api/modules/storyBible.api'
import type { StoryBibleChapterOption } from './storyBibleTypes'

const props = defineProps<{
  chapterId?: string
  chapters: StoryBibleChapterOption[]
  progressions: StoryBibleProgression[]
  effectiveState: Record<string, unknown> | null
}>()
const emit = defineEmits<{
  (
    event: 'create',
    payload: Omit<StoryBibleProgression, 'progressionId' | 'storyBibleId' | 'nodeId' | 'revision'>,
  ): void
  (event: 'update', payload: { progressionId: string; update: StoryBibleProgressionUpdatePayload }): void
  (event: 'delete', payload: StoryBibleProgression): void
}>()
const anchorChapterId = ref(props.chapterId || '')
const endChapterId = ref('')
const summary = ref('')
const patchJson = ref('[\n  { "op": "replace", "path": "/summary", "value": "" }\n]')
const editingProgressionId = ref('')
const editAnchorChapterId = ref('')
const editEndChapterId = ref('')
const editSummary = ref('')
const editPatchJson = ref('[]')
const chapterById = computed(() => new Map(props.chapters.map((chapter) => [chapter.chapterId, chapter])))
const hasChapter = (chapterId: string) => chapterById.value.has(chapterId)
const chapterLabel = (chapterId: string) => {
  const chapter = chapterById.value.get(chapterId)
  return chapter ? `第 ${chapter.displayNo} 章 · ${chapter.title}` : `未解析章节（${chapterId}）`
}
watch(
  () => props.chapterId,
  (value) => {
    if (value) anchorChapterId.value = value
  },
)
const formattedEffectiveState = computed(() =>
  props.effectiveState ? JSON.stringify(props.effectiveState, null, 2) : '选择章节后显示有效状态',
)
const submit = () => {
  emit('create', {
    anchorChapterId: anchorChapterId.value,
    endChapterId: endChapterId.value || null,
    storyEventNodeId: null,
    patchJson: patchJson.value,
    summary: summary.value,
  })
  endChapterId.value = ''
  summary.value = ''
}
const startEdit = (item: StoryBibleProgression) => {
  editingProgressionId.value = item.progressionId
  editAnchorChapterId.value = item.anchorChapterId
  editEndChapterId.value = item.endChapterId || ''
  editSummary.value = item.summary || ''
  editPatchJson.value = item.patchJson
}
const cancelEdit = () => {
  editingProgressionId.value = ''
}
const saveEdit = (item: StoryBibleProgression) => {
  emit('update', {
    progressionId: item.progressionId,
    update: {
      expectedRevision: item.revision,
      anchorChapterId: editAnchorChapterId.value,
      endChapterId: editEndChapterId.value || null,
      storyEventNodeId: item.storyEventNodeId || null,
      patchJson: editPatchJson.value,
      summary: editSummary.value,
    },
  })
  cancelEdit()
}
</script>

<style scoped lang="less">
.progressions-tab {
  display: grid;
  gap: 16px;
  padding: 16px;
}
.effective-preview {
  border: 1px solid var(--border-subtle);
  border-radius: 4px;
  background: rgba(11, 17, 32, 0.62);
}
.effective-preview header {
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 10px;
  border-bottom: 1px solid var(--border-subtle);
}
.effective-preview strong {
  color: var(--amber-gold);
  font-size: 0.76rem;
}
.effective-preview span {
  color: var(--text-muted);
  font-size: 0.68rem;
}
pre {
  max-height: 220px;
  margin: 0;
  overflow: auto;
  padding: 12px;
  color: #b9c9d8;
  font-size: 0.72rem;
  white-space: pre-wrap;
}
.progression-form {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 9px;
}
label {
  display: grid;
  gap: 4px;
  color: var(--text-secondary);
  font-size: 0.7rem;
}
.wide {
  grid-column: 1 / -1;
}
input,
select,
textarea,
button {
  min-width: 0;
  border: 1px solid var(--border-subtle);
  border-radius: 4px;
  color: var(--text-primary);
  background: rgba(11, 17, 32, 0.7);
}
input,
select,
button {
  height: 34px;
  padding: 0 8px;
}
textarea {
  padding: 8px;
  resize: vertical;
}
.progression-form button {
  width: max-content;
  color: var(--amber-gold);
  border-color: var(--border-gold);
  cursor: pointer;
}
.progression-row {
  min-height: 70px;
  display: grid;
  grid-template-columns: minmax(180px, 0.8fr) minmax(0, 1fr) 68px;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid var(--border-subtle);
}
.anchor {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px;
  color: var(--amber-gold);
  font-size: 0.7rem;
}
.progression-copy {
  min-width: 0;
  display: grid;
  gap: 4px;
}
.progression-copy strong {
  font-size: 0.78rem;
}
.progression-copy code {
  overflow: hidden;
  color: var(--text-muted);
  font-size: 0.68rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.progression-edit {
  grid-column: 1 / 3;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  padding: 8px 0;
}
.progression-edit textarea {
  grid-column: 1 / -1;
}
.row-actions {
  display: flex;
  gap: 2px;
}
.progression-row button {
  width: 32px;
  padding: 0;
  color: var(--amber-gold);
  border-color: transparent;
  background: transparent;
  cursor: pointer;
}
.progression-row button.danger {
  color: #c9827b;
}
.empty-state {
  padding: 24px;
  text-align: center;
  color: var(--text-muted);
}
@media (max-width: 680px) {
  .progression-form {
    grid-template-columns: 1fr;
  }
  .wide {
    grid-column: auto;
  }
  .progression-row {
    grid-template-columns: minmax(0, 1fr) 68px;
    align-items: start;
    padding: 10px 0;
  }
  .anchor,
  .progression-copy,
  .progression-edit {
    grid-column: 1;
  }
  .row-actions {
    grid-column: 2;
    grid-row: 1 / span 2;
  }
  .progression-edit {
    grid-template-columns: 1fr;
  }
  .progression-edit textarea {
    grid-column: 1;
  }
}
</style>
