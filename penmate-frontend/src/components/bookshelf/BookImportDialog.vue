<template>
  <div v-if="visible" class="import-layer" role="presentation" @mousedown.self="closeDialog">
    <section ref="dialogRef" class="import-dialog" role="dialog" aria-modal="true" aria-labelledby="import-title" tabindex="-1">
      <header>
        <div>
          <h2 id="import-title">从内容创建作品</h2>
          <p>{{ stageCaption }}</p>
        </div>
        <button type="button" class="icon-button" title="关闭" :disabled="running" @click="closeDialog"><CloseOutlined /></button>
      </header>

      <div v-if="!draft" class="source-stage">
        <div class="source-tabs" role="tablist" aria-label="导入来源">
          <button type="button" role="tab" :class="{ active: sourceMode === 'file' }" @click="sourceMode = 'file'"><FileOutlined />文件</button>
          <button type="button" role="tab" :class="{ active: sourceMode === 'paste' }" @click="sourceMode = 'paste'"><SnippetsOutlined />粘贴</button>
        </div>
        <input ref="fileInput" class="sr-only" type="file" accept=".txt,.md,.markdown,.docx,text/plain,text/markdown,application/vnd.openxmlformats-officedocument.wordprocessingml.document" aria-label="选择导入文件" @change="handleFileChange" />
        <button v-if="sourceMode === 'file'" type="button" class="file-drop" data-dialog-initial-focus :disabled="previewing" @click="fileInput?.click()" @dragover.prevent @drop.prevent="handleDrop">
          <UploadOutlined />
          <strong>{{ previewing ? '正在分析目录' : 'TXT、Markdown 或 DOCX' }}</strong>
          <span>最大 20 MB</span>
          <span class="file-command"><LoadingOutlined v-if="previewing" spin /><UploadOutlined v-else />{{ previewing ? '正在解析' : '选择文件' }}</span>
        </button>
        <div v-else class="paste-stage">
          <div ref="pasteEditor" class="paste-editor" contenteditable="true" role="textbox" aria-multiline="true" data-placeholder="在这里粘贴正文或带标题的富文本"></div>
          <button type="button" class="primary" :disabled="previewing" @click="previewPaste">
            <LoadingOutlined v-if="previewing" spin /><ImportOutlined v-else />分析粘贴内容
          </button>
        </div>
      </div>

      <div v-else-if="!session" class="review-stage">
        <div class="project-bar">
          <label for="import-project-title">作品名</label>
          <input id="import-project-title" v-model="draft.projectTitle" maxlength="200" />
          <span>{{ draft.volumes.length }} 卷 · {{ chapterCount }} 章</span>
        </div>
        <div class="review-grid">
          <aside class="directory-pane">
            <div class="pane-title"><strong>目录</strong><button type="button" title="添加卷" @click="addVolume"><FolderAddOutlined /></button></div>
            <div class="volume-list">
              <button v-for="(volume, index) in draft.volumes" :key="index" type="button" :class="{ selected: index === selectedVolumeIndex }" @click="selectVolume(index)">
                <span>{{ volume.title || '未命名卷' }}</span><small>{{ volume.chapters.length }}</small>
              </button>
            </div>
            <div v-if="selectedVolume" class="volume-editor">
              <input v-model="selectedVolume.title" maxlength="200" aria-label="卷名" />
              <div class="tool-row">
                <button type="button" title="上移卷" :disabled="selectedVolumeIndex === 0" @click="moveVolume(selectedVolumeIndex, -1)"><ArrowUpOutlined /></button>
                <button type="button" title="下移卷" :disabled="selectedVolumeIndex === draft.volumes.length - 1" @click="moveVolume(selectedVolumeIndex, 1)"><ArrowDownOutlined /></button>
                <button type="button" title="删除空卷" :disabled="draft.volumes.length <= 1 || selectedVolume.chapters.length > 0" @click="removeVolume(selectedVolumeIndex)"><DeleteOutlined /></button>
                <button type="button" title="添加章节" @click="addChapter"><FileAddOutlined /></button>
              </div>
            </div>
            <div class="chapter-list">
              <button v-for="item in visibleChapters" :key="item.index" type="button" :class="{ selected: item.index === selectedChapterIndex }" @click="selectChapter(item.index)">
                <span>{{ item.index + 1 }}</span><strong>{{ item.chapter.title || '未命名章节' }}</strong>
              </button>
            </div>
            <div v-if="pageCount > 1" class="pager">
              <button type="button" title="上一页" :disabled="chapterPage === 0" @click="chapterPage--"><LeftOutlined /></button>
              <span>{{ chapterPage + 1 }} / {{ pageCount }}</span>
              <button type="button" title="下一页" :disabled="chapterPage >= pageCount - 1" @click="chapterPage++"><RightOutlined /></button>
            </div>
          </aside>

          <main v-if="selectedChapter" class="content-pane">
            <div class="chapter-toolbar">
              <input v-model="selectedChapter.title" maxlength="200" aria-label="章节名" />
              <select :value="selectedVolumeIndex" aria-label="移动到其他卷" @change="moveChapterToVolume(Number(($event.target as HTMLSelectElement).value))">
                <option v-for="(volume, index) in draft.volumes" :key="index" :value="index">{{ volume.title }}</option>
              </select>
              <button type="button" title="上移章节" :disabled="selectedChapterIndex === 0" @click="moveChapterOrder(selectedChapterIndex, -1)"><ArrowUpOutlined /></button>
              <button type="button" title="下移章节" :disabled="selectedChapterIndex === selectedVolume!.chapters.length - 1" @click="moveChapterOrder(selectedChapterIndex, 1)"><ArrowDownOutlined /></button>
              <button type="button" title="合并到上一章" :disabled="selectedChapterIndex === 0" @click="mergePrevious"><MergeCellsOutlined /></button>
              <button type="button" title="在光标处拆分" @click="splitAtCursor"><SplitCellsOutlined /></button>
              <button type="button" title="删除章节" :disabled="selectedVolume!.chapters.length <= 1" @click="deleteChapter(selectedChapterIndex)"><DeleteOutlined /></button>
            </div>
            <textarea ref="contentEditor" v-model="selectedChapter.content" aria-label="章节正文"></textarea>
            <footer class="content-meta"><span>{{ selectedChapter.content.replace(/\s/g, '').length }} 字</span></footer>
          </main>
        </div>
        <details v-if="issues.length" class="issues"><summary>{{ issues.length }} 项需要留意</summary><p v-for="(issue, index) in issues" :key="index" :class="issue.severity">{{ issue.message }}</p></details>
      </div>

      <div v-else class="progress-stage">
        <Progress type="circle" :percent="progressPercent" :status="session.status === 'FAILED' ? 'exception' : session.status === 'COMPLETED' ? 'success' : 'active'" aria-label="导入进度" />
        <strong>{{ progressTitle }}</strong>
        <p>{{ session.progressMessage || `${session.checkpointChapter || 0} / ${session.totalChapters || chapterCount} 章` }}</p>
        <div class="progress-actions">
          <button v-if="running" type="button" class="secondary" @click="pause"><PauseOutlined />暂停</button>
          <button v-if="session.status === 'PAUSED'" type="button" class="primary" :disabled="session.jobStatus !== 'CANCELLED'" @click="resume"><CaretRightOutlined />继续</button>
          <button v-if="['FAILED', 'CANCELLED'].includes(session.status)" type="button" class="primary" @click="retry"><ReloadOutlined />重试</button>
          <button v-if="running || session.status === 'PAUSED'" type="button" class="danger" @click="cancel"><StopOutlined />取消导入</button>
        </div>
      </div>

      <p v-if="error || session?.errorMessage" class="import-error" role="alert">{{ error || session?.errorMessage }}</p>
      <footer v-if="draft && !session" class="dialog-footer">
        <button type="button" class="secondary" :disabled="submitting" @click="chooseAnotherFile">重新选择</button>
        <button type="button" class="primary" :disabled="!canConfirm" @click="confirmImport"><LoadingOutlined v-if="submitting" spin /><ImportOutlined v-else />{{ submitting ? '正在提交' : '开始导入' }}</button>
      </footer>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Progress } from 'ant-design-vue'
