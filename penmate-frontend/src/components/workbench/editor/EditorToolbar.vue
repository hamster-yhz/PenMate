<script setup lang="ts">
import { computed } from 'vue'

type ChapterVersionItem = {
  chapterVersionId?: number | string
  versionNo?: number | string
  changeReason?: string
  changeType?: string
}

const props = withDefaults(defineProps<{
  currentChapterTitle: string
  selectedVersionNo: string
  versionBusy: boolean
  activeChapter: string
  versions: ChapterVersionItem[]
}>(), {
  currentChapterTitle: '',
  selectedVersionNo: '',
  versionBusy: false,
  activeChapter: '',
  versions: () => [],
})

const emit = defineEmits<{
  (e: 'save'): void
  (e: 'undo'): void
  (e: 'redo'): void
  (e: 'wrap-selection', before: string, after: string): void
  (e: 'insert-prefix', prefix: string): void
  (e: 'update:selectedVersionNo', value: string): void
  (e: 'restore-version'): void
  (e: 'view-version'): void
  (e: 'publish-chapter'): void
}>()

const versionSelectDisabled = computed(() => props.versionBusy || props.versions.length === 0)
const versionActionDisabled = computed(() => props.versionBusy || !props.selectedVersionNo)
const publishDisabled = computed(() => props.versionBusy || !props.activeChapter)

const onVersionChange = (event: Event) => {
  emit('update:selectedVersionNo', (event.target as HTMLSelectElement).value)
}
</script>

<template>
  <div class="editor-toolbar">
    <div class="toolbar-left">
      <button data-testid="toolbar-save" class="toolbar-btn" type="button" @click="emit('save')">💾</button>
      <button data-testid="toolbar-undo" class="toolbar-btn" type="button" @click="emit('undo')">↩️</button>
      <button data-testid="toolbar-redo" class="toolbar-btn" type="button" @click="emit('redo')">↪️</button>
      <button data-testid="toolbar-bold" class="toolbar-btn toolbar-btn-text" type="button" @click="emit('wrap-selection', '**', '**')">B</button>
      <button data-testid="toolbar-italic" class="toolbar-btn toolbar-btn-text" type="button" @click="emit('wrap-selection', '*', '*')">I</button>
      <button data-testid="toolbar-quote" class="toolbar-btn" type="button" @click="emit('insert-prefix', '> ')">❝</button>
    </div>

    <div class="toolbar-right">
      <span data-testid="chapter-label" class="toolbar-chapter">{{ currentChapterTitle }}</span>
      <select
        data-testid="version-select"
        class="toolbar-select"
        :value="selectedVersionNo"
        :disabled="versionSelectDisabled"
        @change="onVersionChange"
      >
        <option value="">版本记录</option>
        <option
          v-for="version in versions"
          :key="String(version.chapterVersionId ?? version.versionNo ?? '')"
          :value="String(version.versionNo ?? '')"
        >
          v{{ version.versionNo ?? '-' }} · {{ String(version.changeReason ?? version.changeType ?? '无备注') }}
        </option>
      </select>
      <button
        data-testid="toolbar-restore-version"
        class="toolbar-btn toolbar-action"
        type="button"
        :disabled="versionActionDisabled"
        @click="emit('restore-version')"
      >
        恢复版本
      </button>
      <button
        data-testid="toolbar-view-version"
        class="toolbar-btn toolbar-action"
        type="button"
        :disabled="versionActionDisabled"
        @click="emit('view-version')"
      >
        查看版本
      </button>
      <button
        data-testid="toolbar-publish-chapter"
        class="toolbar-btn toolbar-action toolbar-action-primary"
        type="button"
        :disabled="publishDisabled"
        @click="emit('publish-chapter')"
      >
        发布章节
      </button>
    </div>
  </div>
</template>

<style scoped lang="less">
.editor-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--border-subtle);
  background: linear-gradient(180deg, rgba(17, 24, 39, 0.9), rgba(11, 17, 32, 0.68));
  box-shadow: inset 0 -1px 0 rgba(201, 169, 110, 0.04);
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.toolbar-right {
  justify-content: flex-end;
}

.toolbar-btn,
.toolbar-select {
  min-height: 38px;
  border: 1px solid var(--border-subtle);
  border-radius: 10px;
  background: rgba(11, 17, 32, 0.56);
  color: var(--text-secondary);
  transition: all 0.25s var(--ease-silk);
}

.toolbar-btn {
  min-width: 38px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 12px;
  cursor: pointer;
}

.toolbar-btn:hover:not(:disabled),
.toolbar-select:hover:not(:disabled) {
  color: var(--amber-gold);
  border-color: var(--border-gold);
  box-shadow: var(--shadow-gold);
  transform: translateY(-1px);
}

.toolbar-btn:disabled,
.toolbar-select:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.toolbar-btn-text {
  font-family: var(--font-heading);
  font-weight: 600;
}

.toolbar-action {
  padding: 0 14px;
  font-size: 0.8rem;
  letter-spacing: 0.06em;
}

.toolbar-action-primary {
  color: var(--amber-gold);
  background: linear-gradient(135deg, rgba(201, 169, 110, 0.14), rgba(201, 169, 110, 0.05));
  border-color: var(--border-gold);
}

.toolbar-chapter {
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: var(--font-heading);
  font-size: 0.88rem;
  letter-spacing: 0.08em;
  color: var(--amber-gold);
}

.toolbar-select {
  min-width: 180px;
  padding: 0 12px;
  outline: none;
}

@media (max-width: 1280px) {
  .editor-toolbar {
    padding: 12px 14px;
  }

  .toolbar-chapter {
    max-width: 180px;
  }
}
</style>
