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
      <button data-testid="toolbar-save" type="button" @click="emit('save')">💾</button>
      <button data-testid="toolbar-undo" type="button" @click="emit('undo')">↩️</button>
      <button data-testid="toolbar-redo" type="button" @click="emit('redo')">↪️</button>
      <button data-testid="toolbar-bold" type="button" @click="emit('wrap-selection', '**', '**')">B</button>
      <button data-testid="toolbar-italic" type="button" @click="emit('wrap-selection', '*', '*')">I</button>
      <button data-testid="toolbar-quote" type="button" @click="emit('insert-prefix', '> ')">❝</button>
    </div>

    <div class="toolbar-right">
      <span data-testid="chapter-label">{{ currentChapterTitle }}</span>
      <select
        data-testid="version-select"
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
        type="button"
        :disabled="versionActionDisabled"
        @click="emit('restore-version')"
      >
        恢复版本
      </button>
      <button
        data-testid="toolbar-view-version"
        type="button"
        :disabled="versionActionDisabled"
        @click="emit('view-version')"
      >
        查看版本
      </button>
      <button
        data-testid="toolbar-publish-chapter"
        type="button"
        :disabled="publishDisabled"
        @click="emit('publish-chapter')"
      >
        发布章节
      </button>
    </div>
  </div>
</template>
