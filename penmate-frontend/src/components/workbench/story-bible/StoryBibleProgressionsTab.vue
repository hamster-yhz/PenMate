<template>
  <div class="progressions-tab">
    <section class="effective-preview">
      <header><strong>当前章节有效状态</strong><span v-if="chapterId">{{ chapterLabel(chapterId) }}</span></header>
      <div v-if="effectiveEntries.length" class="state-list">
        <div v-for="entry in effectiveEntries" :key="entry.key" class="state-row"><strong>{{ entry.key }}</strong><span>{{ entry.value }}</span></div>
      </div>
      <div v-else class="empty-state">{{ chapterId ? '当前章节没有额外状态' : '选择章节后显示有效状态' }}</div>
    </section>

    <form class="progression-form" @submit.prevent="submit">
      <label><span>起始章节</span><select v-model="anchorChapterId" required><option value="" disabled>选择起始章节</option><option v-if="anchorChapterId && !hasChapter(anchorChapterId)" :value="anchorChapterId">{{ chapterLabel(anchorChapterId) }}</option><option v-for="chapter in chapters" :key="chapter.chapterId" :value="chapter.chapterId">{{ chapterLabel(chapter.chapterId) }}</option></select></label>
      <label><span>结束章节</span><select v-model="endChapterId"><option value="">持续生效</option><option v-if="endChapterId && !hasChapter(endChapterId)" :value="endChapterId">{{ chapterLabel(endChapterId) }}</option><option v-for="chapter in chapters" :key="chapter.chapterId" :value="chapter.chapterId">{{ chapterLabel(chapter.chapterId) }}</option></select></label>
      <label class="wide"><span>变化摘要</span><input v-model="summary" placeholder="例如：加入远征队后身份发生变化" /></label>
      <div class="wide change-editor">
        <header><span>属性变化</span><button type="button" @click="addChange(changes)"><PlusOutlined />添加属性</button></header>
        <div v-for="change in changes" :key="change.id" class="change-row">
          <select v-model="change.op" aria-label="变化方式"><option value="add">增加</option><option value="replace">修改</option><option value="remove">删除</option></select>
          <input v-model.trim="change.key" required placeholder="属性名" aria-label="属性名" />
          <input v-if="change.op !== 'remove'" v-model="change.value" required placeholder="新的值" aria-label="属性值" />
          <button type="button" class="icon-button danger" title="移除属性变化" @click="removeChange(changes, change.id)"><DeleteOutlined /></button>
        </div>
        <p v-if="!changes.length">尚未添加属性变化</p>
      </div>
      <button type="submit" class="add-progression"><PlusOutlined />添加状态演进</button>
    </form>

    <div class="progression-list">
      <div v-for="item in progressions" :key="item.progressionId" class="progression-row">
        <template v-if="editingProgressionId === item.progressionId">
          <div class="progression-edit">
            <select v-model="editAnchorChapterId" required aria-label="编辑起始章节"><option v-if="editAnchorChapterId && !hasChapter(editAnchorChapterId)" :value="editAnchorChapterId">{{ chapterLabel(editAnchorChapterId) }}</option><option v-for="chapter in chapters" :key="chapter.chapterId" :value="chapter.chapterId">{{ chapterLabel(chapter.chapterId) }}</option></select>
            <select v-model="editEndChapterId" aria-label="编辑结束章节"><option value="">持续生效</option><option v-if="editEndChapterId && !hasChapter(editEndChapterId)" :value="editEndChapterId">{{ chapterLabel(editEndChapterId) }}</option><option v-for="chapter in chapters" :key="chapter.chapterId" :value="chapter.chapterId">{{ chapterLabel(chapter.chapterId) }}</option></select>
            <input v-model="editSummary" aria-label="编辑变化摘要" />
            <div class="change-editor edit-changes">
              <header><span>属性变化</span><button type="button" @click="addChange(editChanges)"><PlusOutlined />添加属性</button></header>
              <div v-for="change in editChanges" :key="change.id" class="change-row">
                <select v-model="change.op" aria-label="编辑变化方式"><option value="add">增加</option><option value="replace">修改</option><option value="remove">删除</option></select>
                <input v-model.trim="change.key" required aria-label="编辑属性名" />
                <input v-if="change.op !== 'remove'" v-model="change.value" required aria-label="编辑属性值" />
                <button type="button" class="icon-button danger" title="移除属性变化" @click="removeChange(editChanges, change.id)"><DeleteOutlined /></button>
              </div>
            </div>
          </div>
          <div class="row-actions"><button type="button" title="保存状态演进" @click="saveEdit(item)"><SaveOutlined /></button><button type="button" title="取消编辑" @click="cancelEdit"><CloseOutlined /></button></div>
        </template>
        <template v-else>
          <div class="anchor"><span>{{ chapterLabel(item.anchorChapterId) }}</span><ArrowRightOutlined /><span>{{ item.endChapterId ? chapterLabel(item.endChapterId) : '持续生效' }}</span></div>
          <div class="progression-copy"><strong>{{ item.summary || '状态演进' }}</strong><small>{{ describePatch(item.patchJson) }}</small></div>
          <div class="row-actions"><button type="button" title="编辑状态演进" @click="startEdit(item)"><EditOutlined /></button><button type="button" title="删除状态演进" class="danger" @click="emit('delete', item)"><DeleteOutlined /></button></div>
        </template>
      </div>
      <div v-if="!progressions.length" class="empty-state">当前节点尚无状态演进</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ArrowRightOutlined, CloseOutlined, DeleteOutlined, EditOutlined, PlusOutlined, SaveOutlined } from '@ant-design/icons-vue'