import { ArrowDownOutlined, ArrowUpOutlined, CaretRightOutlined, CloseOutlined, DeleteOutlined, FileAddOutlined, FileOutlined, FolderAddOutlined, ImportOutlined, LeftOutlined, LoadingOutlined, MergeCellsOutlined, PauseOutlined, ReloadOutlined, RightOutlined, SnippetsOutlined, SplitCellsOutlined, StopOutlined, UploadOutlined } from '@ant-design/icons-vue'
import { useNovelImport } from '@/features/bookshelf/useNovelImport'
import { useDialogFocus } from '@/composables/useDialogFocus'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{ close: []; imported: [projectId: string] }>()
const sourceMode = ref<'file' | 'paste'>('file')
const fileInput = ref<HTMLInputElement | null>(null)
const pasteEditor = ref<HTMLElement | null>(null)
const contentEditor = ref<HTMLTextAreaElement | null>(null)
const dialogRef = ref<HTMLElement | null>(null)
const state = useNovelImport()
const { draft, session, filename, previewing, submitting, error, selectedVolumeIndex, selectedChapterIndex, chapterPage, selectedVolume, selectedChapter, chapterCount, pageCount, visibleChapters, issues, canConfirm, running, progressPercent, reset, selectFile, selectPaste, selectVolume, selectChapter, addVolume, removeVolume, moveVolume, addChapter, deleteChapter, moveChapterOrder, moveChapterToVolume, splitChapter, mergePrevious, confirmImport, pause, resume, cancel, retry } = state

