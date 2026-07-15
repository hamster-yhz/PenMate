<template>
  <div class="progressions-tab">
    <section class="effective-preview">
      <header>
        <strong>当前章节有效状态</strong>
        <span v-if="chapterId">章节 {{ chapterId }}</span>
      </header>
      <pre>{{ formattedEffectiveState }}</pre>
    </section>

    <form class="progression-form" @submit.prevent="submit">
      <label><span>起始章节</span><input v-model="anchorChapterId" required /></label>
      <label><span>结束章节</span><input v-model="endChapterId" /></label>
      <label class="wide"><span>变化摘要</span><input v-model="summary" /></label>
      <label class="wide"><span>RFC 6902 Patch</span><textarea v-model="patchJson" rows="5" required></textarea></label>
      <button type="submit"><PlusOutlined /> 添加状态演进</button>
    </form>

    <div class="progression-list">
      <div v-for="item in progressions" :key="item.progressionId" class="progression-row">
        <div class="anchor"><span>{{ item.anchorChapterId }}</span><ArrowRightOutlined /><span>{{ item.endChapterId || '持续' }}</span></div>
        <div class="progression-copy"><strong>{{ item.summary || '状态演进' }}</strong><code>{{ item.patchJson }}</code></div>
        <button type="button" title="删除状态演进" @click="emit('delete', item)"><DeleteOutlined /></button>
      </div>
      <div v-if="!progressions.length" class="empty-state">当前节点尚无状态演进</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ArrowRightOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons-vue'
import type { StoryBibleProgression } from '@/api/modules/storyBible.api'

const props = defineProps<{ chapterId?: string; progressions: StoryBibleProgression[]; effectiveState: Record<string, unknown> | null }>()
const emit = defineEmits<{
  (event: 'create', payload: Omit<StoryBibleProgression, 'progressionId' | 'storyBibleId' | 'nodeId' | 'revision'>): void
  (event: 'delete', payload: StoryBibleProgression): void
}>()
const anchorChapterId = ref(props.chapterId || '')
const endChapterId = ref('')
const summary = ref('')
const patchJson = ref('[\n  { "op": "replace", "path": "/summary", "value": "" }\n]')
watch(() => props.chapterId, (value) => { if (value) anchorChapterId.value = value })
const formattedEffectiveState = computed(() => props.effectiveState ? JSON.stringify(props.effectiveState, null, 2) : '选择章节后显示有效状态')
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
</script>

<style scoped lang="less">
.progressions-tab { display: grid; gap: 16px; padding: 16px; }
.effective-preview { border: 1px solid var(--border-subtle); border-radius: 4px; background: rgba(11, 17, 32, 0.62); }
.effective-preview header { height: 38px; display: flex; align-items: center; justify-content: space-between; padding: 0 10px; border-bottom: 1px solid var(--border-subtle); }
.effective-preview strong { color: var(--amber-gold); font-size: 0.76rem; }
.effective-preview span { color: var(--text-muted); font-size: 0.68rem; }
pre { max-height: 220px; margin: 0; overflow: auto; padding: 12px; color: #b9c9d8; font-size: 0.72rem; white-space: pre-wrap; }
.progression-form { display: grid; grid-template-columns: 1fr 1fr; gap: 9px; }
label { display: grid; gap: 4px; color: var(--text-secondary); font-size: 0.7rem; }
.wide { grid-column: 1 / -1; }
input, textarea, button { min-width: 0; border: 1px solid var(--border-subtle); border-radius: 4px; color: var(--text-primary); background: rgba(11, 17, 32, 0.7); }
input, button { height: 34px; padding: 0 8px; }
textarea { padding: 8px; resize: vertical; }
.progression-form button { width: max-content; color: var(--amber-gold); border-color: var(--border-gold); cursor: pointer; }
.progression-row { min-height: 70px; display: grid; grid-template-columns: 120px minmax(0, 1fr) 32px; align-items: center; gap: 12px; border-bottom: 1px solid var(--border-subtle); }
.anchor { display: flex; align-items: center; gap: 5px; color: var(--amber-gold); font-size: 0.7rem; }
.progression-copy { min-width: 0; display: grid; gap: 4px; }
.progression-copy strong { font-size: 0.78rem; }
.progression-copy code { overflow: hidden; color: var(--text-muted); font-size: 0.68rem; text-overflow: ellipsis; white-space: nowrap; }
.progression-row button { width: 32px; padding: 0; color: #c9827b; border-color: transparent; background: transparent; cursor: pointer; }
.empty-state { padding: 24px; text-align: center; color: var(--text-muted); }
</style>