import type { StoryBibleProgression, StoryBibleProgressionUpdatePayload } from '@/entities/story-bible/model'
import type { StoryBibleChapterOption } from './storyBibleTypes'

type ChangeOperation = 'add' | 'replace' | 'remove'
interface EditableChange { id: number; op: ChangeOperation; key: string; value: string }
const props = defineProps<{ chapterId?: string; chapters: StoryBibleChapterOption[]; progressions: StoryBibleProgression[]; effectiveState: Record<string, unknown> | null }>()
const emit = defineEmits<{
  (event: 'create', payload: Omit<StoryBibleProgression, 'progressionId' | 'storyBibleId' | 'nodeId' | 'revision'>): void
  (event: 'update', payload: { progressionId: string; update: StoryBibleProgressionUpdatePayload }): void
  (event: 'delete', payload: StoryBibleProgression): void
}>()
let changeId = 0
const anchorChapterId = ref(props.chapterId || '')
const endChapterId = ref('')
const summary = ref('')
const changes = ref<EditableChange[]>([])
const editingProgressionId = ref('')
const editAnchorChapterId = ref('')
const editEndChapterId = ref('')
const editSummary = ref('')
const editChanges = ref<EditableChange[]>([])
const chapterById = computed(() => new Map(props.chapters.map((chapter) => [chapter.chapterId, chapter])))
const hasChapter = (chapterId: string) => chapterById.value.has(chapterId)
const chapterLabel = (chapterId: string) => { const chapter = chapterById.value.get(chapterId); return chapter ? `第 ${chapter.displayNo} 章 · ${chapter.title}` : '未解析章节' }
const displayValue = (value: unknown) => typeof value === 'string' ? value : JSON.stringify(value)
const effectiveEntries = computed(() => Object.entries(props.effectiveState || {}).map(([key, value]) => ({ key, value: displayValue(value) })))
watch(() => props.chapterId, (value) => { if (value) anchorChapterId.value = value })