const stageCaption = computed(() => session.value ? '后台导入' : draft.value ? filename.value : '创建前先校对目录与正文')
const progressTitle = computed(() => {
  const titles: Partial<Record<string, string>> = { COMPLETED: '作品已创建', FAILED: '导入失败', CANCELLED: '导入已取消', PAUSED: '导入已暂停' }
  return titles[session.value?.status || ''] || '正在创建作品'
})
const closeDialog = () => { if (running.value) return; reset(); emit('close') }
const chooseAnotherFile = () => { reset(); if (fileInput.value) fileInput.value.value = '' }
const handleFileChange = async (event: Event) => { const file = (event.target as HTMLInputElement).files?.[0]; if (file) await selectFile(file) }
const handleDrop = async (event: DragEvent) => { const file = event.dataTransfer?.files?.[0]; if (file) await selectFile(file) }
const previewPaste = async () => { if (pasteEditor.value) await selectPaste(pasteEditor.value.innerHTML, pasteEditor.value.innerText) }
const splitAtCursor = () => { if (contentEditor.value) splitChapter(contentEditor.value.selectionStart) }

watch(() => session.value?.status, (status) => {
  const projectId = String(session.value?.projectId || '')
  if (status === 'COMPLETED' && projectId) { reset(); emit('imported', projectId) }
})
useDialogFocus({ open: () => props.visible, dialog: dialogRef, close: closeDialog, canClose: () => !running.value })
</script>

<style scoped>
.import-layer { position: fixed; inset: 0; z-index: 700; display: grid; place-items: center; padding: 20px; background: var(--overlay); }
.import-dialog { display: flex; width: min(1120px, 100%); height: min(820px, calc(100vh - 40px)); flex-direction: column; overflow: hidden; color: var(--text-primary); background: var(--bg-surface); border: 1px solid var(--border-strong); border-radius: 8px; box-shadow: var(--shadow-lg); }
.import-dialog > header, .dialog-footer, .project-bar, .pane-title, .tool-row, .chapter-toolbar, .content-meta, .progress-actions { display: flex; align-items: center; }
.import-dialog > header { min-height: 64px; justify-content: space-between; padding: 10px 16px; border-bottom: 1px solid var(--border-subtle); }.import-dialog h2 { font-size: 17px; }.import-dialog header p { margin-top: 2px; color: var(--text-muted); font-size: 11px; }
.icon-button, .tool-row button, .chapter-toolbar button, .pane-title button, .pager button { display: grid; width: 32px; height: 32px; place-items: center; color: var(--text-secondary); background: transparent; border: 0; border-radius: 4px; cursor: pointer; }
.source-stage { display: flex; min-height: 0; flex: 1; flex-direction: column; }.source-tabs { display: flex; padding: 10px 16px 0; border-bottom: 1px solid var(--border-subtle); }.source-tabs button { display: inline-flex; min-height: 38px; align-items: center; gap: 7px; padding: 0 14px; color: var(--text-secondary); background: transparent; border: 0; border-bottom: 2px solid transparent; cursor: pointer; }.source-tabs button.active { color: var(--accent); border-color: var(--accent); }
.file-drop { display: grid; width: 100%; flex: 1; place-content: center; justify-items: center; gap: 10px; color: var(--text-muted); background: transparent; border: 0; cursor: pointer; }.file-drop > :deep(.anticon) { font-size: 34px; }.file-drop strong { color: var(--text-primary); font-size: 16px; }.file-drop span { font-size: 12px; }.file-command { display: inline-flex; min-height: 36px; align-items: center; gap: 7px; padding: 0 13px; color: var(--text-inverse); background: var(--accent); border-radius: 4px; }
.paste-stage { display: flex; min-height: 0; flex: 1; flex-direction: column; gap: 12px; padding: 16px; }.paste-editor { min-height: 0; flex: 1; overflow: auto; padding: 14px; border: 1px solid var(--border-strong); outline: 0; white-space: pre-wrap; }.paste-editor:empty::before { color: var(--text-muted); content: attr(data-placeholder); }.paste-stage .primary { align-self: flex-end; }
.review-stage { display: flex; min-height: 0; flex: 1; flex-direction: column; }.project-bar { gap: 10px; padding: 10px 16px; border-bottom: 1px solid var(--border-subtle); }.project-bar label { font-size: 12px; font-weight: 650; }.project-bar input { flex: 1; }.project-bar span { color: var(--text-muted); font-size: 12px; }.review-grid { display: grid; min-height: 0; flex: 1; grid-template-columns: 330px 1fr; }.directory-pane { display: flex; min-height: 0; flex-direction: column; border-right: 1px solid var(--border-subtle); }.pane-title { min-height: 42px; justify-content: space-between; padding: 0 10px 0 14px; }.volume-list { display: flex; max-height: 150px; flex-direction: column; overflow: auto; border-block: 1px solid var(--border-subtle); }.volume-list > button { display: flex; min-height: 34px; align-items: center; justify-content: space-between; padding: 0 12px; color: var(--text-secondary); background: transparent; border: 0; text-align: left; cursor: pointer; }.volume-list > button.selected, .chapter-list > button.selected { color: var(--accent); background: var(--accent-soft); }.volume-list small { color: var(--text-muted); }.volume-editor { padding: 8px 10px; border-bottom: 1px solid var(--border-subtle); }.volume-editor input { width: 100%; }.tool-row { justify-content: flex-end; margin-top: 5px; }.chapter-list { min-height: 0; flex: 1; overflow: auto; }.chapter-list > button { display: grid; width: 100%; min-height: 36px; align-items: center; grid-template-columns: 30px 1fr; padding: 0 10px; color: var(--text-secondary); background: transparent; border: 0; text-align: left; cursor: pointer; }.chapter-list span { color: var(--text-muted); font-size: 10px; }.chapter-list strong { overflow: hidden; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }.pager { display: flex; min-height: 38px; align-items: center; justify-content: center; gap: 10px; border-top: 1px solid var(--border-subtle); font-size: 11px; }
.content-pane { display: flex; min-width: 0; min-height: 0; flex-direction: column; }.chapter-toolbar { gap: 4px; padding: 8px 10px; border-bottom: 1px solid var(--border-subtle); }.chapter-toolbar input { min-width: 120px; flex: 1; }.chapter-toolbar select { width: 150px; }.content-pane textarea { min-height: 0; flex: 1; resize: none; padding: 18px; color: var(--text-primary); background: var(--bg-surface); border: 0; outline: 0; font: 14px/1.85 ui-monospace, SFMono-Regular, Consolas, monospace; }.content-meta { min-height: 34px; justify-content: flex-end; padding: 0 14px; color: var(--text-muted); border-top: 1px solid var(--border-subtle); font-size: 11px; }.issues { max-height: 110px; overflow: auto; padding: 8px 16px; border-top: 1px solid var(--border-subtle); font-size: 11px; }.issues summary { cursor: pointer; }.issues p { margin-top: 5px; }.issues .error { color: var(--danger); }.issues .warning { color: var(--warning); }
.progress-stage { display: grid; flex: 1; place-content: center; justify-items: center; gap: 14px; text-align: center; }.progress-stage > strong { font-size: 18px; }.progress-stage > p { color: var(--text-muted); }.progress-actions { gap: 8px; }
input, select { min-height: 34px; padding: 0 9px; color: var(--text-primary); background: var(--bg-surface); border: 1px solid var(--border-strong); border-radius: 4px; outline: 0; } input:focus, select:focus, .paste-editor:focus { border-color: var(--accent); box-shadow: 0 0 0 2px var(--accent-soft); }
.primary, .secondary, .danger { display: inline-flex; min-height: 36px; align-items: center; justify-content: center; gap: 7px; padding: 0 13px; border-radius: 4px; cursor: pointer; }.primary { color: var(--text-inverse); background: var(--accent); border: 1px solid var(--accent); }.secondary { color: var(--text-primary); background: var(--bg-surface); border: 1px solid var(--border-strong); }.danger { color: var(--danger); background: var(--bg-surface); border: 1px solid var(--danger-border); }.dialog-footer { min-height: 58px; justify-content: flex-end; gap: 8px; padding: 10px 16px; border-top: 1px solid var(--border-subtle); }.import-error { margin: 0; padding: 8px 16px; color: var(--danger); background: var(--danger-soft); border-top: 1px solid var(--danger-border); font-size: 12px; }button:disabled { opacity: .45; cursor: not-allowed; }
@media (max-width: 760px) { .import-layer { padding: 0; }.import-dialog { width: 100%; height: 100%; border: 0; }.review-grid { grid-template-columns: 42% 58%; }.chapter-toolbar { flex-wrap: wrap; }.chapter-toolbar input { width: 100%; flex: none; }.chapter-toolbar select { flex: 1; }.project-bar { align-items: stretch; flex-wrap: wrap; }.project-bar label { width: 100%; } }
</style>