const addChange = (target: EditableChange[]) => target.push({ id: ++changeId, op: 'replace', key: '', value: '' })
const removeChange = (target: EditableChange[], id: number) => { const index = target.findIndex((item) => item.id === id); if (index >= 0) target.splice(index, 1) }
const decodePath = (path: string) => path.replace(/^\//, '').split('/').map((part) => part.replace(/~1/g, '/').replace(/~0/g, '~')).join('.')
const encodePath = (key: string) => `/${key.split('.').map((part) => part.replace(/~/g, '~0').replace(/\//g, '~1')).join('/')}`
const parseValue = (value: string) => { try { return JSON.parse(value) } catch { return value } }
const parsePatch = (patchJson: string): EditableChange[] => {
  try {
    const patch = JSON.parse(patchJson) as Array<{ op?: string; path?: string; value?: unknown }>
    if (!Array.isArray(patch)) return []
    return patch.filter((item) => ['add', 'replace', 'remove'].includes(item.op || '') && item.path)
      .map((item) => ({ id: ++changeId, op: item.op as ChangeOperation, key: decodePath(item.path || ''), value: item.op === 'remove' ? '' : displayValue(item.value) }))
  } catch { return [] }
}
const serializePatch = (rows: EditableChange[]) => JSON.stringify(rows.filter((row) => row.key.trim()).map((row) => ({ op: row.op, path: encodePath(row.key.trim()), ...(row.op === 'remove' ? {} : { value: parseValue(row.value) }) })))
const describePatch = (patchJson: string) => { const rows = parsePatch(patchJson); if (!rows.length) return '没有属性变化'; const counts = { add: 0, replace: 0, remove: 0 }; rows.forEach((row) => { counts[row.op] += 1 }); return [`增加 ${counts.add}`, `修改 ${counts.replace}`, `删除 ${counts.remove}`].filter((_, index) => Object.values(counts)[index]).join(' · ') }
const submit = () => {
  emit('create', { anchorChapterId: anchorChapterId.value, endChapterId: endChapterId.value || null, storyEventNodeId: null, patchJson: serializePatch(changes.value), summary: summary.value })
  endChapterId.value = ''; summary.value = ''; changes.value = []
}
const startEdit = (item: StoryBibleProgression) => { editingProgressionId.value = item.progressionId; editAnchorChapterId.value = item.anchorChapterId; editEndChapterId.value = item.endChapterId || ''; editSummary.value = item.summary || ''; editChanges.value = parsePatch(item.patchJson) }
const cancelEdit = () => { editingProgressionId.value = '' }
const saveEdit = (item: StoryBibleProgression) => {
  emit('update', { progressionId: item.progressionId, update: { expectedRevision: item.revision, anchorChapterId: editAnchorChapterId.value, endChapterId: editEndChapterId.value || null, storyEventNodeId: item.storyEventNodeId || null, patchJson: serializePatch(editChanges.value), summary: editSummary.value } })
  cancelEdit()
}
</script>

<style scoped>
.progressions-tab { display: grid; gap: 16px; padding: 16px; }.effective-preview, .change-editor { background: var(--bg-surface); border: 1px solid var(--border-subtle); border-radius: 4px; }.effective-preview > header, .change-editor > header { display: flex; min-height: 38px; align-items: center; justify-content: space-between; gap: 10px; padding: 0 10px; border-bottom: 1px solid var(--border-subtle); }.effective-preview strong, .change-editor header > span { font-size: 12px; }.effective-preview header span { color: var(--text-muted); font-size: 11px; }.state-list { display: grid; }.state-row { display: grid; grid-template-columns: minmax(100px, .4fr) 1fr; gap: 12px; padding: 8px 10px; border-bottom: 1px solid var(--border-subtle); font-size: 12px; }.state-row:last-child { border: 0; }.state-row span { color: var(--text-secondary); overflow-wrap: anywhere; }
.progression-form { display: grid; grid-template-columns: 1fr 1fr; gap: 9px; }.progression-form label { display: grid; gap: 4px; color: var(--text-secondary); font-size: 11px; }.wide { grid-column: 1 / -1; }input, select, button { min-width: 0; height: 34px; color: var(--text-primary); background: var(--bg-surface); border: 1px solid var(--border-strong); border-radius: 4px; }input, select { padding: 0 8px; }.change-editor header button { display: inline-flex; align-items: center; gap: 4px; padding: 0 8px; color: var(--accent); background: transparent; border: 0; }.change-row { display: grid; grid-template-columns: 84px minmax(100px, .7fr) minmax(120px, 1fr) 32px; gap: 6px; padding: 7px 8px; border-bottom: 1px solid var(--border-subtle); }.change-editor > p { padding: 14px; color: var(--text-muted); font-size: 11px; }.icon-button { width: 32px; padding: 0; background: transparent; border: 0; }.danger { color: var(--danger); }.add-progression { width: max-content; padding: 0 10px; color: var(--accent); cursor: pointer; }
.progression-row { display: grid; grid-template-columns: minmax(180px, .8fr) minmax(0, 1fr) 68px; min-height: 70px; align-items: center; gap: 12px; border-bottom: 1px solid var(--border-subtle); }.anchor { display: flex; align-items: center; flex-wrap: wrap; gap: 5px; color: var(--accent); font-size: 11px; }.progression-copy { display: grid; gap: 4px; min-width: 0; }.progression-copy strong { font-size: 12px; }.progression-copy small { color: var(--text-muted); }.progression-edit { grid-column: 1 / 3; display: grid; grid-template-columns: 1fr 1fr; gap: 6px; padding: 8px 0; }.progression-edit > input, .edit-changes { grid-column: 1 / -1; }.row-actions { display: flex; gap: 2px; }.row-actions button { width: 32px; padding: 0; color: var(--accent); background: transparent; border: 0; }.empty-state { padding: 24px; color: var(--text-muted); text-align: center; }
@media (max-width: 680px) { .progression-form { grid-template-columns: 1fr; }.wide { grid-column: auto; }.change-row { grid-template-columns: 80px minmax(0, 1fr) 32px; }.change-row input:nth-of-type(2) { grid-column: 1 / 3; }.progression-row { grid-template-columns: minmax(0, 1fr) 68px; align-items: start; padding: 10px 0; }.anchor, .progression-copy, .progression-edit { grid-column: 1; }.row-actions { grid-column: 2; grid-row: 1 / span 2; }.progression-edit { grid-template-columns: 1fr; }.progression-edit > input, .edit-changes { grid-column: 1; } }
</style>
